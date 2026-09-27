# ADR 0002: Use PostgreSQL as the primary database

Status: Accepted for the initial architecture. Phase 1 provisions local PostgreSQL through Compose and verifies Flyway using Testcontainers.

## Context

FieldOps manages related tenant, customer, technician, work-order, appointment, invoice, and payment data. These workflows need reliable transactions, explicit relationships, and enforceable invariants. Tenant isolation must hold for reads, writes, and associations, including under concurrent use.

The initial system should have a single understandable source of truth and a repeatable development and testing environment.

## Decision

Use PostgreSQL as the initial primary database, shared by tenant-aware records in the modular monolith. Business tables include `tenant_id` where ownership requires it. Application authorization and scoped queries enforce access; tenant-aware constraints reinforce data integrity. Choosing PostgreSQL does not automatically provide tenant isolation.

PostgreSQL provides relational modeling, ACID transactions, foreign keys, unique and check constraints, indexing, and SQL querying suited to operational workflows. Its concurrency controls support enforcing scheduling and financial rules when combined with a deliberately designed transaction strategy. JSON support remains available for justified flexible attributes, without replacing clear relational models.

Manage every schema change through Flyway and keep released migrations immutable. Use Hibernate mapping validation once migrations exist, not automatic production schema updates. Test persistence behavior against real PostgreSQL through Testcontainers. Select a supported PostgreSQL version and verify stack compatibility when provisioning begins.

## Alternatives Considered

- **MySQL/MariaDB:** credible relational alternatives that meet many needs. PostgreSQL is selected as the project's consistent baseline for relational constraints, querying, and future data needs; there is no requirement to support multiple database engines.
- **Document database as the primary store:** flexible document shapes are useful for some workloads, but FieldOps centers on related operational and financial records. A document-first design would increase the burden of modeling and enforcing these relationships.
- **SQLite:** useful for embedded/local applications, but a shared multi-tenant server with concurrent scheduling and billing warrants a server database from the start.
- **Database or schema per tenant:** offers different isolation and customization tradeoffs, but increases provisioning, migration, and operational complexity before any customer requirement justifies it.

## Consequences

- Transactions and constraints support business correctness within one datastore.
- A shared database keeps provisioning and migrations manageable, but increases the importance of tenant-scoped queries, constraints, integration tests, and capacity planning.
- PostgreSQL-specific behavior may be used deliberately; switching databases later has a migration cost.
- Schema design, query tuning, backups, restore validation, and connection management remain engineering responsibilities.
- No cache, search engine, analytics store, or message broker is added by this decision. Add other stores only for demonstrated requirements and record their ownership and consistency tradeoffs.

Revisit tenancy storage strategy if contractual isolation, data residency, or demonstrated workload requirements exceed the shared model. Such a change needs its own ADR and migration plan.
