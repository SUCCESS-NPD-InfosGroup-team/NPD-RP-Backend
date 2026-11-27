# SCRUM-228 — Design

How the backend edits and deactivates a project, described in [spec.md](spec.md).

> **Contract.** Shared with the frontend (SCRUM-229) from day one, so it can work against a mock. The
> routes, the request and response bodies and the error codes below are the contract.

## Overview

Three write requests in the `planner` module. Only the Operations administrator (P-14); every other role
gets 403.

| # | Request | What it does |
|---|---------|--------------|
| 1 | `PUT /api/v1/planner/projects/{id}` | Edits the name and the client contract end (CA-01, CA-06). |
| 2 | `POST /api/v1/planner/projects/{id}/deactivation` | Deactivates a project without team (CA-01). |
| 3 | `POST /api/v1/planner/projects/{id}/reactivation` | Reactivates an inactive project (CA-05). |

Plus two additive fields in `ProjectResponse` (create, read, edit): `version` and `updatedAt`.

## Decision 1 — Deactivation and reactivation are their own requests

**Options considered**

- **A. `active: false` in the `PUT`.** One route, but the edit form would mix two actions with different
  rules (the team check) and different confirmations in the screen.
- **B. `POST .../deactivation` and `POST .../reactivation` (chosen).** One action, one rule, one error
  each. Same style as `POST .../members/removal` of SCRUM-127.

## Decision 2 — `version` on the project

Two administrators can open "Editar proyecto" at the same time. Like "Editar recurso", the form sends
the `version` it read and a stale one gets `409 CONCURRENT_MODIFICATION`, so nobody overwrites a rename
or a contract end without knowing it.

`project` has no `version` today. **V11** adds it:

```sql
ALTER TABLE project ADD COLUMN version integer NOT NULL DEFAULT 0;
COMMENT ON COLUMN project.version IS 'Optimistic locking for concurrent edits';
```

`Project` gets `@Version Integer version` and a read-only `updatedAt` (the column and its trigger exist
since V1). `project_summary_v` is not changed: the header reads `version` and `updatedAt` from `project`
through `ProjectRepository`, or V11 appends both columns at the end of the view (`CREATE OR REPLACE VIEW`
only allows appending) — whichever is smaller in the PR.

## Decision 3 — Audit with the existing service

`AuditService.recordChanges("project", id, before, after, actor)` with the same keys `recordCreation`
already uses for a project (`name`, `contractEndDate`) plus `active` ("Sí"/"No", like `isInternal` of the
client). Only the fields that changed get a row; `audit_log.action` is `UPDATE` (allowed by V5).

## Decision 4 — People move with the contract end in the same transaction (CA-04)

When `contractEndDate` changes from an old date `D` to a new date `D'` (both not null):

1. Inside the edit transaction, read the team members (Baja excluded) with `project_end_date = D`.
2. Set their `project_end_date = D'` through the `Employee` entity, so each one gets a new `version` and
   the `updated_at` trigger runs.
3. Write one audit row per person (`entity_type = employee`, field `assignment.endDate`, old `D`, new
   `D'`), the same key "Editar recurso" uses.

If one of those people is being saved by someone else at the same moment, the `@Version` check of that
employee fails and the **whole edit** is rolled back with `409 CONCURRENT_MODIFICATION`: either the
project and all its people move together, or nothing changes.

The frontend can warn before saving: in `GET /projects/{id}/members` (SCRUM-185), the people with
`assignmentEnd` equal to the project's `contractEndDate` are the ones that will move.

## Contract

### `ProjectResponse` — two new fields

Returned by "Crear proyecto", "Ver proyecto" (with the fields of SCRUM-185), the deactivation and the
reactivation, and inside `project` of the edit answer.

```json
{
  "id": 7,
  "name": "Core QA",
  "client": { "id": 3, "name": "Banco Mercantil", "isInternal": false },
  "active": true,
  "startDate": "2026-10-21",
  "contractEndDate": "2027-01-31",
  "teamSize": 4,
  "version": 3,                              // new
  "updatedAt": "2026-11-25T21:40:12Z"        // new
}
```

### 1. Edit — `PUT /api/v1/planner/projects/{id}`

```json
{ "name": "Core QA Regional", "contractEndDate": "2027-01-31", "version": 3 }
```

| Field | Rules |
|-------|-------|
| `name` | Required, stripped, at most 150 characters, unique within the client without case. |
| `contractEndDate` | Optional; `null` removes it. If it changes, it cannot be in the past (today is accepted). |
| `version` | Required: the `version` of the project the form read. |

The client is not part of the request: a `clientId` sent is ignored. Both editable fields are always
sent (full replace of what the form shows).

Answer `200`: the `ProjectResponse` with the new `version`, plus how many people moved their end date
with the contract end (CA-04):

```json
{
  "project": { "id": 7, "name": "Core QA Regional", "...": "the ProjectResponse above" },
  "membersEndDateMoved": 2
}
```

