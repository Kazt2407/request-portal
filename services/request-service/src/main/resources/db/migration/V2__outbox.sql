-- Transactional outbox: workflow events are written here in the same transaction as the change that
-- caused them, then published to Kafka by OutboxRelay. Nothing is published for rolled-back changes,
-- and nothing is lost if Kafka is down.
create table outbox_event (
    seq          bigint generated always as identity primary key, -- publish order
    event_id     uuid         not null,
    topic        varchar(100) not null,
    message_key  varchar(100) not null, -- the request id, so a request's events stay in one partition
    event_type   varchar(40)  not null,
    payload      jsonb        not null,
    created_at   timestamptz  not null,
    published_at timestamptz,
    attempts     int          not null default 0,
    last_error   text,
    constraint outbox_event_event_id_uq unique (event_id)
);

create index outbox_event_unpublished_idx on outbox_event (seq) where published_at is null;
create index outbox_event_published_idx on outbox_event (published_at) where published_at is not null;
