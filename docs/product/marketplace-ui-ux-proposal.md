# Marketplace and role-specific workspace proposal

Status: proposed, 2026-09-28. This document changes no application behavior. Implementation is frontend-only; Spring APIs, API contracts, migrations, and the database remain unchanged.

## Review and scope

Reviewed README, repository/frontend instructions, PRODUCT, ROADMAP, all ADRs, architecture/domain/authentication/tenant-access documents, the historical and implemented onboarding documents, and OpenAPI. Also inspected the current account forms, business workspace, routing, styles, and frontend API proxy. Applied the frontend-design skill.

The current implementation contract and actual API take precedence over historical milestone statements. PRODUCT currently excludes public marketplace and customer self-service from MVP. This request proposes bringing their presentation and interaction design forward; it does not make those backend capabilities available or silently redefine them as completed.

The existing backend already requires a verified, authenticated user to create a business. The confusing part is the public “Create a business” link, which starts signup. Remove that public action and make business creation an explicit action inside the signed-in account.

## One account, several contexts

Do not ask a person to choose a permanent owner, employee, or customer role when registering. A person can own one business, work for another, and purchase services personally.

Use a persistent context switcher:

```text
Personal — My service requests
My businesses
  Oak Plumbing        Owner
Businesses I work with
  Northside Heating   Technician
Create business
```

Business entries come only from the authenticated user's eligible businesses. Validate the selected tenant context before rendering its role-specific workspace. Never let a UI dropdown manufacture a business role. Personal is a frontend navigation context, not a new CUSTOMER membership or an authorization grant. Platform administration remains separate and appears only through its existing access checks.

Keep selection per tab and keyed by user; cancel prior tenant loads when switching and clear user-specific state on logout. A person with multiple contexts chooses or resumes an eligible context without being permanently forced into their owner role.

## Public marketplace

| Page | Proposed content and interaction |
| --- | --- |
| Home `/` | Service-and-location search, service categories, how it works, selected example providers, and a section explaining the business tools |
| Find services `/services` | Search results, category and service-area filters, sort, result count, clear filters, and useful no-results state |
| Provider `/services/[slug]` | Services offered, service area, description, and a request-service entry point; published provider data only when available |
| Features `/features` | Explain customer requests, owner operations, and field-worker workflows; distinguish available account/team capabilities from previews |
| For businesses `/for-businesses` | Explain managing a service business; anonymous CTA is “Sign in,” with account registration available through login |
| About us `/about` | FieldOps purpose, intended users, and product principles; no invented company history, staff, customers, or traction |
| Contact us `/contact` | Support and business-inquiry guidance; use a real owner-supplied contact address when available, with no pretend form submission |

Header: Find services, For businesses, Features, About us, Contact us, Sign in. On mobile use an accessible menu. Footer repeats useful navigation and includes genuine contact information when supplied.

The landing headline can be “Find the right help for the work at hand.” Pair it with service and location fields, then plumbing, heating and cooling, electrical, cleaning, and appliance repair categories. Browsing should not require login. Account actions and request submission should.

Do not present private tenant metadata as a public marketplace directory. `GET /businesses` lists the current user's memberships, not publicly listed providers. Search is an interactive, visibly labeled sample catalog in this phase. Omit unsupported ratings, verified-provider badges, live availability, distance calculations, guarantees, and invented prices.

Contact submission has no existing API. Build the page now; enable an “Email us” link only when a real contact address is supplied. A visual form, if included in the preview, must say it does not send a message.

## Login-first business creation

1. Anonymous visitors browse the public site and choose Sign in.
2. Login includes “Create an account” for new users. Registration remains name/email, verification email, password creation, then login.
3. After authentication, verification requirements and explicit invitation continuation retain priority.
4. With no explicit intent, restore a valid previous context. New users with no businesses enter their personal home, with invitations visible and an optional “Create business” action.
5. A user with business access and no saved context sees a context chooser with Personal and their actual businesses. Avoid adding this chooser on every visit.
6. “Create business” appears in signed-in navigation and the account/context chooser. Submit only the current API's business name and slug. Suggest an editable slug from the name and explain it as a stable business identifier.
7. Keep owner setup and optional team invitations after successful creation. Do not imply that creating a tenant publishes a searchable marketplace listing.

Direct anonymous visits to `/create-business` or legacy `/onboarding` go to login without showing the business form. Preserve only an allowlisted create-business intent so the user can continue after login and verification. Remove anonymous business-signup links and neutralize the old `/signup?intent=create-business` shortcut in normal public navigation; direct legacy links should pass through the login-first entry.

Invitation links retain the existing safe continuation behavior. A newly registered account is not silently logged in, and registration does not assign a business role.

## Dashboards

### Business owner: business operations

Navigation: Overview, Work orders, Team, Reports, Business settings. Schedule can be a section of Work orders until it merits its own screen.

