# ADR 0003: Model a tenant as one service business

Status: Accepted; persistence implemented in FIELD-002. Authentication and tenant isolation remain deferred.

## Context

FieldOps needs a stable business identity and ownership boundary before introducing tenant-owned records. The product defines one tenant as one service business and excludes franchise complexity from the initial scope. The accepted architecture is a modular monolith with shared PostgreSQL, Flyway migrations, and module-owned persistence.

## Decision

Use one Tenant concept and one shared `public.tenants` table; do not add a separate Business entity initially. The initial fields are id, name, slug, status, createdAt, updatedAt, and version.

Use application-generated UUID v4 identity, a stable globally unique lowercase slug, and nonunique business display names. Represent ACTIVE and SUSPENDED as string enum values with a database check constraint. Use explicit suspension/reactivation operations, server-controlled timestamps, and optimistic locking.

Initially require only the primary-key and slug-uniqueness indexes. Future tenant-owned records reference the UUID with required foreign keys that restrict deletion. Use composite foreign keys for relationships that must remain within a tenant. Keep role and tenant/resource authorization separate from database integrity.

The [tenant domain design](../architecture/tenant-domain.md) specifies fields, constraints, lifecycle, ownership, security obligations, and implementation boundaries.

## Alternatives Considered

- **Separate Tenant and Business:** enables independent lifecycles, but currently adds an unnecessary one-to-one relationship. Multiple businesses per tenant introduce unsupported franchise complexity.
- **Sequential numeric IDs:** compact and efficient, but database-generated and sequential. UUID v4 provides identity before persistence with standard tooling. UUID v7 locality does not justify extra generation considerations for this workload. Slug keys couple relationships to naming policy.
- **Mutable/mixed-case slugs:** more naming flexibility, but require normalization, redirect, and reuse policies. Stable lowercase identifiers keep ownership independent of display-name changes.
- **Boolean status, PostgreSQL enum, or status table:** a boolean is less expressive; a database enum adds type-specific migration concerns; configurable status rows have no current use case. String values with a check constraint are sufficient.
- **Pending, archived, or soft-deleted states:** need onboarding/offboarding and retention semantics not yet defined. Start with reversible suspension.
- **Last-write-wins and database timestamp triggers:** last-write-wins can overwrite concurrent suspension; triggers support independent writers but add behavior before multiple writers exist.
- **Composite primary keys everywhere:** can enforce tenant-aware identity, but complicate JPA mappings. UUID primary keys plus composite relationship constraints preserve simpler identities.
- **Row-level security now:** adds defense in depth but requires deliberate role and connection-context management. Follow the existing architecture's application enforcement and constraints first; do not claim they provide isolation before implemented and tested.

## Consequences

- One identity and lifecycle fit the current product with no extra infrastructure.
- UUID indexes are larger and less insertion-local than sequential numeric indexes.
- Stable slugs simplify references but require a separate future policy if renaming becomes necessary.
- Tenant status and constraints alone do not authorize access. Subsequent features must enforce trusted context, suspension, scoped queries, and resource permissions.
- Composite association constraints may require additional unique indexes on parent tables; add them alongside the relationships that need them.
- Timestamps are not complete audit history. Actor-based lifecycle auditing awaits authenticated operations.
- Business details, memberships, timezone, currency, offboarding, and additional statuses remain feature-driven extensions. Persistence uses V2, preserving released V1.
