# SCRUM-181 — Upcoming events strip in Recursos, backend (HU-06)

Jira: [SCRUM-181](https://infosgroup-sucess.atlassian.net/browse/SCRUM-181) · Story: SCRUM-52 (HE-01-HU-03,
"Search and filter resources") · Criterion: CA-01 of HE-01-HU-06 ("View upcoming events list") ·
Frontend: SCRUM-182 · Related: SCRUM-179 (upcoming events list, backend)

Mockup: Figma "NPD-InfosGroup", node `180:1469` (the strip at the top of Recursos).

## Context

SCRUM-179 delivered the "Arribos próximos" screen: `GET /api/v1/planner/upcoming-events`, with the
list and a count per type. The Recursos screen opens with a strip that summarises that list and links to
it. Today the strip in the frontend shows fixed numbers and calls no endpoint.

The existing list cannot feed the strip as it is:

- Its rows go by date, and every assignment end already passed ("Pendiente de confirmación") is always
  listed, whatever the window. The first four rows would be the oldest pending ones (for example "-40
  días"), not the four nearest events of the mockup (11, 12, 14 and 19 days).
- It counts every type separately and paginates (seven queries), while the strip needs one number and
  four rows, on a screen that is opened very often.

## User story

As a **platform user (any role)**, when I open **Recursos** I want to **see the nearest upcoming events
and how many are due**, so that **I notice what is coming without opening the Arribos screen**.

## What the strip shows (mockup)

| Part | Content |
|------|---------|
| Header | "Arribos próximos", "Lo que vence en los próximos 30 días" and the link "Ver los N eventos →", which opens the Arribos screen. |
| Four events | For each one: days remaining ("11 días"), type ("Fin de probatoria", "Vacaciones"…), person, and client · date ("TIGO · 18 oct 2026"). |

## Acceptance criteria

| ID | Criterion | What the backend guarantees |
|----|-----------|-----------------------------|
| CA-01 | Strip in Recursos (HU-06 CA-01): *Given I am on Recursos, when the page loads, then a strip shows the 4 nearest events and the link "Ver los N eventos →", which opens the Arribos screen.* | One request returns **N** and the **four nearest events**. |
| R-01 | N matches the Arribos screen | N is the same total the Arribos screen shows when it opens: window of 30 days, no filters, pending confirmations included. |
| R-02 | The four nearest are upcoming | The four events are the nearest ones **from today on** within the 30 days, by date. Pending confirmations (dates already passed) are **not** among them; they are counted in N and seen in Arribos. |
| R-03 | Fewer than four | If fewer than four events are coming, it returns the ones there are (none when there are none). |
| R-04 | Same event shape as Arribos | Each event carries the same data as a row of the Arribos list (type, date, days remaining, person, client, project, lead, notice), so the frontend reuses how it reads them. |
| R-05 | Every role, read only | Any authenticated role can read it, like the Arribos screen. |

The same rules as SCRUM-179 apply: employees on Baja and inactive projects give no events, and "today" is
the Bogotá date of the application clock.

## Scope

**Included**

- Reading N and the four nearest events for the strip, for any role.
- The OpenAPI description, so the frontend can connect the strip without guessing.

**Out of scope**

- The frontend (SCRUM-182): drawing the strip, the link to Arribos, and removing the four cards with fixed
  numbers that `ResourcesPage.tsx` shows today ("Arribos próximos", "Fin de contrato ≤ 30 días", "Fin de
  contrato ≤ 7 días", "Colaboradores sin proyecto"), which are not in the mockup.
- Filters on the strip (client, lead, type, window): it always summarises the whole base for 30 days.
- Changes to the Arribos list (`GET /api/v1/planner/upcoming-events`): it stays as it is and is still
  used by the Arribos screen.
- The e-mail notices: as in SCRUM-179, every event goes with notice `NONE` until the notifications
  module exists.
- The expirations panel of the Tablero (HE-07-HU-03).

## Open questions

None. The vacation date and the lead of a contract end follow what SCRUM-179 decides (its open questions
1 and 2).
