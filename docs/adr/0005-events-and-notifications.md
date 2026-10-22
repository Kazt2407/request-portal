# ADR 0005: Workflow events and notifications

- Status: accepted
- Date: 2026-10-22

## Context

Requesters and departments need emails when a request moves (submitted, waiting for a department, due
soon, overdue, completed, rejected, cancelled). Sending email from inside request-service would tie the
workflow to the mail server: a slow SMTP server would slow approvals, and an outage would either fail them
or lose emails. Other consumers (analytics, document generation) will want the same facts later.

## Decisions

**request-service publishes workflow events to Kafka through a transactional outbox.** The request
aggregate records events (`REQUEST_SUBMITTED`, `STEP_STARTED`, `STEP_FINISHED`, `REQUEST_COMPLETED`,
`REQUEST_REJECTED`, `REQUEST_CANCELLED`) and the services write them to the `outbox_event` table in the
same transaction as the change. `OutboxRelay` then publishes them to the `request-events` topic.

- No dual write: an event exists exactly when its change was committed. A rolled-back bulk action
  publishes nothing (there is a test for this).
- Kafka being down delays events; it never loses them or blocks the workflow.
- Order: the message key is the request id, so a request's events share a partition. The relay publishes
  in insertion order and stops at the first failure. Only one relay runs at a time, using a
  transaction-scoped PostgreSQL advisory lock, so extra replicas cannot reorder events.
- Delivery is at least once (a crash after sending but before marking the row sends it again), so every
  event carries an `eventId` for consumers to deduplicate.
- A polling relay was chosen over change data capture (Debezium): no extra infrastructure, and the
  latency (half a second) is irrelevant for emails. CDC would be the next step at higher volume.

Events use a small, versioned JSON envelope documented in [docs/events.md](../events.md). It carries the
request's code, title and requester, so consumers rarely need to call back (event-carried state transfer).

**notification-service consumes the events and owns all email.**

- *Idempotent consumer:* the handler inserts the `eventId` into `processed_event` in the same transaction
  as its effects; a redelivered event finds it there and does nothing.
- *Poison messages:* a record that still fails after two retries goes to `request-events.DLT`, so one bad
  message cannot block its partition. Unreadable JSON skips the retries.
- *Email outbox:* the handler does not talk to SMTP. It renders the email (Thymeleaf) and queues it in the
  `notification` table; `NotificationSender` delivers it, with backoff (1, 5, 15 minutes, then hourly)
  and up to five attempts. Several instances can send in parallel (`for update skip locked`). Admins can
  list notifications per request and retry failed ones.
- *Department mailboxes* come from org-service, called with notification-service's own Keycloak service
  account (client credentials), because there is no user in a background job. The mailbox is resolved
  when sending, so an org-service outage delays those emails instead of failing event handling.

**Deadline reminders use a local copy of open steps and atomic claims, not a distributed lock.**
`STEP_STARTED` and `STEP_FINISHED` events maintain `tracked_step`. A scheduled job claims due-soon and
overdue steps with a single `update ... returning` statement that marks them, so each reminder is queued
once even if every instance runs the job at the same moment. This removes the need for a Redis or
ShedLock lock. Steps whose whole deadline is shorter than the reminder window get no "due soon" email; the
"waiting" email already gave the deadline.

**Redis is used where it fits: gateway rate limiting.** Spring Cloud Gateway's `RequestRateLimiter`
keeps a token bucket per user (the token's subject) and route in Redis, shared by all gateway replicas. Over the
limit the gateway returns `429`; if Redis is down it lets traffic through rather than failing the API.

## Consequences

- The workflow keeps working when Kafka, the mail server or org-service is down; emails catch up later.
- Consumers must tolerate duplicates and events they do not know (notification-service ignores unknown
  types, so request-service can add event types without breaking it).
- Emails are eventually consistent with the request: typically a few seconds behind.
- Two more tables per service to keep small: published outbox rows (7 days) and processed event ids
  (30 days, longer than Kafka's retention) are cleaned up nightly.
