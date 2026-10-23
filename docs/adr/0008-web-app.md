# ADR 0008: Web app

- Status: accepted
- Date: 2026-10-23

## Context

Request Portal is mainly a backend project, but people use it through a browser: requesters submit and
follow requests, staff work through their department's queue, admins configure templates, forms and
accounts. The web app has to cover those three roles, stay small enough to maintain alongside six
services, and run in every environment the services run in.

## Decisions

**Vue 3 with TypeScript and Vite, without a component library.** The screens are forms, tables and
dialogs, which plain components and one stylesheet of design tokens (with a dark variant) cover well.
The main bundle is about 50 KB gzipped and each page loads its own chunk. Native `<dialog>` gives modal
focus handling and Escape for free.

**The server is the source of truth; no client-side store.** Each page loads what it shows through a
small `useLoad` composable and reloads after a change. A store such as Pinia would mostly cache data the
next page fetches anyway. The only shared client state is the signed-in user and the toasts.

**Sign-in with Keycloak: authorization code with PKCE, tokens in memory.** `keycloak-js` signs the user
in before the app starts and refreshes the access token before each API call. Tokens are never written
to browser storage. Roles and department come from the token and decide the navigation and route guards,
but only to hide what would fail: the services enforce every rule themselves.

**One origin for the app and the API.** In development Vite proxies `/api` to the gateway; on Kubernetes
the ingress sends `/api` to the gateway and everything else to the app. The browser never makes a
cross-origin API call, so the API needs no CORS. The exceptions are Keycloak (token requests) and file
storage: files go straight between the browser and S3 with pre-signed URLs, so the bucket allows the
app's origin (document-service sets this up locally, Terraform in AWS).

**A typed client that turns problem details into field errors.** API types mirror the services' records.
Every error is an RFC 9457 problem; the client turns it into an `ApiError` whose per-field messages appear
under the matching inputs, and whose bulk failures list each request that could not be changed.

**One image for every environment.** nginx (unprivileged, read-only filesystem) serves the built files.
At startup it writes `/config.json` (Keycloak address, realm, client) and the Content Security Policy
from environment variables, so the same image runs locally, on k3d and in AWS.

**Tests at two levels.** Vitest covers the client, formatting, route guards and components in CI. Playwright
tests sign in through the real Keycloak page and walk through whole workflows against the running stack:
a request through two departments with a stamped scan, a requester's upload ending in a staff ZIP
download, validation messages, and an admin building a template. They found a real bug on their first
run: a debounced search could clear the selection behind an open dialog, which then acted on nothing.

**Dependencies wait two weeks.** pnpm installs no version published less than 14 days ago
(`minimumReleaseAge`), and the lockfile pins exact versions.

## Consequences

- API types are kept in step with the Java records by hand. Generating them from OpenAPI would remove
  that drift; the services do not publish OpenAPI documents yet.
- The end-to-end tests need the whole stack, so CI runs only the unit tests; the end-to-end tests run
  before a release.
- The interface is English only. A Vietnamese translation would be the first step for real users.
