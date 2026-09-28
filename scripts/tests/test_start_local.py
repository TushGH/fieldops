"""Exercise startup orchestration without real Docker, Maven, npm, or application data."""
import os
from pathlib import Path
import shutil
import signal
import subprocess
import tempfile
import time
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "start-local.sh"


class LocalStartupTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for directory in ("scripts", "apps/api", "apps/web", "bin"):
            (self.root / directory).mkdir(parents=True)
        shutil.copy(SCRIPT, self.root / "scripts/start-local.sh")
        (self.root / ".env").write_text("POSTGRES_PASSWORD=ephemeral-test-only\n")
        self.env = {**os.environ, "PATH": f"{self.root}/bin:{os.environ['PATH']}", "JAVA_HOME": str(self.root), "TEST_ROOT": str(self.root)}
        self.command("java", 'echo \'openjdk version "25.0.1"\' >&2')
        self.command("node", 'if [ "${1:-}" = --version ]; then echo v24.1.0; else cat >/dev/null; fi')
        self.command("docker", 'echo "$*" >> "$TEST_ROOT/docker-calls"')
        self.command("curl", 'exit 0')
        application = 'sleep 300 &\necho $! >> "$TEST_ROOT/children"\nwait'
        self.command("npm", 'if [ "$1" = ci ]; then exit 0; fi\n' + application)
        self.command("../apps/api/mvnw", application)

    def command(self, name, body):
        path = self.root / "bin" / name
        path.write_text("#!/usr/bin/env bash\n" + body + "\n")
        path.chmod(0o755)

    def run_script(self, *args):
        return subprocess.run(["bash", str(self.root / "scripts/start-local.sh"), *args], env=self.env, capture_output=True, text=True, timeout=15)

    def test_check_only_does_not_start_services(self):
        result = self.run_script("--check")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("checks passed", result.stdout)
        self.assertNotIn("up", (self.root / "docker-calls").read_text())
        self.assertFalse((self.root / "children").exists())

    def test_missing_configuration_fails_without_starting_services(self):
        (self.root / ".env").unlink()
        result = self.run_script()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Copy .env.example", result.stderr)
        self.assertFalse((self.root / "docker-calls").exists())

    def test_child_failure_is_reported_and_other_application_stops(self):
        self.command("../apps/api/mvnw", "exit 1")
        result = self.run_script()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("exited", result.stderr)
        self.assertIn("PostgreSQL remains running", result.stdout)

    def test_termination_stops_application_process_groups_and_keeps_database(self):
        process = subprocess.Popen(["bash", str(self.root / "scripts/start-local.sh")], env=self.env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        self.addCleanup(lambda: process.kill() if process.poll() is None else None)
        deadline = time.monotonic() + 10
        children = self.root / "children"
        while time.monotonic() < deadline:
            if children.exists() and len(children.read_text().splitlines()) == 2:
                break
            time.sleep(0.05)
        self.assertTrue(children.exists(), "Applications did not start")
        process.send_signal(signal.SIGTERM)
        stdout, stderr = process.communicate(timeout=15)
        self.assertEqual(process.returncode, 143, stderr)
        self.assertIn("PostgreSQL remains running", stdout)
        calls = (self.root / "docker-calls").read_text()
        self.assertIn("compose up -d --wait postgres", calls)
        self.assertNotIn("down", calls)
        self.assertNotIn("stop", calls)
        for pid in children.read_text().splitlines():
            with self.assertRaises(ProcessLookupError):
                os.kill(int(pid), 0)


if __name__ == "__main__":
    unittest.main()
