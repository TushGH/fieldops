# Tenant domain design

## Identity-onboarding update (2026-09-27)

Identity onboarding adds PROVISIONING for assisted creation. Existing ACTIVE/SUSPENDED behavior remains, and ordinary reactivation cannot activate a provisioning tenant. Initial-owner acceptance is the only request-facing activation path. See [identity onboarding](identity-onboarding.md) for the V7 lifecycle extension and shared setup state. Earlier FIELD-001/002 scope statements below are historical.

Status: FIELD-001 design implemented for persistence in FIELD-002. Tenant domain code, V2 migration, repository, and internal creation/retrieval use cases exist. Authentication, authorization, and tenant isolation remain unimplemented.

This design follows [AGENTS.md](../../AGENTS.md), the [product definition](../product/PRODUCT.md), [architecture v1](architecture-v1.md), and [ADR 0003](../adr/0003-tenant-domain-model.md).

## Tenant and Business

A Tenant represents one service business and its data-isolation boundary. Use Tenant in the domain and business in user-facing language. Initially there is no separate Business entity.

The product targets individual service businesses, not franchise groups. Separate one-to-one Tenant and Business entities would add joins and synchronized lifecycles without an independent requirement. A one-to-many model would introduce another ownership and authorization boundary. Both remain possible later if legal entities or business groups become real requirements.

Future business locations belong to a tenant; they do not automatically become tenants. If Business becomes a separate concept later, preserve the tenant identifier and isolation boundary.

## Fields and PostgreSQL schema

Use one `public.tenants` table in the existing shared PostgreSQL database. A schema or database per tenant adds provisioning and migration complexity without a current isolation requirement that justifies it.

| Domain field | PostgreSQL column | Type | Rules |
| --- | --- | --- | --- |
| `id` | `id` | `uuid` | Required primary key; immutable; application-generated UUID v4 |
| `name` | `name` | `varchar(200)` | Required trimmed, nonblank display name; not unique |
| `slug` | `slug` | `varchar(63)` | Required canonical lowercase identifier; globally unique; stable after creation |
| `status` | `status` | `varchar(20)` | Required; `ACTIVE` or `SUSPENDED` |
| `createdAt` | `created_at` | `timestamptz` | Required creation instant; immutable |
| `updatedAt` | `updated_at` | `timestamptz` | Required last persisted change instant |
| `version` | `version` | `bigint` | Required optimistic-lock version; initially zero |

The application supplies IDs, initial status, timestamps, and initial version. These are server-controlled values, not client-selected persistence metadata. `version` is concurrency metadata, not audit history; persistence increments it when updating the row. The Java version is nullable only before persistence so Spring Data recognizes a new entity with an assigned UUID; Hibernate initializes the persisted version to zero.

Do not initially add owner IDs, contact addresses, subscription plans, tax details, generic JSON settings, or soft-delete fields. FIELD-003 adds memberships linking global users to tenants; ownership roles remain deferred. See the [user and membership design](user-membership-domain.md). Add business timezone and currency when scheduling and billing need them, with explicit configuration rather than guessed defaults.

### Constraints

| Constraint | Purpose |
| --- | --- |
| `pk_tenants` on `id` | Stable unique identity |
| `uq_tenants_slug` on `slug` | Global uniqueness across all statuses |
| `NOT NULL` on all seven columns | No incomplete required values |
| Name check | Reject empty/whitespace-only names and leading/trailing whitespace |
| Slug length check | Require 3–63 characters |
| Slug format check | Require `^[a-z0-9]+(-[a-z0-9]+)*$` |
| Status check | Permit only `ACTIVE` and `SUSPENDED` |
| Version check | Require a nonnegative value |

The name column length bounds it to 200 characters. Different tenants may have the same name. Application validation should normalize surrounding whitespace, handle Unicode whitespace, reject control characters, and provide useful errors. Database constraints reject invalid stored representations; PostgreSQL-backed tests should document the exact whitespace behavior of the chosen checks.

Use a Java enum persisted as a string with a database check constraint. A PostgreSQL enum offers a dedicated type but adds type-specific migration and mapping concerns. A lookup table suits configurable statuses, which are not required. An unconstrained string permits invalid data. The check enforces allowed values, not transitions between old and new states.

