# SCRUM-93 — Design

How the API creates users with their role and lists them. What and why: [spec.md](spec.md). PRs:
[plan.md](plan.md).

## 1. Overview

Keycloak keeps the identity: the account, its password and its realm role. The API keeps a local
`app_user` table with the users created through the platform, which is what the list reads.

```
POST /api/v1/access/users
  → validate (fields, role, duplicate email in app_user)
  → Keycloak Admin REST API: create account → assign role → send invitation email
  → one DB transaction: insert app_user + audit rows
  → if anything after the account creation fails: delete the Keycloak account (rollback)
```

All code lives in the `access` module: `client` (Keycloak), `persistence`, `service`, `web`, `dto`.

## 2. Decision: how the API talks to Keycloak

**Chosen: Spring `RestClient` + Spring Security OAuth2 client (`client_credentials`).**

| Option | Why not / why |
|--------|---------------|
| A. `keycloak-admin-client` (official Java library) | Complete and typed, but brings a second HTTP stack (RESTEasy) and Jackson 2 next to Spring Boot 4's Jackson 3, JAX-RS exceptions and `Response` objects that must be closed, chained interfaces that are hard to mock, and a library version that must follow the Keycloak server version. |
| **B. `RestClient` + OAuth2 client** | **Chosen.** We need five endpoints, not the whole Admin API. Same HTTP stack, Jackson, timeouts and observability as the rest of the app; Spring Security gets and caches the token; Spring exceptions map directly to our error codes; `MockRestServiceServer` tests without Keycloak. Cost: we write the five calls and two small records. |
| C. `RestClient` + token handled by hand | Rewrites (and must test under concurrency) what Spring Security already does in B. |

Details of B:

- The client registration is built in code (`KeycloakAdminClientConfig`), not with
  `spring.security.oauth2.client.*`, so Spring Boot adds no OAuth2 login and the client also exists
  in the planner import, which runs without a web server.
- `AuthorizedClientServiceOAuth2AuthorizedClientManager` (works outside a web request) with a **fixed
  principal**: the token belongs to the API, so every request shares one cached token, whoever is
  logged in.
- The token and admin URLs come from the existing `KEYCLOAK_ISSUER_URI`
  (`{keycloak}/realms/{realm}` → `.../protocol/openid-connect/token` and
  `{keycloak}/admin/realms/{realm}`), so there is a single Keycloak URL to configure.
- Timeouts: 2 s to connect, 5 s per answer, for the admin calls and the token requests.

## 3. Keycloak Admin REST calls

The API calls Keycloak as the service account of the confidential client `npd-rp-api-admin`, with the
`realm-management` roles `manage-users`, `view-users` and `query-users`.

| # | Call | Answer |
|---|------|--------|
| 1 | `POST /realms/{realm}/protocol/openid-connect/token` (`grant_type=client_credentials`) | Token, cached until it is about to expire |
| 2 | `POST /admin/realms/{realm}/users` | 201 + `Location: .../users/{id}`; 409 if the email exists |
| 3 | `GET /admin/realms/{realm}/roles/{role}` | `{id, name}`, needed to map the role |
| 4 | `POST /admin/realms/{realm}/users/{id}/role-mappings/realm` | 204 |
| 5 | `PUT /admin/realms/{realm}/users/{id}/execute-actions-email?client_id=...&redirect_uri=...&lifespan=172800` with `["UPDATE_PASSWORD"]` | 204 |
| — | `DELETE /admin/realms/{realm}/users/{id}` | Rollback |

Body of call 2: `{"username": email, "email": email, "firstName": fullName, "enabled": true,
"emailVerified": false}`. The full name goes in `firstName` (splitting names such as "Juan David Vela
Coronado" would be wrong), so `lastName` must be optional in the realm's user profile.

Call 5 sends the invitation: a link valid for **48 hours** to set the password. `client_id` is the
frontend's client and `redirect_uri` is `{APP_FRONTEND_URL}/recursos`. Keycloak does not sign the user
in after setting the password: it shows a "Back to application" link, and the frontend's auth guard
sends them to the login page.

### Error mapping

| Keycloak answers | Cause | API answers |
|------------------|-------|-------------|
| 409 on call 2 | Email already exists (CA-05) | 409 `EMAIL_TAKEN` |
| 401 / 403, or the token request is refused | Wrong secret or missing roles: our configuration | 500, logged as an error |
| 5xx, timeout, connection refused (calls or token) | Keycloak down (CA-07) | 503 `IDENTITY_PROVIDER_UNAVAILABLE` |
| Failure on call 5 | Usually SMTP failing | Rollback and 503, so the administrator can retry |

### Order and consistency

Keycloak and the database cannot share a transaction, so:

1. Validate the request and check `app_user` for the email (fast, no Keycloak call).
2. Calls 2 → 5.
3. Insert `app_user` and the audit rows in one transaction.
4. If call 3, 4 or 5, or step 3, fails: delete the Keycloak account (best effort; if the delete also
   fails it is logged with the Keycloak id).

No database transaction is open while waiting for Keycloak.

## 4. Data model

New table `app_user` (migration `V8__create_app_user.sql`):

| Column | Type | Notes |
|--------|------|-------|
| `id` | `bigint` identity | Primary key; the id in the API and in `audit_log.entity_id` |
| `keycloak_id` | `uuid` | Unique; id of the Keycloak account |
| `full_name` | `varchar(150)` | |
| `email` | `varchar(254)` | Unique, stored in lower case; also the username |
| `role` | `varchar(40)` | One of the four realm role names (`Role` enum) |
| `state` | `varchar(20)` | `PENDING`, `ACTIVE` (`DISABLED` reserved for a future story) |
| `last_access_at` | `timestamptz` | Null until the first access |
| `created_at`, `created_by` | `timestamptz`, `varchar(150)` | When, and the `preferred_username` of the administrator |

The creation is audited with `AuditService.recordCreation("app_user", id, values, actor)`, which
writes one row per field (CA-08).

## 5. Decision: state and last access

**Chosen: tracked in `app_user` by the API.** Keycloak has no "last login" field: it can only be read
from login events, which must be enabled in the realm, expire, and need one or more calls per user on
every list request.

- `state`: `PENDING` on creation; `ACTIVE` on the first API call with that user's token (having a token
  proves the password was set).
