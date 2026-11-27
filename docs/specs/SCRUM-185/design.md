# SCRUM-185 — Design

How the backend serves the project detail and its team, described in [spec.md](spec.md).

> **Contract.** Shared with the frontend (SCRUM-188) from day one, so it can work against a mock. The
> routes, the response bodies and the error codes below are the contract.

## Overview

Two read requests in the `planner` module. Every role (CA-03); no change in `SecurityConfig`.

| # | Request | What it does |
|---|---------|--------------|
| 1 | `GET /api/v1/planner/projects/{id}` (existing, new fields) | Header of the detail (CA-02). |
| 2 | `GET /api/v1/planner/projects/{id}/members` (new) | Team table and timeline (CA-02). |

## Decision 1 — Header and team in two requests

**Options considered**

- **A. Everything in `GET /projects/{id}`.** One call, but the same response is returned by "Crear
  proyecto" (201) and will be returned by "Editar proyecto" (SCRUM-228), where the team is not needed.
- **B. Header in `GET /projects/{id}`, team in `GET /projects/{id}/members` (chosen).** The header stays
  small and shared by create, read and edit. The team is reloaded alone after "Agregar colaboradores" or
  "Quitar del proyecto", and the route matches the `/members` routes of SCRUM-127.

## Decision 2 — The header reuses the figures of the list

The new header fields are the same, with the same names and meaning, as the fields of
`ProjectListItemResponse` (SCRUM-121), so the frontend shows the same "D-N" and "N por confirmar" in the
list and in the detail. All of them come from `project_summary_v`; only `leads` needs the existing
`EmployeeRepository.findProjectLeads` query.

## Decision 3 — The team is read from `resource_v`

`resource_v` (`RosterEntry`) already has every column of the row: level, role, lead, status, situation,
bar start (`assignment_start_date`) and end (`project_end_date`). The team is
`project_id = :id AND employment_status_code <> 'BAJA'`, with the existing `RosterEntrySpecifications`.

`version` is not in `resource_v` today. SCRUM-127 PR 1 adds `version` to the roster rows; if it is merged
first, it is read from there. If not, it is read with one query on `employee` for the ids of the team
(`id, version`), never one query per person.

No paging: a project team is a few dozen people at most. The list is capped at 200 rows as a safety
limit; `total` (always the real team size) tells if it was cut.

## Contract

### 1. Project header — `GET /api/v1/planner/projects/{id}`

New fields marked with `// new`. Everything else is as today.

```json
{
  "id": 7,
  "name": "Core QA",
  "client": { "id": 3, "name": "Banco Mercantil", "isInternal": false },
  "active": true,
  "startDate": "2026-10-21",
  "contractEndDate": "2026-12-20",
  "daysToContractEnd": 25,                                  // new
  "teamSize": 4,
  "pendingConfirmation": 1,                                 // new
  "indefiniteCount": 1,                                     // new
  "nextAssignmentEnd": "2026-12-15",                        // new
  "leads": [ { "id": 755, "name": "Jorge Ruiz" } ]          // new
}
```

| Field | Meaning |
|-------|---------|
| `daysToContractEnd` | Days from today (America/Bogota) to `contractEndDate`; negative if it passed; `null` without contract end. "D-N" in red when ≤ 30. |
| `pendingConfirmation` | Team members whose end date passed ("N por confirmar"). |
| `indefiniteCount` | Team members without end date. |
| `nextAssignmentEnd` | Earliest end date still to come in the team; `null` if none. |
| `leads` | NPD leads of the team, by name, without repeats; `[]` without team. |

The 201 of "Crear proyecto" returns the same shape (new project: `teamSize` 0, counts 0, `leads` []).
SCRUM-228 adds `version` and `updatedAt` to this same response.

Errors: `401`; `404 NOT_FOUND` if the project does not exist.

### 2. Project team — `GET /api/v1/planner/projects/{id}/members`

```json
{
  "items": [
    {
      "id": 1003,
      "employeeNumber": "1003",
      "name": "Michelle Peña",
      "level": 2,
      "clientRole": "QA Manual",
      "leader": { "id": 755, "name": "Jorge Ruiz" },
      "status": { "code": "ACTIVO", "label": "Activo" },
      "situation": { "code": "PENDIENTE_CONFIRMACION", "label": "Pendiente de confirmación" },
      "assignmentStart": "2026-10-21",
      "assignmentEnd": "2026-11-20",
      "indefinite": false,
      "daysToAssignmentEnd": -5,
      "version": 4,
      "equipmentProvider": "Cliente",
      "clientContactEmail": "contacto@caja.com"
    },
    {
      "id": 1071,
      "employeeNumber": "1071",
      "name": "Kevin Rodríguez",
      "level": 3,
      "clientRole": null,
      "leader": { "id": 755, "name": "Jorge Ruiz" },
      "status": { "code": "LICENCIA", "label": "Licencia" },
      "situation": { "code": "ASIGNADO", "label": "Asignado" },
      "assignmentStart": "2026-10-21",
      "assignmentEnd": null,
      "indefinite": true,
      "daysToAssignmentEnd": null,
      "version": 2,
      "equipmentProvider": null,
      "clientContactEmail": null
    }
  ],
  "total": 2
}
```

