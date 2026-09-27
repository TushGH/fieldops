# FieldOps

FieldOps is a planned multi-tenant SaaS platform for field-service businesses: HVAC companies, plumbers, electricians, appliance repair companies, and similar service organizations. Each business is a tenant with its own people, customers, and operational records.

The project is being developed as a realistic startup proof of concept and documented through a YouTube series. Engineering decisions should be understandable and production-minded; educational value does not justify weakening correctness or adding technology without a concrete need.

## Business problem

Small service businesses often coordinate customer requests, technician schedules, job progress, and invoices across phone calls, spreadsheets, and disconnected tools. This creates missed appointments, duplicate data entry, unclear job ownership, and delayed payment. FieldOps aims to connect the workflow from service request through completed work and payment tracking.

## Target users and capabilities

- **Business owners:** oversee operations, manage staff access, and track outstanding work and invoices.
- **Dispatchers:** maintain customer records, create work orders, and coordinate technician assignments and appointments.
- **Technicians:** view assigned work and record progress and completion from a responsive web interface.
- **Customers:** receive service updates and invoice information; a self-service portal is a future opportunity.
- **Platform administrators:** manage tenant onboarding and platform operations through explicitly authorized access.

Planned capabilities include tenant isolation, role-based access, customer and technician management, work-order lifecycles, scheduling, invoicing, payment tracking, and notifications. These capabilities are not implemented yet.

## Current status

**Phase 1: Application Foundation.** The repository now has a working Spring Boot backend, Next.js development page, PostgreSQL Compose service, Flyway migration pipeline, and GitHub Actions validation. There are no business entities, authentication, tenant isolation, or business workflows yet. The application is a local development foundation, not a production release.

[PRODUCT.md](docs/product/PRODUCT.md) defines the planned product. [ROADMAP.md](docs/ROADMAP.md) distinguishes completed foundation work from the next identity and multi-tenancy phase.

## Technology stack

| Area | Implemented foundation |
| --- | --- |
| Backend | Java 25, Spring Boot 4.0.8, Maven 3.9.16 via wrapper; Spring MVC, Validation, Data JPA, Actuator |
| Data | PostgreSQL 17.9, Flyway; Hibernate schema validation |
| Frontend | Next.js 16.3.6, React, TypeScript, Tailwind CSS 4, Node.js 24, npm lockfile |
| Testing | JUnit 5.14.4, Testcontainers, AssertJ; Node test runner for the health proxy |
| Quality | ESLint, TypeScript checks, backend and frontend builds, GitHub Actions |
| Local infrastructure | Docker Compose runs PostgreSQL; applications run on the host |

Spring Security, richer frontend libraries, and business modules from the planned stack will be introduced when their features need them. Next.js uses its supported Webpack compiler for development and production builds because Turbopack's worker port binding failed in the initial development environment. ESLint 9 is retained to match the peer dependency range of Next.js's React/import/accessibility plugins; revisit this when those plugins support ESLint 10.

## Repository layout

```text
.github/workflows/ci.yml    Backend and frontend validation
apps/api/                  Spring Boot application and Maven wrapper
apps/web/                  Next.js development page and health proxy
docker-compose.yml         Local PostgreSQL service
.env.example               Shared local database/backend configuration example
services/                  Reserved; no independent services
infrastructure/            Reserved directories; no extra infrastructure deployed
scripts/                   Reserved for future useful scripts
docs/product/              Product vision and scope
docs/architecture/          Architecture and health endpoint OpenAPI contract
docs/adr/                   Architecture decision records
docs/ROADMAP.md             Milestones and learning objectives
```

Reserved empty directories have no placeholder files. Git does not track them until real files are added.

## Local development

### Prerequisites

- A JDK **25**, with `JAVA_HOME` pointing to it and its `bin` directory on `PATH`.
- **Node.js 24** and npm. With nvm installed, run `nvm install` and `nvm use` from `apps/web`.
- Docker with Compose and a running Docker daemon (for example, Docker Desktop).
- Git; OpenSSL for the password-generation command below.

Check `java -version`, `node --version`, and `docker compose version`. No global Maven installation is needed; the wrapper downloads Maven on first use. Dependency downloads and Testcontainers' first image pull need internet access.

The commands below use a POSIX shell (bash/zsh).

### 1. Clone and configure

```sh
git clone https://github.com/TushGH/fieldops.git
cd fieldops
cp .env.example .env
cp apps/web/.env.example apps/web/.env.local
openssl rand -hex 24
```

Paste the generated value into `POSTGRES_PASSWORD` in `.env`. The example intentionally has no password, and Compose refuses to start until you set one. Use a hexadecimal value to keep shell sourcing straightforward. Both `.env` and `.env.local` are ignored; never commit credentials.

### 2. Start PostgreSQL

From the repository root:

```sh
docker compose up -d --wait
docker compose ps
```

PostgreSQL is published only on `127.0.0.1:5432`. Its data survives container restarts in the `fieldops_postgres-data` volume. Compose waits for database readiness before returning.

### 3. Start the backend

In a terminal at the repository root:

```sh
set -a
. ./.env
set +a
cd apps/api
./mvnw spring-boot:run
```

