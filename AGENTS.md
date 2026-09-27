# FieldOps Engineering Instructions

## Project purpose

FieldOps is a simulated production SaaS startup for field-service businesses such as HVAC companies, plumbers, electricians, appliance repair businesses, cleaning companies, and similar service organizations.

This repository is both:

1. A realistic startup proof of concept that could eventually become a real product.
2. An educational project used to demonstrate production software engineering patterns in a YouTube series.

The goal is NOT to add technologies simply because they are popular.

Every architectural component should solve a concrete problem in the application.

---

# Engineering philosophy

Follow these principles throughout the project:

- Start simple and evolve architecture when actual requirements justify it.
- Prefer a modular monolith initially.
- Do not introduce microservices prematurely.
- Do not introduce Kafka until asynchronous/event-driven communication provides a clear benefit.
- Do not introduce Redis until there is a concrete caching, rate-limiting, locking, or performance requirement.
- Do not introduce Kubernetes during the initial MVP.
- Do not introduce AI functionality during the initial MVP.
- Prefer boring, maintainable technology over unnecessary complexity.
- Architecture decisions should include the reasoning and tradeoffs.
- Code should look like production-quality application code, not tutorial/demo code.
- Avoid overengineering.
- Avoid unnecessary abstractions.
- Prefer clear domain terminology.

When proposing a new technology, explain:

1. What current problem it solves.
2. Why the existing implementation is insufficient.
3. What alternatives exist.
4. What tradeoffs the technology introduces.

---

# Initial technology stack

Backend:

- Java 25
- Spring Boot 4.x
- Maven
- Spring Web
- Spring Security
- Spring Data JPA
- Bean Validation
- PostgreSQL
- Flyway
- OpenAPI
- JUnit 5
- Testcontainers

Frontend:

- Next.js
- TypeScript
- Tailwind CSS
- shadcn/ui
- TanStack Query
- React Hook Form
- Zod

Infrastructure during MVP:

- Docker
- Docker Compose
- PostgreSQL
- GitHub Actions

Technologies that may be introduced later when justified:

- Redis
- Apache Kafka
- S3-compatible object storage
- Stripe
- OpenTelemetry
- Prometheus
- Grafana
- Terraform
- Kubernetes
- FastAPI
- LangGraph

Do not introduce these prematurely.

---

# Repository structure

Target repository structure:

fieldops/

    apps/
        api/
        web/

    services/
        # Future independently deployed services go here.
        # Keep empty initially.

    infrastructure/
        docker/
        terraform/
        kubernetes/

    docs/
        architecture/
        adr/
        product/

    scripts/

    AGENTS.md
    README.md
    docker-compose.yml

The Spring Boot modular monolith lives under:

apps/api

The Next.js frontend lives under:

apps/web

---

# Backend architecture

The backend starts as a modular monolith.

Use package-by-domain rather than package-by-technical-layer.

Example:

com.fieldops

    identity
    tenant
    customer
    technician
    servicecatalog
    workorder
    scheduling
    billing
    payment
    notification
    reporting
    audit

Within modules, prefer clear separation between:

- API/controller
- application/use-case
- domain
- infrastructure/persistence

Do not create interfaces merely for the sake of having interfaces.

Do not create generic BaseController, BaseService, BaseRepository, or similar abstractions.

Favor composition and explicit code.

---

# Domain model

Initial important concepts include:

Tenant
User
Role
Permission

Business
BusinessLocation

Customer
CustomerAddress

Technician
TechnicianSkill
TechnicianAvailability

ServiceType

WorkOrder
WorkOrderItem
WorkOrderStatusHistory

Appointment

Invoice
InvoiceItem

Payment

Notification
NotificationPreference

Attachment

AuditLog

Not all models should be created immediately.

Create them as features require them.

---

# Initial user roles

The long-term system can support:

PLATFORM_ADMIN
BUSINESS_OWNER
DISPATCHER
TECHNICIAN
CUSTOMER
ACCOUNTANT

Do not implement all permissions immediately.

Start with the permissions needed for the current milestone.

Authorization should ultimately include both:

- role-level authorization
- resource/tenant-level authorization

Never rely only on hiding buttons in the frontend for authorization.

Backend authorization is authoritative.

---

# Multi-tenancy

FieldOps is a multi-tenant SaaS.

A tenant represents a service business.

Examples:

ABC Heating & Cooling
Smith Plumbing
Northwest Electrical

During the MVP, use a shared PostgreSQL database with tenant-aware records.

Most business-owned records should eventually include tenant_id.

Never trust tenant_id supplied directly by a client when the authenticated context can determine it.

Prevent cross-tenant data access.

Add integration tests for tenant isolation when multi-tenancy is implemented.

---

# Work-order lifecycle

A likely lifecycle is:

REQUESTED
CONFIRMED
SCHEDULED
ASSIGNED
EN_ROUTE
IN_PROGRESS
COMPLETED
INVOICED
PAID

CANCELLED may occur from appropriate states.

Do not allow arbitrary status changes.

Use explicit domain operations such as:

assignTechnician()
startWork()
completeWork()
cancelWork()

Record important status transitions for auditing.

Do not implement the entire lifecycle until features require it.

---

# API guidelines

Use REST initially.

Version APIs under:

/api/v1/

