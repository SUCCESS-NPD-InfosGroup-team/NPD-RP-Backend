# SCRUM-70 — Design

How the backend implements the changes of [spec.md](spec.md) in "Buscar y filtrar recursos"
(`GET /api/v1/planner/resources`).

## Overview

| Change | Where | Migration |
|--------|-------|-----------|
| R-01 to R-04: project, client and project active on every row | `ResourceListItemResponse`, `ResourceSearchService.item()` | No |
| R-05: group totals in one query | New repository fragment of `RosterEntryRepository`, `ResourceSearchService.groups()` | No |

Nothing changes in the route, the parameters, the order, the errors or the security of the endpoint.
No new setting and no change outside the repository (Keycloak, Railway).

## Decision 1 — New row fields

`RosterEntry` (the read-only entity over `resource_v`) already maps `project_id`, `project`,
`client_id`, `client` and `project_active`, so the row only has to expose them. New fields of
`ResourceListItemResponse`:

| JSON path | Type | Source | Without project |
|-----------|------|--------|-----------------|
| `project` | `IdName` | `projectId` + `projectName` | `null` |
| `client` | `IdName` | `clientId` + `clientName` | `null` |
| `projectActive` | boolean, nullable | `projectActive`; `false` = the inactive "No especificado" project | `null` |

**Options considered**

- **A. Flat fields** (`projectName`, `clientId`, `clientName`). Simple, but every other reference in the
  API (`leader` on the same row, `client` on the groups, the catalogs) is an `IdName`, so the row would
  mix two shapes.
- **B. `IdName` objects (chosen).** Same shape as `leader` on the row and `client` on the groups, and it
  is what the frontend already reads (`item.project?.name`, `item.client?.name`).
- **C. Reuse `ResourceGroupResponse` inside the row.** It carries the type, contract end and total of the
  group, which do not belong to a person; it would also repeat the total on every row.

**Why `projectActive` is a separate field and not part of `project`:** `IdName` is the shared shape of
every reference in the API; adding a property to it would change all of them (same reason as in
SCRUM-85, Decision 2).

**`projectId` stays.** It is kept with `@Schema(deprecated = true)` and the description "Use
`project.id`", so the current screen keeps working (R-04). It is removed in a later task, once the
frontend uses `project`.

The new fields are placed at the end of the record so the order of the existing JSON fields does not
change. The OpenAPI description of each one says what `null` means.

## Decision 2 — Group totals in one query

Today `groups()` runs `roster.count(filters AND inGroup(projectId))` once per group on the page: up to
25 extra queries (one per row in the worst case), on top of the page query and its count.

**Options considered**

- **A. Keep one count per group.** Correct, but the number of queries grows with the groups on the page.
- **B. JPQL `@Query` with `GROUP BY`.** The filters are built as `Specification`s at run time (any
  combination of eight optional filters). A fixed JPQL query cannot receive them without repeating every
  filter as `(:param IS NULL OR ...)`, which duplicates the search logic and drifts from it.
- **C. Repository fragment with the Criteria API (chosen).** A fragment of `RosterEntryRepository`,
  like the existing `EmployeeRefresh`, that turns the same `Specification` into the `WHERE` of a
  `SELECT projectId, count(*) ... GROUP BY projectId`. The filters are written once and shared by the
  page, its count and the totals. It is still JPA (no native SQL, no `JdbcClient`), as AGENTS.md
  requires.

**Which groups are counted.** The query counts **every** group that matches the filters, not only the
ones on the page, and the service picks the ones it needs.

- Restricting it to the page's project ids adds an `IN (...)` plus a special case for the "Sin
  proyecto" group (`project_id IS NULL`, which `IN` does not match).
- The result has at most one row per project plus one for "Sin proyecto" (a few dozen today), so
  returning all of them costs nothing measurable and keeps the query simple.

**Shape**

```java
// persistence/repository
public interface RosterGroupTotals {
    /** How many rows of each group (project id, null = without project) match the filters. */
    Map<Long, Long> countByGroup(Specification<RosterEntry> filters);
}

class RosterGroupTotalsImpl implements RosterGroupTotals { /* EntityManager + CriteriaBuilder */ }

public interface RosterEntryRepository
        extends Repository<RosterEntry, Long>, JpaSpecificationExecutor<RosterEntry>, RosterGroupTotals { }
```

- The implementation builds a `CriteriaQuery<Tuple>` over `RosterEntry`, applies
  `filters.toPredicate(root, query, cb)`, selects `projectId` and `count(root)` and groups by
  `projectId`. The `null` key (people without project) is kept in a `HashMap`, which allows it.
- `RosterEntrySpecifications.inGroup()` is only used by today's per-group count, so it is removed.
- Queries per request: **3** (page, its count, the totals), whatever the number of groups.

## Data model

No change. `resource_v` (V7) already has every column; no migration.

## Endpoint

`GET /api/v1/planner/resources`: same parameters, status codes and errors. Example of a row after the
change (new fields last):

```json
{
  "id": 1078,
  "employeeNumber": "1078",
  "name": "Iván Zúñiga",
  "...": "existing fields unchanged",
  "projectId": 12,
  "project": { "id": 12, "name": "Programa de automatización" },
  "client": { "id": 4, "name": "TIGO" },
  "projectActive": true
}
```

Without project: `"projectId": null, "project": null, "client": null, "projectActive": null`.

## Tests

- **`ResourceSearchControllerTest`** (Testcontainers, end to end):
  - `describesEachRow` also checks `project`, `client` and `projectActive` on a row with an active
    project, on a row of the inactive "No especificado" project, and on a row without project.
  - `ordersGroupsAndPeople`, `keepsGroupTotalsAcrossPages` and `combinesFilters` stay as they are: they
    already cover the totals, so they prove the new query returns the same numbers, including the "Sin
    proyecto" group and a group cut by the page end.
- **Fragment test** with the real database: totals for no filters, for a combination of filters, and
  for the `null` group.
- Coverage of the new code ≥ 80% (Sonar gate).

## Error codes, settings and environment variables

None new.
