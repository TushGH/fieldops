# Identity onboarding implementation

Implemented 2026-09-27. [ADR 0007](../adr/0007-identity-onboarding.md) records the decisions; the [proposal](identity-onboarding-experience-proposal.md) remains the broader design reference. Integration with main replaces the earlier combined account/business signup endpoint (`/api/v1/onboarding`) and its frontend proxy with the email-first flow. Existing users, credentials, memberships, and businesses are preserved; legacy users must verify their email.

## Delivered workflows

1. **Register:** `/signup` collects display name and email. POST `/auth/signup` returns generic 202, stores a 24-hour challenge, and attempts email after commit. No User, credential, or business exists yet. An existing email receives the same response but no replacement challenge or credential.
2. **Complete registration:** open `/signup/complete#token=...`, choose a password, and explicitly submit. One transaction consumes the challenge and creates the verified User and BCrypt credential. Password rules remain at least 15 Unicode code points and at most 72 UTF-8 bytes. The user then signs in through the existing session protocol.
3. **Legacy verification:** an existing unverified user may log in and access `/auth/me`, CSRF, logout, and verification. Tenant access, business creation, and invitation acceptance require verification. The confirmation challenge must match both authenticated User UUID and canonical email. Users without a local credential need the existing trusted provisioning procedure first; registration cannot overwrite their identity.
4. **Create business:** a verified signed-in user supplies only name and slug. User ID comes from the authenticated identity. Tenant, BUSINESS_OWNER membership, and incomplete business progress commit together. A slug conflict rolls everything back. The same user may create another business.
5. **Join business:** an owner issues an invitation without creating a User or membership. The recipient signs in or registers, verifies their email, and explicitly accepts. Inbox recovery works without the original token and across devices. The public token resolver returns only an invitation UUID, never recipient/business metadata.
6. **Assisted provisioning:** an independently authorized platform operator creates a PROVISIONING tenant and initial-owner invitation. No operator membership is created. Valid owner acceptance creates membership and activates the business atomically. Reissuing invalidates previous pending initial-owner offers. Grant revocation blocks unaccepted offers.
7. **Business readiness:** owners review the stored name/slug and confirm checklist version 1. The server validates a live active business, the real business fields, and current active ownership. Inviting a team is optional. Setup is shared by all owners in that business; another business remains independent.

## Migration inventory

| Migration | Changes | Existing-data behavior |
| --- | --- | --- |
| V6 | Nullable users.email_verified_at; email_challenges; security_audit_events | Users remain unverified; credentials unchanged |
| V7 | PROVISIONING tenant state; platform_role_grants; invitations | Tenant states and memberships unchanged; nobody receives a platform grant |
| V8 | business_onboarding; membership_onboarding | Existing tenants get version-zero incomplete progress; no membership completion is fabricated |

V1–V5 are unchanged. Hibernate remains `ddl-auto=validate`. IDs, canonical email uniqueness, tenant slugs, credentials, and membership pairs survive upgrades. New records use UUIDs, restrictive foreign keys, timestamp evidence, and nonnegative optimistic versions. Progress tables couple positive completed versions with nonnull completion timestamps.

Challenges and invitations store unique 64-character lowercase SHA-256 digests, never raw tokens. Invitations constrain roles, purposes, state-specific actors/timestamps, and one PENDING offer per `(tenant_id, email)`. The partial index does not use `now()`; issuing a replacement first marks elapsed offers EXPIRED. Every acceptance independently checks expiry. Tenant administration and inbox indexes follow their actual query shapes.

## API additions

All paths below are relative to `/api/v1`. Cookie-authenticated mutations, including public signup, completion, and token resolution, require CSRF. Fetch a fresh token after authentication changes. The [OpenAPI contract](openapi.yaml) includes payload/response schemas.

