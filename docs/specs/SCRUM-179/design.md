# SCRUM-179 - Design

How the API serves the upcoming events list. What and why: [spec.md](spec.md). PRs: [plan.md](plan.md).

## 1. Overview

```
GET /api/v1/planner/upcoming-events?windowDays=30&clientId=&leaderId=&type=&page=0&size=25
  -> UpcomingEventController -> UpcomingEventQueryService
  -> UpcomingEventRepository (JPA, Specifications) -> upcoming_event_v (view, V9)
```

Everything lives in the `planner` module, the owner of the data.

## 2. Decision: where the events are built

**Chosen: a database view with one row per event, filtered with JPA Specifications.**

| Option | Why not / why |
|--------|---------------|
| A. Read employees and projects and build the events in Java | Paginating and counting per type would need every event in memory. |
| **B. View `upcoming_event_v` (`UNION ALL` of the four sources)** | **Chosen.** Same pattern as `resource_v` and `project_summary_v`: an `@Immutable` entity, filters as `Specification`, and pagination and counts done by the database. No native SQL in code (AGENTS.md). |
| C. Native SQL query in the repository | Not allowed by the team's data-access rule. |

The view has **no time window and no "today"** for assignment ends: the service adds the window and
splits the assignment ends in pending (`date < today`) and upcoming with the application clock, so a
call near midnight uses the Bogota date, not the database's.

## 3. The view (V9)

| Kind | Source | Rule |
|------|--------|------|
| `ASSIGNMENT_END` | `employee.project_end_date` | Employee with a project and an end date. Shown as `PENDING_CONFIRMATION` when it has passed. |
| `PROBATION_END` | `employee.probation_end_date` | `on_probation = true`. |
| `VACATION_START` | `employee.vacation_month` | First day of the month, this year and next (a 90-day window can cross into January), from the first vacation 11 months after hire. Uses the database `current_date` to pick the years only. |
| `CONTRACT_END` | `project.contract_end_date` | Active projects. No person; `resource_count` = team size from `project_summary_v`; lead only when the whole team shares one. |

Employees on Baja are excluded everywhere. The id is stable: `<kind>-<employee or project id>`, plus
`-<year>` for vacations.

## 4. Endpoint

- Parameters: `windowDays` (30 by default; 30, 60 or 90), `clientId`, `leaderId`, `type`, `page`,
  `size`. `sort` is ignored: always by date and id.
- Response: `summary` (total and count per type, without the type filter), `items`, `page`
  (`PageInfo`). Shape documented in OpenAPI and matching the frontend adapter.
- `summary` runs one count per type (five `count` queries); the list is small, so this is cheaper to
  read than an aggregate query in JPQL over the view.
- Errors: 400 `INVALID_REQUEST` (window not 30/60/90, unknown type, malformed number), 401
  `UNAUTHORIZED`. Every role can read it: no change in `SecurityConfig`.
- `notice` is always `{"status":"NONE"}` until the notifications module exists.

## 5. Tests

| Test | What |
|------|------|
| `UpcomingEventViewTest` | The view with Testcontainers: rows per kind, Baja and inactive projects out, team and lead of a contract end, vacation dates. |
| `UpcomingEventSpecificationsTest` | Window, pending/upcoming split, client and lead filters against the view. |
| `UpcomingEventQueryServiceTest` | Without database: counts per type, row mapping, Bogota date at 23:30, sort ignored, rejected window. |
| `UpcomingEventControllerTest` | End to end: JSON shape, pagination, 400 and 401, roles. |

`UpcomingEventFixture` holds the shared rows, with every date relative to today.
