# SCRUM-53 — Design

How the API edits a user's name and role. What and why: [spec.md](spec.md). PRs: [plan.md](plan.md).

## 1. Overview

```
PUT /api/v1/access/users/{id}
  → validate: fields, version, CA-13, last administrator, NPD lead employee data (no writes)
  → nothing changed? return the user as it is (CA-05)
  → Keycloak: name → role → close sessions          (remember the old values)
  → one DB transaction: app_user (+ employee) + audit, rechecking version and last administrator
  → if anything after the first Keycloak change fails: put the old name and role back in Keycloak
```

Same shape as creating a user (SCRUM-93 design §3): Keycloak and the database cannot share a transaction,
so Keycloak goes first and is undone on failure, and no transaction is open while waiting for Keycloak.

Code: `access` (`web`, `service`, `client`, `dto`, `persistence`) and a new service in `planner` for the
employee side, which `access` calls (module rule: through `service`, never `persistence`).

## 2. Decision: how the role change takes effect (CA-01)

**Chosen: change the realm role in Keycloak and close the user's sessions** (decided in the spec).

| Option | Why not / why |
|--------|---------------|
| A. Read the role from `app_user` on every request | One more query per request, and Keycloak would keep the old role: tokens, the frontend's own role checks and any other client of the realm would disagree with the API. |
| **B. Keycloak role + `POST /users/{id}/logout`** | **Chosen.** Keycloak stays the only source of the role. Every request already goes through the introspection validator (SCRUM-159), so the next request with the old token gets 401 and the frontend sends the user to the login, where they get a token with only the new role. |
| C. Keycloak role only, wait for the token to expire | The old role keeps working until the token expires (minutes): breaks CA-01. |

- Sessions are closed only when the **role** changes. A name change alone does not affect permissions.
- An administrator who changes their own role (not being the last one, CA-04) is logged out too.

## 3. Keycloak Admin REST calls

Same client and service account as SCRUM-93 (`npd-rp-api-admin`). `manage-users` already allows every call
below: **no change in the realm**.

| # | Call | When |
|---|------|------|
| 1 | `GET /admin/realms/{realm}/users/{id}` | Name changes: read the whole representation |
| 2 | `PUT /admin/realms/{realm}/users/{id}` with the same representation and the new `firstName` | Name changes. The whole representation is sent back so no other field or attribute is lost |
| 3 | `GET /admin/realms/{realm}/users/{id}/role-mappings/realm` | Role changes: the current realm roles |
| 4 | `DELETE /admin/realms/{realm}/users/{id}/role-mappings/realm` with the platform roles it has (only the four of `Role`; `default-roles-*`, `offline_access` are not touched) | Role changes |
| 5 | `POST /admin/realms/{realm}/users/{id}/role-mappings/realm` with the new role (`GET /roles/{role}` as today) | Role changes |
| 6 | `POST /admin/realms/{realm}/users/{id}/logout` | Role changes, after 5 |

Errors are mapped as in SCRUM-93: 5xx, timeout or connection refused → 503 `IDENTITY_PROVIDER_UNAVAILABLE`;
401/403 → our configuration, 500. A 404 (account deleted in the Keycloak console) → 500, logged with the id.

### Order and compensation

1. Validate everything that can be validated without writing (§6). Errors here change nothing.
2. Calls 1–2, then 3–5, then 6.
3. DB transaction (§5).
4. If a call after the first write, or step 3, fails: undo in Keycloak what was done (old `firstName`, old
   role), best effort; if the undo fails it is logged with the Keycloak id. Closed sessions are not undone
   (the user just logs in again). Then the original error is returned.

## 4. Decision: the employee side lives in `planner`

**Chosen: a new `planner/service/NpdLeadEmployeeService`**, called by the `access` edit service inside its
transaction (`@Transactional` methods with the default propagation join it).

