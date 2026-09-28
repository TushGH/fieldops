#!/usr/bin/env bash
# Run the local stack from any working directory. Requires Bash 3.2 or newer.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
CHECK_ONLY=false
case "${1:-}" in
  '') ;;
  --check) CHECK_ONLY=true ;;
  --help|-h)
    echo "Usage: ./scripts/start-local.sh [--check]"
    echo "Starts PostgreSQL, API, and web. Ctrl+C stops API/web; PostgreSQL and its data remain."
    echo "--check validates tools, configuration, Docker, and application ports without starting services."
    exit 0 ;;
  *) echo "Unknown argument: $1" >&2; exit 1 ;;
esac

fail() { echo "Error: $*" >&2; exit 1; }
command -v docker >/dev/null || fail "Install Docker and start Docker Desktop."
command -v curl >/dev/null || fail "curl is required for readiness checks."
[[ -f "$ROOT/.env" ]] || fail "Copy .env.example to .env and set POSTGRES_PASSWORD first."
# The repository's local .env is a trusted shell-compatible configuration file.
set -a
source "$ROOT/.env"
set +a
[[ -n "${POSTGRES_PASSWORD:-}" ]] || fail "Set POSTGRES_PASSWORD in .env."

java_version() { java -version 2>&1 | head -n 1; }
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if ! java_version | grep -Eq 'version "25[.\"]'; then
  # Prefer installed macOS Java, then the toolchain used during project setup.
  candidate=$(/usr/libexec/java_home -v 25 2>/dev/null || true)
  for candidate in "$candidate" /private/tmp/fieldops-toolchain/jdk-25*/Contents/Home; do
    if [[ -x "$candidate/bin/java" ]]; then
      export JAVA_HOME="$candidate" PATH="$candidate/bin:$PATH"
      break
    fi
  done
fi
java_version | grep -Eq 'version "25[.\"]' || fail "Java 25 is required. Set JAVA_HOME to your JDK 25 installation."

if ! command -v node >/dev/null || [[ "$(node --version)" != v24.* ]]; then
  for candidate in /opt/homebrew/opt/node@24/bin /private/tmp/fieldops-toolchain/node-v24*/bin; do
    if [[ -x "$candidate/node" ]]; then export PATH="$candidate:$PATH"; break; fi
  done
fi
command -v node >/dev/null && [[ "$(node --version)" == v24.* ]] || fail "Node.js 24 is required. Activate it before running this script."
command -v npm >/dev/null || fail "npm is required."
docker info >/dev/null 2>&1 || fail "Docker is unavailable. Start Docker Desktop and try again."
docker compose version >/dev/null 2>&1 || fail "Docker Compose is required."

export API_PORT="${API_PORT:-8080}"
export PORT="${WEB_PORT:-3000}"
export API_BASE_URL="http://127.0.0.1:$API_PORT"
export WEB_ORIGIN="http://127.0.0.1:$PORT"
export API_BIND_ADDRESS=127.0.0.1
# The script deliberately launches local HTTP applications.
export SESSION_COOKIE_SECURE=false
node <<'JS'
const net = require('node:net');
(async () => {
  const ports = [process.env.API_PORT, process.env.PORT];
  if (ports[0] === ports[1]) throw new Error('API_PORT and WEB_PORT must differ.');
  for (const raw of ports) {
    const port = Number(raw);
    if (!/^\d+$/.test(raw) || port < 1 || port > 65535) throw new Error(`Invalid port: ${raw}`);
    await new Promise((resolve, reject) => {
      const server = net.createServer();
      server.once('error', () => reject(new Error(`Port ${port} is unavailable. Stop its process or change API_PORT/WEB_PORT.`)));
      server.listen(port, '127.0.0.1', () => server.close(resolve));
    });
  }
})().catch(error => { console.error(`Error: ${error.message}`); process.exit(1); });
JS
if [[ "$CHECK_ONLY" == true ]]; then
  echo "Local startup checks passed: Java 25, Node 24, Docker, configuration, and ports."
  exit 0
fi

cd "$ROOT"
mkdir -p logs
docker compose up -d --wait postgres
(cd apps/web && npm ci)

# Separate process groups let cleanup stop Maven/Next and their child processes,
# without touching an independently running application or the database container.
set -m
api_pid=''
web_pid=''
cleanup() {
  trap - EXIT INT TERM
  for pid in "$api_pid" "$web_pid"; do
    if [[ -n "$pid" ]]; then kill -TERM -- "-$pid" 2>/dev/null || true; fi
  done
  # Give Spring and Next time to exit gracefully, then stop any remaining children.
  for attempt in 1 2 3 4 5; do
    local alive=false
    for pid in "$api_pid" "$web_pid"; do
      if [[ -n "$pid" ]] && kill -0 -- "-$pid" 2>/dev/null; then alive=true; fi
    done
    [[ "$alive" == true ]] || break
    sleep 1
  done
  for pid in "$api_pid" "$web_pid"; do
    if [[ -n "$pid" ]]; then kill -KILL -- "-$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true; fi
  done
  echo "API and frontend stopped. PostgreSQL remains running; stop it with: docker compose stop postgres"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

(cd apps/api && exec ./mvnw spring-boot:run) > logs/local-api.log 2>&1 &
api_pid=$!
(cd apps/web && exec npm run dev -- --port "$PORT") > logs/local-web.log 2>&1 &
web_pid=$!
echo "Starting applications. Logs: $ROOT/logs/local-api.log and $ROOT/logs/local-web.log"
ready=false
for ((attempt = 0; attempt < 180; attempt++)); do
  kill -0 "$api_pid" 2>/dev/null || fail "Backend exited. See logs/local-api.log."
  kill -0 "$web_pid" 2>/dev/null || fail "Frontend exited. See logs/local-web.log."
  if curl --fail --silent --max-time 2 "$API_BASE_URL/actuator/health" >/dev/null 2>&1 \
    && curl --fail --silent --max-time 2 "http://127.0.0.1:$PORT" >/dev/null 2>&1; then
    ready=true; break
  fi
  sleep 1
done
[[ "$ready" == true ]] || fail "Startup timed out. Inspect the application logs."
echo "FieldOps: http://127.0.0.1:$PORT"
echo "API: $API_BASE_URL"
echo "Email signup requires your separately configured SMTP server. Press Ctrl+C to stop the applications."
while kill -0 "$api_pid" 2>/dev/null && kill -0 "$web_pid" 2>/dev/null; do sleep 1; done
fail "An application exited. Inspect logs/local-api.log and logs/local-web.log."
