# ADR 0006: Resolve tenant access per request and scope application operations

Status: Accepted for FIELD-005 through FIELD-008.

## Context

FieldOps has authenticated global users who can belong to several tenants. Sessions contain no role or tenant authority. The existing tenant-owned data is Membership; customer and work-order features do not exist yet. We need an enforceable boundary for current tenant operations and a pattern future domains can follow.

## Decision

Assign one BUSINESS_OWNER, DISPATCHER, or TECHNICIAN role per membership. Introduce no configurable permission tables or platform-admin bypass. Owners can rename their tenant and read/manage its memberships; all three roles can read their own tenant/context. Backfill existing memberships as TECHNICIAN rather than silently granting administrative privileges.

Resolve X-Tenant-ID against the authenticated global User, active membership, active tenant, and current membership role in a request-scoped component. Cache only within that request. Missing, ambiguous, or unauthorized selection fails closed. Do not persist selected tenant or role in the login session.

Guard request-facing application operations and query by validated tenant ID. Membership lookup includes both tenant and resource IDs; pagination and counts are tenant-scoped. Use explicit DTOs without ownership fields and reject unknown JSON properties. Foreign resource IDs return the same 404 as nonexistent IDs.

Serialize membership administration on the selected tenant row and recheck owner permission after acquiring the lock. Preserve at least one active owner membership. Keep trusted provisioning separate from the HTTP workspace API. Use PostgreSQL-backed integration tests to prove cross-tenant failures and valid same-tenant operations.

## Alternatives Considered

- **One role on User:** cannot represent an owner in one business and a technician in another. Membership roles preserve the domain relationship.
- **Session-stored tenant and authorities:** fewer lookups, but stale privileges require invalidation and concurrent tabs can overwrite each other's selection. Per-request validation costs database reads but keeps behavior explicit.
- **Unvalidated header or body tenant IDs:** convenient but forgeable and therefore not an authorization boundary.
- **Custom ThreadLocal context:** possible with careful cleanup, but request scope supplies isolation/lifecycle management without maintaining another holder. No async or scheduled-work propagation is implied.
- **Controller-only role checks:** leave application operations easy to invoke without checks. Guards belong in request-facing use cases as well as the authentication filter chain.
- **Generic tenant repository/base entity or Hibernate filters:** reduce repeated predicates but can hide missing scope, especially for native SQL. Explicit scoped methods are clearer for the current small model.
- **PostgreSQL row-level security:** useful defense in depth, but adds database-role and connection-context obligations. Continue the existing application-enforcement decision; do not claim safety for arbitrary SQL.
- **Uncoordinated last-owner counts:** two concurrent requests can both observe another owner and remove both. A tenant-row lock plus post-lock actor validation solves this existing administrative invariant without Redis or distributed locking.

## Consequences

- Multi-tenant users can select different businesses concurrently without cross-request state.
- Revocation affects subsequent requests without re-login; tenant and user status remain independent.
- Tenant administration is serialized per business. It is low-volume; future high-volume operations should not inherit this lock indiscriminately.
- Existing memberships receive no owner privileges. First-owner onboarding remains explicit work in FIELD-009.
- Current isolation coverage is limited to Tenant/Membership. Every future business module must preserve scoped queries, relationship constraints, and its own isolation tests.
- Trusted provisioning and direct SQL remain privileged internal operations; there is no platform-admin API, ownership reassignment, or deletion endpoint.

The [tenant access design](../architecture/tenant-access.md) records the permission matrix, API, error behavior, security boundaries, and verification evidence. Request scope follows [Spring Framework bean scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html); transaction locks use [Spring Data JPA locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html).
