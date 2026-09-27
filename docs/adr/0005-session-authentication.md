# ADR 0005: Use Spring Security session authentication

Status: Accepted for FIELD-004. Business authorization remains deferred.

## Context

FieldOps has global users and tenant memberships but no credential or session mechanism. The initial client is a browser and the backend is a single Spring Boot application. Public signup, tenant onboarding UI, and role/resource permissions are not part of FIELD-004. The GitHub issue specifies authentication without a required identity provider or token format.

## Decision

Use Spring Security's standard username/password filter and DAO authentication provider, with server-side servlet sessions. Login accepts form-encoded email/password and returns HTTP 204; current-user data is retrieved separately. Retain framework CSRF protection, session-ID rotation, and logout handling. Use an HttpOnly, SameSite=Lax session cookie with Secure enabled by default, and a 30-minute idle timeout. Local HTTP development explicitly disables the Secure flag.

Keep password hashes in a separate one-to-one table keyed by User UUID. Encode with Spring Security's delegating password encoder using BCrypt cost 12 and a random salt. Initial provisioning requires at least 15 characters and at most 72 UTF-8 bytes, without composition rules or password normalization. Oversized login input is rejected rather than truncated. No custom hashing algorithm is introduced.

Use stable User UUID identity and no authorities/tenant claims in the session. Disabled users cannot authenticate; existing sessions recheck global user state on each request and are invalidated when the user is missing or disabled. This is account authentication state, not membership or business authorization.

Provision initial credentials only through a trusted internal application service. Existing users receive no default credential. Public signup, email verification, recovery, MFA, password changes, and operator onboarding tooling remain separate workflows. Authentication does not prove mailbox ownership.

## Alternatives Considered

- **JWT access/refresh tokens:** useful for independently deployed APIs and external clients, but introduce signing keys, refresh rotation, revocation, and browser storage decisions without a current need. Sessions support immediate logout and straightforward browser use.
- **External OIDC provider:** avoids owning password authentication and can add MFA/SSO, but requires provider selection, account-linking rules, external configuration, and operational dependencies. Reconsider when those requirements exist; stable User UUIDs preserve that option.
- **HTTP Basic:** simple for tooling but sends credentials repeatedly and lacks a browser logout lifecycle. Disabled explicitly.
- **Argon2/scrypt:** credible memory-hard alternatives, but Spring's BCrypt implementation fits the current dependency set. BCrypt imposes a 72-byte limit; enforce it explicitly and reevaluate cost/algorithm using deployment measurements. The stored encoder prefix identifies the algorithm; introducing another algorithm also needs a migration of the current format constraint.
- **Password hash on User:** fewer database operations but mixes credential material into general profile persistence. A separate table limits accidental exposure and allows users without local credentials.
- **Distributed session storage:** preserves sessions across nodes/restarts but adds infrastructure. In-memory servlet sessions are sufficient for the current single-instance foundation; they are not a multi-instance solution.

## Consequences

- Clients must retain the session cookie and fetch/send CSRF tokens for login and logout. No bearer token is issued or stored in browser local storage.
- Sessions are local to a backend instance and do not survive a restart; cookies are restricted to cookie-based tracking. Deployment across multiple instances requires another decision.
- Generic HTTP 401 responses hide distinctions between unknown users, absent credentials, disabled accounts, and wrong passwords. This does not promise indistinguishable response timing.
- Checking account state adds a database read per authenticated request, allowing subsequent requests to reject a disabled account without Redis or session registries. Requests already in flight are not retroactively cancelled.
- No public signup/provisioning endpoint exists. Operator tooling must invoke trusted application services when that workflow is implemented; do not add an unauthenticated shortcut.
- Public internet deployment still needs login abuse controls/rate limiting and recovery/verification policies. These are explicit follow-up work, not features supplied by the session mechanism.
- Authentication checks protect the current-user endpoint; no role, membership, tenant resolution, or business-resource permissions are implemented. Business APIs still require authorization before exposure.

See the [authentication contract](../architecture/authentication.md), [Spring form login](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html), [CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [session management](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html), and [password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).