The first screen prioritizes work needing attention, followed by the current work list and a compact team summary. Show a date-range control and a small metrics strip: open work, completed work, team members, and outstanding invoices. Operational and financial values are preview-only until the relevant APIs exist; do not display fabricated zeros as live facts.

Work orders: search, status/assignee/date filters, sortable list, and detail view with customer request, assigned worker, schedule, and status history. Reports: completion trend, workload by employee, and invoice/payment summaries with explicit date ranges and definitions. Start with a few useful reports, not an arbitrary “all reports” catalog. Revenue cannot be inferred from completed jobs.

Team: active memberships, roles, status, invitations, resend/revoke, and supported membership administration. This is access management today, not payroll, employee profiles, skills, or availability.

### Employee: work to do

Technician navigation: Today, Upcoming, Completed, Account. The primary content is the next assigned visit, then the day's ordered job list. Details show service summary, appointment window, address, and completion notes in the preview. Use large touch targets and a mobile-first layout. No invented GPS, route optimization, or staff-wide customer directory.

Dispatcher navigation: Overview, Work orders, Schedule, Account. Emphasize unassigned requests and scheduling conflicts in the preview. Dispatchers need business-wide coordination rather than the technician's “my work” list. Keep owner-only team administration and financial reporting out of this view.

Both experiences use the actual membership role. Work assignment and completion are not enforced by frontend filtering; until scoped APIs exist, all work-order interaction is sample-only.

### Customer: find help and track requests

Navigation: Overview, Find services, My requests, Account. Use “service request” in customer copy and “work order” in business operations.

Overview shows upcoming visits first, active requests with understandable status, and completed service history. Keep service search visible. A request detail includes provider, issue, requested date, and a timeline. An appointment is only confirmed when the business confirms it; submitting a preferred time never promises a booking.

Proposed request flow: select provider/service, describe the issue, enter service address, choose a preferred date/window, review, submit. For this frontend-only phase, the preview ends with “Preview complete — no request was sent.” Do not create a fake server ID or pretend a business was notified. Use synthetic addresses in sample journeys and do not persist personal request data in browser storage.

## What can be live now

| Area | Existing support | Frontend delivery |
| --- | --- | --- |
| Login, signup, verification, logout | Existing identity endpoints | Fully functional redesigned screens |
| Create/select business, owner setup | Existing business/context/onboarding endpoints | Fully functional login-first flow |
| Invitation inbox and owner invitations | Existing recipient and tenant invitation endpoints | Fully functional account/team pages |
| Business name and membership administration | Existing tenant and membership endpoints | Live where supported; narrowly extend the Next proxy allowlist |
| Employee names/contact profiles | Membership DTO returns user IDs, role/status, not a directory of names/emails | Show membership reference and available metadata; no invented identity join |
| Team size | Paginated membership total is available | Label accurately as memberships; do not equate all memberships to active employees |
| Public business catalog/search/profile | No public directory or publish-listing API | Separate sample catalog and filtering preview |
| Customer requests and worker job lists | No work-order/customer self-service APIs | Interactive sample views and honest unavailable live states |
| Operational charts, invoices, revenue | No reporting/billing APIs | Sample reports only; unavailable values in live workspaces |
| Contact message delivery | No endpoint | Static contact content; real mail link if supplied |

The Next proxy currently excludes membership routes even though Spring supports them. Add only exact existing routes/methods, including PUT for role changes, and forward only validated page/size query parameters for membership pagination. Preserve CSRF, cookie forwarding, origin checks, payload limits, and no-store behavior. This is frontend integration, not a Spring API change.

Use an explicit “Preview with sample data” entry for unavailable operations. Sample catalog/provider pages display “Sample providers — bookings are not available yet.” Live screens must not silently substitute fixtures on API failure. Show an error with retry when a real request fails; show a genuine empty state only when a supported request succeeds with no records.

Use small typed fixture modules passed into presentation components. Keep sample navigation isolated from authenticated API actions and never store sample users or roles in the real account context. No mock backend, new service, database seed, or browser persistence layer is needed.

## Visual direction

Aim for a practical local-services marketplace: clear search, recognizable trade categories, approachable provider presentation, and compact operational lists. Keep public discovery spacious and business workspaces denser while sharing typography and controls.

| Token | Value | Use |
| --- | --- | --- |
| Paper | `#FFFFFF` | Primary surfaces |
| Mist | `#F3F6F8` | Page canvas and alternate sections |
| Ink | `#173047` | Main text and navigation |
| Service blue | `#175CD3` | Primary actions and focus emphasis |
| Evergreen | `#237A57` | Successful/complete states with text labels |
| Amber | `#D99A16` | Attention accents, never small white text |