| Field | Meaning |
|-------|---------|
| `id`, `employeeNumber`, `name`, `level` | As in the roster (`ResourceListItemResponse`). |
| `clientRole` | Role in the client; `null` = "Sin rol". |
| `leader` | NPD lead; `null` if the person has none. |
| `status` | `ACTIVO` or `LICENCIA` (Baja is never in the team). |
| `situation` | `ASIGNADO` or `PENDIENTE_CONFIRMACION`. |
| `assignmentStart` | Start of the person's bar: the later of the project's creation and the hire date. |
| `assignmentEnd` / `indefinite` | End of the bar; `null` with `indefinite = true`. |
| `daysToAssignmentEnd` | Negative if passed; `null` when indefinite. |
| `version` | The person's version, for "Quitar del proyecto" (SCRUM-187) and "Editar" (409 if stale). |
| `equipmentProvider` | «Equipo»: name of the person's laptop provider (`Cliente` or `Infos`); `null` if not set. Added on 2026-11-27. |
| `clientContactEmail` | «Contacto por cliente»: contact e-mail at the client; `null` if not set. Added on 2026-11-27. |
| `total` | People in the team. Equal to `teamSize` of the header. |

Order: `PENDIENTE_CONFIRMACION` first, then `assignmentEnd` ascending, indefinite last, then `name`.
A project without team answers `200` with `"items": []` and `"total": 0` ("Proyecto sin equipo").

Errors: `401`; `404 NOT_FOUND` if the project does not exist.

## Change on 2026-11-27 — laptop and client contact in the team table

The two columns of the mockup's team table that the first version did not return (spec, "Change on
2026-11-27"). Both values are already in `resource_v`, which the team is read from (`RosterEntry`):
`equipment_provider` (the provider's **name**, from the `equipment_provider` catalog) and
`client_contact_email`.

Checked against the mockup on 2026-11-27: the column «Equipo» shows «Cliente» (a value of the
`equipment_provider` catalog) and «Contacto por cliente» shows an e-mail; the headers have no sorting or
filtering. The section title «Equipo (4)» is the project team; the column «Equipo» is the laptop.

**Options considered for the laptop provider**

- **A. `{ "id", "name" }`, like `AssignmentResponse.equipment`.** `resource_v` has no
  `equipment_provider_id`, so it needs a new migration that appends the column to the view
  (`CREATE OR REPLACE VIEW` only allows adding columns at the end) and a new field in `RosterEntry`. The
  id is not needed by the table: "Editar" on a row opens the resource form, which reads the resource on
  its own.
- **B. The name only, as text (chosen).** It is what the mockup shows, and the same as the other catalog
  columns of the row (`clientRole` is also the name only). No migration: two new mapped columns in
  `RosterEntry` and two fields in `ProjectMemberResponse`. If the table ever needs the id, a field
  `equipmentProviderId` can be added later without breaking anything.

The field is called `equipmentProvider`, not `equipment`: in `AssignmentResponse`, `equipment` is the
object `{id, name}`, and the same field name with two types would confuse the frontend.

**Changes**

- `RosterEntry`: map `equipment_provider` and `client_contact_email` (read-only view columns, like the
  rest of the entity).
- `ProjectMemberResponse`: `String equipmentProvider` and `String clientContactEmail` **at the end** of the
  record, so the order of the existing fields does not change; both documented in OpenAPI as nullable.
- `ProjectTeamService.member(...)`: fills them from the row.
- No change in security, order, filters or the `total` of the team.

**Tests (added to the existing ones)**

- A member with laptop provider and client contact returns both; a member without them returns `null`
  in both (in `ProjectControllerTest`, next to the existing `/members` tests, with Testcontainers).

## Tests

- Header: new fields with a team that has one pending, one indefinite and two leads; project without team;
  project without contract end (`daysToContractEnd` null); 404.
- Team: Baja excluded and Licencia included; order; indefinite; bar start = later of creation and hire
  date; `version` present; inactive project readable; empty team; 404; 200 with each of the 4 roles
  (Testcontainers + `@SpringBootTest`).
- The `total` of the team equals the `teamSize` of the header for the same project.
