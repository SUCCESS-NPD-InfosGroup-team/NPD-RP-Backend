# SCRUM-93 — PR plan

PRs in merge order, each under 400 changed lines with its own tests. What and why: [spec.md](spec.md).
How: [design.md](design.md).

| # | Branch | Status |
|---|--------|--------|
| 0a | `docs/agents-in-english-and-pr-size` | Merged ([#20](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/20)) |
| 0b | `docs/spec-driven-development` | Merged ([#21](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/21)) |
| 0c | `docs/SCRUM-93-spec` | Merged ([#22](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/22)) |
| 1a | `feature/SCRUM-93-keycloak-admin-rest-client` | Merged ([#23](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/23)) |
| 1b | `feature/SCRUM-93-keycloak-user-operations` | Merged ([#24](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/24)) |
| 1c | `feature/SCRUM-93-keycloak-invitation` | Merged ([#25](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/25)) |
| 2 | `feature/SCRUM-93-app-user-registration` | This PR |
| 3 | `feature/SCRUM-93-create-user-endpoint` | Pending |
| 4 | `feature/SCRUM-93-list-users` | Pending |
| 5 | `feature/SCRUM-93-last-access` | Pending |

## 0a. AGENTS.md in English and PR size — merged

`AGENTS.md` translated to English with the language rule, PRs under 400 changed lines, `start_env.bat`
ignored. 204 lines.

## 0b. Spec-driven development and no AI commits before review — merged

The `docs/specs/<JIRA-ID>/` rule and the rule that AI agents do not commit before review. 25 lines.

## 0c. SCRUM-93 spec, design and plan — merged

This folder. Depends on 0b only for the rule it follows; can be merged in any order with it.

## 1a. Keycloak Admin REST client — part 1 of 7 — merged

- `RestClient` for the Keycloak Admin REST API with the service-account token (`KeycloakAdminClientConfig`).
- Validated settings `app.keycloak.*` (`KeycloakAdminProperties`); `KEYCLOAK_ADMIN_CLIENT_ID` and
  `KEYCLOAK_ADMIN_CLIENT_SECRET` in `.env.example`; test settings in
  `src/test/resources/config/application.properties`.
- Tests with `MockRestServiceServer`: token requested once and sent on every call whoever is logged in;
  rejected credentials; URLs from the issuer; the app does not start with invalid settings.
- Its comments point to this `design.md`.
- ~335 lines. Needs Keycloak changes 1–2 and the Railway variables before it is deployed (design §8).

## 1b. Keycloak user operations — part 2 of 7 — merged

- `KeycloakAdminClient`: create account (id from `Location`), assign realm role, delete account.
- Error mapping (design §3): `KeycloakUserExistsException` on 409, `IdentityProviderUnavailableException`
  on 5xx, timeouts and connection errors, also on the token request (code `IDENTITY_PROVIDER_UNAVAILABLE`
  with its Spanish texts).
- Tests with `MockRestServiceServer` for each call and error.
- 396 lines. Depends on 1a. Split from the invitation (1c) to stay under 400 lines. Needs Keycloak changes 4–5.

## 1c. Invitation email — part 3 of 7 — merged

- `KeycloakAdminClient.sendInvitation`: `execute-actions-email` with `UPDATE_PASSWORD`.
- Settings `frontend-client-id`, `invitation-redirect-uri`, `invitation-lifespan`;
  `KEYCLOAK_FRONTEND_CLIENT_ID` and `APP_FRONTEND_URL` in `.env.example`.
- Tests of the call and of the new settings.
- ~120 lines. Depends on 1b. Needs Keycloak changes 3, 6 and 7.

## 2. `app_user` and registration service — part 4 of 7

- Migration `V8__create_app_user.sql`, `AppUser` entity, `AppUserRepository`.
- `CreateUserCommand`, `UserRegistrationService`: duplicate email check, Keycloak calls, insert and audit
  in one transaction, rollback in Keycloak on failure.
- Error code `EMAIL_TAKEN` with its Spanish texts.
- Unit tests of the service (Keycloak client mocked), including each rollback case.
- 399 lines. Depends on 1c.

## 3. `POST /api/v1/access/users` — part 5 of 7

- `UserController` (POST), `CreateUserRequest` and response DTOs, 201 with `Location`, full OpenAPI
  description with every error.
- `SecurityConfig`: `/api/*/access/**` → `operations-administrator`.
- End-to-end tests: 201, 400, 401, 403, 409, 503 (Keycloak client replaced in the test context).
- ~300 lines. Depends on 2.

## 4. `GET /api/v1/access/users` and `GET /api/v1/access/roles` — part 6 of 7

- Paged list sorted by full name, without filters; response with user, role, state and last access.
- The four roles with their Spanish names and descriptions from `messages.properties`.
- OpenAPI and end-to-end tests.
- ~280 lines. Depends on 2 (can be built in parallel with 3).

## 5. Last access and state — part 7 of 7

- After authentication, update `last_access_at` of the calling user at most once every 5 minutes, and
  move `PENDING` to `ACTIVE` on the first access (design §5).
- Tests of the throttling and the state change.
- ~200 lines. Depends on 2.
