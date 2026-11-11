# SCRUM-85 — Design

How the backend serves the resource detail side panel described in [spec.md](spec.md).

## Overview

The panel makes two requests, both `GET` and open to every role:

| # | Request | Module | What it returns |
|---|---------|--------|-----------------|
| 1 | `GET /api/v1/planner/resources/{id}` ("Ver recurso", existing) | `planner` | Collaborator, contract and assignment. Gains four new fields. |
| 2 | `GET /api/v1/history/resources/{id}/last-change` (new) | `history` | The last operation saved through the API, with every field it changed. |

No migration, no new setting and no change outside the repository (Keycloak, Railway).

## Decision 1 — Two requests, not three

**Options considered**

- **A. Three requests** (collaborator, assignment, audit). The collaborator and the assignment are the
  same row of `employee` (one project per person). Splitting them would read it twice and add a round
  trip, with no gain in separation: the server code that builds both already lives in one mapper.
- **B. One request** that also embeds the last change in "Ver recurso". The `planner` module would have
  to return audit data, and "Ver recurso" (also used by the edit form) would pay for it on every read.
- **C. Two requests (chosen).** "Ver recurso" for the record, plus a `history` endpoint for the last
  change. Each module answers for its own data; the frontend can load both in parallel.

## Decision 2 — New fields in "Ver recurso" (additive only)

The main list `GET /api/v1/planner/resources` is not touched. "Ver recurso" keeps every existing field.
New fields:

| JSON path | Type | Source |
|-----------|------|--------|
| `vacationMonth` | integer 1–12, nullable | `employee.vacation_month` (generated column, already mapped as read-only). "Mes de vacaciones". |
| `assignment.leaderEmployeeNumber` | string, nullable | `employee.code` of the NPD lead ("cód. 631"). `null` without a lead. |
| `assignment.startDate` | date | `resource_v.assignment_start_date`: the later of the project's creation and the hire date. Same rule as the roster bar. |
| `assignment.daysToEndDate` | integer, nullable | Days from today (Bogotá, application `Clock`) to `assignment.endDate`; negative once it has passed ("Venció hace 7 días"); `null` when indefinite. Same rule as `daysToAssignmentEnd` of the roster. |

Why the leader number is a flat field and not a field inside `assignment.leader`: `leader` is an
`IdName`, the shape shared by every catalog value of the API. Adding a property to it would change all
of them.

**Implementation**

- `ResourceSituation` (read-only entity over `resource_v`) also maps `assignment_start_date`.
  `ResourceQueryService.situationOf(id)` returns that row instead of only the situation code, so the
  view is read once.
- `ResourceMapper.toResponse(Employee, ResourceSituation, String)` receives the row; `auditValues`
  passes `null` (the situation and the dates are not audited). The mapper receives the `Clock` to count
  the days.
- `AssignmentResponse` and `ResourceResponse` gain the fields above. Registration (`POST`) and edit
  (`PUT`) return the same shape, so their answers also include them.

## Decision 3 — Last change endpoint in `history`

**Route.** `GET /api/v1/history/resources/{id}/last-change`. It belongs to `history` because it reads
the audit log, and the route names its module (AGENTS.md). `resources` is the name the API gives to
`employee` records, as in `planner/resources`.

**Answer.**

```json
{
  "action": "UPDATE",
  "actor": "maria.perez",
  "occurredAt": "2026-10-02T15:14:00Z",
  "fields": [
    { "field": "assignment.endDate", "label": "Fecha final", "oldValue": "2026-09-30", "newValue": "2026-10-31" }
  ]
}
```

- `action`: `CREATE` (registration) or `UPDATE` (edit). On `CREATE`, every `oldValue` is `null` and
  `newValue` is the registered value.
- `actor`: the Keycloak username stored in `audit_log.actor`, as it is.
- `fields`: **every** field of that one operation, in the order they were written (form order). At most
  one per audited field of the record (about 25). The history before it is never read.
- `label`: Spanish label from `messages.properties`, key `audit.field.<field>`. A field without a key
  falls back to its technical name, so a new audited field never breaks the answer.
- Values are returned as the audit log stores them (dates as `yyyy-MM-dd`, catalogs by name). Formatting
  dates for the user is the frontend's job, as with every other date of the API.

**No change yet → `204 No Content`.** A resource that came from the Excel import and was never changed
has no audit rows.

**Unknown resource → also `204`.** Answering `404` would make `history` ask `planner` whether the
employee exists, while `planner` already uses `history` (to audit): a dependency cycle between modules.
The panel only calls this after "Ver recurso" answered `200`, so the `404` already reaches the user
there. Documented in OpenAPI.

**Query.** The two derived queries that already exist in `AuditEntryRepository`: the latest row of the
record, then every row with the same `occurred_at` (an operation writes all its rows with one
timestamp). Both use `audit_log_entity_idx (entity_type, entity_id, occurred_at)` from V5, so no new
index is needed.

**Components.**

| Package | Class | Role |
|---------|-------|------|
| `history/web` | `AuditController` | The route above. |
| `history/service` | `AuditService.findLastOperation(entityType, id)` | Reads the rows of the last operation, in a read-only transaction. |
| `history/service/mapper` | `AuditMapper` | Builds the response and resolves the labels. |
| `history/dto/response` | `LastChangeResponse`, `FieldChangeResponse` | The answer. |

## Security

Both requests are `GET`, so `.anyRequest().authenticated()` in `SecurityConfig` already applies: every
role with a valid token. No rule changes.

## Errors

| Request | Status | Code | When |
|---------|--------|------|------|
| Both | 401 | `UNAUTHORIZED` | No token, invalid token, or the session ended (SCRUM-159). |
| Both | 503 | `SESSION_CHECK_UNAVAILABLE` | Keycloak could not check the session. |
| Ver recurso | 404 | `NOT_FOUND` | The resource does not exist (unchanged). |
| Last change | 400 | `INVALID_REQUEST` | `{id}` is not a number. |

## Tests

- `ResourceControllerTest`: "Ver recurso" returns the four new fields (lead number, start date = the
  later of project creation and hire date, days to end positive and negative, `null` when indefinite,
  vacation month).
- `AuditControllerTest` (`@SpringBootTest`, random port, Testcontainers): `CREATE` operation, `UPDATE`
  operation with several fields and labels, only the last operation is returned, `204` without changes,
  any role can read it, `401` without a token.
- `AuditServiceTest` / mapper unit tests: label fallback and ordering.
