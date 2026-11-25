# SCRUM-241 — Flag that marks which employees are NPD leads (BUG-39)

Jira: [SCRUM-241](https://infosgroup-sucess.atlassian.net/browse/SCRUM-241) · Story: SCRUM-84 (HU-01,
"Register resource record", CA-15 "Who can be chosen as NPD lead") · Also affects: SCRUM-51 (HU-02, "Edit
resource record", CA-10) · Related: SCRUM-239 (HU-10, "Register an NPD lead from Usuarios")

## Context

### What a resource is

A resource is **one row of the `employee` table** (V2): the person's own data, their status and their
current work on a project. Catalogs, clients and projects are separate tables that `employee` points to,
and they must exist before. Registering or editing a resource writes only that row (plus its entry in
`audit_log`). The `resource_v` view (V2, V4, V7) only reads: it joins `employee` with the catalogs, the
project, the client and the lead to give the names and the situation of each resource.

### The bug

Nothing in the database says whether an employee is an NPD lead. `employee.leader_id` only points to
another employee, so today an NPD lead is whoever leads at least one other employee:

- The "Líder NPD" selector (`GET /api/v1/planner/resources/npd-leads`) lists the employees who lead at
  least one collaborator and are not on Baja. A lead whose last collaborator leaves or is reassigned
  disappears from the selector, and a lead registered with no collaborators yet (HU-10) never appears.
- Registering or editing a resource accepts **any** employee who exists and is not on Baja as its lead:
  the API does not check that the chosen lead is an NPD lead.
- Creating a user (`POST /api/v1/access/users`) accepts the role `npd-leader`, which creates a platform
  user with that role and no employee record behind it. Registering an NPD lead is its own story
  (HU-10), with steps the other roles do not have (for example, choosing the new lead's own lead).

In the Excel every collaborator has a lead (`COD LIDER`), and some leads have a lead too. The import
finds the lead by `COD LIDER` and only checks the short name in `LIDER TLP` ("JORGE RUIZ") against the
full name of the employee, so a different spelling of the name does not stop the lead from being found.

## User story

As an **Operations administrator**, when I choose the **Líder NPD** of a resource I want to **see only
the employees registered as NPD leads**, so that **I cannot assign a collaborator to someone who is not
a lead**.

## Acceptance criteria

| ID | Criterion | What the backend guarantees |
|----|-----------|-----------------------------|
| CA-01 | Who is an NPD lead | Every employee carries a mark that says whether they are an NPD lead. It does not depend on having collaborators. |
| CA-02 | Leads that already exist | In every database that already has the Excel imported (each developer's and production), the employees who are the lead of at least one other employee are marked as NPD leads **when the application starts with this version**, without importing the Excel again. |
| CA-03 | Leads from the import | Importing the Excel marks as NPD lead every employee found as the lead (`COD LIDER`) of another row. An import never removes the mark, so running it again does not undo a change made later. |
| CA-04 | "Líder NPD" selector (HU-01 CA-15, HU-02 CA-10) | `GET /api/v1/planner/resources/npd-leads` returns only the employees marked as NPD lead who are not on Baja, by name, with the same `{ id, name }` shape as today. A lead with no collaborators is listed. |
| CA-05 | Choosing a lead that is not one (register and edit) | Registering or editing a resource with an `assignment.leaderId` that is not an NPD lead is rejected with a field error on `assignment.leaderId`, together with the other errors of the form. On edit, the check applies only when the lead **changes**: a resource that keeps its current lead can still be saved. |
| CA-06 | Lead only with a project (unchanged) | Without a project ("Sin proyecto por ahora", `assignment = null`) a resource has no lead, and on edit removing the project removes the lead. With a project the lead is required. This bug keeps both rules as they are. |
| CA-07 | Lead on Baja | A lead on Baja keeps the mark but is not listed in the selector and cannot be chosen (as today, `INACTIVE_LEADER`). Their collaborators keep them as lead until someone reassigns them. |
| CA-08 | No NPD leads from "Crear usuario" | `POST /api/v1/access/users` with `role = npd-leader` is rejected with a field error on `role`, together with the other errors of the form, and nothing is created (no account in Keycloak, no invitation, no `app_user`). The other three roles are created as today. |
| CA-09 | Registering or editing keeps the mark | Registering, editing or importing a resource never changes the mark of an employee who already has one, except the import setting it as in CA-03. |
| CA-10 | No NPD lead card in "Crear usuario" | `GET /api/v1/access/roles` lists only the three roles that can be created (`operations-administrator`, `customer-success`, `guest`), so the frontend stops showing the NPD lead card without a change of its own. |

## Scope

**Included**

- The mark in the database, filled in for the employees who already lead someone.
- The import marking the leads it finds.
- The "Líder NPD" selector reading the mark.
- Register resource (HU-01) and Edit resource (HU-02): the check that the chosen lead is an NPD lead.
- Create user (HU-02 of Usuarios): rejecting the role `npd-leader` and leaving it out of the role list
  until HU-10 gives NPD leads their own registration.
- The OpenAPI description of the selector, of the register and edit endpoints and of the create user
  endpoint, and the Spanish messages of the new errors in `messages.properties`.

**Out of scope**

- Assign several collaborators to a project (SCRUM-126, HU-08): it is not touched in this bug.
- Registering an NPD lead, choosing their own lead, marking or unmarking a lead from the platform and
  changing a user's role: SCRUM-239 (HU-10).
- `app_user.employee_id`, the link between a user and their employee record (SCRUM-239).
- The NPD lead users that may already exist in `app_user` and Keycloak: they are not changed.
- The frontend: the selector keeps the same endpoint and response shape, and the forms already show
  field errors.
- The filters by lead in Proyectos, Recursos and Arribos: they filter collaborators by their lead and do
  not change.

## Open questions

None. Resolved while writing it:

1. **Lead without a project:** a resource without a project has no lead, and with a project the lead is
   required (CA-06, the current rule).
2. **NPD lead card in "Crear usuario":** the role list stops returning `npd-leader` (CA-10), so the
   frontend needs no change.
