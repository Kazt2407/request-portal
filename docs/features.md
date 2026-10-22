# Feature specification

Request Portal lets students and lecturers submit administrative requests (certificates, confirmations,
leave, and so on) to a university, and lets department staff process them through a configurable chain
of approval steps. The domain is modeled on a real university e-services portal; this project is an
independent reimplementation with its own design, code and sample data.

Each feature lists the service that owns it and the roadmap phase that delivers it.

## Actors

| Role | Who | Can do |
|---|---|---|
| `STUDENT` | Signs in with a student email | Submit and track own requests, download pre-filled forms |
| `LECTURER` | Signs in with a staff email, no processing role | Same as student, with lecturer-only templates |
| `STAFF` | Member of a department | Process request steps assigned to their department |
| `ADMIN` | System administrator | Manage templates, forms, accounts and departments |

Role defaults come from the email domain at first sign-in; admins can change them later.

## 1. Identity and organization — `org-service` (phase 2)

- Sign-in through Keycloak (OpenID Connect); see [ADR 0003](adr/0003-identity-with-keycloak.md).
  An account is created on the user's first call to `GET /api/v1/accounts/me`.
- Accounts have a name, an email, a department and one or more roles. Roles and department travel in
  the access token, so every service can authorize requests on its own.
- Admin: search accounts (accent-insensitive: "nguyen" finds "Nguyễn"), filter by role and department,
  and change roles and department. Changes are written to Keycloak first, then to the local copy.
- Rules: staff must belong to a department; admins cannot remove their own `ADMIN` role.
- Departments: code (used in request codes, e.g. `ACAD`) and name; admins create and rename them.
  Codes never change. Renames use optimistic locking.
- In production, default roles come from the email domain through Keycloak identity-provider mappers.

Moved to later phases, where they are first needed:

- Academic terms and school years, used as selectable field values (phase 3).
- Student profile: core data fetched from an external Student Information System (mocked), cached, with
  supplementary fields staff can edit; student IDs masked in responses (phase 5, for form filling).

## 2. Request templates — `request-service` (phase 3)

Admins define what can be requested.

- Template: title, description, owning department, audience (`STUDENT`, `LECTURER`, both),
  status (`ACTIVE` / `INACTIVE`), and whether processing time is shown in emails.
- Fields, ordered, each with a key, label, description, hint, `required` flag and a type:
  `TEXT`, `TEXTAREA`, `SELECT` (with an option list), `FILE`, `IMAGE`, `TERM`, `SCHOOL_YEAR`.
  File fields can define their own upload folder and upload notes.
- Steps, ordered, each assigned to a department, with a description, a deadline in days (SLA)
  and whether staff must attach a file when completing it.
- Attachments on the template (for example a blank form) are copied onto every new request.

## 3. Submitting requests — `request-service` (phase 3)

- The requester picks an active template allowed for their role; the form renders from its fields.
- Validation: every required field is present, and unknown fields are rejected.
- Files upload straight to object storage with presigned URLs; the request stores references only.
- On submission, in one transaction:
  - the request gets a human-readable code `<DEPARTMENT_CODE>-<sequence>`, e.g. `ACAD-0042`;
  - one step instance is created per template step: the first is `IN_PROGRESS` with a due date of
    now + SLA days, the rest are `PENDING`;
  - a `RequestSubmitted` event is published.
- Submissions accept an `Idempotency-Key` header so a retried click never creates two requests.
- Requesters see their own requests with a timeline of steps, notes and attachments.
- A requester can cancel a request only while no step is `COMPLETED`; open steps become `CANCELLED`.

## 4. Processing workflow — `request-service` (phase 3)

Step states: `PENDING → IN_PROGRESS → COMPLETED`, or `REJECTED` / `CANCELLED`, plus `OVERDUE`
while an in-progress step is past its due date.

- Staff see requests that have a step assigned to their department; admins see all.
- Filters: template, date range, department, status, "result received" flag, keyword (code, name, email).
- **Complete** the current step (must be `IN_PROGRESS` or `OVERDUE`, and the user must belong to the
  step's department), with an optional note and files. The next `PENDING` step becomes
  `IN_PROGRESS` with a fresh due date; if there is none, the request is complete.
- **Reject** the current step with a note. All later `PENDING` steps become `REJECTED`.
- **Bulk** complete or reject several requests at once.
- **Result received**: staff of the current department mark whether the requester has collected
  the result (single or bulk).
- Concurrent updates to the same request are detected with optimistic locking, so two staff
  members can never both complete the same step.
- Every state change is written to an audit log (who, what, when, before/after).

## 5. Notifications — `notification-service` (phase 4)

Consumes workflow events from Kafka and sends email:

- request submitted (to requester);
- request completed, so the requester can collect the result;
- request rejected, with the reason;
- step overdue (to the responsible department) and reminders before the due date.

A scheduled job marks overdue steps; a distributed lock makes sure only one instance runs it.

## 6. Documents and exports — `document-service` (phase 5)

- **Fillable forms**: admins upload `.docx` templates with `${placeholder}` fields and define extra
  runtime fields. Users download the form pre-filled with their profile data and the values they
  type. Downloads are rate-limited.
- Numbers can be rendered as Vietnamese words (for amounts on forms).
- Export a single request to `.docx`, a filtered request list to `.xlsx`, and statistics to `.xlsx`.
- **Bulk file download**: staff pick a filter, preview the file count and total size, then start a
  background job that zips all attachments, split into parts. The UI polls job status; finished
  archives expire after 3 days.
- Housekeeping jobs delete abandoned uploads (> 24 h) and expired archives.

## 7. Frontend — Vue 3 + TypeScript (phase 6)

Role-based areas: requester (new request, my requests, request detail, forms, profile),
staff (request queue, request detail, bulk download), admin (templates with fields and steps,
forms, accounts, departments).

## 8. Production readiness (phase 7)

Load test with published p95 latencies, OpenTelemetry tracing across services, Prometheus and
Grafana dashboards, Helm charts for Kubernetes, and a Terraform plan for AWS
(EKS, RDS, MSK, ElastiCache, S3, SES).
