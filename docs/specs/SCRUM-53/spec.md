# SCRUM-53 — Edit user name and role (HU-03)

Jira: [SCRUM-53](https://infosgroup-sucess.atlassian.net/browse/SCRUM-53) · Epic: SCRUM-7 Users management ·
Backend subtask: SCRUM-189 · Frontend subtask: SCRUM-190 · Split out: SCRUM-217 (HU-09, deactivate or
reactivate) · Related: SCRUM-239 (HU-10, register an NPD lead), SCRUM-240 (HU-11, edit an NPD lead as user
and employee), SCRUM-241 (NPD lead flag)

Mockups: Figma "NPD-InfosGroup", page "08 · Usuarios y roles": "Usuarios · Editar usuario", "Usuarios ·
Editar · Cambio de rol a NPD Lead", "Usuarios · Editar · Datos de empleado al cambiar a NPD Lead",
"Usuarios · Editar NPD Lead · Deja de ser NPD Lead" and "Usuarios · Editar NPD Lead · Rol cambiado".

## Context

A platform user lives in two places (SCRUM-93): the account, password and role in **Keycloak**, and the
row the user list shows in **`app_user`** (full name, email, role, state, last access, creation date and
author). The role reaches the API inside the access token (`realm_access.roles`), and every request is
also checked against Keycloak's introspection endpoint (SCRUM-159), so a token whose session has ended is
refused on the next request.

What exists today:

- `GET /api/v1/access/users` (list) and `GET /api/v1/access/users/{id}` (one user) return `id`,
  `fullName`, `email`, `role`, `state` and `lastAccessAt`. They do **not** return the creation date and
  author that the modal shows ("Creado 14 ene 2026 por María Pérez"), nor anything about the employee
  record of an NPD lead.
- There is no way to change a user's name or role.
- Nothing links a user to their employee record. HU-10 (SCRUM-239) proposed `app_user.employee_id`; this
  story **brings that link forward** (see [Relation with HU-10 and HU-11](#relation-with-hu-10-and-hu-11)).
  `employee.is_npd_lead` already exists (SCRUM-241).

## User story

As an **Operations administrator**, I want to **edit a user's full name and role**, so that **their access
matches their responsibilities and their name stays correct**.

## Acceptance criteria

Only the part the API serves. The modal, the notices, the toast and the hidden buttons are frontend
(SCRUM-190). CA-10 and CA-11 are changed here from the Jira version (decided 2026-11-26): an NPD lead who
changes role is put on **Baja** instead of losing the NPD lead mark, and comes back to **Activo** if they
become NPD lead again.

| ID | Criterion | What the backend guarantees |
|----|-----------|-----------------------------|
| CA-01 | Role change applies immediately | The new role is set in Keycloak (the old one removed) and in `app_user`, and the user's Keycloak sessions are closed. Their next request is refused (401), so they log in again and get a token with only the new role: the old role no longer grants anything. |
| CA-04 | Last Operations administrator protected | A change that would leave no active Operations administrator is rejected with a specific error that the frontend can explain ("at least one Operations administrator must remain"), and nothing changes. |
| CA-05 | No change, no record | Saving the same full name and role the user already has changes nothing: no call to Keycloak, no closed sessions, no audit entry, and the user is returned as it is. A name that only differs in leading or trailing spaces counts as the same. |
| CA-06 | Only Operations administrators | Any other role (or no token) gets 403 (or 401) from the edit endpoint and nothing changes. |
| CA-07 | Change is audited | Each changed field is recorded in `audit_log` with who, when, the previous value and the new value: the user's fields and, when it changes too, the employee record's (status, name, new record). Unchanged fields are not recorded. |
| CA-09 | Edit modal | The user can be read with everything the modal shows: full name, role, email, state, last access, creation date and the **name** of who created it. Only full name and role can be changed; email, state, last access and creation are read-only. |
| CA-10 | Changing a role to NPD Lead | **User without an employee record:** the request must carry the employee data of HU-10 (N° de empleado, Género, Posición en Infosgroup, Modalidad laboral, Fecha de inicio de contrato, Estado Activo or Licencia), with the rules of HU-10 CA-05 to CA-07, CA-12 and CA-13 when the email or N° already belong to an employee. The record is created (or the existing one linked) as NPD lead, without a project and without a lead of their own. **User whose employee record is already linked** (a former NPD lead, on Baja): no employee data is asked; the record goes back from Baja to **Activo**. In both cases the role changes only if the employee record is saved too: both, or nothing. |
| CA-11 | An NPD lead changes to another role | Before saving, the user can be read with the number of collaborators they lead. On saving, the linked employee record is kept, keeps its NPD lead mark and is put on **Baja**, so it can no longer be chosen as lead in Recursos (SCRUM-241 CA-07). The collaborators keep them as lead until someone reassigns them. The response gives the number of collaborators and the employee id, so the frontend can offer "Ver sus colaboradores". |
| CA-12 | Full name required | An empty (or only spaces) full name is rejected with a field error on `fullName`, together with the other errors of the form, and nothing is saved. |
| CA-13 | NPD lead user without an employee record (new, decided 2026-11-26) | A user with role `npd-leader` and no linked employee record is a data error (created before SCRUM-241). Editing it is rejected with a specific error that says the account must be deleted, and nothing changes. Reading it still works, and the response shows it has no employee record. |

## Agreed details

- **What can be edited.** Only `fullName` and `role` (plus the employee data of CA-10). Email, state, last
  access and creation are never changed by this endpoint, even if the request sends them.
- **Full name.** Required, up to 150 characters (same rule as creating a user), saved trimmed. It is also
  updated in Keycloak and, for a user linked to an employee record, in `employee.full_name`, so the
  account, the user list and Recursos show the same name (HU-11 CA-04, brought forward).
- **Roles.** One of the four realm roles: `operations-administrator`, `npd-leader`, `customer-success`,
  `guest`. A user has exactly one role, so the old one is removed when the new one is given.
- **Closing the sessions (CA-01).** Done through the Keycloak Admin API with the same private client
  (service account) that already creates users. It happens only when the role changes; a name change
  alone does not close the sessions.
- **Active Operations administrator (CA-04).** A user with role `operations-administrator` whose state is
  `ACTIVE`. `PENDING` users (never logged in) do not count, because they may never accept the invitation.
  In practice the rule only blocks an administrator changing their own role when they are the last one.
- **Collaborators of an NPD lead (CA-11).** The employees whose `leader_id` is the lead's employee
  record, Baja excluded: the people that still need a new lead.
- **Why Baja and not unmarking (CA-10, CA-11).** A resource with a project must have a lead. Removing the
  mark would leave collaborators led by someone who is "not a lead"; Baja keeps the mark and only stops
  the person from being chosen, and coming back as NPD lead is a status change, not a new record.
  Consequences: while on Baja, the person counts as Baja in Recursos, the dashboards and the reports; as
  any Baja, they leave their project if they had one, and come back to Activo without a project.
- **Name of the creator (CA-09).** `app_user.created_by` keeps the username of the token. The response
  gives the full name of that user when they are in `app_user`, otherwise the username as it is.
- **Errors.** Same shape as the rest of the API (ProblemDetails, with `errors` for field errors). If
  Keycloak cannot be reached the request fails with 503 (`IDENTITY_PROVIDER_UNAVAILABLE`) and nothing is
  left half-changed, as when creating a user.
- **Concurrent edits.** `app_user` gets a `version` (optimistic locking, as `employee` and `project`). The
  user is read with its `version` and the edit sends it back; if someone saved the user after it was
  read, the edit is rejected with 409 (`CONCURRENT_MODIFICATION`, with `currentVersion`) and nothing
  changes. A save with no changes (CA-05) keeps the same `version`, and recording the last access does not
  change it. When the edit also touches the employee record, a concurrent edit of that record in Recursos
  also gives 409; the modal does not send the employee's `version`.
- **Guard in Recursos.** "Cambio de rol" is not offered as a termination reason in Recursos, and a record
  on Baja for "Cambio de rol" cannot be put back to Activo or Licencia there: it comes back only by
  changing its user's role to NPD Lead (CA-10). So nobody can be chosen as lead again while their user has
  another role.
- **Messages.** New user-facing errors get their Spanish text in `messages.properties`.

## Relation with HU-10 and HU-11

This story brings forward part of both, because CA-10 and CA-11 cannot work without it:

- **From HU-10 (SCRUM-239):** the link `app_user.employee_id`, and creating or linking an NPD lead's
  employee record with its validations (HU-10 CA-02, CA-05 to CA-07, CA-12, CA-13). HU-10 keeps the
  "Crear usuario" flow (registering an NPD lead as user and employee, or only as employee) and reuses
  what is built here.
- **From HU-11 (SCRUM-240):** saving the name in the user and in the employee record (HU-11 CA-04). HU-11
  keeps the second tab ("Registro en NPD Resource Planner") and editing the other employee fields.

Until HU-10 exists, the only way to get an NPD lead user linked to an employee record is CA-10 of this
story, so CA-11 is tested end to end by first changing a user to NPD Lead.

## Scope

**Included**

- `GET /api/v1/access/users/{id}` and the list: the creation date and creator name (CA-09) and, for an
  NPD lead, their employee id (or none, CA-13) and number of collaborators (CA-11). Additive fields; the
  frontend can keep using the data it already loads for the list.
- The edit endpoint for full name and role (CA-01, CA-04 to CA-07, CA-12, CA-13), closing the user's
  sessions when the role changes.
- Changing to NPD Lead with the employee data (CA-10) and from NPD Lead to another role (CA-11), with the
  `app_user.employee_id` link they need.
- The guard in Recursos for records on Baja for "Cambio de rol".
- The OpenAPI description of the new and changed endpoints, and the Spanish messages of the new errors.

**Out of scope**

- Deactivating, reactivating and deleting an account, and the "Desactivar cuenta" zone of the modal:
  SCRUM-217 (HU-09). Editing a deactivated account is defined there. Deleting the NPD lead users of
  CA-13 is done with HU-09.
- Editing the email.
- The rest of HU-10 and HU-11 (see [Relation with HU-10 and HU-11](#relation-with-hu-10-and-hu-11)).
- Search and filters by role or state in the user list (they are in the mockup but not in this story).
- Reassigning the collaborators of a former NPD lead: it is done in Recursos (HU-02 of resources) or with
  "Agregar colaboradores" (HU-08).
- The frontend (SCRUM-190).

## Open questions

None. Resolved while writing it:

1. **Baja or a new employee status for a former NPD lead (CA-10, CA-11).** Baja with a new termination
   reason **"Cambio de rol"** (migration that adds it). The termination date is the day of the change; on
   coming back to Activo the termination is cleared. Every place that excludes Baja (project team and its
   figures, NPD lead selector, upcoming events and notices, lead validation) already behaves as needed.
   A new status was discarded: every place that today excludes only `BAJA` (two views, the selector, the
   lead validation, the project team, the upcoming events filter, the resource form and the frontend)
   would have to exclude it too. Cost accepted: in Recursos the person shows as Baja, and a future
   turnover figure must leave this reason out.

## Decisions taken while writing this spec

- **CA-01:** the role is changed in Keycloak and the user's sessions are closed (2026-11-26).
- **CA-10 / CA-11 delivered with this story,** bringing forward the parts of HU-10 and HU-11 they need, in
  their own PR (2026-11-26).
- **Former NPD lead:** Baja instead of losing the mark; back to Activo if they are NPD lead again
  (2026-11-26).
- **NPD lead users without an employee record** are rejected on edit (CA-13) (2026-11-26).
- **Name change of a linked NPD lead** is saved in the employee record too (2026-11-26).
- **Optimistic locking** of `app_user` with `version` (2026-11-26).
