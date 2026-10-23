-- Fillable forms: a .docx template with placeholder fields, filled from the user's profile, today's date
-- and values the user types. The template is a stored_file kept for as long as the form uses it.
create table form_template (
    id               bigint generated always as identity primary key,
    title            varchar(200) not null,
    description      text,
    audience         varchar(16)  not null,
    active           boolean      not null default true,
    template_file_id uuid         not null references stored_file (id),
    placeholders     text[]       not null, -- found in the template when it was saved
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    version          bigint       not null default 0,
    constraint form_template_title_ck check (btrim(title) <> ''),
    constraint form_template_audience_ck check (audience in ('STUDENT', 'LECTURER', 'ALL'))
);

create index form_template_active_idx on form_template (active, audience);

create table form_field (
    id          bigint generated always as identity primary key,
    form_id     bigint       not null references form_template (id) on delete cascade,
    position    int          not null,
    field_key   varchar(50)  not null,
    label       varchar(200) not null,
    hint        varchar(200),
    field_type  varchar(16)  not null,
    required    boolean      not null default false,
    constraint form_field_position_uq unique (form_id, position) deferrable initially deferred,
    constraint form_field_key_uq unique (form_id, field_key) deferrable initially deferred,
    constraint form_field_key_format_ck check (field_key ~ '^[a-z][a-z0-9_]{0,49}$'),
    constraint form_field_type_ck check (field_type in ('TEXT', 'NUMBER', 'DATE'))
);
