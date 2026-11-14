# SCRUM-181 — Design

How the API serves the upcoming events strip of Recursos described in [spec.md](spec.md).

## Overview

```
GET /api/v1/planner/upcoming-events/summary
  -> UpcomingEventController.summary -> UpcomingEventQueryService.summary
  -> UpcomingEventRepository (JPA, Specifications) -> upcoming_event_v (view, V9)
```

It reuses the pieces of SCRUM-179: the `upcoming_event_v` view, the `UpcomingEvent` entity and repository,
`UpcomingEventSpecifications`, and the mapping of a row to `UpcomingEventResponse`. No migration, no new
setting, no change in `SecurityConfig` and nothing outside the repository (Keycloak, Railway).

## Decision 1 — A new endpoint, not the list with `size=4`

**Options considered**

- **A. The frontend calls `GET /upcoming-events?windowDays=30&size=4`.** No backend code, but the rows go
  by date with the pending confirmations always included, so the four rows would be the oldest pending ones
  instead of the nearest events (spec R-02). It also runs seven queries (five counts per type, the page and
  its count) for one number and four rows.
- **B. A flag on the list to leave out the pending ones** (for example `upcomingOnly=true`). The list would
  then mean two things, and its `summary` would have to keep counting the pending ones while the rows leave
  them out, which is confusing to document and to test.
- **C. A small endpoint for the strip (chosen).** It answers exactly what the strip needs, in two queries,
  and the list of the Arribos screen does not change.

## Decision 2 — Route and response

**Route:** `GET /api/v1/planner/upcoming-events/summary`. It is a view of the same resource as the list,
so it lives in the same controller, and the route names its module (AGENTS.md). "summary" is the name of
the Jira task. There is no `/upcoming-events/{id}` route, so it cannot collide.

**Response:** a new record `UpcomingEventsStripResponse`:

| JSON field | Type | Content |
|------------|------|---------|
| `windowDays` | integer | Always `30`. The frontend shows it ("Lo que vence en los próximos 30 días") and passes it to Arribos, so the number is not repeated in two places. |
| `total` | integer | N of "Ver los N eventos": events of the 30-day window, pending confirmations included (spec R-01). |
| `nearest` | list of `UpcomingEventResponse` | Up to four events from today to today + 30 days, by date and id (spec R-02, R-03). Same shape as the rows of the list (spec R-04). |

```json
{
  "windowDays": 30,
  "total": 8,
  "nearest": [
    {
      "id": "PROBATION_END-41",
      "type": "PROBATION_END",
      "date": "2026-11-25",
      "daysRemaining": 11,
      "resource": { "id": 41, "employeeNumber": "1078", "name": "Iván Castrellón", "clientRole": "QA Funcional" },
      "resourceCount": 1,
      "client": { "id": 4, "name": "TIGO" },
      "project": { "id": 12, "name": "Portal" },
      "leader": { "id": 7, "name": "Abdiel" },
      "notice": { "status": "NONE" }
    }
  ]
}
```

The name avoids `UpcomingEventsSummary`, which already exists: it is the `summary` object (total and count
per type) inside the list response.

**Why the strip has no filters:** it summarises the whole base for any role (spec, out of scope). With the
same window and no filters, `total` is the same number the Arribos screen shows when it opens, so the link
"Ver los N eventos" never lands on a different N.

## Decision 3 — How the two numbers are read

`UpcomingEventQueryService.summary()` takes **one** `today` from the application `Clock` (Bogotá), as the
list does, and runs two queries:

1. **`total`:** `events.count(inWindow(today, today + 30))`. `inWindow` already includes the pending
   confirmations, so this is the same filter the list uses with `windowDays=30` and no other filter. The
   five types of the list split that same set, so one count gives the same number as the sum of the five
   counts of the list.
2. **`nearest`:** the events with `date` between `today` and `today + 30`, ordered by `date` and `id`,
   limited to 4. A new filter `UpcomingEventSpecifications.upcoming(today, lastDay)` (`date BETWEEN today AND
   lastDay`) leaves out the passed assignment ends without depending on the kind. It is read with the
   fluent query of `JpaSpecificationExecutor`:

   ```java
   events.findBy(upcoming(today, lastDay), query -> query.sortBy(LIST_ORDER).limit(NEAREST).all());
   ```

   **Why not `findAll(spec, PageRequest.of(0, 4))`:** a `Page` also runs a count query that the strip does
   not use. The fluent `limit` reads only the four rows.

The rows are mapped with the same private `toResponse(event, today)` as the list, so `daysRemaining`, the
type (an event of today is never pending) and the notice are computed the same way.

Constants in the service: `SUMMARY_WINDOW_DAYS = 30` and `NEAREST = 4`. They are not settings: they come
from the acceptance criterion, and changing them is a product decision, not an environment one.

## Endpoint

| | |
|---|---|
| Method and route | `GET /api/v1/planner/upcoming-events/summary` |
| Parameters | None |
| 200 | `UpcomingEventsStripResponse` |
| 401 | `UNAUTHORIZED` (no token or invalid token), ProblemDetails |
| Roles | Every authenticated role (same rule as the list; no change in `SecurityConfig`) |

OpenAPI: summary "Resumen de arribos próximos", with a description of what `total` counts, why `nearest`
leaves out the pending confirmations, and the 401.

## Data model

No change. `upcoming_event_v` (V9) already has every row; no migration and no new index. The view has
about one row per employee and type, so the count and the four rows are cheap.

## Tests

| Test | What |
|------|------|
| `UpcomingEventSpecificationsTest` | `upcoming` keeps today and the last day of the window, and leaves out a passed assignment end and an event after the window. |
| `UpcomingEventQueryServiceTest` (without database) | `total` uses the 30-day window with the pending ones; `nearest` asks for four rows by date; the rows are mapped like the list; one `today` from the clock. |
| `UpcomingEventControllerTest` (Testcontainers, end to end) | With `UpcomingEventFixture`: `windowDays` 30, `total` equal to `summary.total` of `GET /upcoming-events?windowDays=30`, `nearest` without the pending event and ordered by date, at most four; 401 without token; readable by every role. |

Coverage of the new code ≥ 80% (Sonar gate).

## Error codes, settings and environment variables

None new.
