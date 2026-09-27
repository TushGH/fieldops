# ADR 0001: Use a modular monolith

Status: Accepted for the initial architecture. Phase 1 establishes the application shell; domain modules remain deferred.

## Context

FieldOps needs to connect tenant identity, customer records, technician availability, work orders, scheduling, and billing. The product boundaries and workload are still being discovered. A small initial team needs fast feedback and an application that can be explained publicly without compromising correctness.

Many early workflows benefit from ordinary database transactions. There is no demonstrated need for separate scaling, deployment, or ownership of individual backend domains.

## Decision

Build one Spring Boot backend application under `apps/api`, organized into domain modules under `com.fieldops`. Modules have explicit responsibilities and application interfaces, share PostgreSQL, and deploy together. Begin with synchronous in-process interactions. The Next.js web application lives under `apps/web`.

Protect boundaries through module-owned persistence, explicit use cases, review, and appropriate tests as code arrives. Add modules when features need them rather than generating an empty abstraction hierarchy.

### Why not start with microservices?

Microservices would require network contracts, distributed failure handling, coordinated data changes, additional deployments, and more operational visibility before FieldOps has validated its core workflow. Distributed transactions and eventual consistency would complicate scheduling and billing correctness. Separate services would also freeze uncertain domain boundaries too early.

These costs currently lack a corresponding customer or engineering benefit. Learning distributed systems is a future opportunity, not sufficient justification for putting them on the MVP critical path.

## Alternatives Considered

- **Microservices from day one:** allow independent deployment and scaling, but introduce operational and consistency costs without evidence they are needed.
- **Unstructured monolith organized only by technical layers:** simple to start, but obscures domain ownership and makes unrelated features easier to couple.
- **Function-per-feature/serverless backend:** can suit isolated event workloads, but adds execution and operational boundaries to a transaction-heavy workflow before those boundaries are understood.

## Consequences

- One backend build, deployment, and transactional datastore keep early development and debugging manageable.
- Domain boundaries make responsibilities visible and preserve options for future change.
- Backend modules cannot deploy or scale independently; a failure or resource bottleneck can affect the whole application.
- A shared database permits accidental coupling, so code review and tests must enforce ownership. Package names alone do not create isolation.
- Extracting a module later still requires data ownership, contract, and migration work; modularity does not make extraction automatic.

### Conditions for extracting a service

Reconsider a specific module only when evidence shows an independent scaling bottleneck, a required isolation boundary, or a stable team ownership/release need that the monolith cannot reasonably address. First evaluate query tuning, simpler code changes, or scaling the existing application.

Extraction also requires stable domain contracts, a credible data migration plan, defined consistency and failure behavior, observability, and capacity to operate another service. Record the evidence, alternatives, costs, and rollout in a new ADR. Neither repository size nor reaching v0.9 automatically justifies microservices.