| Method/path | Permission | Result |
| --- | --- | --- |
| POST /auth/signup | Anonymous + CSRF | Generic 202; displayName/email request |
| POST /auth/signup/complete | Anonymous + CSRF | 204; token/password request |
| POST /auth/email-verification/request | Active authenticated account | Generic 202 |
| POST /auth/email-verification/confirm | Matching authenticated account | 204; token request |
| GET /auth/me | Active authenticated account | Adds nullable emailVerifiedAt and platformAdmin to the existing identity DTO |
| GET /businesses | Verified user | Eligible ACTIVE business/membership choices |
| POST /businesses | Verified user | 201; name/slug request; business summary |
| POST /invitations/resolve | Anonymous + CSRF | Non-secret invitation id from a live link token |
| GET /invitations | Verified user | Up to 100 newest unexpired pending invitations addressed to this identity |
| GET /invitations/{id} | Matching verified recipient | Invitation details; wrong account gets 404 |
| POST /invitations/{id}/accept | Matching verified recipient | Tenant/membership IDs; no prior membership required |
| GET /tenant/invitations | Current business owner | Up to 100 latest business invitations |
| POST /tenant/invitations | Current business owner | 201; email/role request |
| POST /tenant/invitations/{id}/revoke | Current business owner | 204; never reverses an already accepted relationship |
| POST /tenant/invitations/{id}/resend | Current business owner | Replacement invitation and fresh token; old pending token revoked |
| GET /tenant/onboarding | Validated tenant member | Current readiness and version-one checklist |
| POST /tenant/onboarding/business/complete | Current business owner | Server-validated shared business progress |
| GET /platform/access | Current platform grant; feature enabled | Platform access confirmation |
| POST /platform/businesses | Current platform grant; feature enabled | 201; name/slug/email request; initial-owner invitation |
| POST /platform/businesses/{id}/owner-invitation | Current platform grant; feature enabled | Replace initial-owner invitation for a PROVISIONING business |

Tenant paths require exactly one X-Tenant-ID and enforce the existing per-request context. Other paths do not infer tenant authority from headers. Public responses do not reveal whether an email exists. Errors use code/message DTOs: 400 invalid input/link, 401 missing authentication, 403 verification/permission denial, 404 missing or inaccessible recipient/scoped resource, 409 conflict or unavailable invitation, and 429 rate limit. Concurrent email uniqueness is enforced by PostgreSQL; a complete-registration conflict cannot replace an existing credential.

## Concurrency and access rules

Invitation creation, revocation, replacement, acceptance, and existing membership administration serialize on the tenant row. Acceptance then locks the invitation row and rechecks state, expiry, tenant eligibility, recipient email, and the issuer's current active owner membership/user. Initial-owner acceptance instead locks and checks the live platform grant and grant-holder account. Platform grants never satisfy tenant membership checks.

An already-active relationship retains its existing role. Inactive relationships are rejected and must use the existing authorized reactivation operation. Repeated acceptance by the same recipient returns the existing active relationship. After suspension or deactivation, even an accepted invitation cannot restore access. A race with revocation yields either accepted membership or a revoked invitation without membership; it cannot yield a revoked invitation that later grants access.

Onboarding completion does not grant permissions. No dispatcher or technician checklist exists yet, so membershipFlows is empty and those users are never sent to an invented setup screen. The membership progress table is ready for a later flow key/version without leaking completion between businesses.

## Browser behavior

Routes are `/login`, `/signup`, `/signup/complete`, `/verify-email`, `/create-business`, `/select-business`, `/invitations`, `/invitations/accept`, `/app/[tenantId]`, `/app/[tenantId]/onboarding/business`, and `/platform`. `/workspace` resolves the appropriate destination; `/onboarding` enters business creation and sends anonymous users to login.

After login: verification first, then explicit invitation/create-business intent, then eligible businesses. One business is automatically selected and validated. Multiple businesses restore a currently eligible user-keyed preference or show selection. Zero businesses show pending invitations, the platform console for a platform-only operator, or a create-business empty state. An operator with tenant membership can explicitly enter the platform console from business/account navigation.

Tokens arrive in URL fragments and are removed after extraction/resolution; confirmation/acceptance requires a POST initiated by the user. Tokens are kept only in component memory. Refreshing a password/verification link page after fragment removal requires reopening the email. sessionStorage contains only `create-business`, an invitation UUID, current user UUID, and a user-keyed tenant preference. It never contains credentials or bearer links. Wrong-account users can sign out and reopen the link or recover it through their own inbox.

The same-origin proxy allows only implemented frontend endpoints, rejects cross-origin mutations and payloads above 16 KiB, forwards only the FieldOps cookie/CSRF/content type/tenant header, preserves rotated cookies, refuses redirect following, and uses a 15-second timeout and no-store responses. It has no arbitrary destination or return URL. Referrer-Policy is no-referrer and pages use no third-party assets.

Business selection is per tab, never written to the server session. Tenant loads use AbortController, and switching unmounts the old page so old loads cannot populate the selected business. There is no shared tenant query cache. Logout invalidates the server session and clears FieldOps browser state. Backend authorization remains authoritative on every request.

## SMTP, limits, audit, and operation

Set these environment variables in the root `.env` and export them before running the API:

- `WEB_ORIGIN`: exact browser origin, default `http://127.0.0.1:3000`; links never derive their origin from a request Host header.
- `MAIL_HOST`, `MAIL_PORT`: SMTP host and port, default loopback port 1025 for a locally supplied SMTP inbox.
- `MAIL_FROM`: sender address; default `fieldops@localhost` is local-only.
- `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_AUTH`, `MAIL_STARTTLS`: configure an authenticated TLS transport for the chosen provider. Keep passwords out of source control.
- `PLATFORM_ENABLED`: defaults false. Enable only for a restricted operator environment. Public privileged access requires stronger authentication/MFA in a subsequent milestone.

