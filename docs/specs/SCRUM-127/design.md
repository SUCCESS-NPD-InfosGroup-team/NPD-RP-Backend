# SCRUM-127 — Design

How the backend assigns, moves and removes several collaborators, described in [spec.md](spec.md).

> **Contract.** Shared with the frontend (SCRUM-128) from day one, so it can work against a mock. The
> routes, the request and response bodies and the error codes below are the contract.

## Overview

Three requests in the `planner` module, all under the project they act on. Only the Operations
administrator (P-14); every other role gets 403.

| # | Request | What it does |
|---|---------|--------------|
| 1 | `GET /api/v1/planner/projects/{projectId}/candidates` | People who can be added to the project (CA-01). |
| 2 | `POST /api/v1/planner/projects/{projectId}/members` | Assigns or moves several people to the project (CA-02 to CA-05). |
| 3 | `POST /api/v1/planner/projects/{projectId}/members/removal` | Removes several people from the project (CA-06). |

Plus one additive field: `version` in each row of "Buscar y filtrar recursos"
(`GET /api/v1/planner/resources`), so the bulk action of Recursos (CA-02) can send the version of each
selected person.

## Decision 1 — Routes under the project

**Options considered**

- **A. Under the resources** (`POST /planner/resources/assignment` with the project in the body). The
  project is what every row shares, and the removal only makes sense for people of one project.
- **B. Under the project (chosen).** `/projects/{projectId}/members` reads as "the team of this project".
  Both entry points of the screen (the project and the bulk bar of Recursos) know the project when they
  call it.

**Removal with `POST .../members/removal`, not `DELETE`.** It removes several people at once and carries
their versions in a body; a `DELETE` with a body is ignored by some proxies and clients.

## Decision 2 — One shared value plus per-person overrides

The screen sets the lead, role, end date and laptop once for everyone and lets each person change them
(CA-03). The request mirrors that: `defaults` for everyone, and each member may repeat any of those
fields to override it.

- A field present (not `null`) on a member wins over `defaults`.
- **End date:** `indefinite: true` means no end date. Otherwise the member's `endDate`, else the default
  `endDate`, else the project's client contract end (P-13). With none of them, the row fails with
  `REQUIRED` on `endDate`.
- **Client contact:** not part of the assignment data of the screen. A person who changes project or is
  removed loses it, because it belongs to the previous client.
- **Laptop:** kept when neither `defaults` nor the member set it.

## Decision 3 — All or nothing, row by row errors

Everything is validated before anything is written, in one transaction (CA-05).

- **Invalid values** → `400 VALIDATION_FAILED`, with one entry per problem in `errors`. Each entry says
  the person (`employeeId`, and `index` in `members`) and the field, so the frontend marks the row.
  `defaults` problems use `field` = `defaults.<name>` and no `employeeId`.
- **Changed by someone else** (the `version` sent is not the current one) → `409 CONCURRENT_MODIFICATION`
  with `members`: each person changed meanwhile, with `currentVersion`, `updatedBy` and `updatedAt`, as in
  "Editar recurso".
- **Project** missing → `404 NOT_FOUND`; inactive («No especificado») → `400` with `field` = `projectId`,
  code `INACTIVE_PROJECT`.

## Contract

### 1. Candidates — `GET /api/v1/planner/projects/{projectId}/candidates`

Query: `q` (optional; name or employee number, without accents or case, like the roster search), `page`
(from 0), `size` (25 by default, at most 100).

Everyone not on Baja and not already in this project. People Sin proyecto first, then the rest; inside
each group, by name.

```json
{
  "items": [
    {
      "id": 890,
      "employeeNumber": "890",
      "name": "Jorge Castillo",
      "level": 3,
      "clientRole": null,
      "status": { "code": "ACTIVO", "label": "Activo" },
      "client": null,
      "project": null,
      "version": 2
    },
    {
      "id": 744,
      "employeeNumber": "744",
      "name": "Alexis Montenegro",
      "level": 3,
      "clientRole": "QA Automatizador",
      "status": { "code": "ACTIVO", "label": "Activo" },
      "client": { "id": 4, "name": "COPA" },
      "project": { "id": 12, "name": "Copa Airlines - Comercial" },
      "version": 5
    }
  ],
  "page": { "number": 0, "size": 25, "totalElements": 31, "totalPages": 2 },
  "withoutProject": 2
}
```

- `client` / `project` `null` = Sin proyecto. A non-null `project` means the person will change project
  ("Sale de ‹cliente · proyecto›").
- `withoutProject`: how many candidates are Sin proyecto in total (the "Sin proyecto (2)" header), whatever
  the page.

