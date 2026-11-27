# SCRUM-224 — Status bar data, backend (HU-11)

Jira: [SCRUM-224](https://infosgroup-sucess.atlassian.net/browse/SCRUM-224) · Story: SCRUM-212
HE-01-HU-11 "Status bar with last data update" · Frontend: SCRUM-225 · Related bug: SCRUM-207 (BUG-34,
"Status bar only shows placeholders").

Mockups: Figma "NPD-InfosGroup", page "03 · Recursos": the bottom bar, shown on every screen.

## User story

As a **platform user (any role)**, I want to **see in the bottom bar when the data was last updated and
by whom, with the main counts**, so that **I know how current the information is before relying on
it**.

Today the bar shows only placeholders («Última actualización de datos: — por —», «Próximo envío de
avisos: —»). The frontend already calls `GET /api/v1/planner/operations-status` for the last update and
the next notice send, but the endpoint does not exist, and it gets the counts from three other requests
(resource search, projects list and project KPIs).

## Acceptance criteria

Only the part the API serves. How the bar looks, the date format («07 oct 2026 · 09:12») and removing
«La sesión expira en…» are frontend (SCRUM-225 / SCRUM-207).

| ID | Criterion | What the backend guarantees |
|----|-----------|-----------------------------|
| CA-01 | Status bar | One request returns: the moment of the last data update and its author (the latest `audit_log.occurred_at` and its `actor`, V5); the number of active resources, active projects and active clients; and the moment of the next scheduled e-mail notice send. Every role can read it. |

## Agreed details

- **Every role** (Operations administrator, NPD lead, Sales / Customer Success, Guest) gets the same
  answer: the permission matrix (T-1) lets every role see all the planner information. Without a valid
  token the answer is 401.
- **Last update** is the most recent row of `audit_log` of any record (resource, project, user…), not of
  one record. Its author is shown by full name (decision 3 below).
- **Active resources** means the people in status Activo, the same number the resource search gives with
  the default filter «Estado = Activo» (HU-03), so the bar and the list do not disagree.
- **Active projects** means the projects with `active = true`, the same number as `activeProjects` in the
  project KPIs without filters.
- **Active clients** means the clients with `active = true`. The imported «No especificado» client is
  inactive, so it does not count.
- **Counts are global**: they never depend on the filters of the screen the user is on.
- **Nothing to show is not an error.** With an empty audit log the last update and its author come back
  empty (`null`) and the bar shows «—»; the same for the next notice send while no send is scheduled.
- The answer is read-only and cheap (four counts and one latest row), so the frontend can call it each
  time the layout loads.

## Scope

**Included**

- `GET /api/v1/planner/operations-status`, keeping the three field names the frontend already reads
  (`lastUpdatedAt`, `lastUpdatedBy`, `nextNotificationAt`) and adding the three counts, so the bar needs
  one request instead of four.
- Its OpenAPI description, tests and Javadoc.

**Out of scope**

- The frontend bar and removing the session countdown (SCRUM-225, SCRUM-207).
- Sending the e-mail notices: the job scheduler (SCRUM-27), the e-mail module (SCRUM-28) and the Sales
  notice (SCRUM-194). This task only reports when the next send will happen.

## Decided on 2026-11-27

1. **Excel import and the last update.** The Excel import (`planner/importer`) does not write
   `audit_log`. Right after a fresh import, with no edit yet, the bar shows «—». **Accepted**: the import
   is a one-time load.
2. **Next notice send.** The send schedule is a setting (cron, America/Bogota time) that this endpoint
   reads to compute the next moment, and that the notice job (SCRUM-27) will reuse. While the setting is
   disabled, the field is `null` and the bar shows «—».
3. **Author shown by name.** The bar shows the **full name** of the user who made the last change, not
   the Keycloak username. When the author is not a user of the platform (an account created only in the
   Keycloak console), the username is shown, as the users list already does for «Creado por».

To do outside the code: write these answers in the HU Excel (sheet HE-01, HU-11 CA-01, and "Open
questions").

## Open questions

None.
