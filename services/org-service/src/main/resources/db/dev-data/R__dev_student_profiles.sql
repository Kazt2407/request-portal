-- Profile of the "student" test user (fixed id in infra/keycloak/realm-request-portal.json).
insert into student_profile (account_id, student_code, date_of_birth, class_name, faculty, major, phone)
values ('a1000000-0000-4000-8000-000000000001', '21004321', '2003-05-14', 'DHCNTT21A',
        'Faculty of Information Technology', 'Information Technology', '0912 345 678')
on conflict (account_id) do nothing;
