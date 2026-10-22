-- Sample departments for local development and tests; codes match the users in
-- infra/keycloak/realm-request-portal.json. Not loaded in production (see application.yml).
insert into department (code, name, email) values
    ('ACAD', 'Academic Affairs Office', 'academic-affairs@example.edu'),
    ('FIN', 'Finance and Accounting Office', 'finance@example.edu'),
    ('STU', 'Student Affairs Office', 'student-affairs@example.edu'),
    ('IT', 'IT Center', 'it-center@example.edu')
on conflict (code) do update set email = coalesce(department.email, excluded.email);
