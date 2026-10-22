-- Academic terms (semesters), offered as values of TERM and SCHOOL_YEAR fields in request templates.
create table academic_term (
    id          bigint generated always as identity primary key,
    school_year varchar(9)   not null, -- e.g. 2026-2027
    term_number smallint     not null, -- 1, 2, or 3 for the summer term
    -- Value stored in requests, e.g. 2026-2027-1; derived so it can never disagree with the columns above.
    code        varchar(16)  generated always as (school_year || '-' || term_number) stored,
    name        varchar(100) not null,
    starts_on   date         not null,
    ends_on     date         not null,
    created_at  timestamptz  not null default now(),
    constraint academic_term_code_uq unique (code),
    constraint academic_term_school_year_ck check (
        school_year ~ '^[0-9]{4}-[0-9]{4}$'
        and substr(school_year, 6, 4)::int = substr(school_year, 1, 4)::int + 1),
    constraint academic_term_number_ck check (term_number between 1 and 3),
    constraint academic_term_dates_ck check (ends_on > starts_on),
    -- Terms never overlap in time.
    constraint academic_term_no_overlap_ex exclude using gist (daterange(starts_on, ends_on, '[]') with &&)
);
