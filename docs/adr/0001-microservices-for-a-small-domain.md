# ADR 0001: Microservices for a small domain

- Status: accepted
- Date: 2026-10-21

## Context

Request Portal's domain is modest: a few thousand users, tens of requests per minute at peak, one team.
For a production system of this size a **modular monolith** would be the pragmatic choice: one
deployable, one database, local transactions, simple debugging.

This project, however, also exists to practice the patterns used by large backend platforms
(for example digital banking): independently deployable services, event-driven integration,
database-per-service, and the operational work that comes with them.

## Decision

Split the system into four services behind an API gateway, along boundaries that would survive
in a real system:

| Service | Owns | Why it is separate |
|---|---|---|
| `org-service` | accounts, roles, departments, terms, student profiles | Reference data used by everyone; changes rarely |
| `request-service` | templates, requests, the approval workflow | The core domain and the only writer of workflow state |
| `document-service` | files, `.docx`/`.xlsx` generation, bulk ZIP jobs | CPU and memory heavy; must scale and fail independently |
| `notification-service` | email, reminders | Purely reactive; must never slow down or break submissions |

Services communicate:

- **synchronously** (REST) only for queries that need a fresh answer, with timeouts and circuit breakers;
- **asynchronously** (Kafka events) for everything that reacts to a state change.

## Consequences

- Each service can be built, tested, deployed and scaled on its own.
- No cross-service transactions: workflow events use a transactional outbox, and consumers are
  idempotent. Data seen by other services is eventually consistent.
- More moving parts locally (Kafka, Keycloak, Redis). Docker Compose keeps this to one command.
- If this were a real product at this scale, the same module boundaries inside one Spring Boot
  application would be the recommended starting point; extracting services later is easier when the
  boundaries are already clean.
