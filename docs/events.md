# Workflow events

request-service publishes these events to the Kafka topic **`request-events`** (see
[ADR 0005](adr/0005-events-and-notifications.md)). The message key is the request id, so a request's events
are in one partition and arrive in order. Delivery is at least once: deduplicate by `eventId`.
Ignore event types you do not know; new ones may be added.

Headers: `eventId`, `eventType`. Value: UTF-8 JSON.

## Envelope (version 1)

```json
{
  "eventId": "4f1c2e7a-9b0d-4d3e-8a51-6c2f0e9b7d10",
  "type": "STEP_STARTED",
  "version": 1,
  "occurredAt": "2026-10-22T04:35:01.562155Z",
  "request": {
    "id": 42,
    "code": "ACAD-0042",
    "title": "Official transcript",
    "departmentCode": "ACAD",
    "requester": {
      "id": "a1000000-0000-4000-8000-000000000001",
      "name": "Nguyễn Văn An",
      "email": "an.nguyen@student.example.edu"
    }
  },
  "data": { }
}
```

## Types and their `data`

| Type | When | `data` |
|---|---|---|
| `REQUEST_SUBMITTED` | A request was submitted | `stepCount`, `expectedDays` (sum of step deadlines, or `null` if the template hides it), `stepDepartments` (department of each step, in order), `fileIds` (files uploaded for the request's fields) |
| `STEP_STARTED` | A step became the current one (the first on submission, then each next one) | `position`, `departmentCode`, `description`, `dueAt` |
| `STEP_FINISHED` | A step was completed, rejected or cancelled | `position`, `departmentCode`, `status` (`COMPLETED`, `REJECTED`, `CANCELLED`), `finishedBy` (`{id, name}`, `null` when cancelled), `note`, `attachmentIds` (files staff attached to the step) |
| `REQUEST_COMPLETED` | The last step was completed | `resultDepartmentCode` (who hands over the result) |
| `REQUEST_REJECTED` | A step was rejected; later steps were cancelled | `position`, `departmentCode`, `reason` |
| `REQUEST_CANCELLED` | The requester cancelled before any step was completed | none |

A two-step request that goes through normally produces:
`REQUEST_SUBMITTED, STEP_STARTED(1), STEP_FINISHED(1), STEP_STARTED(2), STEP_FINISHED(2), REQUEST_COMPLETED`.

## Consumers

| Consumer group | Uses the events to | Dead-letter topic |
|---|---|---|
| `notification-service` | Send emails and schedule deadline reminders | `request-events.DLT` |
| `document-service` | Attach uploaded files to their request and keep a copy of who may see each request, so file downloads are authorized without calling request-service | `request-events.document-service.DLT` |

Messages a consumer cannot process are moved to its dead-letter topic unchanged.