Prefer resource-oriented endpoints for CRUD operations.

For important business operations, explicit action endpoints are acceptable.

Examples:

POST /api/v1/work-orders
GET /api/v1/work-orders/{id}

POST /api/v1/work-orders/{id}/assign
POST /api/v1/work-orders/{id}/start
POST /api/v1/work-orders/{id}/complete
POST /api/v1/work-orders/{id}/cancel

Do not expose persistence entities directly from controllers.

Use request/response DTOs.

Validate API input.

Use consistent error responses.

Eventually support correlation/request IDs.

Document APIs using OpenAPI.

---

# Database guidelines

Use PostgreSQL.

Use Flyway for every schema modification.

Do not allow Hibernate to manage production schema evolution.

Prefer:

ddl-auto=validate

once migrations are established.

Database migrations should be immutable once released.

Use proper:

- foreign keys
- unique constraints
- indexes
- not-null constraints

Do not add indexes blindly. Add them because of access/query patterns.

---

# Testing strategy

Tests are required.

Backend:

- unit tests for important domain logic
- integration tests for persistence and APIs
- Testcontainers for PostgreSQL
- Testcontainers for future infrastructure such as Kafka or Redis

Avoid relying on H2 when behavior should match PostgreSQL.

Frontend:

- component tests where useful
- Playwright for important end-to-end user journeys

Every substantial feature should include tests.

Before declaring a task complete:

1. Compile the project.
2. Run relevant tests.
3. Run static/lint checks where configured.
4. Report what was tested.

---

# Security

Never commit:

- API keys
- database passwords
- secrets
- tokens
- private credentials

Provide `.env.example` files instead.

Use environment variables for secrets.

Do not implement a custom password hashing or cryptography scheme.

Use established security libraries.

Authentication and authorization must be clearly separated.

---

# Scheduled jobs

The application will eventually include jobs such as:

- upcoming appointment reminders
- overdue work-order detection
- notification retries
- daily business reports
- weekly technician utilization reports
- monthly billing processes

Do not implement these until their associated feature exists.

Initially Spring scheduling is acceptable.

Distributed scheduling/locking should only be introduced when multiple application instances create a duplicate-execution problem.

---

# Events

Do not use Kafka during the first MVP.

Start with synchronous domain/application behavior.

When functionality such as:

- notifications
- analytics
- reporting
- integrations

causes excessive coupling or latency, introduce domain events.

Kafka can later provide durable asynchronous communication.

When Kafka is introduced, eventually address:

- transactional outbox
- idempotent consumers
- retries
- exponential backoff
- dead-letter topics
- schema evolution
- observability

Do not pretend distributed messaging guarantees exactly-once business processing without explaining the boundaries.

---

# Caching

Do not introduce Redis immediately.

Introduce Redis only when a concrete use case exists, such as:

- frequently accessed data
- technician availability caching
- rate limiting
- distributed locking
- temporary data

Measure before optimizing whenever practical.

---

# Observability

Eventually implement:

- structured logs
- metrics
- distributed traces
- correlation IDs

Prefer OpenTelemetry.

Important APIs should eventually expose operational metrics such as:

- request count
- latency
- error rate

Business metrics should be separated conceptually from system metrics.

---

# AI

AI is a later-stage capability.

Potential future capabilities:

- classify service requests
- extract issue/category/priority
- recommend technicians
- estimate job duration
- retrieve business manuals using RAG
- AI dispatcher agent

Do not introduce AI until the core service workflow works without it.

AI-generated actions affecting customers, schedules, money, or work orders should have appropriate validation and, where necessary, human approval.

---

# Architecture Decision Records

Important architectural decisions should be documented under:

docs/adr/

Use filenames such as:

0001-modular-monolith.md
0002-postgresql.md
0003-rest-api.md

ADR format:

# Title

## Context

## Decision

## Alternatives Considered

## Consequences

Keep ADRs concise.

---

# YouTube / educational requirement

This project will also be explained publicly.

Therefore code should be:

- readable
- explainable
- reasonably concise
- realistic
- well structured

Avoid huge generated files or unnecessary abstraction layers that make educational explanation difficult.

When implementing a major pattern, make the reasoning visible through documentation and clear code organization.

Never intentionally simplify correctness just to make a tutorial easier.

---

# Git practices

Prefer conventional commits:

feat:
fix:
refactor:
test:
docs:
chore:

Examples:

feat(workorder): add work-order creation
fix(scheduling): prevent technician double booking
docs(adr): document modular monolith decision

Do not make destructive Git operations unless explicitly requested.

Do not rewrite unrelated existing code.

Keep commits scoped to the current task when possible.

---

# Working style for Codex

For every requested implementation:

1. Inspect the existing repository first.
2. Understand relevant existing code before modifying it.
3. Briefly describe the implementation plan.
4. Implement only the requested scope.
5. Do not silently add major technologies or architectural patterns.
6. Add or update tests.
7. Run relevant tests/builds.
8. Report:
   - what changed
   - important design decisions
   - tests executed
   - anything intentionally deferred

If there is an architectural decision with meaningful tradeoffs, explain it before making a large structural change.

If the request would cause unnecessary overengineering, point that out and propose the simpler implementation.

The repository should evolve as if FieldOps were a real startup product.