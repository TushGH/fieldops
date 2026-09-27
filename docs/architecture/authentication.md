# Authentication

Status: FIELD-004 backend authentication implemented. See [ADR 0005](../adr/0005-session-authentication.md) for decisions and tradeoffs. FIELD-005–008 add [tenant selection and membership-role authorization](tenant-access.md); FIELD-009/010 add [onboarding and frontend login](business-onboarding.md).

## HTTP contract

| Method and path | Request | Response |
| --- | --- | --- |
| GET /api/v1/auth/csrf | No authentication required | 200 with headerName/token; establishes an anonymous session if needed |
| POST /api/v1/auth/login | Form-encoded email/password; CSRF header and matching session cookie | 204 with authenticated session cookie; generic 401 for invalid credentials; 403 for missing/invalid CSRF |
| GET /api/v1/auth/me | Authenticated session cookie | 200 with id, displayName, email; 401 without a valid active-user session |
| POST /api/v1/auth/logout | Session cookie and CSRF header | 204; invalidates session and expires cookie; 403 for missing/invalid CSRF |

The contract is also recorded in [OpenAPI](openapi.yaml). Login uses `application/x-www-form-urlencoded`, not JSON. The standard Spring Security form filter reads `email` and `password` parameters; callers must send credentials in the body, never a URL. A GET request does not perform login or logout. There is no generated login page, redirect-based success response, HTTP Basic authentication, remember-me, or bearer token flow.

Use the header name returned by the CSRF endpoint (currently X-CSRF-TOKEN). Spring returns a masked token associated with the session. Tokens cannot be transferred between independent clients. Fetch a fresh CSRF token after login, logout, or failed login: login rotates the session ID and clears the old CSRF token, logout invalidates the session, and a failed login invalidates any prior session to prevent retaining the previous account.

Responses are uncached. Current-user responses are explicit DTOs and contain no credential, membership, role, or tenant fields. Unknown users, users without credentials, disabled users, and incorrect passwords receive the same 401 response body:

```json
{"code":"UNAUTHENTICATED","message":"Authentication is required or credentials are invalid."}
```

CSRF failures return 403 with code REQUEST_REJECTED and a generic message. Do not interpret this as a business permission decision.

## Browser and session behavior

The session cookie is named FIELDOPS_SESSION, has HttpOnly and SameSite=Lax, and defaults to Secure. JavaScript should not read or copy the session cookie into local storage. Sessions use cookie tracking only, preventing URL session identifiers. Session inactivity expires after 30 minutes. No absolute session lifetime or concurrent-session limit is configured yet.

For local HTTP development, set SESSION_COOKIE_SECURE=false in the root .env and export it before starting the API. The .env.example contains this local setting. HTTPS deployments must leave the default true or explicitly set true; configure TLS termination and trusted proxy handling for the deployment topology. This setting does not itself provide HTTPS.

Sessions are in-process and instance-local; backend restarts end sessions. No Redis, shared session store, or JWT infrastructure is introduced. A missing or disabled global User invalidates an existing session on its next request, including one created before the status change. Global authentication does not evaluate Tenant or Membership states. Tenant workspace requests now validate those states separately under FIELD-005–008.

No cross-origin credential sharing is configured. The frontend login flow uses the bounded same-origin Next.js `/api/v1/[...path]` proxy, forwarding session cookies and CSRF headers. Backend clients and tests can use the API origin directly now.

## Credentials and provisioning

V4 creates password_credentials with user_id as its primary key and a restrictive foreign key to users.id, plus password_hash, created_at, and version. The schema requires a BCrypt-prefixed hash representation and nonnegative version. V1–V3 are unchanged, and existing users remain without local passwords until provisioned.

PasswordCredentialService.provision(userId, password) is an internal transactional use case, not an HTTP endpoint. Trusted backend onboarding/operator code can create a User through UserService and provision its initial credential in one outer transaction. Membership provisioning is independent. There is no automatic seed user, generated default application password, or development backdoor. FIELD-009 adds public signup through an atomic business onboarding use case.

Initial passwords must contain at least 15 Unicode code points and at most 72 UTF-8 bytes. Passwords are never trimmed, case-folded, or otherwise normalized. BCrypt is supplied by Spring Security at cost 12; the delegating encoder stores its algorithm prefix. The login encoder also rejects inputs beyond the BCrypt byte limit instead of accepting a truncated prefix. Email normalization follows the existing User policy.

Provisioning inserts a single credential per user. Repeating the operation fails with a database integrity error and does not replace the existing password. There is no password reset/change method. Foreign keys reject nonexistent users, and profile DTOs never include hashes. Passwords must not be logged or supplied in command-line arguments. Actor auditing, verified delivery of initial credentials, reset tokens, MFA, and breach-password screening require their own workflows.

## Authentication versus authorization

The session principal is the immutable User UUID with an empty authority collection. Users need no membership to authenticate; absence of memberships does not grant platform administration. Successful login grants no tenant role or business permission.

The filter chain leaves GET application health, GET aggregate Actuator health, and GET CSRF public, and accepts POST login and CSRF-protected POST onboarding without an existing authenticated session. Other requests require authentication. That boundary is not a substitute for tenant/resource authorization. FIELD-005–008 add guarded Tenant/Membership workspace endpoints, while customer/work-order endpoints remain unimplemented.

Internal User, Membership, and credential application services remain trusted operations without permission checks. Do not expose them directly as administrative endpoints. Keep future role policies, trusted tenant resolution, membership enforcement, and support access deliberate and separate.

## Verification and remaining work

PostgreSQL Testcontainers and real HTTP clients exercise migrations, password hashing/provisioning, normalization, successful/failed logins, missing/invalid CSRF, session-ID rotation, logout/replay, failed relogin, disabled-user session invalidation, password byte limits, cookie flags, generic failures, profile data minimization, and public health endpoints. Existing persistence tests still run.

FIELD-009/010 now implement the signup and login UI. Before exposing login publicly, implement login abuse controls/rate limiting and account recovery; no account lockout or rate limiter is claimed here. Multi-instance sessions, email verification, password changes, MFA, and permissions for future business modules remain deferred.
