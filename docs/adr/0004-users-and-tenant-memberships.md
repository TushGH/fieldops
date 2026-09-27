# ADR 0004: Separate global users from tenant memberships

Status: Accepted for FIELD-003; authentication and authorization remain deferred.

## Context

FieldOps has Tenant persistence and now needs user identity records and their relationship to service businesses. One person may participate in multiple independent businesses. Business roles, credentials, invitations, and tenant context are not yet defined.

## Decision

Keep a global User in the identity module and an explicit Membership in the tenant module. Each membership links one user UUID to one tenant UUID, with a unique `(tenant_id, user_id)` pair and foreign keys restricting parent deletion. Users may have zero or more memberships; membership does not imply authorization.

Use UUID v4 keys, server-controlled timestamps, and optimistic locking consistently with Tenant. Give User ACTIVE/DISABLED status and Membership ACTIVE/INACTIVE status so global identity and tenant relationship lifecycles remain independent. No User.role or credentials are added.

Store a globally unique, trimmed, lowercased ASCII email as unverified contact data. Keep email immutable until an ownership-verification/change workflow exists. Retain plus tags and dots; never automatically link or merge users by email.

Use narrow internal creation/retrieval services and immutable result records. JPA-mapped domain entities follow the existing persistence approach; cross-module relationships use UUIDs and database constraints. See the [user and membership design](../architecture/user-membership-domain.md) for schema, email policy, indexes, and security boundaries.

## Alternatives Considered

- **User belongs to exactly one tenant:** fewer joins, but duplicate profiles or disruptive identity migration when a person participates in several businesses. A membership table directly models the requested relationship without adding a switching UI.
- **Separate user per tenant:** allows tenant-specific identities, but fragments profile state and future account lifecycle. Global identity with tenant-owned memberships keeps those concerns distinct.
- **Bare many-to-many join table:** fewer fields, but provides no stable relationship identity, lifecycle, or concurrency control.
- **Composite Membership primary key:** avoids a surrogate ID, but complicates JPA identity mapping and later relationship auditing. Keep UUID identity plus pair uniqueness.
- **Tenant-scoped or nonunique email:** supports independent profiles for one address, but needs another policy for global identity. Global canonical uniqueness is simpler for this product; it is not evidence of mailbox ownership.
- **Case-sensitive/internationalized addresses or citext:** broader address semantics are possible but need more policy and testing. Canonical ASCII storage is a deliberate initial limitation; local-part case is treated as insensitive.
- **Roles on User or lifecycle cascades:** a global role cannot describe different business roles. Rewriting all memberships on user/tenant suspension loses independent relationship state. Defer permissions and evaluate states together when authorization exists.

## Consequences

- One user can participate in multiple tenants with independent memberships and no duplicated profile.
- Membership adds joins, but remains an ordinary relational model in the modular monolith.
- Foreign keys and unique constraints protect reference integrity and concurrent claims; they do not enforce authorization.
- Global profile data needs separate future edit/read permissions from tenant-owned data.
- Disabled users and inactive memberships retain uniqueness reservations. Deletion and account recovery need explicit future policies.
- Authentication, ownership verification, invitations, role assignments, tenant switching, and public endpoints remain out of scope.
