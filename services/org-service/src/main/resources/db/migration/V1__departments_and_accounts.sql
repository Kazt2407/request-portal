-- Accent-insensitive "contains" search on account email and name ("nguyen" finds "Nguyễn").
create extension if not exists pg_trgm;
create extension if not exists unaccent;

-- unaccent() is only STABLE because its dictionary could change; pinning the dictionary makes this
-- wrapper safe to declare IMMUTABLE, which generated columns and indexes require.
create function immutable_unaccent(text) returns text
    language sql immutable parallel safe strict
    return public.unaccent('public.unaccent'::regdictionary, $1);

create table department (
    id         bigint generated always as identity primary key,
    -- Short code used in request numbers (ACAD-0042) and in the `department` token claim; never changes.
    code       varchar(16)  not null,
    name       varchar(200) not null,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    version    bigint       not null default 0,
    constraint department_code_uq unique (code),
    constraint department_code_format_ck check (code ~ '^[A-Z][A-Z0-9_]{1,15}$'),
    constraint department_name_not_blank_ck check (btrim(name) <> '')
);

-- Read model of Keycloak users, created on first sign-in and kept in sync from the access token.
-- Keycloak stays the source of truth for credentials, roles and department membership.
create table account (
    id            uuid primary key, -- Keycloak user id (`sub` claim)
    email         varchar(320) not null,
    full_name     varchar(200) not null,
    department_id bigint references department (id),
    search_text   text generated always as (lower(immutable_unaccent(email || ' ' || full_name))) stored,
    last_seen_at  timestamptz,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    version       bigint       not null default 0
);

create unique index account_email_uq on account (lower(email));
create index account_department_idx on account (department_id);
create index account_search_trgm_idx on account using gin (search_text gin_trgm_ops);

create table account_role (
    account_id uuid        not null references account (id) on delete cascade,
    role       varchar(16) not null,
    primary key (account_id, role),
    constraint account_role_value_ck check (role in ('STUDENT', 'LECTURER', 'STAFF', 'ADMIN'))
);

create index account_role_role_idx on account_role (role);
