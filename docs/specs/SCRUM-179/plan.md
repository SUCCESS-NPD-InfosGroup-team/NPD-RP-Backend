# SCRUM-179 - PR plan

PRs in merge order, each under 400 changed lines with its own tests. What and why: [spec.md](spec.md).
How: [design.md](design.md).

| # | Branch | Status |
|---|--------|--------|
| 0 | `docs/SCRUM-179-upcoming-events-spec` | Pending |
| 1 | `feature/SCRUM-179-upcoming-event-view` | Pending |
| 2 | `feature/SCRUM-179-upcoming-event-filters` | Pending |
| 3 | `feature/SCRUM-179-upcoming-events-service` | Pending |
| 4 | `feature/SCRUM-179-upcoming-events-endpoint` | Pending |

## 0. Spec, design and plan

This folder.

## 1. View and entity - part 1 of 4

- `V9__upcoming_event_view.sql`, `UpcomingEvent` (read-only entity), `UpcomingEventRepository`.
- Tests: `UpcomingEventFixture`, `UpcomingEventViewTest`.
- ~395 lines. Depends on 0.

## 2. Filters - part 2 of 4

- `UpcomingEventType`, `UpcomingEventSpecifications` (window, type, client, lead).
- Tests: `UpcomingEventSpecificationsTest`.
- ~215 lines. Depends on 1.

## 3. Service - part 3 of 4

- Response DTOs, `UpcomingEventCriteria`, `UpcomingEventQueryService`.
- Tests: `UpcomingEventQueryServiceTest`.
- ~360 lines. Depends on 2.

## 4. Endpoint - part 4 of 4

- `UpcomingEventController` with its OpenAPI description.
- Tests: `UpcomingEventControllerTest`.
- ~210 lines. Depends on 3. After merging, the frontend can switch off the arrivals mock.
