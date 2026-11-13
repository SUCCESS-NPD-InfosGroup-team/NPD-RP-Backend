# SCRUM-91 — View resource audit log (HU-04)

Jira: [SCRUM-91](https://infosgroup-sucess.atlassian.net/browse/SCRUM-91) · Epic: SCRUM-6 NPD resource
information Management · Backend subtask: SCRUM-177 · Frontend subtask: SCRUM-176 · Related story:
HE-01-HU-04

Mockup: Figma "NPD-InfosGroup", frame "Auditoría · Ana Torres" (node `18:488`) on the page
"NPD · Pantallas". It is opened from a resource ("Recursos › Ana Torres #1045 › Auditoría"), through the
"Auditoría" action of the side panel (SCRUM-85). As agreed on the story, it is its own screen, not a part
of the Recursos list.

## User story

As an **Operations administrator**, I want to **view the audit log of changes made to a resource's
record**, so that **I know who changed what and when on any given record**.

## What the screen shows (mockup)

| Part | Content |
|------|---------|
| Header | Initials and name of the resource: "Auditoría · Ana Torres". "Quién cambió qué y cuándo en este registro. Las entradas no se pueden editar ni borrar." |
| Filters | Usuario (Todos or one user), Campo (Todos los campos or one field), Desde and Hasta (dates). |
| Paging | "Más recientes primero", "1–7 de 7" and previous / next. |
| Table | Fecha y hora, Autor, Acción ("Alta", "Edición", "Descarga"), Campo, Valor anterior → nuevo. Rows are grouped by month ("OCTUBRE 2026"). |
| Alta | One row "Registro creado" with a summary of the record as it was registered. |
| Descarga | One row "Exportación .xlsx" with the filter used and the number of rows. |

## Acceptance criteria

The detail of each criterion is in the user story document (HU Excel v5).

| ID | Criterion | What the backend guarantees |
|----|-----------|-----------------------------|
| CA-01 | Field-level audit trail | One entry per changed field, with who, when, the field (its label in Spanish), and the previous and new value. The entries already exist: "Registrar recurso" and "Editar recurso" (SCRUM-51) write them in `audit_log`. |
| CA-02 | Entries sorted by date | Most recent first. Entries of the same operation keep the order of the form. |
| CA-03 | Filter the audit log | Optional filters by user, by field and by a date range (from, to, both included). The backend also returns the users and fields that appear in the resource's log, to fill the two lists. |
| CA-04 | Record never edited | A resource with no entries (for example, imported from the Excel and never changed) returns an empty page, not an error. A resource id that does not exist also returns an empty page, as "Último cambio" does (see design.md). |
| CA-05 | Log cannot be modified | The API only reads the log. There is no endpoint to create, change or delete entries. |
| CA-06 | Only authorized roles | Only the Operations administrator can read it; every other role gets 403. |
| CA-07 | Author is always a user | Each entry has the Keycloak user who made it (taken from the token when it was saved); there are no anonymous or "system" entries. |
| CA-08 | Long log and load error | The log is paginated (25 entries per page by default). Errors answer in the ProblemDetails format, so the frontend can show its message. |
| CA-09 | Downloads are logged as 'Descarga' | **Not in this task**: see "Out of scope". |

## Agreed details

- **One resource.** The log is read for one resource by its id, as in the mockup. There is no log of
  every resource together.
- **Module.** The log belongs to the `history` module, next to "Último cambio" (SCRUM-85), and reuses
  the same table, labels and author.
- **Author.** The Keycloak username stored in the log, as it is, the same as "Último cambio".
- **Who can read it.** Only the Operations administrator, the role of the story. The NPD lead, Sales /
  Customer Success and Invitado get 403.
- **Alta.** A registration is returned as one entry of type "Alta" with the values it was registered
  with, so the frontend shows one row "Registro creado" and its summary, instead of one row per field.
- **Month groups.** The month headers are drawn by the frontend from the date of each entry.

## Scope

**Included**

- Reading the audit log of one resource, paginated and filtered (CA-01 to CA-08).
- The users and fields that appear in that log, for the filter lists.
- The OpenAPI description of the endpoint, so the frontend (SCRUM-176) can connect it without guessing.

**Out of scope**

- **"Descarga" entries (CA-09).** The export to .xlsx does not exist yet, so there is nothing to log.
  When it is built, it writes its own entries with a new action type (this needs a migration, because
  `audit_log.action` only allows `CREATE` and `UPDATE`).
- The "Auditoría" screen and the button in the side panel: frontend (SCRUM-176).
- Auditing other records (projects, users): only resources are audited today.
- Showing the full name of the author instead of the username: it would need Keycloak for each entry.

## Open questions

None.
