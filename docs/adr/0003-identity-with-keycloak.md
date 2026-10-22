# ADR 0003: Identity with Keycloak, authorization data in the token

- Status: accepted
- Date: 2026-10-22

## Context

Every service needs to know who is calling and what they may do: a student may only see their own
requests, staff may only process steps assigned to their department, admins manage everything.
Universities usually already run a single sign-on system, but the application should not depend on any
one university's SSO, and it must be runnable locally without one.

## Decision

**Keycloak is the identity provider.** Locally it runs in Docker Compose with the `request-portal` realm
imported from [`infra/keycloak/realm-request-portal.json`](../../infra/keycloak/realm-request-portal.json):
four realm roles (`STUDENT`, `LECTURER`, `STAFF`, `ADMIN`), test users and three clients:

| Client | Type | Used by |
|---|---|---|
| `portal-spa` | public, authorization code + PKCE | the Vue app (phase 7) |
| `portal-dev` | public, password grant | local scripts and tests only; never enabled in production |
| `org-service` | confidential, service account | org-service calling the Keycloak Admin API |

In production the same realm would *broker* the university's SSO (OpenID Connect or SAML identity
provider in Keycloak), with mappers that give default roles from the email domain. The services never
talk to the university SSO directly, so it can be replaced without code changes.

**Access tokens carry everything needed for authorization:**

```json
{ "sub": "…", "aud": "request-portal-api", "roles": ["STAFF"], "department": "FIN",
  "email": "dung.pham@example.edu", "family_name": "Phạm", "given_name": "Thị Dung" }
```

- `roles` is a flat claim from a realm-role mapper; `department` comes from a user-profile attribute.
- The audience mapper adds `request-portal-api`; services reject tokens issued for other audiences.

**Every service validates the token itself** (signature via JWKS, issuer, audience, expiry), using the
shared `libs/service-security` module. The gateway also validates, to reject bad traffic at the edge, but
services do not trust the network. Authorization rules live in the services (`@PreAuthorize`, domain
checks), not in the gateway.

**org-service owns account administration.** Keycloak is the source of truth for credentials, roles and
department membership; org-service keeps a read model (`account` table) for search and joins:

- The row is created on the user's first call to `GET /api/v1/accounts/me` (an upsert, safe under
  concurrent first sign-ins) and refreshed from the token's claims on every later call.
- Admin changes (`PUT /api/v1/accounts/{id}/access`) are written to Keycloak first through the Admin
  API, then to the read model. The request describes the full desired state, so it is safe to retry; if
  the local write fails, the next sign-in re-syncs it from the token.

## Consequences

- Services authorize requests without calling another service, so org-service being down does not
  block request processing.
- A role or department change takes effect when the user's token is refreshed (at most the access token
  lifetime, 15 minutes). Immediate revocation would need token introspection or shorter lifetimes.
- The `account` table only lists users who have signed in at least once.
- Signing keys are cached by the services, so short Keycloak outages do not reject valid tokens; only
  sign-in and admin changes fail (the latter with `502` and a retry hint).
