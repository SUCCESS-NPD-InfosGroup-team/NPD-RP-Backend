# SCRUM-231 — Plan

PRs in merge order. Each one compiles, passes "Validar ramas" and "Run Unit Tests" (80% coverage on new
code), leaves the application working, and stays under 400 changed lines, deletions included
(`git diff --shortstat origin/develop...HEAD`). What and why: [spec.md](spec.md). How:
[design.md](design.md).

The first version of the story (deactivate, delete one day later) was merged in #79 and #85–#92. The
branches below are stacked: each one starts from the previous, so each PR shows only its own part.

| # | Branch | Content | Lines | Depends on | Status |
|---|--------|---------|-------|------------|--------|
| 1 | `docs/SCRUM-231-delete-user` | `spec.md` and `design.md`, rewritten. | ~305 | — | Pending |
| 2 | `docs/SCRUM-231-delete-user-plan` | This `plan.md`, rewritten. | ~145 | PR 1 | Pending |
| 3 | `feature/SCRUM-231-delete-user` | `DELETE /users/{id}`: service, endpoint, tests. | ~380 | PR 2 | Pending |
| 4 | `feature/SCRUM-231-remove-reactivation` | Reactivation, `ACCOUNT_DELETION_DUE`. | ~350 | PR 3 | Pending |
| 5 | `feature/SCRUM-231-remove-deactivation-endpoint` | Deactivation endpoint, `UserVersionRequest`, end-to-end tests. | ~330 | PR 4 | Pending |
| 6 | `feature/SCRUM-231-remove-deactivation-service` | `AccountActivationService` and its tests. | ~390 | PR 5 | Pending |
| 7 | `feature/SCRUM-231-remove-deletion-scheduler` | The job, `AccountStateChanged`, `SchedulingConfig`. | ~380 | PR 6 | Pending |
| 8 | `feature/SCRUM-231-remove-deletion-service` | `AccountDeletionService`, settings, `deletesAt`. | ~350 | PR 7 | Pending |
| 9 | `feature/SCRUM-231-drop-disabled-state` | `V14`, `DISABLED`, `disabledAt`, `ACCOUNT_DISABLED`, `setEnabled`. | ~300 | PR 8 | Pending |

The docs go in two PRs to stay under 400 lines. The frontend can switch to the new endpoint after
PR 3. The old endpoints answer until PR 5, so the frontend has to stop calling them before PR 5 is
merged. Line counts are estimates; a PR that ends up over 400 is split again and this table updated.

## PR 3 — Delete a user

- `UserDeletionCommand` (id, version, actor); `UserDeletionService.delete` (design §1–2).
- `UserController.delete`: `DELETE /api/v1/access/users/{id}?version=`, 204, OpenAPI (design §4).
- `LAST_OPERATIONS_ADMINISTRATOR` message: "eliminar" instead of "desactivar".
- Tests: `UserDeletionServiceTest` (unit, mocked Keycloak): order Keycloak → delete → audit, CA-04 (last
  one, another active one), version, Keycloak down → nothing deleted. `UserDeletionControllerTest`
  (Testcontainers): 204 and gone from the list, audit rows (CA-06), CA-09, CA-05, 400/404/409.

## PR 4 — Remove the reactivation

- `AccountActivationService.reactivate`, `POST /users/{id}/reactivation`, `ACCOUNT_DELETION_DUE` and its
  messages; `AccountReactivationServiceTest` and the reactivation end-to-end cases.

## PR 5 — Remove the deactivation endpoint

- `POST /users/{id}/deactivation`, `UserVersionRequest`, `UserActivationControllerTest`.

## PR 6 — Remove the deactivation service

- `AccountActivationService`, `AccountActivationCommand`, `AccountActivationServiceTest`.

## PR 7 — Remove the scheduled deletion

- `AccountDeletionScheduler`, `AccountStateChanged`, `SchedulingConfig`, `AccountDeletionService.pending`,
  `AppUserRepository.findByState`, their tests and `AccountDeletionTest`.

## PR 8 — Remove the deletion after one day

- `AccountDeletionService`, `AccountDeletionProperties`, `AccountDeletionConfig`, the `app.access.*`
  settings, `UserResponse.deletesAt`; `AccountDeletionServiceTest`.

## PR 9 — Remove the deactivated state

- `V14__drop_app_user_deactivation.sql` (design §3), `docs/database/README.md`.
- `AppUser.disabledAt`, `AppUserState.DISABLED`, `UserResponse.disabledAt`, the `DISABLED` state in the
  list's OpenAPI.
- `ACCOUNT_DISABLED` in `UserEditService` and its messages; `KeycloakAdminClient.setEnabled` and its tests.
- Tests: `LastAccessTest` without `DISABLED`; migration deletes `DISABLED` rows with their audit.
