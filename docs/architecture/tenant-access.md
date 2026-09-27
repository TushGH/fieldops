# Tenant access and isolation

Status: FIELD-005 through FIELD-008 implemented for existing Tenant and Membership data. See [ADR 0006](../adr/0006-tenant-context-and-access-control.md). FIELD-009/010 subsequently add [business onboarding and frontend login](business-onboarding.md). Customer, technician, work-order, and platform-administration features remain unimplemented.

## Membership roles

One role belongs to each membership, not the global User or login session. The initial roles are BUSINESS_OWNER, DISPATCHER, and TECHNICIAN. There is no implicit role inheritance, configurable permission table, or platform-admin bypass.

| Operation | BUSINESS_OWNER | DISPATCHER | TECHNICIAN |
| --- | --- | --- | --- |
| Read selected tenant and own context | Yes | Yes | Yes |
| Rename selected tenant | Yes | No | No |
| List/read selected tenant memberships | Yes | No | No |
| Change a membership role | Yes | No | No |
| Deactivate/reactivate a membership | Yes | No | No |
| Create or assign existing users/memberships through workspace HTTP APIs | No | No | No |
| Read another tenant's records | No | No | No |

Dispatcher and technician permissions are deliberately identical for the current metadata endpoints. Their operational permissions arrive with their actual product features. Owner status in one tenant grants nothing in another; the same user can have different roles in different businesses.

V5 adds a required role column and an allowed-values constraint. Existing memberships become TECHNICIAN, the least-privileged initial role; none are promoted to owner. The migration drops its temporary default so direct future inserts must state a role. Existing two-argument internal membership creation also selects TECHNICIAN. Trusted provisioning can explicitly create an owner membership using the three-argument use case. Establishing the first owner is part of FIELD-009 onboarding, not a public role-escalation endpoint.

## Authenticated tenant context

Every tenant request supplies exactly one canonical UUID in X-Tenant-ID. This is a selection hint, not an access credential. The backend obtains User UUID from the authenticated session, confirms the global User is active, requires an ACTIVE membership in the selected tenant, and requires an ACTIVE tenant. The validated context contains userId, tenantId, membershipId, and that membership's current role.

Context is request-scoped and resolved when a tenant use case requires it. It is never stored in a static holder, custom ThreadLocal, login session, cookie, JWT claim, or client-controlled body. Concurrent tabs using the same login can select different tenants without changing each other's context. Each subsequent request reloads membership and tenant eligibility. Missing context fails; there is no first-membership or default-tenant fallback.

- No authenticated session: 401.
- Missing header: 400 TENANT_REQUIRED.
- Malformed, duplicate, or comma-separated headers: 400 INVALID_TENANT.
- Unknown/unrelated tenant, inactive membership, or suspended tenant: identical 403 TENANT_ACCESS_DENIED.
- Insufficient role for an operation: 403 TENANT_ACCESS_DENIED.
- Resource absent from the selected tenant, including a foreign tenant's resource ID: 404 RESOURCE_NOT_FOUND.

Authentication and CSRF endpoints do not require tenant selection. A user without memberships can still log in and read their own global profile. Membership or tenant suspension does not end the global login; global User disabling retains FIELD-004's session invalidation behavior.

## API contract

All paths below require the session cookie and X-Tenant-ID. Mutations additionally require the existing CSRF header/token. See [OpenAPI](openapi.yaml) for DTOs and errors.

| Method and path | Behavior |
| --- | --- |
| GET /api/v1/tenant/context | Return the validated selection and current role |
| GET /api/v1/tenant | Return selected tenant metadata |
| PATCH /api/v1/tenant | Rename using a body containing only name |
| GET /api/v1/tenant/memberships | Page through this tenant's memberships; page defaults to 0 and size to 20 |
| GET /api/v1/tenant/memberships/{id} | Read one membership within this tenant |
| PUT /api/v1/tenant/memberships/{id}/role | Assign role from the allowed enum |
| POST /api/v1/tenant/memberships/{id}/deactivate | Make the membership inactive |
| POST /api/v1/tenant/memberships/{id}/reactivate | Reactivate the existing membership |

Membership responses contain IDs, role, status, timestamps, and version, not global user emails or other tenants' memberships. Lists sort by membership UUID and constrain both contents and total count by tenant. Page must be nonnegative and size must be 1–100. There is no unbounded list or client-controlled sort expression.