Typography: locally hosted Barlow Semi Condensed for brief display headings, Source Sans 3 for body, forms, and operational data; system sans-serif fallback. Proposed scale: 14/16/20/28/40/56 px with responsive headings, readable line heights, and body lines under 80 characters. Use tabular numerals for numeric columns. Font files should be included with licenses and require no third-party browser request.

Layout: left-aligned content, a centered maximum-width public container, clear section rhythm, modest radii, borders only where grouping needs them, and restrained shadows for overlays. Trade imagery or illustrations should depict actual service tasks. Avoid generic handshake photos and fabricated provider portraits.

Two homepage options considered:

```text
A — search leads (recommended)
[Brand | Services | Features | About | Contact | Sign in]
[Find the right help for the work at hand.              ]
[Service                         | Location | Search   ]
[Plumbing | Heating | Electrical | Cleaning | Appliances]
[Provider examples and service areas                    ]
[How requests work]       [Tools for service businesses ]
[Footer                                                ]

B — business software leads
[Brand | Features | About | Contact | Sign in]
[Large dashboard illustration | Business pitch]
[Search for services further down the page   ]
```

Choose A because the requested public experience is a marketplace. B would preserve the current business-software emphasis and bury customer discovery. Instead of repeating metric cards throughout, use lists and timelines where users need to act.

```text
Owner desktop
[Context switcher     | Business overview | Account]
[Overview   ][Date range | Compact metrics strip   ]
[Work orders][Needs attention | Team summary       ]
[Team       ][Work-order list with filters         ]
[Reports    ][Completion trend and recent activity ]

Employee mobile              Customer mobile
[Business / role     ]       [Personal / account    ]
[Next assigned visit ]       [Find a service        ]
[Today's ordered jobs]       [Upcoming visit        ]
[Upcoming | Completed]       [Active requests       ]
[Today | Jobs | Me   ]       [Home | Search | Requests]
```

Design review against the brief: keep one shared brand, vary information hierarchy by job, and make search the public focal point. An owner needs exceptions and workload; a technician needs the next action; a customer needs service discovery and reassurance about progress. Three differently colored copies of the same dashboard would not meet the requirement.

## Frontend organization

Retain Next.js, TypeScript, Tailwind, the existing API helper, and Playwright. Split the current large BusinessExperience into focused account, business-creation, invitation, context-selection, and dashboard components as these screens change. Add small shared controls only where repeated usage justifies them. No new state library or charting dependency is required for this first UI iteration.

Use route groups to separate public, account, personal, and business layouts without changing working email-link URLs. Keep `/app/[tenantId]` as the role-resolving business entry, with `/work-orders`, `/team`, and `/reports` subroutes. Introduce `/my` for the personal dashboard and `/my/requests` for the customer preview. Keep `/workspace` as the authenticated destination resolver and `/select-business` as a compatible context chooser. Preview pages can live under `/preview` and never establish real permissions.

Use explicit owner, technician, and dispatcher dashboard components selected after context validation. Share job rows, filters, status labels, and detail layout where appropriate; avoid a generic dashboard schema or a new backend abstraction.

## Implementation sequence and acceptance

1. Establish visual tokens and public/account/business shells; build Home, Features, For businesses, About, and Contact.
2. Remove public business-creation actions, refresh account screens, and implement login-first continuation plus the personal/business context switcher.
3. Build owner, technician, dispatcher, and personal dashboard layouts. Connect supported account, business, team, and invitation operations.
4. Add clearly separated sample search/provider pages, request journeys, job lists, and reports. Include populated, empty, loading, and error presentations.
5. Review screenshots at 390 px mobile, tablet, and desktop widths; refine density, keyboard flow, and content before final validation.

Acceptance tests: anonymous users cannot see/submit business creation; login and verification preserve intended destination; one person can own A and work in B with distinct navigation; customer view is available without granting business access; revoked membership and expired sessions produce recovery; switching cancels stale tenant loads; invitations and logout continue working; public search filters and empty states behave consistently; preview actions never mutate real data; missing APIs never appear as successful live operations.

Accessibility: semantic headings/landmarks, labeled controls, visible keyboard focus, accessible switcher/menu behavior, text with status colors, readable contrast, reduced motion, touch targets, and no horizontal overflow at mobile widths. Provide textual/table equivalents for preview charts.

Run frontend lint, typecheck, Node proxy tests, production build, and Playwright. Exercise the real signup/email/login/business-create flow against local Mailpit after changing authentication navigation. Do not modify backend code or database schema. Product/roadmap notes should explicitly identify delivered UI previews versus future live marketplace capabilities.

## Inputs for final content

The design can proceed with the choices above. A real support email, service launch geography, and approved public brand imagery are needed before publishing factual contact or coverage claims. Until supplied, avoid inventing those facts. Real search, customer-owned requests, employee assignments, and reports require separately authorized backend milestones later.
