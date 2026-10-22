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
  supplementary fields staff can edit; student IDs masked in responses (phase 6, for form filling).

## 2. Request templates — `request-service` (phase 3)

Admins define what can be requested. See [ADR 0004](adr/0004-request-workflow.md) for the design.

- Template: title, description, owning department, audience (`STUDENT`, `LECTURER`, `ALL`),
  status (`ACTIVE` / `INACTIVE`), and whether processing time is shown in emails.
- Fields, ordered, each with a key, label, description, hint, `required` flag and a type:
  `TEXT`, `TEXTAREA`, `SELECT` (with an option list), `FILE`, `IMAGE`, `TERM`, `SCHOOL_YEAR`.
  File fields can define their own upload folder and upload notes.
- Steps, ordered, each assigned to a department, with a description, a deadline in days (SLA)
  and whether staff must attach a file when completing it.
- Saving checks that every department exists in org-service, field keys are unique and select fields
  have options. Updates replace the whole definition and use optimistic locking.
- Requesters only see active templates for their audience.
- Template attachments (for example a blank form copied onto every request) arrive with files in phase 6.

## 3. Submitting requests — `request-service` (phase 3)

- The requester picks an active template allowed for their role; the form renders from its fields.
- Validation reports every problem at once, per field: required values, unknown fields, option lists,
  text lengths, academic terms and school years known to org-service, and file ids for file fields.
- On submission, in one transaction:
  - the request gets a code `<DEPARTMENT_CODE>-<sequence>` from a per-department counter, e.g. `ACAD-0042`;
  - the template's steps are copied into the request: the first is `IN_PROGRESS` with a due date of
    now + SLA days, the rest are `PENDING`;
  - the history records `SUBMITTED`.
- Submissions accept an `Idempotency-Key` header so a retried click never creates two requests.
- Requesters list their own requests and open each one with its values, steps and history.
- A requester can cancel a request only while no step is `COMPLETED`; open steps become `CANCELLED`.

## 4. Processing workflow — `request-service` (phase 3)

Step states: `PENDING → IN_PROGRESS → COMPLETED`, or `REJECTED`, or `CANCELLED` when the request ends
first. A step is *overdue* while it is in progress past its due date; this is computed, not stored.

- Staff see requests that have a step assigned to their department; admins see all and can filter by
  department. Filters: template, status, submitted date range, "result received", "awaiting my
  department", and text (code, requester name or email, accent-insensitive).
- **Complete** the current step (staff of the step's department only), with an optional note and the
  attachments the step requires. The next step starts with a fresh due date; after the last step the
  request is `COMPLETED`.
- **Reject** the current step with a reason. The request is `REJECTED` and later steps are `CANCELLED`.
- **Bulk** complete or reject the current steps of several requests: all succeed or nothing changes, and
  the response lists the reason for every request that failed.
- **Result received**: staff of the last step's department record whether the requester collected the
  result of a completed request (single or bulk).
- Acting on a step that is no longer current is a `409`; two staff acting at once get one success and one
  `409` (optimistic locking).
- Every state change is written to the request's history (who, what, when, note).

## 5. Notifications — `notification-service` (phase 4)

Consumes workflow events from Kafka and sends email:

- request submitted (to requester);
- request completed, so the requester can collect the result;
- request rejected, with the reason;
- step overdue (to the responsible department) and reminders before the due date.

A scheduled job finds steps that became overdue and sends reminders; a distributed lock makes sure
only one instance runs it.

## 6. Documents and exports — `document-service` (phase 6)

- **Fillable forms**: admins upload `.docx` templates with `${placeholder}` fields and define extra
  runtime fields. Users download the form pre-filled with their profile data and the values they
  type. Downloads are rate-limited.
- Numbers can be rendered as Vietnamese words (for amounts on forms).
- Export a single request to `.docx`, a filtered request list to `.xlsx`, and statistics to `.xlsx`.
- **Bulk file download**: staff pick a filter, preview the file count and total size, then start a
  background job that zips all attachments, split into parts. The UI polls job status; finished
  archives expire after 3 days.
- Housekeeping jobs delete abandoned uploads (> 24 h) and expired archives.

## 7. Frontend — Vue 3 + TypeScript (phase 7)

Role-based areas: requester (new request, my requests, request detail, forms, profile),
staff (request queue, request detail, bulk download), admin (templates with fields and steps,
forms, accounts, departments).

## 8. Deployment and operations (phase 5)

- Container images for every service, built in CI.
- Helm charts, deployed to a local Kubernetes cluster (k3d), with liveness and readiness probes.
- Terraform plan for AWS: EKS, RDS for PostgreSQL, MSK for Kafka, ElastiCache for Redis, S3, SES.
- OpenTelemetry tracing across the gateway and services, Prometheus metrics and Grafana dashboards.
- Load test with published p95 latencies.
- An Ansible playbook that installs k3s and Rancher on a plain Linux server.