This repository does not start an SMTP server or send real test emails. Configure your local SMTP inbox/provider before using registration. SMTP calls have bounded connect/read/write timeouts and run only after the database commit. Delivery outcomes are audited without message bodies, emails, tokens, hashes, cookies, or credentials. Users can retry signup/verification or owners can resend invitations. Automatic retries and durable delivery are deliberately not claimed; process failure after commit can lose a send.

The current single-instance limits are 100 relevant POST attempts per network peer per 15 minutes, 20 login attempts per normalized email, five registration attempts per email, five verification sends per user, 30 invitations per issuing user, and five invitations per recipient email. Window storage is capped at 10,000 keys and fails closed when full. Windows expire rather than permanently locking accounts. Limits reset on restart and are not a multi-instance rate limiter. The proxy makes the backend's peer the Next.js server; the network limit is therefore a conservative shared budget for this local deployment. A real gateway must supply independently enforced client-network limits before public rollout; arbitrary forwarding headers are not trusted.

Audit records cover registration challenges/completion, verification, login outcomes, business creation, invitations, business readiness, delivery outcomes, and rejected API requests. Each event receives a generated correlation UUID; there is no cross-service tracing claim. Audit is transactional with successful domain mutations, and delivery/rejection/authentication events use independent transactions. SMTP is excluded from aggregate Actuator health; inspect EMAIL_DELIVERY failures separately.

Initial platform authority is granted through the reviewed operator script [grant-initial-platform-admin.sql](../../scripts/grant-initial-platform-admin.sql), run with `psql -v email=operator@example.com -f scripts/grant-initial-platform-admin.sql` against the intended database using operator credentials. It requires an existing active verified identity, creates no user/password/membership, and inserts audit evidence atomically. It does not reactivate a revoked grant. No public grant-management endpoint exists.

Retain abandoned PROVISIONING tenants and expired/revoked challenges/invitations for operator review in this milestone. There is no automatic deletion, retention job, or irreversible cleanup. Define a production retention duration and recovery process before public launch.

## Validation and deferred work

PostgreSQL 17.9 Testcontainers tests cover fresh application startup, V5 upgrade with unchanged identity/credential/membership data, null verification, incomplete progress, constraints, existing/new recipient flows, concurrent normalized registration, replay/expiry, verified-only creation, slug rollback, CSRF, wrong-recipient rejection, issuer loss, suspension, role preservation, inactive memberships, concurrent acceptance/revocation, platform separation/reissue/revocation, scoped readiness, and mail-failure recovery. Existing authentication and tenant-isolation suites remain regression gates.

Frontend Node tests exercise the proxy allowlist, origin checks, session/CSRF forwarding, cookie rotation, payload bounds, health contract, and safe failures. Playwright exercises the real rendered production frontend with deterministic API fixtures; PostgreSQL/HTTP behavior is tested separately by Testcontainers. These browser tests are not a claim of full SMTP-to-browser integration. Run `npm run build` before `npm run test:e2e`; install Chromium with `npx playwright install chromium` or set PLAYWRIGHT_CHROMIUM_EXECUTABLE to a local Chrome executable.

Deferred: technician/dispatcher domain setup and completion endpoints, timezone/hours/contact details until their features require them, password recovery and breach screening, MFA, public platform administration, distributed throttling/session persistence, durable email retry, invitation-history pagination beyond the current latest-100 lists, audit browsing, and production retention automation. No customer/work-order features, Redis, Kafka, Kubernetes, or AI were introduced.

### Verification result (2026-09-27)

| Check | Result |
| --- | --- |
| `./mvnw --batch-mode --no-transfer-progress verify` with Java 25 and Docker | 95 tests passed, zero failures/errors/skips; executable JAR built |
| `npm run lint` | Passed with zero warnings |
| `npm run typecheck` | Passed |
| `npm test` | 9 proxy tests passed |
| `npm run build` | Production frontend built successfully |
| `npm run test:e2e` against the production build and local Chrome | 9 browser scenarios passed |
| OpenAPI YAML parsing and internal reference resolution | Passed |
| `git diff --check` | Passed |

The backend integration tests used isolated PostgreSQL Testcontainers and an in-memory mail transport; they did not use or mutate the Compose development database or send external email. Browser fixtures isolate UI behavior from those backend tests. SMTP-provider delivery and hosted GitHub Actions execution were not exercised locally.
