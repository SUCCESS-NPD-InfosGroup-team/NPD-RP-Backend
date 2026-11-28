# SCRUM-127 — PR plan

PRs in merge order, each under 400 changed lines with its own tests. What and why: [spec.md](spec.md).
How: [design.md](design.md).

| # | Branch | Depends on | Estimate | Status |
|---|--------|------------|----------|--------|
| 0 | `docs/SCRUM-127-bulk-assignment-spec` | — | 364 lines | Merged ([#53](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/53)) |
| 1 | `feature/SCRUM-127-roster-version` | PR 0 | 113 lines | Merged ([#84](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/84)) |
| 2 | `feature/SCRUM-127-assignment-candidates` | PR 1 | 398 lines | Merged ([#96](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/96)) |
| 3 | `feature/SCRUM-127-assignment-validation` | PR 0 | 356 lines | In review |
| 3b | `feature/SCRUM-127-assignment-values` | PR 3 | ~280 lines | Pending |
| 4 | `feature/SCRUM-127-assignment-service` | PR 3 | ~350 lines | Pending |
| 5 | `feature/SCRUM-127-assignment-endpoint` | PR 4 | ~330 lines | Pending |
| 6 | `feature/SCRUM-127-member-removal` | PR 5 | ~350 lines | Pending |

PRs 1–2 (candidates) and PRs 3–5 (assignment) are two independent lines after PR 0; PR 6 goes last. The
frontend (SCRUM-128) works against the contract of `design.md` until each endpoint is merged.

The candidates and the roster version were one PR in the first plan; measured at about 500 lines, they
were split in two.

## 0. Spec, design and plan — docs

This folder.

## 1. `version` in the roster — part 1 of 6

Design: the additive change.

- `version` in each item of `GET /api/v1/planner/resources`, read in one query for the rows of the page
  (`EmployeeRepository.findVersions`, the JPQL projection `EmployeeVersion` that SCRUM-185 added for the
  project team): `resource_v` has no version and needs no migration.
- Test in `ResourceSearchControllerTest`: a row carries the current version of its record.

## 2. Candidates — part 2 of 6

Design: contract 1.

- `GET /api/v1/planner/projects/{projectId}/candidates` (`q`, paging) in a new `ProjectMembershipController`:
  everyone not on Baja and not in the project, read from `resource_v` (`RosterEntry`) with its search
  text, Sin proyecto first, then by name. Plus `withoutProject` and each person's `version`.
- `SecurityConfig`: the route only for the Operations administrator. OpenAPI of the route.
- Tests: order, exclusions, search without accents, paging, `withoutProject`, 403 for the other roles,
  401, 404 for an unknown project.

## 3. Assignment validation — part 3 of 6

Design: Decision 2 and Decision 3, row errors of contract 2. No endpoint yet.

- Request records: `AssignMembersRequest`, `AssignmentDefaults`, `MemberAssignment`.
- `MemberAssignmentValidator` (`planner/service/validator`): who can be assigned. Every row is checked
  (unknown, on Baja, already in the project, repeated, no version) and all the errors are collected with
  `employeeId` and `index`; 1 to 100 members.
- New `FieldErrorCode` values `TERMINATED`, `ALREADY_IN_PROJECT`, `DUPLICATED`, `NOT_IN_PROJECT`,
  `TOO_MANY`, and their Spanish messages in `messages.properties`.
- Unit tests of every rule.

Measured at about 540 lines with the values of part 3b, the validation was split in two.

## 3b. Assignment values — part 3b of 6

Design: Decision 2, row errors of contract 2.

- `MemberAssignmentValidator` resolves each member's lead, role, laptop and end date (own value, default,
  project contract end) and checks them with the rules of "Editar recurso", including `NOT_NPD_LEADER`
  (SCRUM-241) for a new lead. A problem of a shared value is reported once on `defaults.<field>`.
- Unit tests of every rule and of the resolution.

## 4. Assignment service — part 4 of 6

Design: Decision 2, Decision 3 and "Audit".

- `ProjectMembershipService.assign(...)`: project check (404, inactive), validation (PR 3), version check
  of every member (409 with `members`), writes the assignment of each person in one transaction, clears
  the client contact of whoever changes project, and records the audit rows (`AuditService.recordChanges`).
- Response records `AssignMembersResponse`, `MemberSummary`; counts `added` and `moved`.
- Tests (Testcontainers): add, move, mixed request, defaults and overrides, default end date, one invalid
  row writes nothing, concurrent change, audit rows.

## 5. Assignment endpoint — part 5 of 6

Design: contract 2.

- `POST /api/v1/planner/projects/{projectId}/members` in `ProjectMembershipController`, with its full
  OpenAPI description.
- `SecurityConfig`: the members routes only for the Operations administrator.
- Tests (`@SpringBootTest`, random port): 200 with the summary, 400 with row errors, 403 for the other
  roles, 401, 404, 409.

## 6. Member removal — part 6 of 6

Design: contract 3.

- `ProjectMembershipService.remove(...)` and `POST /api/v1/planner/projects/{projectId}/members/removal`,
  with its OpenAPI description. Clears project, NPD lead, role, end date and client contact; keeps the
  laptop; audited; all or nothing.
- Tests: removal of several people, `NOT_IN_PROJECT`, `DUPLICATED`, concurrent change, audit rows, 403,
  404.
