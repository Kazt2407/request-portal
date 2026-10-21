# ADR 0002: Database per service

- Status: accepted
- Date: 2026-10-21

## Context

Microservices that share tables end up coupled through the schema: one team's migration can break
another service, and cross-service joins make independent deployment impossible.

## Decision

- Every service owns its own PostgreSQL database and database role. No service reads another
  service's tables. Locally, all databases live in one PostgreSQL container
  (`infra/postgres/init/01-databases.sql`); in AWS they can be separate RDS instances or schemas.
- Schemas are versioned with Flyway migrations inside each service. Hibernate only validates the
  schema (`ddl-auto: validate`); it never changes it.
- Schema design rules:
  - foreign keys, `NOT NULL` and `CHECK` constraints for every invariant the database can enforce;
  - proper types (`timestamptz`, `uuid`, `boolean`, enums as constrained `text`), never "text for everything";
  - indexes written for the actual queries, reviewed with `EXPLAIN`;
  - a `version` column on aggregates that are updated concurrently (optimistic locking).
- Data owned by another service is referenced by ID only and, when needed for reads, kept as a
  local copy updated from events (for example department names in `request-service`).

## Consequences

- Services can change their schema freely and be deployed independently.
- Reporting across services needs either API composition or an event-fed read model.
- Integration tests run each service against a real PostgreSQL via Testcontainers, not an in-memory database.
