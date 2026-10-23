# ADR 0007: Files and documents

- Status: accepted
- Date: 2026-10-22

## Context

Requests carry files: evidence uploaded by requesters and scans attached by staff when they complete a
step. Users also download Word forms filled in with their own data, staff export lists and statistics to
Excel, and departments occasionally need every attachment of hundreds of requests at once. Files must be
private, survive any one instance, and not turn the services into a bottleneck for large transfers.

## Decisions

**document-service owns files; file bytes never pass through a service.** Files live in S3 (RustFS, an
S3-compatible store, in docker compose). Uploads are three calls: the client asks for an upload and gets a
pre-signed PUT URL, sends the bytes straight to storage, then confirms; the service then checks the
stored object's size and type against what was declared, and deletes it if they differ. Downloads are
pre-signed GET URLs. The service only signs URLs, so its CPU, memory and network stay flat however
large the files are. Pre-signed URLs are signed for the public endpoint (what the browser calls), which
can differ from the endpoint the service itself uses, as in the local Kubernetes setup.

**Files are attached by events, not by calls from request-service.** A request refers to files by id.
When it is submitted, request-service first asks document-service (synchronously) whether the files are
usable, so the user gets a field error immediately. The attachment itself happens when document-service
consumes the `REQUEST_SUBMITTED` or `STEP_FINISHED` event, so the two databases never need a distributed
transaction. Uploads that are never attached are deleted after 24 hours; a short lease on each file
keeps the cleanup from deleting a file that is being checked at that moment.

**document-service keeps its own copy of who may see each request.** The same events carry the
requester and the departments of the request's steps, which document-service stores. Download
permission is decided locally, with the same rules as request-service, so a download never waits on
request-service and still works when it is down. The cost is eventual consistency of a few hundred
milliseconds after submission.

**Documents are generated where their data lives.** Filled-in forms are produced by document-service
(templates are its files; profile data comes from org-service over HTTP with the caller's token).
Request exports are produced by request-service, which owns the requests; it reuses the same visibility
query as the request list, so an export can never show more than the screen. Word documents use Apache
POI directly with a small placeholder engine that handles placeholders split across formatting runs,
instead of a templating library that lags behind POI releases. Excel exports use POI's streaming
workbook so large exports keep a fixed memory footprint.

**Bulk downloads are background jobs with a database queue.** Starting a job checks the selection
against the access rules and limits (500 requests, 2,000 files, 2 GB) and returns `202 Accepted` with the
job's URL. Workers in every instance claim jobs with `FOR UPDATE SKIP LOCKED`, so instances never build
the same job twice, and stream the ZIP straight from storage into an S3 multipart upload: memory use is
one 8 MB part, whatever the archive size. A job still `RUNNING` after 30 minutes is assumed to belong to
a crashed instance and is claimed again. Archives expire after 3 days (a scheduled job, and an S3
lifecycle rule in AWS as a backstop).

**In AWS, no storage keys exist.** document-service runs under its own Kubernetes service account,
which EKS Pod Identity maps to an IAM role allowed to read and write only the documents bucket. With no
static keys configured, the AWS SDK picks those credentials up automatically.

## Consequences

- The browser talks to storage directly, so the storage endpoint must be reachable by clients and allow
  the web app's origin (CORS on the bucket).
- A file shows as downloadable to staff a moment after submission rather than instantly.
- Bulk jobs run one at a time per instance; more instances build more archives in parallel. A queue
  service would add little at this volume.
- A new document-service consumer group reads the topic from the beginning, which rebuilds the access
  copy (replaying about 10,000 local events took under 20 seconds). Requests older than the topic's
  retention would need a one-off backfill from request-service.
