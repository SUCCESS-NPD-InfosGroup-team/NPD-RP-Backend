# SCRUM-181 — Plan

PRs in merge order, all against `develop`. Each one compiles, passes "Validar ramas" and "Run Unit
Tests", and stays under 400 changed lines (`git diff --shortstat origin/develop...HEAD`). What and why:
[spec.md](spec.md). How: [design.md](design.md).

| # | Branch | Content | Est. lines | Depends on | Status |
|---|--------|---------|------------|------------|--------|
| 1 | `docs/SCRUM-181-upcoming-events-summary` | `spec.md`, `design.md` and `plan.md` of this folder. | ~260 | — | In review |
| 2 | `feature/SCRUM-181-upcoming-events-summary` | The summary endpoint with its tests (design decisions 1 to 3). | ~280 | PR 1 | Pending |

## PR 2 — Summary of the upcoming events for the Recursos strip

**Part 1 of 1 code PRs. Jira: SCRUM-181.**

- `UpcomingEventSpecifications.upcoming(today, lastDay)`: events from today to the last day of the
  window, without the passed assignment ends.
- `UpcomingEventsStripResponse` (`windowDays`, `total`, `nearest`).
- `UpcomingEventQueryService.summary()`: one `today` from the clock, `count(inWindow(...))` for `total`
  and the fluent `findBy(...).limit(4)` for `nearest`, mapped with the same `toResponse` as the list.
- `UpcomingEventController.summary()`: `GET /api/v1/planner/upcoming-events/summary`, with its OpenAPI
  description (200 and 401).
- Tests: `UpcomingEventSpecificationsTest` (`upcoming`), `UpcomingEventQueryServiceTest` (`summary`),
  `UpcomingEventControllerTest` (end to end, `total` equal to the list's `summary.total`, pending ones left
  out of `nearest`, at most four by date, 401, every role).

After merging, the frontend (SCRUM-182) can draw the strip and remove the four cards with fixed numbers of
`ResourcesPage.tsx`.
