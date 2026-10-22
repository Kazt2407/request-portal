-- Accent-insensitive search on request code and requester ("nguyen" finds "Nguyễn").
create extension if not exists pg_trgm;
create extension if not exists unaccent;

-- unaccent() is only STABLE because its dictionary could change; pinning the dictionary makes this
-- wrapper safe to declare IMMUTABLE, which generated columns and indexes require.
create function immutable_unaccent(text) returns text
    language sql immutable parallel safe strict
    return public.unaccent('public.unaccent'::regdictionary, $1);

-- ---------------------------------------------------------------------------------------------------
-- Templates: what can be requested, which fields the form has, which departments approve it, in order.
-- Department codes belong to org-service and are validated through its API when a template is saved.
-- ---------------------------------------------------------------------------------------------------
create table request_template (
    id                   bigint generated always as identity primary key,
    title                varchar(200) not null,
    description          text,
    department_code      varchar(16)  not null, -- owning department; prefixes request codes
    audience             varchar(16)  not null,
    status               varchar(16)  not null,
    show_processing_time boolean      not null default false,
    created_at           timestamptz  not null default now(),
    updated_at           timestamptz  not null default now(),
    version              bigint       not null default 0,
    constraint request_template_title_ck check (btrim(title) <> ''),
    constraint request_template_audience_ck check (audience in ('STUDENT', 'LECTURER', 'ALL')),
    constraint request_template_status_ck check (status in ('ACTIVE', 'INACTIVE'))
);

create index request_template_status_audience_idx on request_template (status, audience);

-- Positions are replaced wholesale when a template is edited, so uniqueness is checked at commit.
create table template_field (
    id            bigint generated always as identity primary key,
    template_id   bigint       not null references request_template (id) on delete cascade,
    position      int          not null,
    field_key     varchar(50)  not null,
    label         varchar(200) not null,
    description   text,
    hint          varchar(200),
    field_type    varchar(16)  not null,
    required      boolean      not null default false,
    options       text[]       not null default '{}',
    upload_folder varchar(120),
    upload_note   text,
    constraint template_field_position_uq unique (template_id, position) deferrable initially deferred,
    constraint template_field_key_uq unique (template_id, field_key) deferrable initially deferred,
    constraint template_field_key_format_ck check (field_key ~ '^[a-z][a-z0-9_]{0,49}$'),
    constraint template_field_type_ck check (
        field_type in ('TEXT', 'TEXTAREA', 'SELECT', 'FILE', 'IMAGE', 'TERM', 'SCHOOL_YEAR')),
    constraint template_field_select_options_ck check (field_type <> 'SELECT' or cardinality(options) > 0)
);

create table template_step (
    id                  bigint generated always as identity primary key,
    template_id         bigint       not null references request_template (id) on delete cascade,
    position            int          not null,
    department_code     varchar(16)  not null,
    description         varchar(500) not null,
    sla_days            int          not null,
    requires_attachment boolean      not null default false,
    constraint template_step_position_uq unique (template_id, position) deferrable initially deferred,
    constraint template_step_sla_ck check (sla_days between 1 and 365)
);

-- ---------------------------------------------------------------------------------------------------
-- Requests: a submitted form plus its own copy of the template's steps, so editing a template never
-- changes requests already in flight.
-- ---------------------------------------------------------------------------------------------------

-- Next request number per department; the upsert row lock serializes concurrent submissions.
create table request_number (
    department_code varchar(16) primary key,
    last_number     int not null
);

