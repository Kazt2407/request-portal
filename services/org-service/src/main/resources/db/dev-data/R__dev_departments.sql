-- Sample departments for local development and tests; codes match the users in
-- infra/keycloak/realm-request-portal.json. Not loaded in production (see application.yml).
insert into department (code, name) values
    ('ACAD', 'Academic Affairs Office'),
    ('FIN', 'Finance and Accounting Office'),
    ('STU', 'Student Affairs Office'),
    ('IT', 'IT Center')
on conflict (code) do nothing;
