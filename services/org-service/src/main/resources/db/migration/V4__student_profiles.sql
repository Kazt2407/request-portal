-- Student records used to pre-fill forms. Keyed by the Keycloak user id rather than referencing account,
-- because profiles are imported from the student information system before students first sign in.
create table student_profile (
    account_id    uuid primary key,
    student_code  varchar(20)  not null,
    date_of_birth date,
    class_name    varchar(50),
    faculty       varchar(200),
    major         varchar(200),
    phone         varchar(20),
    updated_at    timestamptz  not null default now(),
    version       bigint       not null default 0,
    constraint student_profile_code_uq unique (student_code),
    constraint student_profile_code_format_ck check (student_code ~ '^[0-9A-Z]{6,20}$')
);
