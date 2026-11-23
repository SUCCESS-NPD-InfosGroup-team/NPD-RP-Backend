# SCRUM-127 — PR plan

PRs in merge order, each under 400 changed lines with its own tests. What and why: [spec.md](spec.md).
How: [design.md](design.md).

| # | Branch | Depends on | Estimate | Status |
|---|--------|------------|----------|--------|
| 0 | `docs/SCRUM-127-bulk-assignment-spec` | — | 364 lines | Pending |
| 1 | `feature/SCRUM-127-assignment-candidates` | PR 0 | ~330 lines | Pending |
| 2 | `feature/SCRUM-127-assignment-validation` | PR 0 | ~380 lines | Pending |
| 3 | `feature/SCRUM-127-assignment-service` | PR 2 | ~350 lines | Pending |
| 4 | `feature/SCRUM-127-assignment-endpoint` | PR 3 | ~330 lines | Pending |
| 5 | `feature/SCRUM-127-member-removal` | PR 4 | ~350 lines | Pending |

PRs 1 and 2 are independent of each other: both branch from `develop` after PR 0 and can be reviewed and
merged in any order. PRs 3, 4 and 5 go in this order. The frontend (SCRUM-128) works against the
contract of `design.md` until each endpoint is merged.

## 0. Spec, design and plan — docs

This folder.

## 1. Candidates and `version` in the roster — part 1 of 5

Design: contract 1 and the additive change.

- `GET /api/v1/planner/projects/{projectId}/candidates` (`q`, paging): everyone not on Baja and not in
  the project, Sin proyecto first, then by name, read from `resource_v` (`RosterEntry`) with its search
  text. Plus `withoutProject`.
- `version` in each item of `GET /api/v1/planner/resources`.
- `SecurityConfig`: the candidates route only for the Operations administrator.
- OpenAPI of the new route.
- Tests: order, search without accents, Baja and current members excluded, `withoutProject`, 403 for the
  other roles, 404 for an unknown project, `version` in the roster.

## 2. Assignment validation — part 2 of 5

Design: Decision 2 and Decision 3, row errors of contract 2. No endpoint yet.

- Request records: `AssignMembersRequest`, `AssignmentDefaults`, `MemberAssignment`.
- `MemberAssignmentValidator` (`planner/service/validator`): resolves each member's values (override,
  default, project contract end), checks every row with the rules of "Editar recurso" and collects all
  the errors with `employeeId` and `index`.
- New `FieldErrorCode` values `TERMINATED`, `ALREADY_IN_PROJECT`, `DUPLICATED`, `NOT_IN_PROJECT`,
  `TOO_MANY`, and their Spanish messages in `messages.properties`.
- Unit tests of every rule and of the end date resolution.

## 3. Assignment service — part 3 of 5

Design: Decision 2, Decision 3 and "Audit".

- `ProjectMembershipService.assign(...)`: project check (404, inactive), validation (PR 2), version check
  of every member (409 with `members`), writes the assignment of each person in one transaction, clears
  the client contact of whoever changes project, and records the audit rows (`AuditService.recordChanges`).
- Response records `AssignMembersResponse`, `MemberSummary`; counts `added` and `moved`.
- Tests (Testcontainers): add, move, mixed request, defaults and overrides, default end date, one invalid
  row writes nothing, concurrent change, audit rows.

## 4. Assignment endpoint — part 4 of 5

Design: contract 2.

- `POST /api/v1/planner/projects/{projectId}/members` in a new `ProjectMembershipController`, with its
  full OpenAPI description.
- `SecurityConfig`: the members routes only for the Operations administrator.
- Tests (`@SpringBootTest`, random port): 200 with the summary, 400 with row errors, 403 for the other
  roles, 401, 404, 409.

## 5. Member removal — part 5 of 5

Design: contract 3.

- `ProjectMembershipService.remove(...)` and `POST /api/v1/planner/projects/{projectId}/members/removal`,
  with its OpenAPI description. Clears project, NPD lead, role, end date and client contact; keeps the
  laptop; audited; all or nothing.
- Tests: removal of several people, `NOT_IN_PROJECT`, `DUPLICATED`, concurrent change, audit rows, 403,
  404.
