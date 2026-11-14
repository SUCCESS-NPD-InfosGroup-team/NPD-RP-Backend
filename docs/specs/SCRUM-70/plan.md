# SCRUM-70 — Plan

PRs in merge order, all against `develop`. Each one compiles, passes "Validar ramas" and "Run Unit
Tests", and stays under 400 changed lines (`git diff --shortstat origin/develop...HEAD`).

| # | Branch | Content | Est. lines | Depends on | Status |
|---|--------|---------|------------|------------|--------|
| 1 | `docs/SCRUM-70-search-improvements` | `spec.md`, `design.md` and `plan.md` of this folder. | ~230 | — | Pending |
| 2 | `fix/SCRUM-70-search-row-project-client` | Design decisions 1 and 2, with their tests. | ~250 | PR 1 | Pending |

## PR 2 — Project and client on every row, group totals in one query

**Part 1 of 1 code PRs. Jira: SCRUM-70.**

- `ResourceListItemResponse`: new fields `project` (`IdName`), `client` (`IdName`) and `projectActive`
  (Boolean) at the end; `projectId` marked `@Schema(deprecated = true)`.
- `ResourceSearchService.item()`: fills the new fields from `RosterEntry`.
- `RosterGroupTotals` + `RosterGroupTotalsImpl` (repository fragment, Criteria API) and
  `RosterEntryRepository` extending it.
- `ResourceSearchService.groups()`: one `countByGroup(filters)` call instead of a count per group.
- `RosterEntrySpecifications.inGroup()` removed.
- Tests: `ResourceSearchControllerTest` checks the new fields (active project, "No especificado",
  without project); new test of the fragment against the database (Testcontainers).
- OpenAPI: descriptions of the new fields and of what `null` means.

Out of this plan: the frontend change to read `project`/`client`, and removing the deprecated
`projectId` once the frontend no longer uses it (later task).
