# SCRUM-185 — PR plan

PRs in merge order, each under 400 changed lines with its own tests. What and why: [spec.md](spec.md).
How: [design.md](design.md).

| # | Branch | Depends on | Estimate | Status |
|---|--------|------------|----------|--------|
| 0 | `docs/SCRUM-185-228-project-detail-edit-spec` | — | docs | Merged ([#55](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/55)) |
| 1 | `feature/SCRUM-185-project-header` | PR 0 | 111 | Merged ([#59](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/59)) |
| 2 | `feature/SCRUM-185-project-members` | PR 0 | 391 | Merged ([#60](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/60)) |
| 3 | `docs/SCRUM-185-member-equipment-contact` | — | docs | Pending |
| 4 | `fix/SCRUM-185-member-equipment-contact` | PR 3 | ~50 | Pending |

PRs 1 and 2 are independent: both branch from `develop` after PR 0 and can be merged in any order. The
frontend (SCRUM-188) works against the contract of `design.md` until each one is merged.

PRs 3 and 4 come from the change of 2026-11-27 (spec and design, "Change on 2026-11-27"): the laptop
provider and the client contact in the team table.

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

## 3. Laptop and client contact — docs

Spec, design and this plan, "Change on 2026-11-27"; also marks PRs 0 to 2 as merged.

## 4. Laptop and client contact in the team — fix

Design: "Change on 2026-11-27".

- `RosterEntry`: map `equipment_provider` and `client_contact_email` of `resource_v`.
- `ProjectMemberResponse`: `equipmentProvider` and `clientContactEmail` at the end, nullable, with their
  `@Schema` descriptions.
- `ProjectTeamService.member(...)`: fills them from the row.
- Tests in `ProjectControllerTest`: a member with both values and a member without them (`null`).
