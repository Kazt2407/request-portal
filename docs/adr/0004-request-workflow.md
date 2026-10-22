# ADR 0004: The request workflow

- Status: accepted
- Date: 2026-10-22

## Context

A request moves through an ordered chain of department steps defined by an admin-editable template.
The design has to answer: what happens to requests in flight when a template changes, how two staff
members acting at once are kept from corrupting a request, how a retried submission is kept from
creating duplicates, and how "overdue" is tracked.

## Decisions

**A request copies what it needs from the template at submission.** Steps (department, description,
deadline, attachment rule) and the label and type of each submitted value are copied into the request.
Editing or deactivating a template never changes requests already submitted, and the request history
stays readable even if the template is rewritten.

**`ServiceRequest` is the aggregate root; all changes go through it.** It enforces the state machine:

```
step:     PENDING ──▶ IN_PROGRESS ──▶ COMPLETED
                          │
                          └──▶ REJECTED        (later steps ──▶ CANCELLED, request REJECTED)
request:  IN_PROGRESS ──▶ COMPLETED | REJECTED | CANCELLED
```

Only the current step can be completed or rejected, and the client names the step it acted on
(`/steps/{position}/complete`), so acting on a stale screen is a `409` instead of completing the wrong
step. A requester may cancel only until a department completes a step. Every change appends to
`request_history` (who, what, when, note) in the same transaction.

**Concurrency is handled by the database, in layers.**

- Optimistic locking (`version`) on the aggregate root: two staff completing the same step at the same
  time get one `200` and one `409`; there is a test that does exactly this.
- Constraints make illegal states unrepresentable even if the code had a bug: at most one step per
  request is `IN_PROGRESS` (partial unique index), `status = 'IN_PROGRESS'` exactly when `current_step`
  is set, `finished_at` set exactly when the request is finished, and the result can be marked collected
  only on a completed request.

**Request codes are per department** (`ACAD-0042`). They come from an upsert on `request_number` that
locks the department's row until commit, so concurrent submissions never share a number and a rolled-
back submission returns its number.

**Submissions are idempotent with an `Idempotency-Key` header.** The key, a SHA-256 of the body and the
created request id are stored in the same transaction as the request. A retry returns the original
request (`Idempotency-Replayed: true`); the same key with a different body is rejected with `422`. If two
retries race, the second fails on the key's primary key, rolls back, and returns the first one's result.

**"Overdue" is derived, not stored.** A step is overdue when it is `IN_PROGRESS` and `due_at` is in the
past, computed when the request is read. A stored flag would need a job to keep it true and would be
wrong between runs; the job in phase 4 only sends reminders.

**Values are stored as `text[]` per field.** Single-valued fields hold one element; file fields hold
file ids from document-service (phase 6). This keeps the column type-safe and queryable without an
entity-attribute-value table of loosely typed strings or a JSON column the ORM cannot validate.

**Reference data comes from org-service over REST.** Department codes are checked when an admin saves a
template; term and school-year values are checked on submission. The client forwards the user's token,
uses 2 s connect / 3 s read timeouts, and caches terms for 5 minutes, reloading early (at most every
10 s) when it sees an unknown value. If org-service is down, the call fails fast with `502`.

## Consequences

- Template edits are safe at any time, at the cost of some duplicated data per request.
- Clients must handle `409` by reloading the request; the UI should do this automatically.
- Synchronous validation couples submissions to org-service's availability. Phase 4 can remove that by
  keeping a local copy of terms fed by events.
