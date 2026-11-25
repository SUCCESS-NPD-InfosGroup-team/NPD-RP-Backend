# SCRUM-185 — Project detail with its team, backend (HU-13)

Jira: [SCRUM-185](https://infosgroup-sucess.atlassian.net/browse/SCRUM-185) · Story: SCRUM-214
HE-01-HU-13 "View projects and project detail" · Frontend: SCRUM-188 · Client questions to confirm:
P-02, P-04, P-14.

Mockups: Figma "NPD-InfosGroup", page "04 · Proyectos": "Proyecto · Detalle" and "Proyecto sin equipo".

## User story

As a **platform user (any role)**, I want to **view each client's projects and the detail of a project
with its team**, so that **I can see which projects exist, who works on them and when their contracts
end**.

The projects list (CA-01) already exists: `GET /api/v1/planner/projects` and `/kpis` (SCRUM-121). Today
`GET /api/v1/planner/projects/{id}` only returns the project, its client and its team size, so the detail
screen cannot draw the timeline nor the team table. This spec covers what is missing.

## Acceptance criteria

Only the part the API serves. The screen, the timeline drawing and the action buttons are frontend
(SCRUM-188).

| ID | Criterion | What the backend guarantees |
|----|-----------|-----------------------------|
| CA-01 | Project list | Already served by SCRUM-121. Not changed here. |
| CA-02 | Project detail | The header of the detail can be read with the same figures as its row in the list (client contract end, days to it, team size, "N por confirmar", indefinite, NPD leads). The team can be read with what the table and the timeline show for each person: name, employee number, level, role in the client, NPD lead, status, **Situación** (Asignado / Pendiente de confirmación), start and end of the bar, indefinite and days to the end. |
| CA-03 | Visible to every role | Both requests answer 200 to every role. The create, edit and team actions are hidden by the frontend; the backend protects them in their own stories (SCRUM-121, SCRUM-228, SCRUM-127, SCRUM-187). |

## Agreed details

- **Who is in the team.** The people whose current project is this one, **Baja excluded**: the same rule
  as `teamSize` in `project_summary_v`, so the header count and the table always match. People on
  Licencia are in the team.
- **Situación.** The one already calculated in `resource_v`: `PENDIENTE_CONFIRMACION` when the end date
  has passed, otherwise `ASIGNADO`. A person in the team is never `SIN_PROYECTO`.
- **Timeline.** The project bar goes from `startDate` (creation day, P-02) to `contractEndDate` (P-04).
  Each person's bar goes from `assignmentStart` to `assignmentEnd`, with the rule already agreed for the
  roster (BUG-31, SCRUM-204): the later of the project's creation and the hire date.
- **Order of the team.** Pendiente de confirmación first (they need action), then by end date (soonest
  first), indefinite last; ties by name. Same idea as the roster: "first who ends sooner".
- **Inactive projects.** The detail and the team of an inactive project can be read too (it is reached
  from the list with "Incluir inactivos").
- **`version` of each person.** Each team row carries the person's `version`, so "Quitar del proyecto"
  (SCRUM-187 / HU-15) can be sent from the detail without reading each person again.

## Scope

**Included**

- New fields in `GET /api/v1/planner/projects/{id}` (additive; the creation response gets them too).
- New `GET /api/v1/planner/projects/{id}/members`: the team of the project.
- The OpenAPI description, so the frontend (SCRUM-188) can work against the contract from day one.

**Out of scope**

- The projects list and KPIs (SCRUM-121, done).
- Exporting the project detail (HE-06-HU-04).
- The actions of the detail: "Registrar colaborador nuevo" (HU-01), "Agregar colaboradores" (HU-08,
  SCRUM-127), "Quitar del proyecto" (HU-15, SCRUM-187), "Editar" on a row (HU-02, existing PUT of the
  resource) and "Editar proyecto" / "Desactivar" (HU-14, SCRUM-228).
- The history of who was in the project before (BD-07 / P-08).

## Decisions taken while writing this spec

- **Two requests, not one.** The header and the team are read separately (see design, Decision 1).
- **NPD lead of each person.** It is the `leader_id` of each employee, as in the roster. It does not
  depend on the NPD lead flag of SCRUM-241, which only changes who can be *chosen* as lead.

## Open questions

None for the backend. CA-03 (P-14) is already assumed by SCRUM-121.