| Option | Why not / why |
|--------|---------------|
| A. `access` writes `employee` through its own repository | Breaks the module rule (another module's `persistence`). |
| **B. A `planner` service with the NPD lead operations** | **Chosen.** Reuses `ResourceRequestValidator`, `ResourceMapper.auditValues` and the resource audit, so a record created or changed from Usuarios is validated and audited exactly as one from Recursos. HU-10 reuses it for "Crear usuario". |

Operations (inputs are `planner` commands; no `access` type crosses into `planner`):

| Method | Does |
|--------|------|
| `findLinkCandidate(email, employeeNumber)` | The employee that has that email (first) or N°, if any, with what the confirmation shows |
| `problemsOf(NewNpdLeadCommand)` | The field errors `register` would give, without saving: the edit reports them with the user's before calling Keycloak |
| `register(NewNpdLeadCommand)` | Validates as a new resource (same field errors) and inserts it: `is_npd_lead = true`, no project, no lead, email = the user's |
| `link(employeeId, email)` | Checks it is linkable (§6) and fills the email if the record has none (mockup "Ya existe como empleado (N°)") |
| `leaveRole(employeeId)` | Baja: `termination.date` = today (business time zone, `Clock`), reason `CAMBIO_DE_ROL`; as any Baja, it leaves its project (BD-08). Keeps `is_npd_lead` |
| `returnToRole(employeeId)` | From Baja with reason `CAMBIO_DE_ROL` to `ACTIVO`, termination cleared, without project. Any other Baja → `EMPLOYEE_NOT_LINKABLE` (`TERMINATED`) |
| `rename(employeeId, fullName)` | Name of a linked record (HU-11 CA-04) |
| `summaries(employeeIds)` | Per record: N°, status and collaborators (employees with that `leader_id`, Baja excluded). One query for a whole page |

Every write is audited as `employee` with the resource audit values (status, termination, project, name…).
The employee is read and saved in the same transaction; its own `@Version` turns a concurrent edit from
Recursos into 409 `CONCURRENT_MODIFICATION`. The user modal does not send the employee's `version`.

## 5. Data model

Migration `V12__app_user_employee_link.sql`:

| Change | Notes |
|--------|-------|
| `app_user.employee_id bigint NULL UNIQUE REFERENCES employee (id)` | The link proposed by HU-10. `AppUser` maps it as a plain `Long employeeId` (no relation to a `planner` entity) |
| `app_user.version integer NOT NULL DEFAULT 0` | `@Version` on `AppUser`. `recordAccess` is a JPQL bulk update, so it does not change it |
| `INSERT INTO termination_reason (code, name, active) VALUES ('CAMBIO_DE_ROL', 'Cambio de rol', false)` | Inactive: it is not offered in the Recursos form (§7) but its name is shown on the records that have it |

Transaction of step 3:

1. If the role leaves `operations-administrator`: lock the active administrators with a
   `PESSIMISTIC_WRITE` JPQL query **in id order, before the user**. Locking the user first would let two
   administrators who demote each other deadlock (each holds their own row and waits for the other's);
   with one order the second waits for the first.
2. Load the user with `@Lock(PESSIMISTIC_WRITE)` and compare `version` (409 if it differs). If the user
   is the only locked administrator → `LAST_OPERATIONS_ADMINISTRATOR`, so the second of those two edits
   sees no other administrator left.
3. Employee operations (§4), then `app_user` (`full_name`, `role`, `employee_id`), then the audit rows.

## 6. API

### Reading (CA-09, CA-11, CA-13)

`GET /api/v1/access/users` and `GET /api/v1/access/users/{id}` get, additively:

| Field | Value |
|-------|-------|
| `createdAt` | `app_user.created_at` |
| `createdBy` | Full name of the `app_user` whose email is `created_by` (the email is the username); else `created_by` as it is. One query per page |
| `version` | `app_user.version` |
| `employee` | `null` if not linked; else `{ id, employeeNumber, status, collaborators }`. An `npd-leader` with `employee = null` is the CA-13 case |

### Editing

`PUT /api/v1/access/users/{id}` (Operations administrator, already covered by `/api/*/access/**`):

```json
{ "version": 3, "fullName": "Kathya Solís", "role": "npd-leader",
  "employee": { "employeeNumber": "1105", "genderId": 2, "positionId": 14, "workModeId": 3,
                "hireDate": "2026-03-03", "status": "ACTIVO" },
  "linkEmployeeId": null }
```

- `employee` is read only when the role **changes to** `npd-leader` and the user is not linked yet;
  otherwise it is ignored. `status` is `ACTIVO` or `LICENCIA`.
- `linkEmployeeId` confirms the link offered by a previous `EXISTING_NPD_LEAD_EMPLOYEE` answer: the
  employee data is then ignored (the record does not change, except an empty email).
- 200 with the user as in reading, so the toast of CA-11 has `employee.collaborators` and `employee.id`.

What happens by case:

| Before → after | Employee side |
|----------------|---------------|
| Not NPD lead → `npd-leader`, not linked | Email or N° of an existing employee → 409 `EXISTING_NPD_LEAD_EMPLOYEE` (to confirm) or `EMPLOYEE_NOT_LINKABLE`; with `linkEmployeeId` → `link`; neither → `register` |
| Not NPD lead → `npd-leader`, already linked | `returnToRole` (CA-10 second case) |
| `npd-leader` (linked) → other role | `leaveRole` (CA-11) |
| `npd-leader` not linked → anything | 409 `NPD_LEAD_WITHOUT_EMPLOYEE` (CA-13), also for a name-only change |
| Any linked user, name changes | `rename` |

### Errors

| Status / code | When |
|---------------|------|
| 400 `VALIDATION_FAILED` | `fullName` empty or over 150 (CA-12), `version` or `role` missing or unknown, `employee` missing or with invalid fields (same field codes as registering a resource, prefixed `employee.`). All together |
| 401 / 403 | No token / not Operations administrator (CA-06) |
| 404 `NOT_FOUND` | No such user |
| 409 `CONCURRENT_MODIFICATION` | `version` differs, with `currentVersion`; or the employee was edited meanwhile |
| 409 `LAST_OPERATIONS_ADMINISTRATOR` | CA-04 (new) |
| 409 `NPD_LEAD_WITHOUT_EMPLOYEE` | CA-13 (new) |
| 409 `EXISTING_NPD_LEAD_EMPLOYEE` | HU-10 CA-05/CA-06: an NPD lead employee without a user has that email or N°. Properties: `matchedBy` (`EMAIL`, `EMPLOYEE_NUMBER`) and `existingEmployee` (id, name, N°, position, project, lead, status, hire date, email) for the confirmation (new) |
| 409 `EMPLOYEE_NOT_LINKABLE` | HU-10 CA-07/CA-12/CA-13: the employee with that email or N° has a user, is not an NPD lead, or is on Baja. Properties: `matchedBy`, `reason` (`HAS_USER`, `NOT_NPD_LEAD`, `TERMINATED`), `existingResourceId`, `existingResourceName` (new) |
| 503 `IDENTITY_PROVIDER_UNAVAILABLE` | Keycloak down; nothing changed |

New codes get their Spanish text in `messages.properties` (one per `reason`). The exact HU-10 texts come
from the HU Excel; until then they follow the mockups.

## 7. Recursos guard for "Cambio de rol"

`ResourceRequestValidator` (edit resource):

- `termination.reasonId = CAMBIO_DE_ROL` is accepted only if the record already has it (it is inactive, so
  today it would be `NOT_SPECIFIED_VALUE` and the record could not be edited at all).
- A record on Baja with reason `CAMBIO_DE_ROL` cannot change to `ACTIVO` or `LICENCIA`: field error on
  `status` (`ROLE_CHANGE_TERMINATION`), "vuelve a Activo al cambiar su rol a NPD Lead en Usuarios". So an
  employee cannot be chosen as lead again while their user has another role.

## 8. Audit (CA-07)

- `app_user`: `AuditService.recordChanges("app_user", id, before, after, actor)` with `fullName`, `role`
  (realm code, as on creation) and `employee` (N° de empleado of the link). Only changed fields are written.
- `employee`: the resource audit of §4 (`recordCreation` on `register`, `recordChanges` otherwise).
- No change → no rows (CA-05).

## 9. Settings and changes outside the repository

None. No new variable, and the service account's `manage-users` already covers calls 1–6 (to be checked
once in each environment while testing PR 6 of the plan).
