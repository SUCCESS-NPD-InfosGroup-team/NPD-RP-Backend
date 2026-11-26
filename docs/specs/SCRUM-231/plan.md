# SCRUM-231 — Plan

PRs in merge order, all against `develop`. Each one compiles, passes "Validar ramas" and "Run Unit
Tests" (80% coverage on new code), and stays under 400 changed lines (`git diff --shortstat
origin/develop...HEAD`). What and why: [spec.md](spec.md). How: [design.md](design.md).

The code PRs start once SCRUM-53's last-administrator safeguards are merged
([#76](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/76)): they reuse its
`LAST_OPERATIONS_ADMINISTRATOR`, `lockById` and `lockByRoleAndState` instead of writing them again. If
another story takes `V13` first, the migration of PR 3 is renumbered.

| # | Branch | Content | Est. lines | Depends on | Status |
|---|--------|---------|------------|------------|--------|
| 1 | `docs/SCRUM-231-spec-design-plan` | `spec.md`, `design.md` and this `plan.md`. | ~330 | — | In review |
| 2 | `feature/SCRUM-231-keycloak-enable-delete` | `setEnabled` and 404-tolerant `deleteAccount` in the Keycloak client. | ~180 | PR 1 | Pending |
| 3 | `feature/SCRUM-231-deactivate-service` | V13, `AppUser.disabledAt`, settings, deactivation service with CA-04 and audit. | ~390 | PR 2, SCRUM-53 #76 | Pending |
| 4 | `feature/SCRUM-231-reactivate-service` | Reactivation service, `ACCOUNT_DELETION_DUE`. | ~250 | PR 3 | Pending |
| 5 | `feature/SCRUM-231-activation-endpoints` | Both endpoints, `disabledAt`/`deletesAt` in `UserResponse`, OpenAPI, messages, end-to-end tests. | ~380 | PR 4 | Pending |
| 6 | `feature/SCRUM-231-account-deletion` | Deletion scheduler, startup recovery, `AuditService.recordDeletion`, hooks after commit. | ~390 | PR 5 | Pending |
| 7 | `feature/SCRUM-231-block-disabled-edit` | `ACCOUNT_DISABLED` in the SCRUM-53 edit. | ~120 | PR 3, SCRUM-53 PR 6 | Pending |

After PR 5 the frontend can deactivate and reactivate; after PR 6 the whole story is served. PR 7 can go
in any order after PR 3 once SCRUM-53's edit endpoint exists.

## PR 2 — Keycloak calls

**Part 1 of 6 code PRs. Jira: SCRUM-231.**

- `KeycloakAdminClient.setEnabled(id, enabled)`: `GET` the representation, `PUT` it back with `enabled`
  changed (design §4, calls 1–2); 404 → `IllegalStateException`.
- `KeycloakAdminClient.deleteAccount(id)`: call 4; 404 → returns normally.
- Tests in `KeycloakAdminClientTest` (`MockRestServiceServer`): other fields kept, 404 on each,
  5xx/timeout → unavailable.

## PR 3 — Deactivation service

**Part 2 of 6 code PRs. Jira: SCRUM-231.**

- `V13__app_user_deactivation.sql` (design §5): `disabled_at`, the state/moment CHECK, `DELETE` in the
  `audit_log.action` CHECK; `docs/database/README.md`.
- `AppUser.disabledAt`; `AccountLifecycleProperties` (`app.access.*`, design §7) in `application.properties`
  and `.env.example`.
- `AccountActivationCommand` (id, version, actor); `AccountActivationService.deactivate`: checks, Keycloak
  disable + logout, transaction with lock, version and CA-04 recheck, audit, compensation (enable again).
- Tests: `AccountActivationServiceTest` (unit, mocked Keycloak): CA-01 order of calls, CA-04 (last one,
  another active one, a `PENDING` administrator does not count), already disabled → no calls, version
  409, compensation when the DB fails, NPD lead employee not touched (CA-09); migration test of the CHECK.

## PR 4 — Reactivation service

**Part 3 of 6 code PRs. Jira: SCRUM-231.**

- `AccountActivationService.reactivate`: due check, Keycloak enable, transaction, state back to
  `ACTIVE`/`PENDING`, audit, compensation (disable again).
- `ErrorCode.ACCOUNT_DELETION_DUE` (409).
- Tests: back to `ACTIVE` and to `PENDING`, past the deletion time (also under the lock), not disabled →
  no calls, compensation.

## PR 5 — Endpoints

**Part 4 of 6 code PRs. Jira: SCRUM-231.**

- `UserController.deactivate` / `reactivate`: `POST /api/v1/access/users/{id}/deactivation` and
  `/reactivation`, body `{ version }`, 200 with the user.
- `UserResponse.disabledAt` and `deletesAt`; `UserQueryService` fills them.
- OpenAPI (every answer of design §6) and Spanish messages of the new codes.
- Tests: `UserActivationControllerTest` with Testcontainers and mocked Keycloak client: CA-01, CA-02,
  CA-04, CA-05 (403 for each other role, nothing changes), CA-06 (audit rows), CA-07 (`deletesAt` = one
  day later), CA-09 (employee row unchanged), 409 on `version`, 503 leaves `app_user` unchanged.

## PR 6 — Scheduled deletion

**Part 5 of 6 code PRs. Jira: SCRUM-231.**

- `SchedulingConfig` (`@EnableScheduling`); `AccountDeletionScheduler` in `access/job` (design §3):
  `schedule`, `cancel`, the task, retry, recovery on `ApplicationReadyEvent`.
- `AccountActivationService` schedules after the commit of a deactivation and cancels after a
  reactivation.
- `AuditService.recordDeletion` and `AppUserRepository.findByState`.
- Tests: deletion at the time (short delay setting), stale task does nothing, reactivated before the time
  → not deleted, Keycloak down → retried, 404 → row deleted, recovery schedules overdue and future
  accounts, audit `DELETE` rows with name and email and actor `system`, employee row kept (CA-08, CA-09).

## PR 7 — Editing a deactivated account

**Part 6 of 6 code PRs. Jira: SCRUM-231.**

- `ErrorCode.ACCOUNT_DISABLED` (409), Spanish message.
- `UserEditService.update`: rejected before any Keycloak call and under the lock.
- OpenAPI of `PUT /api/v1/access/users/{id}` (new 409).
- Tests: edit of a `DISABLED` user → 409 and nothing changes.
