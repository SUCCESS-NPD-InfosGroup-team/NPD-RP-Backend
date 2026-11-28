# SCRUM-231 — Plan

PRs in merge order. Each one compiles, passes "Validar ramas" and "Run Unit Tests" (80% coverage on new
code), leaves the application working, and stays under 400 changed lines, deletions included
(`git diff --shortstat origin/develop...HEAD`). What and why: [spec.md](spec.md). How:
[design.md](design.md).

The first version of the story (deactivate, delete one day later) was merged in #79 and #85–#92. The
branches below are stacked: each one starts from the previous, so each PR shows only its own part.

| # | Branch | Content | Lines | Depends on | Status |
|---|--------|---------|-------|------------|--------|
| 1 | `docs/SCRUM-231-delete-user` | `spec.md` and `design.md`, rewritten. | 305 | — | In review ([#102](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/102)) |
| 2 | `docs/SCRUM-231-delete-user-plan` | This `plan.md`, rewritten. | ~150 | PR 1 | In review ([#103](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/103)) |
| 3 | `feature/SCRUM-231-delete-user-service` | `UserDeletionService` and its unit tests. | 309 | PR 2 | Pending |
| 4 | `feature/SCRUM-231-delete-user-endpoint` | `DELETE /users/{id}`, OpenAPI, end-to-end tests. | 246 | PR 3 | Pending |
| 5 | `feature/SCRUM-231-remove-reactivation` | Reactivation, `ACCOUNT_DELETION_DUE`. | 390 | PR 4 | Pending |
| 6 | `feature/SCRUM-231-remove-deactivation-endpoint` | Deactivation endpoint, `UserVersionRequest`, end-to-end tests. | 298 | PR 5 | Pending |
| 7 | `feature/SCRUM-231-trim-deactivation-service` | CA-04 and compensation of the now unused deactivation. | 186 | PR 6 | Pending |
| 8 | `feature/SCRUM-231-remove-deactivation-service` | `AccountActivationService` and its tests. | 336 | PR 7 | Pending |
| 9 | `feature/SCRUM-231-remove-deletion-scheduler` | The job, `AccountStateChanged`, `SchedulingConfig`. | 373 | PR 8 | Pending |
| 10 | `feature/SCRUM-231-remove-deletion-service` | `AccountDeletionService`, settings, `deletesAt`. | 354 | PR 9 | Pending |
| 11 | `feature/SCRUM-231-drop-disabled-state` | `V14`, `DISABLED`, `disabledAt`, `ACCOUNT_DISABLED`, `setEnabled`. | 195 | PR 10 | Pending |

The docs go in two PRs, the new service and its endpoint in two, and the deactivation service is
trimmed before it is deleted, all to stay under 400 lines. The frontend can switch to the new endpoint
after PR 4. The old endpoints answer until PR 5 (reactivation) and PR 6 (deactivation), so the frontend
has to stop calling them before those are merged.

## PR 3 — Delete a user: service

- `UserDeletionCommand` (id, version, actor); `UserDeletionService.delete` (design §1–2).
- `UserDeletionServiceTest` (unit, mocked Keycloak): order lock → Keycloak → delete → audit, CA-04 (last
  one, another active one, a pending one), version, unknown user, Keycloak down → nothing deleted.

## PR 4 — Delete a user: endpoint

- `UserController.delete`: `DELETE /api/v1/access/users/{id}?version=`, 204, OpenAPI (design §4).
- `LAST_OPERATIONS_ADMINISTRATOR` message: "eliminar" instead of "desactivar".
- `UserDeletionControllerTest` (Testcontainers): 204 and gone (CA-01, CA-02), audit rows (CA-06), CA-04,
  CA-05, CA-09, 400/404/409, 503 keeps the user.

## PR 5 — Remove the reactivation

- `AccountActivationService.reactivate`, `POST /users/{id}/reactivation`, `ACCOUNT_DELETION_DUE` and its
  messages; `AccountReactivationServiceTest` and the reactivation end-to-end cases.

## PR 6 — Remove the deactivation endpoint

- `POST /users/{id}/deactivation`, `UserVersionRequest`, `UserActivationControllerTest`.

## PR 7 — Trim the deactivation service

- With no caller left, the last-administrator check and the compensation go first, with their tests.

## PR 8 — Remove the deactivation service

- `AccountActivationService`, `AccountActivationCommand`, `AccountActivationServiceTest`.

## PR 9 — Remove the scheduled deletion

- `AccountDeletionScheduler`, `AccountStateChanged`, `SchedulingConfig`, their tests and
  `AccountDeletionTest`.

## PR 10 — Remove the deletion after one day

- `AccountDeletionService`, `AccountDeletionProperties`, `AccountDeletionConfig`,
  `AppUserRepository.findByState`, the `app.access.*` settings, `UserResponse.deletesAt`;
  `AccountDeletionServiceTest`.

## PR 11 — Remove the deactivated state

- `V14__drop_app_user_deactivation.sql` (design §3), `docs/database/README.md`.
- `AppUser.disabledAt`, `AppUserState.DISABLED`, `UserResponse.disabledAt`, the `DISABLED` state in the
  list's OpenAPI.
- `ACCOUNT_DISABLED` in `UserEditService` and its messages; `KeycloakAdminClient.setEnabled` and its tests.
- Tests: `DropDeactivationMigrationTest` (V13 → V14 deletes the `DISABLED` rows with their audit),
  `LastAccessTest` without `DISABLED`.
