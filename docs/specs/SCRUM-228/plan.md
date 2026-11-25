# SCRUM-228 — PR plan

PRs in merge order, each under 400 changed lines with its own tests. What and why: [spec.md](spec.md).
How: [design.md](design.md).

| # | Branch | Depends on | Estimate | Status |
|---|--------|------------|----------|--------|
| 0 | `docs/SCRUM-185-228-project-detail-edit-spec` | — | docs | Pending |
| 1 | `feature/SCRUM-228-project-version` | PR 0, SCRUM-241 V10 | ~120 lines | Pending |
| 2 | `feature/SCRUM-228-project-edit` | PR 1 | ~350 lines | Pending |
| 2b | `feature/SCRUM-228-members-follow-contract-end` | PR 2 | ~200 lines | Pending |
| 3 | `feature/SCRUM-228-project-deactivation` | PR 2 | ~250 lines | Pending |

The frontend (SCRUM-229) works against the contract of `design.md` until each endpoint is merged.

## 0. Spec, design and plan — docs

This folder (shared docs PR with SCRUM-185).

## 1. Project version — part 1 of 3

Design: Decision 2.

- `V11__project_version.sql` (renumber if another migration is merged first).
- `Project`: `@Version Integer version`, read-only `updatedAt`.
- `ProjectResponse`: `version`, `updatedAt`; filled in `ProjectQueryService.get(...)`.
- Tests: the 201 of "Crear proyecto" returns `version` 0; "Ver proyecto" returns both fields.

## 2. Edit — part 2 of 3

Design: contract 1, Decision 3.

- `UpdateProjectRequest` (`name`, `contractEndDate`, `version`) and `UpdateProjectCommand` (with the actor).
- Answer `{ project, membersEndDateMoved }` from the start (`membersEndDateMoved` = 0 until PR 2b), so
  the contract with the frontend does not change between PRs.
- `ProjectRequestValidator.validateUpdate(...)`: same name rules as creation, `DATE_IN_PAST` only when the
  date changes, all problems at once.
- `ProjectUpdateService.update(...)`: 404, `PROJECT_INACTIVE`, version check (409 with `currentVersion`,
  `updatedAt`, `updatedBy` from `AuditService.findLastChange`), save with `saveAndFlush` and the same
  `project_client_name_uk` translation as creation (`PROJECT_NAME_TAKEN`), audit rows of what changed.
- `ErrorCode.PROJECT_INACTIVE` and its messages.
- `PUT /api/v1/planner/projects/{id}` in `ProjectController`, OpenAPI; `SecurityConfig` matcher.
- Tests: the "Edit" cases of the design.

## 2b. People move with the contract end — CA-04

Design: Decision 4.

- `ProjectUpdateService.update(...)`: when the contract end changes from a date to another date, moves
  the team members (Baja excluded) whose end date is the old one, in the same transaction; one
  `assignment.endDate` audit row per person; rollback with 409 if one of them is stale.
- `EmployeeRepository`: the query of the team members with a given end date.
- Response `UpdateProjectResponse` (`project`, `membersEndDateMoved`); the OpenAPI of the `PUT` updated.
- Tests: the "Edit, people that move" cases of the design.

## 3. Deactivation — part 3 of 3

Design: contract 2, Decision 1.

- `ProjectUpdateService.deactivate(...)`: 404, version check, team re-count inside the transaction
  (`PROJECT_HAS_TEAM` with `teamSize`), idempotent when already inactive, audit row `active`.
- `ErrorCode.PROJECT_HAS_TEAM` and its messages.
- `POST /api/v1/planner/projects/{id}/deactivation`, OpenAPI; `SecurityConfig` matcher.
- Tests: the "Deactivate" cases of the design.