Compose reads `.env` automatically, but Spring Boot does not; sourcing it exports the backend settings. Run these commands again in each new backend terminal. The backend defaults to `127.0.0.1:8080`.

On startup, Flyway executes `V1__verify_migration_pipeline.sql` and records version 1 in `flyway_schema_history`. The migration runs `SELECT 1`; it intentionally creates no business tables. Hibernate uses `ddl-auto=validate` and does not modify the schema.

### 4. Start the frontend

In another terminal at the repository root:

```sh
cd apps/web
npm ci
npm run dev
```

Open [http://127.0.0.1:3000](http://127.0.0.1:3000). The development page should report **API connected**. Use **Check connection** to retry. Next.js reads `apps/web/.env.local` automatically.

### 5. Verify the application

```sh
curl --fail http://127.0.0.1:8080/api/v1/health
curl --fail http://127.0.0.1:8080/actuator/health
curl --fail http://127.0.0.1:3000/api/health
```

The application endpoint and frontend proxy return:

```json
{"application":"fieldops-api","status":"UP"}
```

Actuator returns `{"status":"UP"}` when aggregate health, including PostgreSQL, is healthy. The application endpoint only proves application reachability; it does not continuously check the database. The contract is recorded in [openapi.yaml](docs/architecture/openapi.yaml).

The browser requests `/api/health` from Next.js. The server forwards to the fixed backend health path using server-only `API_BASE_URL`, with no caching and a five-second timeout. Failed or malformed backend responses become HTTP 503 with `{"status":"UNAVAILABLE"}`. No CORS policy or public backend URL is needed for this flow.

### Configuration and troubleshooting

| Variable | Location | Purpose |
| --- | --- | --- |
| `POSTGRES_DB` | Root `.env` | Database created by Compose; default `fieldops` |
| `POSTGRES_USER` | Root `.env` | Database login; default `fieldops` |
| `POSTGRES_PASSWORD` | Root `.env` | Required local password, with no committed default |
| `POSTGRES_PORT` | Root `.env` | Host database port; default `5432` |
| `DATABASE_URL` | Root `.env` | Backend JDBC URL; align database name and port with Compose |
| `API_PORT` | Root `.env` | Backend port; default `8080` |
| `API_BASE_URL` | `apps/web/.env.local` | Next.js server's backend URL; default `http://127.0.0.1:8080` |

If port 5432 is occupied, set `POSTGRES_PORT=55432` and `DATABASE_URL=jdbc:postgresql://localhost:55432/fieldops` in `.env`, then rerun Compose and restart the backend with the new environment. If changing `API_PORT`, update the frontend's `API_BASE_URL` and restart it. Set `PORT=3001 npm run dev` if the web port is occupied.

Database initialization variables apply only to a new PostgreSQL data volume. Changing the password or database name in `.env` does not update an existing database; apply the corresponding database administration change first. Avoid deleting a volume to fix configuration unless you explicitly intend to discard its data.

If the page says API unavailable, check the backend logs and direct health URL first. If Maven reports an unsupported release, check `./mvnw --version` uses Java 25. If Testcontainers cannot connect, confirm `docker info` succeeds; tests require Docker and do not silently skip when it is unavailable.

Stop the frontend and backend with Ctrl+C. Stop the database from the repository root with `docker compose down`; this retains its data volume.

## Tests and builds

From `apps/api`, with Java 25 and Docker running:

```sh
./mvnw --batch-mode --no-transfer-progress verify
```

This compiles the application, runs three JUnit 5 integration tests, and builds `target/fieldops-api-0.1.0-SNAPSHOT.jar`. Tests create an isolated PostgreSQL container with ephemeral credentials and a random port, prove migration execution/connectivity and real HTTP responses, then close the application and container. They do not require `.env` or the Compose database.

Spring Boot 4's Spring test extension expects JUnit 6 APIs. To retain the explicitly requested JUnit 5, tests use JUnit lifecycle methods to start and close the real `SpringApplication` directly. No framework dependency is downgraded.

From `apps/web`:

```sh
npm ci
npm run lint
npm run typecheck
npm test
npm run build
```

`typecheck` generates Next.js route types before checking TypeScript, so it works on a fresh checkout. Six proxy tests cover success, invalid responses, HTTP failure, connection failure, and timeout handling. The frontend build does not require a running backend. To run the production build locally, use `npm start` after `npm run build`.

GitHub Actions runs these backend/frontend checks independently on pushes and pull requests. Backend tests use the runner's Docker daemon through Testcontainers. There is no deployment job. Local equivalents have been executed; the hosted workflow will run once the repository is pushed to GitHub.

## Architecture philosophy

Start with a Next.js frontend, one Spring Boot modular monolith, and one shared PostgreSQL database. Organize backend code by business domain, with explicit module ownership and synchronous interactions initially. Modules are internal boundaries in one application, not separately deployed services.

Tenant isolation and backend authorization are correctness requirements. Use Flyway migrations and database constraints, test important domain behavior against PostgreSQL, and document meaningful tradeoffs. Add infrastructure only when a measured or demonstrated requirement warrants its operational cost.

See [architecture v1](docs/architecture/architecture-v1.md), [ADR 0001: modular monolith](docs/adr/0001-use-modular-monolith.md), and [ADR 0002: PostgreSQL](docs/adr/0002-use-postgresql.md).
