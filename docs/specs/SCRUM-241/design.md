# SCRUM-241 — Design

How the API marks the NPD leads and uses the mark, as described in [spec.md](spec.md).

## Overview

```
V10 migration      -> employee.is_npd_lead (+ backfill from leader_id)
Excel import       -> PlannerImportService.assignLeaders -> PlannerImportRepository.markNpdLeads
"Líder NPD"        -> GET /api/v1/planner/resources/npd-leads -> ResourceQueryService.findNpdLeads
                      -> EmployeeRepository.findNpdLeadsExcludingStatus
Register / edit    -> ResourceRequestValidator.checkAssignment (NOT_NPD_LEADER)
Create user, roles -> CreateUserRequestValidator (ROLE_NOT_ASSIGNABLE), RoleQueryService.list
```

Nothing changes outside the repository (Keycloak, Railway) and there is no new setting or environment
variable. The frontend needs no change: the selector keeps its endpoint and shape, and the role list
simply returns one card less.

## Decision 1 — A boolean column on `employee`

**Options considered**

- **A. Keep deriving it from `leader_id`.** That is the bug: a lead with no collaborators does not exist.
- **B. A separate table (`npd_lead(employee_id)`).** It could hold lead-only data later, but HU-10 does
  not ask for any, and every read would need one more join.
- **C. `employee.is_npd_lead boolean NOT NULL DEFAULT false` (chosen, as the ticket says).** One column,
  read in the same row as the status, and a new employee is not a lead unless something marks them.

## Decision 2 — V10 marks the leads that already exist (no new import)

```sql
ALTER TABLE employee ADD COLUMN is_npd_lead boolean NOT NULL DEFAULT false;

UPDATE employee
SET is_npd_lead = true
WHERE id IN (SELECT leader_id FROM employee WHERE leader_id IS NOT NULL);

COMMENT ON COLUMN employee.is_npd_lead IS
    'NPD lead: can be chosen as the lead of other employees. Set by the import and by HU-10';
```

The `UPDATE` applies to the existing data the same rule the import applies (Decision 3): whoever is the
lead of another employee. So a database that already has the Excel imported (each developer's and
production) is right as soon as Flyway runs V10 at startup, and **nobody has to import again** (spec
CA-02). On an empty database (new developer, Testcontainers) the `UPDATE` changes nothing and the import
marks the leads later.

**Why not a list of employee codes in the migration:** it would tie the migration to the production data
and give different results in every database. There is no review of the list: the leads are the ones
the Excel says (spec, context).

No view changes: `resource_v`, `project_summary_v` and `upcoming_event_v` do not show the mark. No index:
the table has a few hundred rows and the selector reads it whole.

## Decision 3 — The import marks the leads it finds, and never unmarks

`PlannerImportService.assignLeaders` already resolves every `COD LIDER` to an employee. It collects the
ids of the leads it assigns and, at the end, calls a new
`PlannerImportRepository.markNpdLeads(Collection<Long> ids)`:

```sql
UPDATE employee SET is_npd_lead = true WHERE id IN (:ids) AND NOT is_npd_lead
```

- `upsertEmployee` does not list `is_npd_lead`, neither in the `INSERT` nor in `ON CONFLICT ... DO
  UPDATE`, so an import never resets it (spec CA-03, CA-09).
- A row whose lead is not found, or is the employee itself, is already left without a lead and
  flagged in the report; it marks nobody.
- One statement for the whole run, inside the import transaction. The log line at the end adds how many
  leads were marked. The import report (`reporte-import-planificador.xlsx`) does not change.
- `PlannerImportRepository` keeps using `JdbcClient`: it is the one exception allowed by AGENTS.md.

`docs/planner-import/README.md` gets one line saying that the import marks the leads.

## Decision 4 — JPA reads the mark but never writes it

```java
@Column(name = "is_npd_lead", nullable = false, insertable = false, updatable = false)
private boolean npdLead;
```

Registering and editing a resource go through `ResourceFields.copy`, which writes every field of the
form. With the column read-only for Hibernate, a new employee gets the database default (`false`) and an
edit cannot change the mark, whatever the entity holds (spec CA-09). Registration already refreshes the
entity after the insert, so the value it returns is the real one. HU-10, which will write the mark, will
change this mapping in its own PR.

## Decision 5 — The "Líder NPD" selector reads the mark

`EmployeeRepository.findLeadsExcludingStatus` is replaced by:

```java
@Query("""
        SELECT l FROM Employee l
        WHERE l.npdLead = true AND l.status.code <> :excludedStatus
        ORDER BY l.fullName""")
List<Employee> findNpdLeadsExcludingStatus(@Param("excludedStatus") String excludedStatus);
```

`ResourceQueryService.findNpdLeads` keeps passing `BAJA` and mapping to `IdName`. The endpoint, its route,
roles and `{ items: [{ id, name }] }` shape do not change (spec CA-04, CA-07). Its OpenAPI description
changes from "who leads at least another" to "employees registered as NPD leads, except those on Baja".

## Decision 6 — Register and edit reject a lead that is not one

New `FieldErrorCode.NOT_NPD_LEADER` on `assignment.leaderId`, collected with the other errors of the form
(400 `VALIDATION_FAILED`, as the existing lead errors).

`ResourceRequestValidator.checkAssignment` already loads the chosen lead. The checks, in order, report
only the first that fails:

