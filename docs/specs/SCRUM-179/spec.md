# SCRUM-179 - Upcoming events list, backend (HU-06)

Jira: [SCRUM-179](https://infosgroup-sucess.atlassian.net/browse/SCRUM-179) - Story: SCRUM-92
HE-01-HU-06 "View upcoming events list" - Frontend: SCRUM-178 - Client question to confirm: P-12.

## User story

As a **platform user (any role)**, I want to **see every upcoming event (assignment end, client
contract end, probation end and vacation start) in a single list**, so that **I can anticipate what is
due soon without reviewing the planner**.

## Acceptance criteria

Only the part the API serves. CA-01 (strip in Recursos) is frontend only.

| ID | Criterion | What the backend guarantees |
|----|-----------|-----------------------------|
| CA-02 | Event types | `ASSIGNMENT_END` (a person's end date on their project), `CONTRACT_END` (the project's contract end with the client, for its whole team), `PROBATION_END` (day 90, only for people still on probation), `VACATION_START` and `PENDING_CONFIRMATION` (an assignment end already passed that nobody extended, RQ-10). Pending events are always listed, whatever the window. |
| CA-03 | Type cards work as filters | A count per type that ignores the type filter, so each card keeps its own total, plus the list filtered by type. |
| CA-04 | Window, client and lead filters | Window of 30, 60 or 90 days ahead; client; NPD lead (their collaborators' events, and the contract ends of the projects where they have a collaborator). |
| CA-05 | Weekly grouping and row content | Events ordered by date with what each row shows: days remaining (negative once passed), person, client role, client, project, lead and the e-mail notice. The grouping by week is done by the frontend. |
| CA-06 | Visible to every role, read only | A single `GET`, for any authenticated role. |
| CA-07 | 25 rows per page | Pagination from page 0, 25 rows by default. |

Employees on Baja give no events and are not counted as team. Dates are calculated with the
application clock (America/Bogota).

## Scope

**Included**

- The `GET /api/v1/planner/upcoming-events` endpoint with the contract the frontend already uses
  (`src/services/arrivals.api.ts` of NPD-RP-frontend).

**Out of scope**

- CA-08 export with notice: SCRUM-158 (export module).
- Sending the e-mail notices (RF-02, RF-03, RF-04, RF-12): the `notifications` module does not exist
  yet, so every event goes with notice `NONE`.

## Open questions

1. **Vacation start date (P-12).** The planner only keeps the vacation *month* (11 months after hire).
   Until the client answers, the event is the first day of that month. The alternative is the exact
   anniversary (`hire_date + 11 months`); changing it touches only the view.
2. **Lead of a contract end.** A project can have several NPD leads. The event carries the lead only
   when the whole team shares one, and `null` otherwise. To confirm with the frontend.