- `last_access_at`: updated when an authenticated user calls the API, at most once every 5 minutes per
  user, so it is not a write per request.
- The list is then one paged JPA query and works when Keycloak is down.
- Trade-offs accepted: "last access" means last use of the API, not the exact login moment; changes
  made directly in the Keycloak console (disabling or deleting a user) are not reflected.

## 6. API

| Endpoint | Roles | Answers |
|----------|-------|---------|
| `POST /api/v1/access/users` | Operations administrator | 201 + `Location` + the user; 400 `VALIDATION_FAILED`; 401; 403; 409 `EMAIL_TAKEN`; 503 `IDENTITY_PROVIDER_UNAVAILABLE` |
| `GET /api/v1/access/users?page=&size=` | Operations administrator | 200, paged and sorted by full name, no filters: `id`, `fullName`, `email`, `role`, `state`, `lastAccessAt`; 401; 403 |
| `GET /api/v1/access/roles` | Operations administrator | 200: each role's `code`, `name` and `description` (Spanish texts from `messages.properties`); 401; 403 |

Request of the POST: `{"fullName": "...", "email": "...", "role": "operations-administrator"}`, with
Jakarta Validation (`@NotBlank`, `@Size(max = 150)`, `@Email`, `@Size(max = 254)`, `@NotNull`) and the
role checked against the `Role` enum. Everything related to user management is only for Operations
administrators: `SecurityConfig` gets the rule `/api/*/access/**` → `operations-administrator`, so any
future `access` endpoint is covered too.

New error codes: `EMAIL_TAKEN` (409) and `IDENTITY_PROVIDER_UNAVAILABLE` (503), with their Spanish texts.

## 7. Settings and environment variables

| Variable | Property | Notes |
|----------|----------|-------|
| `KEYCLOAK_ISSUER_URI` (existing) | `app.keycloak.issuer-uri` | Same realm URL as the resource server |
| `KEYCLOAK_ADMIN_CLIENT_ID` | `app.keycloak.admin-client-id` | Default `npd-rp-api-admin` |
| `KEYCLOAK_ADMIN_CLIENT_SECRET` | `app.keycloak.admin-client-secret` | Required: the app does not start without it |
| `KEYCLOAK_FRONTEND_CLIENT_ID` | `app.keycloak.frontend-client-id` | Required; `client_id` of the invitation link |
| `APP_FRONTEND_URL` | `app.keycloak.invitation-redirect-uri` = `${APP_FRONTEND_URL}/recursos` | Required; differs per environment |
| — | `app.keycloak.invitation-lifespan=48h`, `connect-timeout=2s`, `read-timeout=5s` | Fixed in `application.properties` |

The settings are a validated `@ConfigurationProperties` record (`KeycloakAdminProperties`). Tests set
their values in `src/test/resources/config/application.properties`.

## 8. Changes outside the repository

Needed in the Keycloak realm `npd-rp` of **each environment** (local, staging, production), and the
variables in **Railway**, before the PR that needs them is deployed:

| # | Where | Change | Needed by |
|---|-------|--------|-----------|
| 1 | Clients → Create client | `npd-rp-api-admin`: Client authentication ON, only "Service accounts roles" ticked, no redirect URIs. Its secret goes to `KEYCLOAK_ADMIN_CLIENT_SECRET`. | PR 1a |
| 2 | That client → Service accounts roles | `realm-management`: `manage-users`, `view-users`, `query-users` (not `realm-admin`). | PR 1a |
| 3 | Realm settings → Email | SMTP configured and tested; without it the invitation fails. | PR 1b |
| 4 | Realm settings → Login | Email as username: ON. | PR 1b |
| 5 | Realm settings → User profile → `lastName` | Not required. | PR 1b |
| 6 | Frontend client → Valid redirect URIs | Includes `{APP_FRONTEND_URL}/recursos`. | PR 1b |
| 7 | Realm settings → Localization (optional) | Spanish for the email and the password page. | PR 1b |