create table service_request (
    id              bigint generated always as identity primary key,
    code            varchar(32)  not null, -- e.g. ACAD-0042
    template_id     bigint       not null references request_template (id),
    title           varchar(200) not null, -- template title at submission
    department_code varchar(16)  not null,
    requester_id    uuid         not null, -- Keycloak user id
    requester_name  varchar(200) not null,
    requester_email varchar(320) not null,
    status          varchar(16)  not null,
    current_step    int, -- position of the step in progress; null once the request is finished
    result_received boolean      not null default false,
    search_text     text generated always as (
                        lower(immutable_unaccent(code || ' ' || requester_name || ' ' || requester_email))) stored,
    submitted_at    timestamptz  not null,
    finished_at     timestamptz,
    updated_at      timestamptz  not null,
    version         bigint       not null default 0,
    constraint service_request_code_uq unique (code),
    constraint service_request_status_ck check (status in ('IN_PROGRESS', 'COMPLETED', 'REJECTED', 'CANCELLED')),
    constraint service_request_current_step_ck check ((status = 'IN_PROGRESS') = (current_step is not null)),
    constraint service_request_finished_ck check ((status = 'IN_PROGRESS') = (finished_at is null)),
    constraint service_request_received_ck check (not result_received or status = 'COMPLETED')
);

create index service_request_requester_idx on service_request (requester_id, submitted_at desc);
create index service_request_status_idx on service_request (status, submitted_at desc);
create index service_request_template_idx on service_request (template_id);
create index service_request_search_trgm_idx on service_request using gin (search_text gin_trgm_ops);

create table request_step (
    id                  bigint generated always as identity primary key,
    request_id          bigint       not null references service_request (id) on delete cascade,
    position            int          not null,
    department_code     varchar(16)  not null,
    description         varchar(500) not null,
    sla_days            int          not null,
    requires_attachment boolean      not null,
    status              varchar(16)  not null,
    started_at          timestamptz,
    due_at              timestamptz,
    finished_at         timestamptz,
    finished_by_id      uuid,
    finished_by_name    varchar(200),
    note                text,
    attachment_ids      uuid[]       not null default '{}', -- ids of files stored by document-service
    constraint request_step_position_uq unique (request_id, position),
    constraint request_step_status_ck check (
        status in ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'REJECTED', 'CANCELLED')),
    constraint request_step_started_ck check (status = 'PENDING' or status = 'CANCELLED' or started_at is not null),
    constraint request_step_due_ck check (status <> 'IN_PROGRESS' or due_at is not null)
);

-- At most one step of a request is ever in progress.
create unique index request_step_one_in_progress_uq on request_step (request_id) where status = 'IN_PROGRESS';
-- Staff queues: "requests that involve my department" and "waiting for my department".
create index request_step_department_idx on request_step (department_code, status);

-- Submitted values with the field's label and type copied at submission time.
create table request_field_value (
    id          bigint generated always as identity primary key,
    request_id  bigint       not null references service_request (id) on delete cascade,
    position    int          not null,
    field_key   varchar(50)  not null,
    label       varchar(200) not null,
    field_type  varchar(16)  not null,
    value       text[]       not null, -- one element, or several file ids for FILE and IMAGE fields
    constraint request_field_value_key_uq unique (request_id, field_key),
    constraint request_field_value_not_empty_ck check (cardinality(value) > 0)
);

-- Audit trail: every state change, who made it and when. Append-only.
create table request_history (
    id            bigint generated always as identity primary key,
    request_id    bigint       not null references service_request (id) on delete cascade,
    step_position int,
    action        varchar(32)  not null,
    actor_id      uuid         not null,
    actor_name    varchar(200) not null,
    note          text,
    created_at    timestamptz  not null,
    constraint request_history_action_ck check (action in (
        'SUBMITTED', 'STEP_COMPLETED', 'STEP_REJECTED', 'COMPLETED', 'CANCELLED',
        'RESULT_RECEIVED', 'RESULT_NOT_RECEIVED'))
);

create index request_history_request_idx on request_history (request_id, created_at);

-- Idempotency-Key header of POST /api/v1/requests: a retried submission returns the original request.
create table idempotency_key (
    requester_id    uuid         not null,
    idempotency_key varchar(100) not null,
    request_hash    char(64)     not null, -- SHA-256 of the body, to detect a key reused for another request
    request_id      bigint       not null references service_request (id) on delete cascade,
    created_at      timestamptz  not null default now(),
    primary key (requester_id, idempotency_key)
);
