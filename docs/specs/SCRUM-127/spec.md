# SCRUM-127 — Assign, move and remove several collaborators, backend (HU-08)

Jira: [SCRUM-127](https://infosgroup-sucess.atlassian.net/browse/SCRUM-127) · Story: SCRUM-126
HE-01-HU-08 "Assign several collaborators to a project" · Frontend: SCRUM-128 · Client questions to
confirm: P-01, P-13, P-14, P-17.

Mockups: Figma "NPD-InfosGroup", page "04 · Proyectos": "Proyecto sin equipo · Agregar colaboradores"
(node `187:44`) and "Proyecto · Quitar del proyecto" (node `191:44`).

## User story

As an **Operations administrator**, I want to **assign several collaborators to a project in one step**,
so that **I can build or move a team without editing each person**.

Until now a person could only be assigned by editing their record ("Editar recurso", SCRUM-72). A person
is in **one project at a time** (P-01): assigning someone who is on another project moves them.

## Acceptance criteria

Only the part the API serves. The screens, the selection and the summary texts are frontend (SCRUM-128).

| ID | Criterion | What the backend guarantees |
|----|-----------|-----------------------------|
| CA-01 | From the project | The candidates of "Agregar colaboradores" can be read with what the list shows: first the people Sin proyecto, then the rest, each with their current client and project, searchable by name or employee number. |
| CA-02 | From Recursos | The same assignment request serves the bulk action of Recursos: the frontend sends the selected people and the chosen project. "Crear proyecto" already exists (SCRUM-120). |
| CA-03 | Assignment data | One request carries the shared NPD lead, role in the client (optional), end date or "indefinite" and laptop, and an override per person for any of them. Without an end date and without "indefinite", the end date is the project's client contract end. |
| CA-04 | Project change | People already on another project are moved. The answer tells how many are new to the project and how many change project, so the frontend can write "N a ‹proyecto› · M cambian de proyecto". |
| CA-05 | All or nothing | If one person has an invalid value, or another user changed one of the people since they were read, nobody is assigned. The answer lists every person to fix and why. |
| CA-06 | Remove from project | One or several people can be removed: they become Sin proyecto and their NPD lead, role and end date are cleared. The project is kept. All or nothing, like CA-05. |
| CA-07 | Audit | Each person assigned, moved or removed gets an audit entry with the previous and the new value of each field that changed, the project among them. |
| — | Only the Operations administrator (P-14) | Assigning, moving and removing answer 403 to every other role. |

## Agreed details

- **Same rules as "Editar recurso".** Each person's new assignment is checked with the rules the edit
  form already applies:
  - the project must be active (not «No especificado»);
  - the NPD lead is required, must not be on Baja and cannot be the person themselves;
  - the end date must be in the future, unless "indefinite";
  - the role in the client is optional (P-17).
- **People on Baja are not assigned.** A person on Baja is rejected as a row to fix. People on Licencia
  can be assigned.
- **Someone already in the project** is not a valid row: changing the lead or end date of the team is
  "Editar recurso" (or a future bulk edit), not an assignment.
- **Concurrent changes.** The frontend sends the version of each person it showed. If someone else saved
  that person in the meantime, the whole request is rejected (CA-05), like the 409 of "Editar recurso".
- **Size.** A request carries at most 100 people, the largest page of Recursos.

## Scope

**Included**

- Reading the candidates for "Agregar colaboradores" (CA-01).
- Assigning and moving several people to one project in one request (CA-02 to CA-05).
- Removing several people from a project in one request (CA-06).
- The audit entries of every assignment, move and removal (CA-07).
- The OpenAPI description, so the frontend (SCRUM-128) can work against the contract from day one.

**Out of scope**

- The screens, the bulk bar of Recursos and the project actions of the side panel (frontend: SCRUM-128,
  SCRUM-85 CA-05).
- Changing the lead or end date of several people who stay in the same project ("Marca colaboradores para
  cambiar su líder o fecha final" in the project detail): it is not in the acceptance criteria.
- Creating a project from this flow: "Crear proyecto" already exists (SCRUM-120).

## Decisions taken while writing this spec

- **BD-09 and BD-10 are API rules, not database changes.** SCRUM-123 (V4) says so: "BD-08, BD-09 and
  BD-10 are API rules: [...] BD-09 and BD-10 go with the project-edit and bulk-assignment stories". This
  spec covers them through CA-03 (default end date) and CA-06 (what a removal clears). No migration.
- **The roster bar keeps its agreed start.** The bar of a moved person keeps starting on the later of the
  project's creation and the hire date, the rule of BUG-31 (SCRUM-204), SCRUM-85 and `resource_v`. The
  mockup draws it from the day the person joined, but no story asks for that date. Storing it is the
  project history of BD-07, left for when the client asks for it (P-08).
- **Client contact and laptop.** The client contact e-mail belongs to the previous client, so it is
  cleared on a move and on a removal (it can be set again in "Editar recurso"). The laptop is kept unless
  the request sets it.

## Open questions

None.