`membersEndDateMoved` is `0` when the contract end did not change, was removed, or nobody had the old
date. The frontend can show "2 colaboradores cambiaron su fecha final a 31/01/2027". If nothing changed,
`200` with the same `version`, `membersEndDateMoved` 0 and no audit rows.

Errors:

| Status | `code` | When | Extra properties |
|--------|--------|------|------------------|
| `400` | `VALIDATION_FAILED` | Invalid fields, all at once in `errors`: `name` `REQUIRED` / `TOO_LONG`; `contractEndDate` `DATE_IN_PAST`; `version` `REQUIRED`. | `errors[]` with `field`, `code`, `message` |
| `401` | `UNAUTHORIZED` | No token or invalid token. | |
| `403` | `FORBIDDEN` | Not an Operations administrator. | |
| `404` | `NOT_FOUND` | The project does not exist. | |
| `409` | `PROJECT_NAME_TAKEN` | Another project of the same client has that name. | `existingProjectId` |
| `409` | `CONCURRENT_MODIFICATION` | `version` is not the current one, or one of the people that move with the contract end was saved by someone else at the same moment (nothing is saved). | `currentVersion`, `updatedAt`, `updatedBy` |
| `409` | `PROJECT_INACTIVE` (new) | The project is inactive: reactivate it first (CA-05). | |
| `503` | `SERVICE_UNAVAILABLE` | The database did not answer; nothing saved. | |

### 2. Deactivate — `POST /api/v1/planner/projects/{id}/deactivation`

```json
{ "version": 3 }
```

Answer `200`: the `ProjectResponse` with `"active": false` and the new `version`. Already inactive: `200`
with no change.

Errors:

| Status | `code` | When | Extra properties |
|--------|--------|------|------------------|
| `400` | `VALIDATION_FAILED` | `version` missing. | `errors[]` |
| `401` / `403` / `404` | | As in the edit. | |
| `409` | `PROJECT_HAS_TEAM` (new) | The project has people (Baja excluded). The frontend can offer "Quitar del proyecto". | `teamSize` |
| `409` | `CONCURRENT_MODIFICATION` | `version` is not the current one. | `currentVersion`, `updatedAt`, `updatedBy` |

The team is counted in the same transaction as the write, right before it. An assignment saved by
someone else in that same instant is not blocked (no row lock); the risk is accepted because only the
Operations administrator does both actions.

### 3. Reactivate — `POST /api/v1/planner/projects/{id}/reactivation`

```json
{ "version": 4 }
```

Answer `200`: the `ProjectResponse` with `"active": true` and the new `version`. Already active: `200`
with no change. The contract end is kept as it was, even if it already passed.

Errors:

| Status | `code` | When | Extra properties |
|--------|--------|------|------------------|
| `400` | `VALIDATION_FAILED` | `version` missing; or the client of the project is inactive (`field` = `clientId`, code `INACTIVE_CLIENT`, the imported «No especificado» project). | `errors[]` |
| `401` / `403` / `404` | | As in the edit. | |
| `409` | `CONCURRENT_MODIFICATION` | `version` is not the current one. | `currentVersion`, `updatedAt`, `updatedBy` |

### New error codes

| `ErrorCode` | Status | Spanish message (`messages.properties`) |
|-------------|--------|------------------------------------------|
| `PROJECT_HAS_TEAM` | 409 | «El proyecto tiene {0} colaboradores. Quítalos del proyecto antes de desactivarlo.» |
| `PROJECT_INACTIVE` | 409 | «El proyecto está inactivo y no se puede editar.» |

## Security

`SecurityConfig`: `PUT /api/*/planner/projects/*`, `POST /api/*/planner/projects/*/deactivation` and
`POST /api/*/planner/projects/*/reactivation` only for the Operations administrator. Keep the matchers specific: SCRUM-127 adds
`POST /api/*/planner/projects/*/members` and `.../members/removal` with the same role.

## Tests

- Edit: rename; change and remove the contract end; past date rejected only when it changes; name taken
  in the same client (409) and allowed in another client; same name with other case; invalid fields all
  at once; stale `version` (409 with `currentVersion`); inactive project (409); nothing changed (same
  `version`, no audit); audit rows of each field; 403 for the other three roles; 401; 404.
- Edit, people that move (CA-04): only the people with the old contract end move; indefinite people and
  people with another date keep theirs; people on Baja do not move; Pendiente de confirmación with the
  old date moves and becomes Asignado; contract end removed → nobody moves; project without contract end
  → nobody moves; `membersEndDateMoved`; one `assignment.endDate` audit row per person; a concurrent
  change on one of those people rolls back the whole edit (409).
- Deactivate: without team; with team (409 `PROJECT_HAS_TEAM`, `teamSize`); only people on Baja (allowed);
  already inactive (200, no audit); stale `version`; audit row `active`; 403; 404.
- Reactivate: inactive project; already active (200, no audit); client inactive (400 `INACTIVE_CLIENT`);
  contract end in the past kept; stale `version`; audit row `active`; 403; 404.
- The 201 of "Crear proyecto" returns `version` 0.
