-- Request codes name the folders inside bulk ZIP downloads.
alter table request_access add column request_code varchar(32);

-- A ZIP of the attachments of many requests, built in the background and stored for a few days.
create table bulk_download (
    id           uuid primary key,
    requested_by uuid        not null,
    request_ids  bigint[]    not null,
    status       varchar(16) not null,
    file_count   int,
    total_bytes  bigint,
    object_key   varchar(300),
    error        text,
    created_at   timestamptz not null,
    started_at   timestamptz,
    finished_at  timestamptz,
    expires_at   timestamptz,
    constraint bulk_download_status_ck check (status in ('PENDING', 'RUNNING', 'READY', 'FAILED', 'EXPIRED')),
    constraint bulk_download_ready_ck check (status <> 'READY' or (object_key is not null and expires_at is not null))
);

create index bulk_download_queue_idx on bulk_download (created_at) where status in ('PENDING', 'RUNNING');
create index bulk_download_expiry_idx on bulk_download (expires_at) where status = 'READY';
create index bulk_download_owner_idx on bulk_download (requested_by, created_at desc);
