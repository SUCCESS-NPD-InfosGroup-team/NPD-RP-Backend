# SCRUM-241 — Plan

PRs in merge order, all against `develop`. Each one compiles, passes "Validar ramas" and "Run Unit
Tests", and stays under 400 changed lines (`git diff --shortstat origin/develop...HEAD`). What and why:
[spec.md](spec.md). How: [design.md](design.md).

| # | Branch | Content | Est. lines | Depends on | Status |
|---|--------|---------|------------|------------|--------|
| 1 | `docs/SCRUM-241-npd-lead-flag` | `spec.md`, `design.md` and `plan.md` of this folder. | ~350 | — | Pending |
| 2 | `fix/SCRUM-241-npd-lead-column` | V10, the entity, the import and the "Líder NPD" selector (design decisions 1 to 5). | ~250 | PR 1 | Pending |
| 3 | `fix/SCRUM-241-npd-lead-validation` | `NOT_NPD_LEADER` when registering and editing a resource (decision 6). | ~220 | PR 2 | Pending |
| 4 | `fix/SCRUM-241-block-npd-lead-user` | "Crear usuario" stops offering and accepting `npd-leader` (decision 7). | ~150 | PR 1 | Pending |

PR 4 does not depend on PR 2 or 3 and can be reviewed in parallel.

## PR 2 — The NPD lead mark and the selector

**Part 1 of 3 code PRs. Jira: SCRUM-241.**

- `V10__employee_npd_lead.sql`: the column, the backfill from `leader_id` and its comment.
- `Employee.npdLead`, a plain column that HU-10 can write; `ResourceFields` does not touch it.
- `PlannerImportRepository.markNpdLeads(ids)` and its call at the end of
  `PlannerImportService.assignLeaders`; the final log line counts the marked leads.
- `EmployeeRepository.findNpdLeadsExcludingStatus` replaces `findLeadsExcludingStatus`;
  `ResourceQueryService.findNpdLeads` uses it; new OpenAPI description of `GET /npd-leads`.
- Docs: `docs/planner-import/README.md` (the import marks the leads) and `docs/database/` (new column).
- Tests: `PlannerImportServiceTest`, the import repository test, `ResourceControllerTest.listsNpdLeads`,
  and `is_npd_lead = true` in the fixtures whose employees are used as leads.

After merging, every developer has the leads marked when they start the API, with no new import.

## PR 3 — Only NPD leads can be chosen

**Part 2 of 3 code PRs. Jira: SCRUM-241.**

- `FieldErrorCode.NOT_NPD_LEADER` and its Spanish message.
- `ResourceRequestValidator.checkAssignment`: the new check after `INACTIVE_LEADER`, only when the lead
  changes; `validateUpdate(id, request, currentLeaderId)`.
- `ResourceUpdateService.update` passes the saved lead.
- OpenAPI of `POST` and `PUT /api/v1/planner/resources` (`leaderId` from `GET /npd-leads`, 400 with
  `NOT_NPD_LEADER`).
- Tests: `ResourceRequestValidatorTest`, `ResourceControllerTest`, `ResourceUpdateControllerTest`.

## PR 4 — No NPD leads from "Crear usuario"

**Part 3 of 3 code PRs. Jira: SCRUM-241.**

- `CreateUserRequestValidator.ASSIGNABLE` and `FieldErrorCode.ROLE_NOT_ASSIGNABLE` for `npd-leader`.
- `RoleQueryService.list` returns only the assignable roles.
- Messages: `validation.user.role.ROLE_NOT_ASSIGNABLE` and the new text of
  `validation.user.role.UNKNOWN_VALUE`.
- OpenAPI: `CreateUserRequest.role`, `RoleResponse.code` and the description of `POST /api/v1/access/users`.
- Tests: `CreateUserRequestValidatorTest`, `UserControllerTest`, `UserRegistrationServiceTest`,
  `RoleControllerTest`.

After merging, the frontend shows three role cards in "Crear usuario" without a change of its own.
