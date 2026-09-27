# FieldOps product definition

Status: proposed product baseline for Phase 0. All capabilities below are planned, not implemented.

## Product vision

Help service businesses deliver dependable work from the first customer request through payment tracking. FieldOps should provide a shared operational record so owners, dispatchers, and technicians can answer: what needs doing, who is responsible, when will it happen, and has it been paid?

Build a credible startup product incrementally while explaining its engineering through a YouTube series. Validate workflows before investing in advanced automation or infrastructure.

## Target customers

The initial customer is a small field-service business with an owner, one or more people coordinating work, and technicians working at customer locations. Examples include HVAC, plumbing, electrical, appliance repair, and cleaning businesses.

The initial focus is businesses moving beyond spreadsheets and phone-based coordination, with straightforward dispatching and invoicing needs. Enterprise franchises, complex multi-region operations, and regulated specialist workflows are outside the initial target. A tenant represents one service business; a customer receiving service is a record within that tenant, not another tenant.

## User personas

| Persona | Need and expected workflow | Initial access boundary |
| --- | --- | --- |
| `BUSINESS_OWNER` | Oversees work, staff access, scheduling, invoices, and payment status for the business. May also perform dispatching in a small team. | Authorized business records and staff management within their tenant. |
| `DISPATCHER` | Receives requests, maintains customer details, creates work orders, books appointments, and assigns technicians. | Tenant operations appropriate to dispatching; no platform administration. |
| `TECHNICIAN` | Checks assigned visits, reads service details, updates job progress, and records completion notes. | Assigned work and the customer details necessary to perform it; no general financial or staff administration. |
| `CUSTOMER` | Requests service, coordinates a visit, receives updates, and reviews invoice/payment information. | Initially interacts through staff and communications; authenticated self-service is deferred. Future access must be limited to their own records. |
| `PLATFORM_ADMIN` | Onboards tenants and supports platform operations. | Explicit platform permissions; this role must not imply unrestricted access to every tenant's business data. Any future support access needs deliberate authorization and auditing. |

Personas describe product needs, not a commitment to implement every login or permission immediately. Start with permissions required for each milestone. `ACCOUNTANT` is a future specialist role, not part of the initial permission model.

## Primary user journeys

1. **Onboard a business:** a platform administrator establishes a tenant and its owner. The owner brings dispatchers and technicians into that tenant with appropriate access. Initial onboarding may be operator-assisted; public signup and subscription checkout are deferred.
2. **Capture a service request:** a customer contacts the business; an owner or dispatcher finds or creates the customer and service address, records the issue, and opens a work order. Staff confirm or cancel it through allowed operations.
3. **Plan and assign a visit:** a dispatcher selects an appointment window and eligible technician, checks availability, and confirms the assignment. Conflicts are rejected or resolved explicitly. Rescheduling retains a clear record of the change.
4. **Perform the work:** the assigned technician views the visit and relevant customer information, marks progress, and records completion notes. Important state changes are validated and recorded so office staff can see reliable status.
5. **Invoice and track payment:** authorized office staff generate an itemized invoice for completed work, communicate it to the customer, and record payment received outside FieldOps. Invoice and payment records determine financial status; a work-order label cannot manufacture a payment.
6. **Follow up:** staff see upcoming visits, unfinished work, and unpaid invoices in operational lists. Notifications and reminders help keep customers and technicians informed without replacing the underlying records.

## MVP scope

The MVP supports an end-to-end workflow for multiple isolated businesses:

- Tenant onboarding and staff authentication, with backend-enforced role and resource authorization.
- Customer contact details and service addresses; technician profiles and basic availability.
- Work-order creation, detail, list/filter views, explicit lifecycle operations, assignment, and status history.
- Appointment scheduling and rescheduling, business timezone handling, and protection against technician double booking.
- A responsive technician view for assigned jobs, progress updates, and completion notes; online connectivity is assumed.
- Itemized invoices and recorded payments, including outstanding balances. Begin with one configured currency per tenant, without conversion or cross-currency invoices. Provider-based payment collection is not required for MVP completion.
- Essential notifications and appointment reminders, with delivery status and safe retries when delivery is introduced.
- Basic operational views for upcoming work and unpaid invoices, plus auditing of important changes.
- Validation, useful errors, tenant-isolation tests, deployment and recovery documentation, and operational visibility sufficient for an initial pilot.

Detailed permission matrices, scheduling rules, invoice policies, and notification channels will be specified before their corresponding implementation milestone. Jurisdiction-specific tax calculation is not promised by this baseline.

MVP acceptance means two tenant businesses can independently complete the request-to-payment-tracking journey, unauthorized access is rejected, scheduling conflicts are handled, and important automated side effects can be retried without duplicate business results. A scripted pilot and critical automated journeys should demonstrate this before v1.0.

## Explicitly out of scope for the MVP

- Native mobile apps, offline synchronization, live GPS tracking, route optimization, or automated dispatching.
- A customer self-service portal, public booking marketplace, and public tenant subscription checkout.
- Payroll, inventory procurement, full accounting, automated tax compliance, multi-currency conversion, and complex franchise management.
- Advanced analytics, configurable workflow builders, and broad third-party integration catalogs.
- AI classification, recommendations, assistants, or autonomous customer-facing actions.
- Microservice decomposition, Kafka, Kubernetes, and infrastructure expansion without a concrete requirement. These are architecture choices, not customer deliverables.

## Future product opportunities

After validating the core workflow, investigate customer self-service booking, hosted online payments, service catalogs and recurring maintenance plans, attachments, accounting integrations, customer feedback, and richer reporting. Mobile/offline support and route planning should follow evidence from field users.

AI-assisted request classification or technician suggestions may eventually help, but only after the workflow works reliably without AI and consequential actions have appropriate validation and human approval where necessary.

Track potential product outcomes such as request-to-scheduling time, missed appointments, technician adoption, completion-to-invoice time, and overdue invoice volume. Establish baselines with pilot users before setting numerical targets; these are hypotheses to validate, not current metrics.