| Case | Code | Change |
|------|------|--------|
| The lead is the resource itself | `SELF_LEADER` | none |
| No employee with that id | `UNKNOWN_ID` | none |
| The lead is on Baja | `INACTIVE_LEADER` | none |
| Not marked as NPD lead and the lead changes | `NOT_NPD_LEADER` | **new** |

"The lead changes" (spec CA-05): on register always; on edit when `assignment.leaderId` differs from the
lead saved today. `validateUpdate` gets the saved lead from the caller:

```java
public short validateUpdate(long id, UpdateResourceRequest request, Long currentLeaderId)
```

`ResourceUpdateService.update` already loads the employee (to compare versions) before validating, so it
passes `employee.getLeaderId()` without another query. The rule "lead only with a project" (spec CA-06)
is not touched: `leaderId` stays `@NotNull` inside `AssignmentRequest`, and `assignment = null` keeps
clearing it in `ResourceFields`.

Message (`messages.properties`):
`validation.NOT_NPD_LEADER=La persona elegida no es líder NPD. Elige otro líder NPD.`

OpenAPI of `POST` and `PUT /api/v1/planner/resources`: the bullet about `leaderId` adds that it must be
an NPD lead from `GET /npd-leads`, and the 400 lists `NOT_NPD_LEADER`.

## Decision 7 — "Crear usuario" stops offering and accepting `npd-leader`

**Options considered**

- **A. Remove `NPD_LEADER` from `Role`.** Not possible: `SecurityConfig` and the tokens of existing users
  still use it.
- **B. Keep listing it and only reject it.** The frontend would show a card that always fails.
- **C. A set of assignable roles in `access` (chosen).** `CreateUserRequestValidator` gets
  `static final Set<Role> ASSIGNABLE = EnumSet.of(OPERATIONS_ADMINISTRATOR, CUSTOMER_SUCCESS, GUEST)`.
  `RoleQueryService.list` returns only those, in the order of `Role` (spec CA-10). `Role` in `shared`
  stays as it is.

The validator still recognises the four realm names: `npd-leader` adds `FieldProblem("role",
ROLE_NOT_ASSIGNABLE)`, any other unknown text keeps `UNKNOWN_VALUE`. Validation runs before any call to
Keycloak, so nothing is created (spec CA-08). HU-10 will add its own endpoint and leave this set as it is.

Messages:

```
validation.user.role.ROLE_NOT_ASSIGNABLE=Los líderes NPD no se crean desde aquí.
validation.user.role.UNKNOWN_VALUE=Selecciona uno de los roles disponibles.
```

The `role.npd-leader.*` texts stay: HU-10 and the user list still show that role's name.

OpenAPI: `CreateUserRequest.role` loses `npd-leader` from `allowableValues` (example `customer-success`),
the `POST /api/v1/access/users` description lists three roles and the 400 mentions `ROLE_NOT_ASSIGNABLE`,
and `RoleResponse.code` changes its example.

## Data model

| Table | Change |
|-------|--------|
| `employee` | `is_npd_lead boolean NOT NULL DEFAULT false` (V10), filled from `leader_id`. |

`docs/database/` (README and ER diagram) adds the column.

## Error codes

| Code | Field | Endpoint | When |
|------|-------|----------|------|
| `NOT_NPD_LEADER` (new) | `assignment.leaderId` | `POST`, `PUT /api/v1/planner/resources` | The chosen lead is not marked as NPD lead and differs from the saved one. |
| `ROLE_NOT_ASSIGNABLE` (new) | `role` | `POST /api/v1/access/users` | `role = npd-leader`. |

## Tests

| Test | What |
|------|------|
| `V10` (through `ResourceControllerTest`, Testcontainers) | The fixtures that use a lead set `is_npd_lead = true` in their `INSERT`; without it the register and edit tests would get `NOT_NPD_LEADER`. |
| `ResourceControllerTest.listsNpdLeads` | A marked lead with no collaborators is listed; an employee who leads someone but is not marked is not; a marked lead on Baja is not. |
| `ResourceControllerTest` / `ResourceUpdateControllerTest` | Register with a non-lead → 400 `NOT_NPD_LEADER`; edit keeping a non-lead saved lead → 200; edit changing to a non-lead → 400; register and edit do not change `is_npd_lead` of the resource. |
| `ResourceRequestValidatorTest` | Order of the four lead checks; `NOT_NPD_LEADER` only when the lead changes. |
| `PlannerImportServiceTest` | `markNpdLeads` gets the ids of the found leads, once, without the missing and self leads. |
| `PlannerImportRepository` (Testcontainers, existing import test) | After an import the leads are marked; a second import keeps a mark it did not set. |
| `CreateUserRequestValidatorTest`, `UserControllerTest`, `UserRegistrationServiceTest` | `npd-leader` → 400 `ROLE_NOT_ASSIGNABLE` and Keycloak is never called; the other three roles as today. |
| `RoleControllerTest` | Three roles, without `npd-leader`. |

Coverage of the new code ≥ 80% (Sonar gate).

## Rollout

- **Developers:** pull `develop` and start the API; V10 marks the leads of the data already imported. No
  new import.
- **Production:** V10 runs when the release reaches `main`. If production already has the Excel
  imported, the leads are marked then; if it is imported after the release, the import marks them.
- **Frontend:** no change needed. Its role cards and the "Líder NPD" selector read the same endpoints.
