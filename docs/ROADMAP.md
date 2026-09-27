# FieldOps roadmap

This is a sequence of product and engineering milestones, not a dated delivery promise. Capabilities are planned unless explicitly marked complete. Each milestone should deliver a reviewable increment with relevant tests and documentation; infrastructure is introduced only when that increment needs it.

## v0.1 Foundation

- **Goals:** establish the Phase 0 product and architecture baseline before implementation.
- **Major capabilities:** product vision, personas and MVP boundaries; repository layout; architecture diagram; modular-monolith and PostgreSQL ADRs; roadmap and root ignore rules.
- **Engineering concepts to learn:** requirements discovery, domain boundaries, architectural tradeoffs, decision records, and keeping planned architecture distinct from working software.

Phase 0 delivers this documentation milestone. It does not include application scaffolding or runnable infrastructure.

## v0.2 Identity and Multi-Tenancy

- **Goals:** establish the first runnable system and make business identity and isolation foundational.
- **Major capabilities:** minimal Spring Boot and Next.js scaffolding; PostgreSQL and Flyway setup; verified local development commands and CI; operator-assisted tenant/owner onboarding; staff authentication and initial role permissions; authenticated tenant context and protected APIs. Add configuration examples only for real settings.
- **Engineering concepts to learn:** authentication versus authorization, trusted tenant resolution, resource permissions, migration discipline, DTO validation, API contracts, and PostgreSQL-backed integration testing.

## v0.3 Customers and Technicians

- **Goals:** give a business a reliable directory of the people and places involved in service delivery.
- **Major capabilities:** tenant-scoped customer contacts and addresses, technician profiles, relevant skills, basic availability, and list/search/edit workflows with appropriate permissions.
- **Engineering concepts to learn:** aggregate ownership, relational constraints, pagination, form validation, query-driven indexes, and preventing cross-tenant associations.

## v0.4 Work Orders

- **Goals:** create a dependable operational record from request through completed work.
- **Major capabilities:** work-order details and items, explicit allowed transitions, status history, assignment operations, technician views and completion notes, and cancellation rules. Integrate appointment-dependent states in v0.5 and financial states in v0.6 rather than faking those capabilities here.
- **Engineering concepts to learn:** domain invariants, state transitions, transaction boundaries, concurrency handling, audit history, and meaningful domain tests.

## v0.5 Scheduling

- **Goals:** coordinate appointments and technician capacity without accidental double booking.
- **Major capabilities:** appointment creation, dispatch views, assignment integration, rescheduling/cancellation, availability checks, timezone handling, and conflict enforcement under concurrent requests.
- **Engineering concepts to learn:** time modeling, interval overlap, transactional concurrency controls, cross-module orchestration, and integration tests for competing requests.

## v0.6 Billing and Payments

- **Goals:** connect completed work to invoices and accountable payment tracking.
- **Major capabilities:** invoice items and totals, invoice lifecycle, recording externally received payments, outstanding balances, and authorized financial views. Begin with one currency per tenant. Online collection is optional future scope; no payment provider is required for this milestone.
- **Engineering concepts to learn:** decimal money arithmetic, rounding policies, immutable financial facts, idempotent payment recording, and separating work status from financial truth.

## v0.7 Notifications and Background Jobs

- **Goals:** provide useful follow-up without requiring staff to monitor every record manually.
- **Major capabilities:** essential service notifications, appointment reminders, delivery status, retryable failures, and preferences needed by the selected delivery channel. Start with Spring scheduling; persist pending work when reliable retry requires it. Address duplicate execution if multiple instances are introduced.
- **Engineering concepts to learn:** job lifecycle, explicit tenant context outside HTTP requests, retry/backoff policies, idempotency, external delivery failures, and separating database commits from network calls.

## v0.8 Production Readiness

- **Goals:** make the pilot operable, diagnosable, secure, and recoverable.
- **Major capabilities:** dependable CI and release checks, deployment documentation, environment/secrets handling, structured logs and request correlation, health checks and useful metrics, backup/restore exercises, migration/rollback procedures, and performance checks against expected pilot usage.
- **Engineering concepts to learn:** operational readiness, threat modeling, service objectives, failure diagnosis, recovery testing, safe migrations, and evidence-based capacity planning. Select observability tooling only for concrete needs.

## v0.9 Event-Driven Architecture

- **Goals:** evaluate and reduce concrete coupling or latency in side effects while retaining the modular monolith.
- **Major capabilities:** review notification/reporting coupling; introduce internal domain/application events where they simplify existing workflows; define event ownership and commit timing. For any durable asynchronous workflow, define persistence, retries, idempotency, and failure recovery. If no use case benefits, document that finding and defer the mechanism.
- **Engineering concepts to learn:** synchronous versus asynchronous semantics, delivery guarantees, eventual consistency, event evolution, and the gap between committing data and delivering a side effect. Evaluate an outbox only when that gap requires durable handling.

This milestone does not require Kafka or service extraction. Kafka is excluded from the first MVP. In-memory events must not be represented as durable delivery or exactly-once business processing.

## v1.0 MVP

- **Goals:** validate a coherent, usable service workflow with pilot businesses.
- **Major capabilities:** complete tenant onboarding through request, scheduling, technician completion, invoice, and recorded payment; essential notifications; verified tenant isolation; usable responsive screens; operator and user documentation; pilot feedback and defect resolution.
- **Engineering concepts to learn:** end-to-end acceptance, usability validation, regression prevention, production feedback, and measuring business outcomes independently from system health.

Release requires the [product MVP acceptance criteria](product/PRODUCT.md) and operational checks, not merely completion of a technology checklist. Deferred infrastructure and future product opportunities do not block release.

## Phase 1: Application Foundation — implemented

Phase 1 establishes the runnable portion of v0.2: Spring Boot and Next.js scaffolding, PostgreSQL via Compose, Flyway, application health, a frontend connection check, integration tests, and CI validation. It implements no business features, authentication, or tenant isolation. The remainder of v0.2 is deferred to Phase 2.

## Recommended Phase 2: Identity and Multi-Tenancy

Define the authentication approach and initial permission matrix in an ADR before adding implementation. Build a narrow path covering operator-assisted tenant/owner onboarding, staff authentication, trusted tenant context, and backend-enforced permissions. Add only the identity and tenant records required for that path through Flyway migrations.

Prove isolation with PostgreSQL integration tests for cross-tenant reads, mutations, lists, and associations. Add a minimal authenticated frontend entry point and document session/security behavior. Do not begin customers, technician management, work orders, or additional infrastructure as part of this foundation. FIELD-002 starts Phase 2 with tenant persistence: a validated Tenant model, V2 migration, internal creation/retrieval use cases, and PostgreSQL integration tests. FIELD-003 adds global User records and independent tenant Membership records, supporting multiple tenants per user without duplicate profiles. FIELD-004 adds backend email/password session authentication, CSRF protection, logout, and current-user retrieval. FIELD-005–008 add membership roles, validated request-scoped tenant context, guarded Tenant/Membership APIs, and PostgreSQL-backed isolation tests. Operator onboarding, frontend login/onboarding, and permissions/isolation for future business workflows remain outstanding.
