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
- Student profile (phase 6, for form filling): student code, date of birth, class, faculty, major and
  phone. Students read their own; staff and admins create and correct them (optimistic locking). In
  production these would be synchronized from the Student Information System.

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
- Later: template attachments (for example a blank form copied onto every request).

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

Driven by workflow events from Kafka ([event catalog](events.md), [ADR 0005](adr/0005-events-and-notifications.md)).
Emails go to the requester or to the department's shared mailbox (set on the department in org-service):

| Email | To | When |
|---|---|---|
| Request received (with expected days if the template shows them) | requester | submitted |
| New request waiting, with the step and its deadline | department | each step starts |
| Due within a day | department | 24 hours before a step's deadline |
| Overdue | department | a step passes its deadline |
| Ready to collect, naming the department that hands it over | requester | completed |
| Rejected, with the reason | requester | a step is rejected |
| Cancelled | requester | the requester cancels |

- Each event is handled exactly once, even when Kafka delivers it twice.
- Emails are queued and retried with backoff; failures never block the workflow.
- Admins can list a request's notifications with their delivery status and retry failed ones.
- Reminders are claimed atomically, so running several instances never sends one twice.

## 6. Documents and exports — `document-service`, `request-service` (phase 6)

See [ADR 0007](adr/0007-files-and-documents.md).

- **Files on S3**: the browser asks document-service for an upload, PUTs the file straight to storage
  with a pre-signed URL (15 minutes), then confirms; the service checks the stored size and type. PDFs,
  images and Office documents up to 20 MB. Files never travel through the services.
- Submissions and step completions refer to uploaded files by id. request-service asks document-service
  whether each file exists, belongs to the caller, has finished uploading and is not already used, and
  whether image fields got images. Once the request is submitted, the files are attached to it.
- **Who may download** a file: its uploader until it is attached; then the requester, staff of any
  department with a step on the request, and admins. Downloads are pre-signed URLs that keep the original
  (Vietnamese) file name.
- **Fillable forms**: admins upload `.docx` templates with `${placeholder}` fields and define the extra
  fields users type (text, number, date). Users download the form filled with their name, email, student
  profile, today's date and their values; a number field `x` also fills `${x_words}` with the amount in
  Vietnamese words ("Một triệu hai trăm nghìn"). Placeholders split across Word formatting runs, in tables,
  headers and footers all work. The gateway limits form downloads to 1 per second per user (bursts of 5).
- **Exports** (request-service): a single request as `.docx` (values, steps, history; for anyone who may
  see the request); the filtered request list as `.xlsx` (up to 50,000 rows, streamed); statistics by
  template and by department (totals, outcomes, overdue, average days to complete, on-time rate) as JSON
  or `.xlsx`. Staff statistics always cover their own department.
- **Bulk file download**: staff select requests (for example a filtered list), preview how many requests,
  files and bytes they may download, then start a background job that zips the attachments, one folder
  per request code, with repeated file names numbered. The archive is streamed to S3 in 8 MB parts, so
  memory use does not grow with its size. Limits: 500 requests, 2,000 files, 2 GB. The client polls the
  job; when ready it returns a download URL. Only the person who started a job (or an admin) can see it.
  Archives expire after 3 days, and a job left running by a crashed instance is picked up again after
  30 minutes.
- Housekeeping jobs delete abandoned uploads (> 24 h) and expired archives.

## 7. Web app — Vue 3 + TypeScript (phase 7)

See [ADR 0008](adr/0008-web-app.md). Navigation follows the roles in the access token; the services still
check every action.

- **Requesters**: their requests by status; choose a request type and fill in its form (choices, terms,
  school years, file uploads with progress; a retried submit never creates two requests); a request's
  details, steps with deadlines and attachments, and history; cancel before any step is done; download
  the request as Word; fillable forms; their account and student record.
- **Staff**: the department queue with search and filters (status, request type, collected, submission
  dates, waiting for my department); complete a step with a note and attachments, or reject it with a
  reason; select many requests to complete, reject or mark as collected at once (all or nothing, with the
  reason for each failure); export the list to Excel; download the selected requests' files as a ZIP
  built in the background; statistics by request type and department, with an Excel export.
- **Admins**: everything staff see across all departments; request templates with ordered fields and
  steps (keys suggested from Vietnamese labels); forms (upload the .docx, see which placeholders nothing
  fills, add fields); accounts (roles, department, student records); departments; academic terms; and
  each request's emails with their delivery status, retrying failed ones.
- Validation messages from the API appear next to the fields they concern. Light and dark themes;
  usable on a phone.

## 8. Deployment and operations (phase 5)

See [ADR 0006](adr/0006-deployment-and-operations.md).

- Container images for every service from one layered, non-root Dockerfile; CI publishes them to GHCR.
- A Helm chart deploying all services with probes, resource limits, autoscaling and hardened pods; run
  locally on k3d against the docker compose infrastructure.
- OpenTelemetry traces that follow a request from the gateway through Kafka to notification-service,
  metrics over OTLP, and a provisioned Grafana dashboard (traffic, p95 latency, errors, outbox backlog,
  email queue, JVM and database pool).
- A k6 load test with published results in [performance.md](performance.md).
- Terraform for AWS (EKS, RDS, MSK, ElastiCache for Valkey, S3, SES, Pod Identity) and an Ansible
  playbook installing k3s and Rancher; both validated but not applied.
