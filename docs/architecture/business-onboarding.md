# Business onboarding and login UI

FIELD-009/010 implement direct signup, as selected by the product owner, superseding the initial operator-assisted assumption. See [ADR 0007](../adr/0007-business-onboarding.md) for decisions and tradeoffs.

## User flow

1. Open `/onboarding`. Enter business name, a permanent unique business identifier (slug), owner name, email, and password.
2. Submit to create the business and new owner atomically. Successful setup redirects to `/login?created=1`; no credentials appear in that URL.
3. Sign in. `/workspace` lists only active businesses accessible to the current user. Select a business to validate access and show the welcome screen.
4. Sign out to invalidate the server session. Opening the workspace without a session returns to login.

The root page redirects to login. The interface supports narrow mobile and desktop layouts, labeled fields, password visibility, pending states, validation, retryable errors, setup confirmation, and a no-business state. Native form controls and React state are sufficient; no form or global-state framework is introduced for two forms. No operational dashboard is implied.

## API

- `POST /api/v1/onboarding`: public but CSRF-protected, JSON `{businessName, slug, ownerName, email, password}`. Returns 201 with no body. Invalid values or unknown fields: 400 INVALID_REQUEST. Duplicate slug or canonical email: 409 ONBOARDING_CONFLICT. CSRF failure: 403 REQUEST_REJECTED. Failure rolls back all four records. Response contains no user or credential details and signup does not authenticate the caller.
- `GET /api/v1/businesses`: session required, no tenant selection required. Returns an uncached array of `{id, name, slug, role}` for the authenticated user's active memberships in active tenants, ordered by name/ID. No user ID request parameter establishes identity; a forged tenant header does not broaden the results. An authenticated user without eligible memberships receives `[]`.

The [OpenAPI contract](openapi.yaml) also documents these paths. Existing login/logout/CSRF/current-user and tenant context contracts are unchanged.

The slug follows the existing 3–63 lowercase alphanumeric/hyphen policy; it cannot later be changed. Email follows existing global canonical uniqueness. Passwords need at least 15 Unicode code points and at most 72 UTF-8 bytes; they are not trimmed or truncated. No client may provide roles, status, identifiers, or audit fields. Existing-account emails are rejected even if the caller is authenticated; no account linking or second-business creation workflow is implemented here.

## Persistence and security

BusinessOnboardingService in the tenant application layer coordinates TenantService, UserService, PasswordCredentialService, and MembershipService through one Spring transaction. Identity repositories remain internal to identity. Existing V2–V5 tables, foreign keys, and unique constraints are sufficient; adding a migration without a schema change would serve no purpose.

Business discovery queries start with the authenticated user UUID and filter membership and tenant status. Existing memberships(user_id) and tenant primary-key indexes support this query. No additional index is justified by current access patterns. Listing is limited to the current user's memberships; general tenant/user directories are not exposed.

The frontend `/api/v1/[...path]` route allows only the GET/POST paths used by onboarding/login/welcome. It forwards only the FieldOps session cookie, content type, CSRF header, and tenant selection. It preserves backend session-cookie attributes and uses no-store requests/responses, a 15-second timeout, and no automatic redirect following. POST requests with a foreign Origin are rejected; Spring CSRF remains authoritative. Configure server-only API_BASE_URL to the trusted API origin. Local HTTP needs SESSION_COOKIE_SECURE=false; HTTPS deployments keep it true.

## Running tests

- `apps/api`: `./mvnw --batch-mode --no-transfer-progress verify` uses PostgreSQL Testcontainers, never H2.
- `apps/web`: `npm run lint`, `npm run typecheck`, `npm test`, `npm run build`.
- With the API and web servers started and Google Chrome installed: `npm run test:e2e`. Optional E2E_BASE_URL chooses the web origin. Browser tests create uniquely named test businesses in the connected local database; use a disposable development database if those rows are unwanted. Testcontainers tests remain fully isolated. Tracing is disabled to avoid recording credentials/session state.

The browser suite tests successful desktop/mobile onboarding, wrong-password retry then successful login, business entry, logout, anonymous workspace redirect, duplicate signup, and weak-password validation. Proxy tests exercise cookie/CSRF forwarding, path/method restrictions, foreign Origin rejection, safe outages, and response status handling.
