# SCRUM-70 — Search and filter API: project and client on every row

Jira: [SCRUM-70](https://infosgroup-sucess.atlassian.net/browse/SCRUM-70) · Story: SCRUM-52 (HE-01-HU-03,
"Search and filter resources") · Acceptance criteria of the story covered by the endpoint: CA-01, CA-02,
CA-03, CA-05, CA-06, CA-07

## Context

"Buscar y filtrar recursos" (`GET /api/v1/planner/resources`) already exists and filters by employee
number, client, project, NPD lead, role, level, status and situation, alone or combined, ignoring case and
accents, paginated and grouped by project.

While connecting the screen, the table shows **"Sin proyecto"** and **"No especificado"** on every row,
even for people who do have a project. The data is in the database: `resource_v` already has the project
and client names, and the endpoint reads them. But each row only carries the **project id**. The names
travel once per project in the `groups` list of the response, so a screen that draws one row per person
(the table and the Excel export) does not find them on the row.

A second issue: to give each group its total ("2 colaboradores", "1 de 2 · sigue en la pág. 2"), the
endpoint runs one extra query per group shown on the page, so a page of 25 rows can take up to 27
queries. The response is correct, but this is the most frequently called endpoint of the module.

## User story

As a **platform user (any role)**, I want **every row of the resource list to show its client and
project**, so that **I can read and export the list without depending on how the rows are grouped**.

## What changes

| ID | Change | What the backend guarantees |
|----|--------|-----------------------------|
| R-01 | Project on every row | Each row carries its project as id and name. People without project have no project (`null`), so the screen can show "Sin proyecto". |
| R-02 | Client on every row | Each row carries the project's client as id and name; `null` for people without project. |
| R-03 | "No especificado" is recognisable | Each row says whether its project is active, so the screen can tell the inactive "No especificado" project (imported rows with a project to correct) from a real one. |
| R-04 | Nothing breaks | No existing field changes or disappears. `projectId` on the row stays (and is marked as deprecated in the OpenAPI) so the screen keeps working until it moves to the new field. `groups` stays as it is. |
| R-05 | Fewer queries per page | The totals of the groups on a page are obtained in a single query instead of one per group. The response (rows, groups, totals, order, page) is exactly the same. |

## Scope

**Included**

- New row fields for project, client and project active (R-01 to R-03).
- Computing the group totals in one query (R-05).
- Updated OpenAPI description and tests.

**Out of scope**

- The frontend: mapping the new fields, and its other mismatches with the contract (it reads `status` as
  text and `startDate`/`endDate` instead of `assignmentStart`/`assignmentEnd`). These are fixed in the
  frontend; the backend names stay as they are.
- New indexes or a performance study with `EXPLAIN ANALYZE`: with about 162 employees PostgreSQL reads
  the whole table regardless, and the view cannot be indexed. To be revisited if the data grows.
- New filters, a different order, or changes to `resource_v`: the view already has every column needed,
  so no migration is expected.
- Restricting which resources each role can see.
- "Arribos próximos" (SCRUM-181): its own spec.

## Open questions

None.
