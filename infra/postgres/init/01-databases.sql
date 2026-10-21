-- One database and one owner role per service (database-per-service, see docs/adr/0002).
-- Runs only on the first start of an empty volume. Passwords are for local development only.

CREATE ROLE org_service LOGIN PASSWORD 'org_service';
CREATE DATABASE org_db OWNER org_service;

CREATE ROLE request_service LOGIN PASSWORD 'request_service';
CREATE DATABASE request_db OWNER request_service;

CREATE ROLE document_service LOGIN PASSWORD 'document_service';
CREATE DATABASE document_db OWNER document_service;

CREATE ROLE notification_service LOGIN PASSWORD 'notification_service';
CREATE DATABASE notification_db OWNER notification_service;

CREATE ROLE keycloak LOGIN PASSWORD 'keycloak';
CREATE DATABASE keycloak OWNER keycloak;