Errors: `401`, `403`, `404` (project).

### 2. Assign or move — `POST /api/v1/planner/projects/{projectId}/members`

```json
{
  "defaults": {
    "leaderId": 755,
    "clientRoleId": 8,
    "equipmentId": 1,
    "endDate": null,
    "indefinite": false
  },
  "members": [
    { "employeeId": 890, "version": 2 },
    { "employeeId": 744, "version": 5, "clientRoleId": 3, "endDate": "2027-06-30" },
    { "employeeId": 1092, "version": 0, "indefinite": true }
  ]
}
```

| Field | Rules |
|-------|-------|
| `defaults.leaderId` | Required unless every member sets its own. Another employee, not on Baja, not the member themselves. |
| `defaults.clientRoleId` | Optional (catalog `clientRoles`). |
| `defaults.equipmentId` | Optional (catalog `equipment`). |
| `defaults.endDate` / `defaults.indefinite` | Optional; see Decision 2. A date must be in the future; not both. |
| `members` | 1 to 100 people, no repeated `employeeId`. |
| `members[].employeeId`, `members[].version` | Required. `version` is the one the screen showed. |
| `members[].leaderId`, `clientRoleId`, `equipmentId`, `endDate`, `indefinite` | Optional overrides, same rules as in `defaults`. |

Answer `200`:

```json
{
  "project": { "id": 7, "name": "Core QA" },
  "client": { "id": 3, "name": "Banco Mercantil" },
  "assigned": 3,
  "added": 2,
  "moved": 1,
  "members": [
    { "id": 890, "name": "Jorge Castillo", "previousProject": null, "endDate": "2027-03-31", "indefinite": false, "version": 3 },
    { "id": 744, "name": "Alexis Montenegro", "previousProject": { "id": 12, "name": "Copa Airlines - Comercial" }, "endDate": "2027-06-30", "indefinite": false, "version": 6 },
    { "id": 1092, "name": "Rocío Batista", "previousProject": null, "endDate": null, "indefinite": true, "version": 1 }
  ]
}
```

`added` = were Sin proyecto, `moved` = came from another project; the summary "2 a Core QA · 1 cambia de
proyecto" comes from these two.

Row errors (`400 VALIDATION_FAILED`), each with `employeeId` and `index`:

| `field` | `code` | When |
|---------|--------|------|
| `employeeId` | `UNKNOWN_ID` | The person does not exist. |
| `employeeId` | `TERMINATED` | The person is on Baja. |
| `employeeId` | `ALREADY_IN_PROJECT` | The person is already in this project. |
| `employeeId` | `DUPLICATED` | The same person appears twice in `members`. |
| `leaderId` | `REQUIRED` / `UNKNOWN_ID` / `INACTIVE_LEADER` / `SELF_LEADER` | No lead, unknown, on Baja, or the person themselves. |
| `clientRoleId` / `equipmentId` | `UNKNOWN_ID` | Not in its catalog. |
| `endDate` | `REQUIRED` / `DATE_IN_PAST` / `CONFLICTING_VALUES` | No date and no contract end; date passed; date and indefinite. |

Request-level errors: `members` empty or over 100 → `400 VALIDATION_FAILED`, `field` = `members`,
codes `REQUIRED` / `TOO_MANY`. `401`, `403`, `404` (project), `409` (see Decision 3).

### 3. Remove — `POST /api/v1/planner/projects/{projectId}/members/removal`

```json
{ "members": [ { "employeeId": 1003, "version": 4 }, { "employeeId": 1071, "version": 2 } ] }
```

Each person becomes Sin proyecto: project, NPD lead, role in the client, end date and client contact are
cleared; the laptop is kept.

Answer `200`:

```json
{
  "project": { "id": 9, "name": "Caja de Ahorros" },
  "removed": 2,
  "members": [ { "id": 1003, "name": "Michelle Peña", "version": 5 }, { "id": 1071, "name": "Kevin Rodríguez", "version": 3 } ]
}
```

Row errors: `employeeId` with `UNKNOWN_ID`, `NOT_IN_PROJECT` (the person is not in this project) or
`DUPLICATED`. Request-level: `members` `REQUIRED` / `TOO_MANY`; `401`, `403`, `404`, `409`.

### Additive change — `version` in "Buscar y filtrar recursos"

Each item of `GET /api/v1/planner/resources` gains `"version": 3`. Nothing else changes.

## Audit (CA-07)

Each person written gets the same per-field audit rows as "Editar recurso" (`AuditService.recordChanges`):
`assignment.project` with the previous and the new project, plus the lead, role, end date, laptop and
client contact when they change. All the rows of one request share its moment.