DTOs accept no tenantId, userId, or ownership changes. Unknown JSON fields are rejected, as are invalid role values, null roles, invalid names, and malformed resource IDs. Request validation returns 400 INVALID_REQUEST without echoing input or persistence details. Role/status actions update the existing relationship rather than reassigning its tenant or user. There is no deletion or membership-creation endpoint.

## Enforcement and persistence boundaries

TenantWorkspaceService owns request-facing operations and requires validated context before accessing data. Role checks are in the application service, not only controllers or hidden frontend controls. It accepts resource IDs but never an ownership ID. Membership queries use both tenant_id and id; list/count queries always include tenant_id. Tenant lookup uses only the validated selection. Cross-tenant IDs never become an unscoped lookup followed by a late ownership comparison.

V5 adds an index on `(tenant_id, id)` for tenant-scoped pagination. The existing unique `(tenant_id, user_id)` index supports membership resolution. The existing foreign keys and immutable JPA ownership fields remain in place. Because this issue adds no new associations between tenant-owned tables, no new composite foreign key is needed. Future such associations must use tenant-aware composite references as described in the [tenant design](tenant-domain.md).

Existing TenantService, MembershipService, UserService, and PasswordCredentialService remain explicitly trusted internal persistence/provisioning operations used by tests and future operator onboarding. They expose no HTTP endpoints and are not request-facing authorization APIs. Raw repositories and direct SQL are likewise trusted implementation mechanisms, not a database security boundary. New controllers must use guarded use cases rather than expose those internal operations. Request-facing service calls outside an HTTP request fail because there is no active context scope; scheduled/system work will need separate explicit authorization and tenant ownership, not a fabricated request or fallback tenant.

Application enforcement and relational integrity are the current boundary. PostgreSQL row-level security is not enabled; this implementation does not claim that arbitrary unscoped SQL is safe. Every new business module still needs scoped use cases, queries, relationship constraints, and isolation tests.

## Membership administration and concurrency

An owner may promote or demote other memberships, and may change their own role only if another active owner membership remains. The last ACTIVE BUSINESS_OWNER membership cannot be deactivated or demoted; attempts return 409 LAST_OWNER. Reactivating an existing membership retains its role. Repeating an already-applied state leaves it unchanged.

Membership administration locks the selected tenant row for the transaction, then rechecks the actor's role and membership/tenant status using database aggregate queries. This avoids authorizing a queued operation from a stale role captured before the lock. Counting active owner memberships under the same lock prevents two owners from concurrently removing each other. Tenant renaming uses the same lock and post-lock permission recheck. Optimistic locking remains active, and stale entity updates return 409 CONCURRENT_UPDATE.

The invariant is specifically about active owner memberships, not a promise that every owner user remains globally enabled. Global account disabling and trusted provisioning are not exposed as tenant operations; future platform workflows must define their owner-availability policies. Changes already in flight are not retroactively cancelled; subsequent requests re-resolve eligibility. These checks do not create an audit history beyond the existing timestamps and versions; actor-based administrative auditing remains follow-up work.

## Isolation evidence

The PostgreSQL Testcontainers suite uses real session login and CSRF-protected HTTP requests with owners, dispatchers, technicians, an unrelated user, and a user with different roles in two tenants. It verifies:

- V4-to-V5 migration preserves legacy membership without owner promotion; invalid/null roles are rejected by PostgreSQL.
- Authentication and explicit single-tenant selection are required; forged role/user headers and tenant query parameters cannot establish authority.
- The permission matrix applies to the selected tenant, even when the user owns another tenant.
- Lists, pagination, total counts, and single-resource reads cannot expose other tenants' membership records.
- Foreign membership IDs cannot be updated, reactivated, deactivated, or used to change roles; rejected writes leave their records unchanged.
- Body fields cannot reassign ownership; no deletion endpoint exists.
- CSRF protection remains active for mutations, and owners cannot remove the final active owner membership.
- Role changes, membership deactivation, and tenant suspension affect existing sessions on subsequent tenant requests.
- Concurrent requests sharing a login keep distinct tenant contexts; a later request without a header does not inherit the prior selection.
- Two competing owner deactivations cannot leave zero active owner memberships.

These tests cover the records implemented today. Customer/work-order isolation must be tested when those modules exist, including their foreign-key relationships, search/filter paths, bulk operations, and background jobs.
