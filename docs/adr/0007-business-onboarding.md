# ADR 0007: Atomic business signup and same-origin login UI

Status: superseded by [email-first identity onboarding](0007-identity-onboarding.md). The combined `/api/v1/onboarding` endpoint and its legacy UI/proxy are removed when integrating the newer flow. Existing accounts and businesses are preserved by migrations V6–V8.

## Context

FIELD-009/010 need a business onboarding flow and usable login screens. The product baseline anticipated operator-assisted onboarding. For this milestone, the product owner explicitly chose direct signup by new business owners. Existing global users, tenant memberships, password sessions, and tenant access checks already supply the persistence and security foundation.

## Decision

Expose CSRF-protected POST `/api/v1/onboarding` accepting business name, stable slug, owner name, email, and password. Create a new Tenant, User, password credential, and ACTIVE BUSINESS_OWNER Membership in one database transaction through existing application services. Assign identifiers, lifecycle, audit values, and role on the server. Reuse existing constraints and migrations; no schema change is needed.

Signup always creates a new identity. A canonical email or slug conflict rolls back all records and returns a generic conflict. Never link a supplied email to an existing user, reset their password, or add them to a business. Existing-account business creation and staff invitations need separate authenticated workflows.

After signup, require ordinary login. Keep the existing session/CSRF protocol and BCrypt policy. The Next.js server proxies only an explicit set of paths/methods, forwards the session cookie and CSRF/tenant selection headers, preserves Set-Cookie flags, disables caching, and does not follow upstream redirects. Credentials are submitted in request bodies and never kept in browser storage or URLs.

GET `/api/v1/businesses` uses only the session's user identity and returns active memberships in active businesses. After login, users explicitly select a business, including when only one is available. The backend validates that selection on `/tenant/context`; it is not a role or tenant claim stored in the session. The welcome screen is the endpoint of this milestone, not an operational dashboard.

## Alternatives Considered

- **Operator-assisted signup:** restricts enrollment, but requires a separate operator/invitation workflow and does not deliver the chosen direct signup experience.
- **Multiple independent creation requests:** simpler individual endpoints, but can leave orphan users, businesses, or credentials. One synchronous transaction fits the monolith.
- **Link existing users by email:** convenient but unsafe without proof of account ownership. Reject duplicates instead.
- **Automatically log in after signup:** saves one action but couples provisioning to session establishment and creates ambiguous recovery when one succeeds and the other fails. Separate login reuses the tested authentication flow.
- **Cross-origin browser API calls or browser bearer tokens:** adds CORS/cookie deployment complexity or a second authentication mechanism. A bounded same-origin proxy retains the existing session design.
- **New Business entity, onboarding table, or workflow engine:** unnecessary because the current transaction has no external steps or partial lifecycle to track.

## Consequences

Signup either commits the complete owner/business setup or commits nothing. Concurrent duplicate requests are arbitrated by PostgreSQL uniqueness. A lost response can leave a completed account; the UI advises trying login before repeating setup. Generic conflict responses do not distinguish email from slug conflicts, but signup success/failure still permits availability inference; there is no claim of complete enumeration resistance.

Email remains unverified. Email verification, invitation acceptance, password recovery, subscription checkout, signup/login abuse controls, and operational workflows remain separate milestones. Do not claim public-production readiness: public deployment needs deliberate enrollment abuse controls and recovery. No platform administration, customer, work-order, or billing feature is added.

PostgreSQL Testcontainers test successful signup/login, normalization, owner access, rollback, uniqueness races, invalid/forged input, CSRF, and business-list isolation. Browser tests cover desktop/mobile signup, login retries, business selection, logout, validation, and conflicts.
