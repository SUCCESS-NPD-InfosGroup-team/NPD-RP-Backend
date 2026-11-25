# SCRUM-185 — PR plan

PRs in merge order, each under 400 changed lines with its own tests. What and why: [spec.md](spec.md).
How: [design.md](design.md).

| # | Branch | Depends on | Estimate | Status |
|---|--------|------------|----------|--------|
| 0 | `docs/SCRUM-185-228-project-detail-edit-spec` | — | docs | Pending |
| 1 | `feature/SCRUM-185-project-header` | PR 0 | 111 | Pending |
| 2 | `feature/SCRUM-185-project-members` | PR 0 | 391 | Pending |

PRs 1 and 2 are independent: both branch from `develop` after PR 0 and can be merged in any order. The
frontend (SCRUM-188) works against the contract of `design.md` until each one is merged.

## 0. Spec, design and plan — docs

This folder (shared docs PR with SCRUM-228).

## 1. Project header — part 1 of 2

Design: contract 1.

- `ProjectResponse`: `daysToContractEnd`, `pendingConfirmation`, `indefiniteCount`, `nextAssignmentEnd`,
  `leads`, with their `@Schema` descriptions.
- `ProjectQueryService.get(...)`: fills them from `ProjectSummary` and `findProjectLeads` (same code path
  as `list`, extracted to one private method so list and detail cannot drift).
- OpenAPI description of "Ver proyecto" updated.
- Tests: the cases of "Tests — Header" in the design; the 201 of "Crear proyecto" still passes.

## 2. Project team — part 2 of 2

Design: contract 2, Decisions 1 and 3.

- `ProjectMemberResponse` and `ProjectMembersResponse` (`items`, `total`).
- `ProjectQueryService.members(long id)`: 404 if the project does not exist; reads `RosterEntry` with
  `project_id = id` and status not `BAJA`; the versions (from the roster if SCRUM-127 PR 1 is merged,
  otherwise one `employee` query by ids); order of the design; cap 200.
- `GET /api/v1/planner/projects/{id}/members` in `ProjectController`, with its OpenAPI description.
- Tests: the cases of "Tests — Team" in the design.
