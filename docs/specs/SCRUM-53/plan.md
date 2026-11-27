# SCRUM-53 — Plan

PRs in merge order, all against `develop`. Each one compiles, passes "Validar ramas" and "Run Unit
Tests" (80% coverage on new code), and stays under 400 changed lines (`git diff --shortstat
origin/develop...HEAD`). What and why: [spec.md](spec.md). How: [design.md](design.md).

| # | Branch | Content | Est. lines | Depends on | Status |
|---|--------|---------|------------|------------|--------|
| 1 | `docs/SCRUM-53-spec-design` | `spec.md` and `design.md` of this folder. | ~340 | — | Merged ([#69](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/69)) |
| 2 | `docs/SCRUM-53-plan` | This `plan.md`. | ~130 | PR 1 | Merged ([#71](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/71)) |
| 3 | `feature/SCRUM-53-user-read-fields` | V12 and the new fields of the user list and detail (CA-09, CA-13, collaborators of CA-11). | ~330 | PR 1 | Merged ([#72](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/72)) |
| 4 | `feature/SCRUM-53-keycloak-edit-operations` | Keycloak calls to rename, change the role and close the sessions. | ~300 | PR 1 | Merged ([#73](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/73)) |
| 5a | `feature/SCRUM-53-user-edit-validation` | Request, command and validator of the edit; new error codes. | ~190 | PR 3 | In review ([#74](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/74)) |
| 5b | `feature/SCRUM-53-user-edit-service` | Edit service for name and role: CA-05, CA-13, Keycloak calls, transaction, audit. | ~370 | PR 4, PR 5a | In review ([#75](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/75)) |
| 5c | `feature/SCRUM-53-user-edit-safeguards` | Last administrator (CA-04) with locks, and the Keycloak compensation. | ~280 | PR 5b | In review ([#76](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/76)) |
| 6 | `feature/SCRUM-53-edit-user-endpoint` | `PUT /api/v1/access/users/{id}`, OpenAPI, messages, end-to-end tests. | ~300 | PR 5c | In review ([#77](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/77)) |
| 7a | `feature/SCRUM-53-npd-lead-employee-service` | `planner` service: registering the record of a new NPD lead. | ~215 | PR 3 | In review ([#78](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/78)) |
| 7b | `feature/SCRUM-53-npd-lead-role-changes` | `planner` service: Baja by role change, back to Activo, rename. | ~300 | PR 7a | In review ([#80](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/80)) |
| 8 | `feature/SCRUM-53-role-change-npd-lead` | CA-10 and CA-11 in the edit: register, Baja, back to Activo, rename. | ~360 | PR 6, PR 7b | In review ([#81](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/81)) |
| 9 | `feature/SCRUM-53-link-existing-employee` | Linking an existing NPD lead employee (HU-10 CA-05 to CA-07, CA-12, CA-13). | ~350 | PR 8 | In review ([#82](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/82)) |
| 10 | `feature/SCRUM-53-role-change-termination-guard` | Guard in Recursos for Baja by "Cambio de rol" (design §7). | ~160 | PR 3 | In review ([#83](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/83)) |

PR 4 can be reviewed in parallel with PR 3; PR 7a, 7b and PR 10 in parallel with PRs 4 to 6. After PR 6 the
frontend can edit name and role of every user except NPD leads; after PR 9 the whole story is served.
The estimates include tests; a PR that grows past 400 lines is split again before it is opened.

## PR 3 — Read fields and data model

**Part 1 of 11 code PRs. Jira: SCRUM-53.**

- `V12__app_user_employee_link.sql` (design §5): `app_user.employee_id` (unique, FK to `employee`),
  `app_user.version`, the inactive termination reason `CAMBIO_DE_ROL`, and their comments.
- `AppUser`: `employeeId` (plain `Long`) and `version` (`@Version`).
- `NpdLeadEmployeeService.summaries(ids)` in `planner/service` (only this method here): N°, status and
  collaborators (Baja excluded) of each record, in one JPQL query of `EmployeeRepository`.
- `UserResponse`: `createdAt`, `createdBy` (name resolved per page, design §6), `version`, `employee`.
  `UserQueryService.list` and `get` fill them with one query each per page.
- OpenAPI of `GET /api/v1/access/users` and `/{id}` (new fields, `employee = null` for an NPD lead
  without employee record).
- `docs/database/README.md`: V12 and the rule of the link (the ER diagram only has the planner tables).
- Tests: `UserControllerTest` (new fields, creator not in `app_user`), `LastAccessTest` (the last
  access does not change `version`), the collaborators query (Baja excluded, several leads).

## PR 4 — Keycloak edit operations

**Part 2 of 11 code PRs. Jira: SCRUM-53.**

- `KeycloakAdminClient.rename(id, fullName)`: `GET` the representation, `PUT` it back with the new
  `firstName` (calls 1–2).
- `KeycloakAdminClient.replaceRealmRole(id, role)`: current realm roles, remove the platform roles it
  has (only the four of `Role`), add the new one (calls 3–5).
- `KeycloakAdminClient.logout(id)` (call 6).
- Same error mapping as today; 404 of the account → `IllegalStateException` logged with the id.
- Tests with `MockRestServiceServer` in `KeycloakAdminClientTest`: each call, other attributes kept on
  rename, `default-roles-*` and `offline_access` not removed, 5xx/timeout → unavailable.

## PR 5a — Edit validation

**Part 3 of 11 code PRs. Jira: SCRUM-53.** PR 5 was split in three (5a to 5c) because it reached ~780
lines.

- `UpdateUserRequest` (`version`, `fullName`, `role`; `employee` and `linkEmployeeId` come in PR 8 and
  9) and `UpdateUserCommand` (id, request, actor).
- `UpdateUserRequestValidator` in `service/validator`: `fullName` required and up to 150 (CA-12),
  `role` one of the four, `version` required; every error together, in the `user` message scope.
- `Role.ofRealmName`, shared with `CreateUserRequestValidator`.
- `ErrorCode.LAST_OPERATIONS_ADMINISTRATOR` and `NPD_LEAD_WITHOUT_EMPLOYEE` (409).
- Tests: `UpdateUserRequestValidatorTest` (the four roles, every missing field together, long name and
  unknown role).

## PR 5b — Edit service

**Part 4 of 11 code PRs. Jira: SCRUM-53.**

- `UserEditService.update` (design §1, §3, §5): version check before the fields, no-change check
  (CA-05), CA-13 (`NPD_LEAD_WITHOUT_EMPLOYEE`), Keycloak calls (rename; replace the role and log out),
  transaction with the user locked (`AppUserRepository.lockById`, `@Lock(PESSIMISTIC_WRITE)`), version
  recheck, audit of `fullName` and `role`.
- Until PR 8, a change **to** `npd-leader` is rejected with the field error `role.ROLE_NOT_ASSIGNABLE`
  (the employee record it needs comes there).
- Tests: `UserEditServiceTest` (unit, mocked Keycloak): rename, role change and logout in order, CA-05
  calls nothing, stale version, unknown user, CA-13, NPD lead blocked, version changed meanwhile.

## PR 5c — Edit safeguards

**Part 5 of 11 code PRs. Jira: SCRUM-53.**

- CA-04: first check with `countByRoleAndStateAndIdNot`; in the transaction, the active administrators
  locked in id order (`lockByRoleAndState`) before the user, so two administrators demoting each other
  wait for one another instead of deadlocking, and the second one gets `LAST_OPERATIONS_ADMINISTRATOR`.
- Compensation (design §3): after a failure, the old role and name put back in Keycloak, best effort; a
  failed undo is logged with the account id and the original error is returned.
- Tests in `UserEditServiceTest`: last administrator, pending administrator not counted, recheck with
  the lock, undo when the role or the database fails, nothing to undo when the rename fails, failed undo.

## PR 6 — Edit endpoint

**Part 6 of 11 code PRs. Jira: SCRUM-53.**

- `UserController.update`: `PUT /api/v1/access/users/{id}` (version `v1`), 200 with the user.
- OpenAPI: summary, body, every answer of design §6 for name and role (400, 401, 403, 404, 409 with
  each code, 503).
- Spanish messages of the new error codes and of the field errors of `fullName`, `role`, `version`.
- Tests: `UserUpdateControllerTest` with Testcontainers and mocked Keycloak client: CA-01 (role and
  sessions), CA-04 (own role, last administrator), CA-05 (no audit rows, same `version`), CA-06 (403 for
  each other role), CA-07 (audit rows), CA-12, CA-13, 409 on `version`, 503 leaves `app_user` unchanged.

## PR 7a — NPD lead employee registration

**Part 7 of 11 code PRs. Jira: SCRUM-53.** PR 7 was split in two (7a, 7b) because it reached ~500
lines.

- `NewNpdLeadCommand` and `NpdLeadEmployeeRequest` (N°, gender, position, work mode, hire date,
  status) in `planner/dto/request`.
- `NpdLeadEmployeeService.register` (design §4): reuses `ResourceRegistrationService` (same validation
  and creation audit as Recursos), field errors prefixed `employee.`, status only `ACTIVO` or
  `LICENCIA`; the record is marked as NPD lead, without project or lead.
- Tests: `NpdLeadEmployeeServiceTest` with Testcontainers: record created as NPD lead without project
  or lead and audited, field errors together with the prefix, Baja refused.

## PR 7b — NPD lead role changes on the employee record

**Part 8 of 11 code PRs. Jira: SCRUM-53.**

- `leaveRole(id, actor)`, `returnToRole(id, actor)`, `rename(id, fullName, actor)` (design §4), each
  audited as a resource edit and with the employee's `@Version` (409 on a concurrent edit).
- `leaveRole`: Baja with today's date and `CAMBIO_DE_ROL`, leaves its project
  (`ResourceFields.leaveProject`), keeps the NPD lead mark. `returnToRole`: only from that reason, else
  `EMPLOYEE_NOT_LINKABLE` (`reason = TERMINATED`, new code and generic message); a record that is not
  on Baja keeps its status.
- Tests in `NpdLeadEmployeeServiceTest`: Baja and project left (collaborators keep their lead), back to
  Activo, other Baja rejected, not on Baja, rename audited, unknown record.

## PR 8 — Changing to and from NPD Lead (CA-10, CA-11)

**Part 9 of 11 code PRs. Jira: SCRUM-53.** The PR for the two NPD lead criteria.

- `UpdateUserRequest.employee` (`NpdLeadEmployeeRequest`: N°, gender, position, work mode, hire date,
  status `ACTIVO` or `LICENCIA`).
- `UserEditService`: the cases of design §6 "What happens by case" except linking; `employee_id` saved
  and audited; the employee errors returned together with the user's.
- OpenAPI of `employee` and of the 400 of its fields.
- Tests: end to end, Customer Success → NPD Lead (record created and linked), NPD Lead → Guest (Baja,
  collaborators kept, response with `employee.collaborators`), back to NPD Lead (Activo, no data asked),
  name of a linked lead saved in both, Keycloak failure leaves no employee record.

## PR 9 — Linking an existing employee

**Part 10 of 11 code PRs. Jira: SCRUM-53.**

- `NpdLeadEmployeeService.findLinkCandidate(email, number)` and `link(id, email)`.
- `UpdateUserRequest.linkEmployeeId`; `EXISTING_NPD_LEAD_EMPLOYEE` and `EMPLOYEE_NOT_LINKABLE` (409)
  with their properties and Spanish messages (one per `reason`).
- OpenAPI of both answers and of the confirmation flow.
- Tests: match by email and by N°, confirmation links without changing the record (only an empty
  email filled), `HAS_USER`, `NOT_NPD_LEAD`, `TERMINATED`, a `linkEmployeeId` that is no longer valid.

## PR 10 — Guard in Recursos

**Part 11 of 11 code PRs. Jira: SCRUM-53.**

- `ResourceRequestValidator`: `CAMBIO_DE_ROL` accepted only when the record already has it;
  `ROLE_CHANGE_TERMINATION` on `status` when such a record leaves Baja.
- Spanish message and OpenAPI of `PUT /api/v1/planner/resources/{id}` (new field error).
- Tests in `ResourceUpdateControllerTest`: edit a record on Baja by "Cambio de rol" keeping it, try to
  reactivate it, try to choose the reason by hand on register and edit.
