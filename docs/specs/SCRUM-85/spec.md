# SCRUM-85 — View resource detail in side panel (HU-05)

Jira: [SCRUM-85](https://infosgroup-sucess.atlassian.net/browse/SCRUM-85) · Epic: SCRUM-6 NPD resource
information Management · Backend subtask: SCRUM-180 · Frontend subtask: SCRUM-90 · Related story:
HE-01-HU-05 · Client question: P-12

Mockup: Figma "NPD-InfosGroup", frame "Sidebar - Detalle del recurso" (node `183:1507`), in "Rediseño
oct-2026 · Estados de proyectos y asignación" on the page "NPD · Estados". Its project-action buttons
belong to CA-05 and are out of scope. The older drawer `49:1484` ("Obsoleto (varias asignaciones)") is
not used.

## User story

As a **platform user (any role)**, I want to **open a resource's detail in a side panel without leaving
the list**, so that **I can check an assignment quickly while keeping my search and filters**.

## What the panel shows (mockup)

| Section | Content |
|---------|---------|
| Header | Initials, full name, employee number, level, work modality. Badges: status ("Activo") and situation ("Pendiente de confirmación"). |
| Proyecto | Client and project, with the days until the end date ("Venció hace 7 días"). Mini lane and "En el proyecto desde 02 jun 2025 → fecha final 30 sep 2026". NPD lead with their employee number ("Ricardo Him · cód. 631"), role in the client, client contact, laptop ("Equipo / laptop"), and the project's client contract end ("31 dic 2026 · del proyecto"). A warning when the person is Pendiente de confirmación. |
| Contrato | Hire date, on probation (yes/no), day 90 of probation (computed), vacation month (computed, every year). |
| Colaborador | Job title, position, business unit, work modality, email, Talent Review, termination. |
| Último cambio | Who changed what, from which value to which, and when ("María Pérez cambió Fecha final de 30 sep 2026 a 31 …"). |
| Actions | "Editar registro", "Historial" and "Auditoría". The project actions (CA-05) are out of scope. |

## Acceptance criteria

\* = changed or new in the Oct-2026 redesign.

| ID | Criterion | What the backend guarantees |
|----|-----------|-----------------------------|
| CA-01\* | Detail content | The backend returns everything the panel shows for one resource: status and situation, the end date (so the frontend can count the days left), probation, the data for the mini lane, their project (client, project, NPD lead, role, contact, team, end date and the project's client contract end), the collaborator data, and the last change. |
| CA-02 | Read-only except for the Operations administrator | Every role can read the detail. Only the Operations administrator can change the resource; this is already enforced by "Editar recurso" (SCRUM-72), which answers 403 to the other roles. |
| CA-03 | Actions by role | The frontend shows or hides "Editar registro", "Historial" and "Auditoría" from the role in the token. The backend needs nothing new. |
| CA-04 | Same panel from every entry point | One way to read the detail by resource id, used the same way from the roster and from the table. |
| CA-05\* | Project actions | **Not in this sprint** (later sprint, with HU-08). |

## Agreed details

- **Two requests.** The panel reads the collaborator and their assignment in one request and the last
  change in another. The collaborator and the assignment are the same record, because there is one
  project per person and it is stored on the employee. The last change belongs to the `history` module.
- **Collaborator and assignment: "Ver recurso"** (`GET /api/v1/planner/resources/{id}`, the one-resource
  read that the edit form uses to prefill its fields). It gains, as **new fields only**, what the panel
  needs and it does not return yet:
  - the NPD lead's employee number;
  - the start of the assignment for the mini lane, with the same rule as the roster: the later of the
    project's creation date and the hire date;
  - the days until the end date, negative when it has passed, computed with the Bogotá date like the
    roster;
  - the vacation month.

  No existing field changes or disappears, so the edit form keeps working. The main list of the module
  (`GET /api/v1/planner/resources`, "Buscar y filtrar recursos") is not touched.
- **Last change.** It is the latest operation saved through the API, a registration or an edit
  (SCRUM-51 / SCRUM-72). It returns **every field of that operation only** (at most about 25, one per
  field of the record), each with its label in Spanish and its previous and new value, plus who made it
  and when. The frontend decides how many to show (for example, the first one and "+N more"). The full
  history of a resource is not returned. Every role can read it.
- **Who made it.** The Keycloak username stored in the audit log, as it is.
- **Registration.** If the latest operation is the registration, the panel shows it as a registration:
  who, when, and the values it was registered with (no previous values).
- **No change.** A resource that came from the Excel import and was never changed has no last change,
  and the panel shows nothing there.
- **Performance.** Reading the last change uses the existing index `audit_log_entity_idx`
  (`entity_type`, `entity_id`, `occurred_at`) from V5. No new index or migration is needed.

## Scope

**Included**

- Reading the data the panel shows, for one resource, by any role (CA-01, CA-02, CA-04).
- Reading the last change of a resource: who, when, and the fields with their previous and new values.
- The OpenAPI description of what the frontend calls, so it can be connected without guessing.

**Out of scope**

- "Editar registro": the edit form and its API already exist (SCRUM-72). Connecting the button is
  frontend work.
- The "Auditoría" and "Historial" screens and the full change history: later sprints. In this sprint
  the panel only shows or hides those actions by role.
- The project actions "Cambiar de proyecto", "Quitar del proyecto" and "Extender fecha final" (CA-05):
  later sprint, together with HU-08 (SCRUM-126).
- Opening the panel from Arribos, the alert center or the Tablero: those screens do not exist yet.
- Restricting which resources each role can see (for example, an NPD lead seeing only their own
  collaborators): not requested for this story.

## Open questions

None.
