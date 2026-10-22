-- Events already handled, so a redelivered Kafka message (at-least-once delivery) has no second effect.
create table processed_event (
    event_id     uuid primary key,
    event_type   varchar(40) not null,
    processed_at timestamptz not null default now()
);

create index processed_event_processed_at_idx on processed_event (processed_at);

-- Every email the system decided to send, with its delivery state. Rows are written in the same transaction
-- as the event that caused them and delivered by NotificationSender, which retries with backoff.
create table notification (
    id                   bigint generated always as identity primary key,
    event_id             uuid, -- null for reminders, which come from the scheduler
    request_id           bigint       not null,
    request_code         varchar(32)  not null,
    kind                 varchar(32)  not null,
    recipient_email      varchar(320),
    recipient_department varchar(16), -- resolved to the department's mailbox when sending
    subject              varchar(300) not null,
    body                 text         not null,
    status               varchar(16)  not null,
    attempts             int          not null default 0,
    next_attempt_at      timestamptz  not null,
    last_error           text,
    created_at           timestamptz  not null,
    sent_at              timestamptz,
    version              bigint       not null default 0,
    constraint notification_kind_ck check (kind in (
        'REQUEST_SUBMITTED', 'REQUEST_COMPLETED', 'REQUEST_REJECTED', 'REQUEST_CANCELLED',
        'STEP_WAITING', 'STEP_DUE_SOON', 'STEP_OVERDUE')),
    constraint notification_status_ck check (status in ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    constraint notification_one_recipient_ck check ((recipient_email is null) <> (recipient_department is null)),
    constraint notification_sent_ck check ((status = 'SENT') = (sent_at is not null))
);

create index notification_due_idx on notification (next_attempt_at) where status = 'PENDING';
create index notification_request_idx on notification (request_id, created_at);

-- Steps in progress, fed by STEP_STARTED and STEP_FINISHED events, for deadline reminders.
create table tracked_step (
    request_id           bigint       not null,
    position             int          not null,
    request_code         varchar(32)  not null,
    title                varchar(200) not null,
    department_code      varchar(16)  not null,
    description          varchar(500) not null,
    started_at           timestamptz  not null,
    due_at               timestamptz  not null,
    finished_at          timestamptz,
    due_soon_notified_at timestamptz,
    overdue_notified_at  timestamptz,
    primary key (request_id, position)
);

create index tracked_step_open_idx on tracked_step (due_at) where finished_at is null;
