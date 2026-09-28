# ADR 0007: Separate account identity, business creation, and invitations

Status: Accepted and implemented for the identity-onboarding milestone, 2026-09-27.

## Context

The checked-out baseline has global users, membership roles, session authentication, and tenant APIs through V5. It has no combined signup endpoint or login UI. The earlier [proposal](../architecture/identity-onboarding-experience-proposal.md) describes a later baseline that is not present in this checkout; its references to the former business-onboarding ADR and frontend components are historical, not migration inputs.

People must be able to own one business and join another using the same identity. An existing account must not be replaced or assigned a password through an invitation. Email ownership, account state, membership access, and setup readiness are distinct facts.

## Decision

Keep the existing global User, tenant Membership, and Spring Security session model. Introduce email-first registration: a 24-hour challenge creates no User until the mailbox holder explicitly chooses a password. Store SHA-256 digests of 256-bit random link tokens. Existing users remain unverified and can request verification while authenticated. Verified identity is required for business creation, invitation acceptance, and tenant operations.

Create a business, its initial owner membership, and incomplete business setup atomically. Invitations create neither identities nor memberships. Accept an invitation using verified recipient identity, recheck live issuer authority, and serialize changes using the existing tenant-row lock. Existing roles are retained; inactive relationships cannot be revived by invitations. Platform grants remain independent of tenant membership. Assisted creation produces a PROVISIONING tenant that is activated only through initial-owner acceptance.

Use new Flyway migrations V6–V8, retaining released migrations and all existing records. Use JPA for existing entities and explicit transactional JDBC for challenge consumption, invitation state, audit, and progress records. Database constraints and locks remain authoritative; no generic persistence layer or workflow engine is introduced.

Business checklist version 1 validates the implemented business name, slug, and current active owner. Team invitations are optional. Membership progress has its own schema, but technician/dispatcher checklists and completion endpoints wait for actual domain requirements. Do not invent setup screens to fill those tables.

Add SMTP delivery after commit, audited delivery failures, and manual rate-limited resend. No Kafka, Redis, asynchronous executor, or durable mail queue is added. The bounded limiter is instance-local. Platform HTTP operations default to disabled; enable only in a restricted operator environment until stronger authentication is provided.

Use tenant UUID routes with validated per-request X-Tenant-ID. Per-tab, user-keyed storage holds only a selection preference and allowlisted continuation intent. The same-origin Next.js proxy forwards only approved paths, headers, and the session cookie. Registration and invitation tokens never enter persistent browser storage.

## Alternatives Considered

- A combined account/business transaction prevents existing users from creating another business. Separate verified identity creation avoids that coupling.
- Creating an unverified user with an attacker-chosen password before mailbox proof introduces account pre-creation and recovery risks.
- Tenant-specific identities or a global business role cannot represent different roles across businesses.
- A session-wide active tenant breaks independent tabs. JWTs or Redis add no required capability here.
- One global onboarding boolean has the wrong scope; configurable workflows add complexity without a checklist requiring them.
- A durable email outbox would improve retry guarantees, but requires delivery state and a worker. Rate-limited resend and audit are the explicit first increment.

## Consequences

Existing users with credentials must verify before entering tenant APIs; migration never manufactures verification. Legacy users without credentials still require trusted credential provisioning before this authenticated transition. Password recovery remains separate work.

SMTP is a new real dependency for registration and invitation links. A process crash after commit can lose delivery; a mail transport failure does not roll back committed records. Operators inspect delivery audit outcomes, and users request another email. The aggregate health endpoint continues to measure the application/database, not SMTP availability.

Platform provisioning does not create operator membership. Bootstrap grants are controlled, audited SQL operations with no default administrator. Platform endpoints must remain disabled on public deployments until MFA or equivalent stronger authentication is implemented.

See the [implementation contract](../architecture/identity-onboarding.md) for APIs, limits, operator steps, tests, and deferred work.
