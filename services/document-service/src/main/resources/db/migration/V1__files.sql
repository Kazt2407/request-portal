-- Files in object storage. Clients upload straight to S3 with a pre-signed URL; this table tracks each
-- file from the issued URL (PENDING) through the verified upload (UPLOADED) to its use (ATTACHED).
create table stored_file (
    id                uuid primary key,
    owner_id          uuid         not null, -- Keycloak user id of the uploader
    purpose           varchar(32)  not null,
    file_name         varchar(255) not null,
    content_type      varchar(150) not null,
    size_bytes        bigint       not null,
    object_key        varchar(300) not null,
    status            varchar(16)  not null,
    request_id        bigint, -- the request the file belongs to, once attached
    created_at        timestamptz  not null,
    uploaded_at       timestamptz,
    attached_at       timestamptz,
    last_checked_at   timestamptz, -- request-service checked it for a submission; keeps cleanup away
    constraint stored_file_object_key_uq unique (object_key),
    constraint stored_file_purpose_ck check (purpose in ('REQUEST_FIELD', 'STEP_ATTACHMENT', 'FORM_TEMPLATE')),
    constraint stored_file_status_ck check (status in ('PENDING', 'UPLOADED', 'ATTACHED')),
    constraint stored_file_size_ck check (size_bytes > 0),
    constraint stored_file_uploaded_ck check (status = 'PENDING' or uploaded_at is not null),
    constraint stored_file_attached_ck check ((status = 'ATTACHED') = (attached_at is not null))
);

create index stored_file_owner_idx on stored_file (owner_id, created_at desc);
create index stored_file_request_idx on stored_file (request_id) where request_id is not null;
create index stored_file_unattached_idx on stored_file (created_at) where status <> 'ATTACHED';

-- Who may see each request's files, kept from workflow events (the same rule as request-service:
-- the requester, staff of any department with a step, and admins).
create table request_access (
    request_id   bigint primary key,
    requester_id uuid        not null,
    departments  text[]      not null,
    updated_at   timestamptz not null
);

-- Workflow events already handled, for exactly-once effects under at-least-once delivery.
create table processed_event (
    event_id     uuid primary key,
    event_type   varchar(40) not null,
    processed_at timestamptz not null default now()
);
