# SCRUM-53 — Plan

PRs in merge order, all against `develop`. Each one compiles, passes "Validar ramas" and "Run Unit
Tests" (80% coverage on new code), and stays under 400 changed lines (`git diff --shortstat
origin/develop...HEAD`). What and why: [spec.md](spec.md). How: [design.md](design.md).

| # | Branch | Content | Est. lines | Depends on | Status |
|---|--------|---------|------------|------------|--------|
| 1 | `docs/SCRUM-53-spec-design` | `spec.md` and `design.md` of this folder. | ~340 | — | Merged ([#69](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/69)) |
| 2 | `docs/SCRUM-53-plan` | This `plan.md`. | ~130 | PR 1 | In review |
| 3 | `feature/SCRUM-53-user-read-fields` | V12 and the new fields of the user list and detail (CA-09, CA-13, collaborators of CA-11). | ~330 | PR 1 | Pending |
| 4 | `feature/SCRUM-53-keycloak-edit-operations` | Keycloak calls to rename, change the role and close the sessions. | ~300 | PR 1 | Pending |
| 5 | `feature/SCRUM-53-user-edit-service` | Edit service for name and role: validation, Keycloak, transaction, compensation. | ~390 | PR 3, PR 4 | Pending |
| 6 | `feature/SCRUM-53-edit-user-endpoint` | `PUT /api/v1/access/users/{id}`, OpenAPI, messages, end-to-end tests. | ~300 | PR 5 | Pending |
| 7 | `feature/SCRUM-53-npd-lead-employee-service` | `planner` service with the NPD lead employee operations. | ~380 | PR 3 | Pending |
| 8 | `feature/SCRUM-53-role-change-npd-lead` | CA-10 and CA-11 in the edit: register, Baja, back to Activo, rename. | ~360 | PR 6, PR 7 | Pending |
| 9 | `feature/SCRUM-53-link-existing-employee` | Linking an existing NPD lead employee (HU-10 CA-05 to CA-07, CA-12, CA-13). | ~350 | PR 8 | Pending |
| 10 | `feature/SCRUM-53-role-change-termination-guard` | Guard in Recursos for Baja by "Cambio de rol" (design §7). | ~160 | PR 3 | Pending |

PR 4 can be reviewed in parallel with PR 3; PR 7 and PR 10 in parallel with PRs 4 to 6. After PR 6 the
frontend can edit name and role of every user except NPD leads; after PR 9 the whole story is served.
The estimates include tests; a PR that grows past 400 lines is split again before it is opened.

## PR 3 — Read fields and data model

**Part 1 of 8 code PRs. Jira: SCRUM-53.**

- `V12__app_user_employee_link.sql` (design §5): `app_user.employee_id` (unique, FK to `employee`),
  `app_user.version`, the inactive termination reason `CAMBIO_DE_ROL`, and their comments.
- `AppUser`: `employeeId` (plain `Long`) and `version` (`@Version`).
- `NpdLeadEmployeeService.countCollaborators(ids)` in `planner/service` (only this method here) with its
  JPQL query in `EmployeeRepository` (`leader_id` in the ids, Baja excluded, grouped by lead), and
  `summaries(ids)` for the `employee` field (N°, status).
- `UserResponse`: `createdAt`, `createdBy` (name resolved per page, design §6), `version`, `employee`.
  `UserQueryService.list` and `get` fill them with one query each per page.
- OpenAPI of `GET /api/v1/access/users` and `/{id}` (new fields, `employee = null` for an NPD lead
  without employee record).
- `docs/database/README.md` and the ER diagram: the new columns.
- Tests: `UserControllerTest` (new fields, creator not in `app_user`), `LastAccessTest` (the last
  access does not change `version`), the collaborators query (Baja excluded, several leads).

## PR 4 — Keycloak edit operations

**Part 2 of 8 code PRs. Jira: SCRUM-53.**

- `KeycloakAdminClient.rename(id, fullName)`: `GET` the representation, `PUT` it back with the new
  `firstName` (calls 1–2).
- `KeycloakAdminClient.replaceRealmRole(id, role)`: current realm roles, remove the platform roles it
  has (only the four of `Role`), add the new one (calls 3–5).
- `KeycloakAdminClient.logout(id)` (call 6).
- Same error mapping as today; 404 of the account → `IllegalStateException` logged with the id.
- Tests with `MockRestServiceServer` in `KeycloakAdminClientTest`: each call, other attributes kept on
  rename, `default-roles-*` and `offline_access` not removed, 5xx/timeout → unavailable.

## PR 5 — Edit service for name and role

**Part 3 of 8 code PRs. Jira: SCRUM-53.**

- `UpdateUserRequest` (`version`, `fullName`, `role`; `employee` and `linkEmployeeId` come in PR 8 and
  9) and `UpdateUserCommand` (request, id, actor).
- `UpdateUserRequestValidator` in `service/validator`: `fullName` required and up to 150 (CA-12),
  `role` one of the four, `version` required; every error together.
- `UserEditService.update` (design §1, §3, §5): no-change check (CA-05), CA-13
  (`NPD_LEAD_WITHOUT_EMPLOYEE`), first check of CA-04, Keycloak calls, transaction with the pessimistic
  lock of the user and the recheck of the last administrator (`AppUserRepository` JPQL with
  `@Lock(PESSIMISTIC_WRITE)`), audit of `fullName` and `role`, compensation on failure.
- `ErrorCode.LAST_OPERATIONS_ADMINISTRATOR` and `NPD_LEAD_WITHOUT_EMPLOYEE` (409).
- Tests: `UserEditServiceTest` (unit, mocked Keycloak): each case, the order of the calls, the
  compensation when the role or the database fails, a failed compensation is logged, no calls on CA-05.

## PR 6 — Edit endpoint

**Part 4 of 8 code PRs. Jira: SCRUM-53.**

- `UserController.update`: `PUT /api/v1/access/users/{id}` (version `v1`), 200 with the user.
- OpenAPI: summary, body, every answer of design §6 for name and role (400, 401, 403, 404, 409 with
  each code, 503).
- Spanish messages of the new error codes and of the field errors of `fullName`, `role`, `version`.
- Tests: `UserUpdateControllerTest` with Testcontainers and mocked Keycloak client: CA-01 (role and
  sessions), CA-04 (own role, last administrator), CA-05 (no audit rows, same `version`), CA-06 (403 for
  each other role), CA-07 (audit rows), CA-12, CA-13, 409 on `version`, 503 leaves `app_user` unchanged.

## PR 7 — NPD lead employee service

**Part 5 of 8 code PRs. Jira: SCRUM-53.**

- `NpdLeadEmployeeService` (design §4): `register(NewNpdLeadCommand)`, `leaveRole(id, actor)`,
  `returnToRole(id, actor)`, `rename(id, fullName, actor)`. `findLinkCandidate` and `link` come in
  PR 9.
- `register` reuses `ResourceRequestValidator` (field errors prefixed `employee.`) and the resource
  audit; `leaveRole` puts the record on Baja with today's date and `CAMBIO_DE_ROL` and leaves its
  project; `returnToRole` only from that reason.
- Tests: `NpdLeadEmployeeServiceTest` with Testcontainers: record created as NPD lead without project
  or lead, Baja and project left, back to Activo, other Baja rejected, audit rows of each operation,
  concurrent edit → `CONCURRENT_MODIFICATION`.

## PR 8 — Changing to and from NPD Lead (CA-10, CA-11)

**Part 6 of 8 code PRs. Jira: SCRUM-53.** The PR for the two NPD lead criteria.

- `UpdateUserRequest.employee` (`NpdLeadEmployeeRequest`: N°, gender, position, work mode, hire date,
  status `ACTIVO` or `LICENCIA`).
- `UserEditService`: the cases of design §6 "What happens by case" except linking; `employee_id` saved
  and audited; the employee errors returned together with the user's.
- OpenAPI of `employee` and of the 400 of its fields.
- Tests: end to end, Customer Success → NPD Lead (record created and linked), NPD Lead → Guest (Baja,
  collaborators kept, response with `employee.collaborators`), back to NPD Lead (Activo, no data asked),
  name of a linked lead saved in both, Keycloak failure leaves no employee record.

## PR 9 — Linking an existing employee

**Part 7 of 8 code PRs. Jira: SCRUM-53.**

- `NpdLeadEmployeeService.findLinkCandidate(email, number)` and `link(id, email)`.
- `UpdateUserRequest.linkEmployeeId`; `EXISTING_NPD_LEAD_EMPLOYEE` and `EMPLOYEE_NOT_LINKABLE` (409)
  with their properties and Spanish messages (one per `reason`).
- OpenAPI of both answers and of the confirmation flow.
- Tests: match by email and by N°, confirmation links without changing the record (only an empty
  email filled), `HAS_USER`, `NOT_NPD_LEAD`, `TERMINATED`, a `linkEmployeeId` that is no longer valid.

## PR 10 — Guard in Recursos

**Part 8 of 8 code PRs. Jira: SCRUM-53.**

- `ResourceRequestValidator`: `CAMBIO_DE_ROL` accepted only when the record already has it;
  `ROLE_CHANGE_TERMINATION` on `status` when such a record leaves Baja.
- Spanish message and OpenAPI of `PUT /api/v1/planner/resources/{id}` (new field error).
- Tests in `ResourceUpdateControllerTest`: edit a record on Baja by "Cambio de rol" keeping it, try to
  reactivate it, try to choose the reason by hand on register and edit.