V2__create_tenants.sql implements the table while preserving released V1 and Hibernate `ddl-auto=validate`. The name check explicitly lists Unicode White_Space for edge trimming and rejects PostgreSQL control characters. Application validation also rejects ISO control characters. Tests cover ASCII whitespace, nonbreaking spaces, and em spaces.

## Primary key strategy

Generate a UUID v4 once when creating a tenant, represent it with Java `UUID`, and persist it as PostgreSQL `uuid`. This gives the domain an identity before persistence without another ID-generation dependency. It avoids coupling relationships to business naming.

| Alternative | Tradeoffs |
| --- | --- |
| UUID v4 — recommended | Standard generation and opaque identity; larger indexes and less insertion locality than sequential IDs |
| `bigint` identity | Compact and efficient; identity is database-generated and identifiers are sequential |
| UUID v7 | Better insertion locality; extra generation considerations are unnecessary for the expected tenant-table workload |
| Slug primary key | Readable, but couples all relationships to a human-facing naming policy |

UUIDs are identifiers, not authorization. Native UUID storage does not require generating IDs in PostgreSQL. See [PostgreSQL UUID documentation](https://www.postgresql.org/docs/17/datatype-uuid.html).

## Slug strategy

Use a readable slug such as `abc-heating-cooling`, with lowercase ASCII letters, digits, and single separating hyphens. Require 3–63 characters. No leading or trailing hyphen is allowed.

- Keep slugs globally unique, including suspended tenants.
- Keep the slug stable after creation; renaming the business does not rename the slug.
- Reference tenants by UUID, never by slug, in foreign keys.
- A slug identifies a tenant but never establishes access to it.

For future operator-assisted creation, suggest a slug from the name and allow correction. Do not require automatic transliteration for every business name. On a collision, return a clear conflict so the operator can select another value. The unique constraint is authoritative under concurrency; an availability check is only feedback.

Automatic numeric/random suffixes can help public signup but make identifiers less predictable. Mixed-case slugs with `citext` or a functional index add complexity avoided by canonical lowercase storage. Mutable slugs require redirect, reservation, and reuse policies; defer them.

Do not invent a reserved-word list before defining the routing namespace. Define reserved names before exposing top-level tenant paths or subdomains if either becomes a requirement. The syntax preserves that option without selecting a routing architecture.

## Lifecycle

| Status | Meaning |
| --- | --- |
| `ACTIVE` | Operationally eligible to use FieldOps, subject to authorization |
| `SUSPENDED` | Business access is blocked while data and identity remain intact |

Creation produces an ACTIVE tenant. Use explicit `suspend()` and `reactivate()` domain operations instead of an unrestricted status setter. Repeating an operation in its target state can be a no-op. Optimistic locking prevents stale updates from overwriting concurrent lifecycle changes; callers must handle a conflict rather than blindly retry stale state.

ACTIVE does not imply an owner exists, onboarding is complete, or a bill is paid. Those workflows are outside this issue. Suspension must eventually block existing sessions as well as new access; a status column alone does not implement that enforcement.

A boolean active flag is simpler but less expressive. Add PENDING only if an onboarding workflow persists incomplete tenants across steps. Add CLOSED or ARCHIVED only when offboarding, retention, and restoration semantics are defined. Soft deletion introduces filtering and uniqueness behavior without a current requirement.

Do not offer routine tenant deletion initially. Suspension is reversible access control, not deletion or a retention policy. Future tenant-owned foreign keys should restrict tenant deletion.

## Indexes

Initially rely only on the primary-key index on `id` and unique index on `slug`. They serve identity lookup, foreign-key targeting, slug lookup, and uniqueness. PostgreSQL creates these indexes for the corresponding constraints; do not duplicate them.

Do not add indexes on name, status, or timestamps without a query requiring them. If a future operator list filters by status and sorts by creation time, choose an index for its actual filtering and pagination behavior.

See [PostgreSQL constraints documentation](https://www.postgresql.org/docs/17/ddl-constraints.html).

## Audit fields and concurrency

Represent timestamps as Java `Instant` and PostgreSQL `timestamptz`. Set createdAt and updatedAt from the same server-controlled clock on creation. Preserve createdAt and advance updatedAt on persisted changes, including status changes. Neither timestamp is client-authoritative.

Prefer application-managed timestamps initially. Database triggers cover independent writers but add behavior outside the application before multiple writers exist. Future bulk writes must explicitly preserve timestamp and optimistic-lock behavior. Last-write-wins is simpler than version checking, but risks overwriting a concurrent suspension.

`timestamptz` represents an instant and does not preserve the original timezone identifier. A future business timezone requires a separate field. See [PostgreSQL date/time documentation](https://www.postgresql.org/docs/17/datatype-datetime.html).

Defer createdBy and updatedBy until authenticated actor identity exists; do not invent placeholder users. Timestamps are not complete audit history. When operator lifecycle actions are implemented, record actor, reason, previous/new status, and time in durable audit history.

## Future tenant-owned records

Each independently accessed business record should have `tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT`. Ownership is immutable in ordinary update operations.

Use tenant UUIDs across module boundaries. Each domain module owns its records and persistence; avoid bidirectional Tenant collections of all customers, work orders, and other records.

A tenant foreign key alone cannot prevent cross-tenant associations. For a future customer/work-order relationship:

| Table | Relevant constraints |
| --- | --- |
| `customers` | UUID primary key `id`; required tenant foreign key; unique `(tenant_id, id)` |
| `work_orders` | Required tenant foreign key; composite foreign key `(tenant_id, customer_id)` referencing `customers(tenant_id, id)` |

This prevents a work order in one tenant from referencing another tenant's customer. A foreign key on customer_id alone proves only existence. Composite primary keys `(tenant_id, id)` are also valid, but complicate JPA identity mappings. Separate UUID primary keys plus composite relationship constraints are the recommended compromise.

Choose tenant-leading indexes for scoped queries. PostgreSQL does not automatically index referencing foreign-key columns. Avoid a standalone tenant_id index when an existing composite index already serves that access pattern. See [PostgreSQL foreign-key documentation](https://www.postgresql.org/docs/17/ddl-constraints.html#DDL-CONSTRAINTS-FK).

Use tenant-scoped uniqueness for future business identifiers where appropriate. Global identity/platform records need separately defined ownership; nullable tenant_id must not silently mean shared access.

## Multi-tenancy security obligations

These are requirements for later implementation, not safeguards provided by this design document:

- Derive tenant context from authenticated identity and validated membership. IDs, slugs, headers, and subdomains may indicate a selection but cannot authorize it.
- Fail closed when business operations lack trusted tenant context; never fall back to unscoped queries.
- Scope reads, writes, deletes, lists, counts, searches, exports, and relationship lookups by tenant.
- Combine membership with role/resource checks. Technician assignment restrictions still apply.
- Do not allow ordinary update DTOs to change tenant_id.
- Check tenant status for existing sessions as well as new access once authorization exists.
- Keep platform administration separate from access to tenant business data. Support access requires deliberate authorization and auditing.
- Carry explicit tenant ownership through future background jobs, cache keys, files, and events.
- Test cross-tenant reads, mutations, lists, and associations, plus missing context and suspended access when implemented.

Initially use application enforcement and database integrity constraints, consistent with architecture v1. Row-level security can later reduce exposure from omitted predicates, but requires careful connection-context and database-role handling. Table owners and privileged roles can bypass policies under documented conditions. It supplements authorization rather than replacing it. See [PostgreSQL row security documentation](https://www.postgresql.org/docs/17/ddl-rowsecurity.html).

## Implementation and verification boundaries

FIELD-001 records the model, ownership strategy, and decisions. FIELD-002 implements persistence and PostgreSQL Testcontainers tests for creation, retrieval, constraints, timestamps, optimistic concurrency, concurrent slug conflicts, and invalid direct SQL writes. Unit tests cover name/slug validation and lifecycle behavior.

TenantService exposes transactional create and read-only findById operations returning TenantDetails snapshots. It is an internal, unauthenticated use case, not a public API. Duplicate slug claims fail with Spring DataIntegrityViolationException and the named uq_tenants_slug constraint; a future API must translate that into an appropriate conflict response. The repository deliberately exposes no delete or unrestricted list operation. Domain entities carry JPA mappings to avoid a duplicate persistence model; persistence access remains inside the tenant module. Lifecycle changes are domain operations exercised in persistence tests, without adding operator endpoints or authorization workflows.

Tenant isolation tests involving business records and authenticated access belong with those features. Authentication, RBAC, trusted tenant resolution, frontend onboarding, customer/technician entities, Redis, Kafka, and service extraction remain outside this issue. No public business API should be inferred from the existence of tenant persistence.
