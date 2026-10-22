-- Sample academic terms for local development and tests. Not loaded in production.
insert into academic_term (school_year, term_number, name, starts_on, ends_on) values
    ('2025-2026', 1, 'Semester 1, 2025–2026', '2025-09-08', '2026-01-18'),
    ('2025-2026', 2, 'Semester 2, 2025–2026', '2026-01-26', '2026-06-14'),
    ('2025-2026', 3, 'Summer term, 2025–2026', '2026-06-22', '2026-08-16'),
    ('2026-2027', 1, 'Semester 1, 2026–2027', '2026-09-07', '2027-01-17'),
    ('2026-2027', 2, 'Semester 2, 2026–2027', '2027-01-25', '2027-06-13'),
    ('2026-2027', 3, 'Summer term, 2026–2027', '2027-06-21', '2027-08-15')
on conflict (code) do nothing;
