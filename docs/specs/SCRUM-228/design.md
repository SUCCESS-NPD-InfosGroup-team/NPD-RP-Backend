# SCRUM-228 — Design

How the backend edits and deactivates a project, described in [spec.md](spec.md).

> **Contract.** Shared with the frontend (SCRUM-229) from day one, so it can work against a mock. The
> routes, the request and response bodies and the error codes below are the contract.

## Overview

Two write requests in the `planner` module. Only the Operations administrator (P-14); every other role
gets 403.

| # | Request | What it does |
|---|---------|--------------|
| 1 | `PUT /api/v1/planner/projects/{id}` | Edits the name and the client contract end (CA-01, CA-06). |
| 2 | `POST /api/v1/planner/projects/{id}/deactivation` | Deactivates a project without team (CA-01). |

Plus two additive fields in `ProjectResponse` (create, read, edit): `version` and `updatedAt`.

## Decision 1 — Deactivation is its own request

**Options considered**

- **A. `active: false` in the `PUT`.** One route, but the edit form would mix two actions with different
  rules (the team check) and different confirmations in the screen.
- **B. `POST .../deactivation` (chosen).** One action, one rule, one 409. A future reactivation (CA-05)
  becomes `POST .../reactivation` without touching the edit. Same style as
  `POST .../members/removal` of SCRUM-127.

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

## Contract

### `ProjectResponse` — two new fields

Returned by "Crear proyecto", "Ver proyecto" (with the fields of SCRUM-185) and the two requests below.

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

Answer `200`: the `ProjectResponse` with the new `version`. If nothing changed, `200` with the same
`version` and no audit rows.

Errors:

| Status | `code` | When | Extra properties |
|--------|--------|------|------------------|
| `400` | `VALIDATION_FAILED` | Invalid fields, all at once in `errors`: `name` `REQUIRED` / `TOO_LONG`; `contractEndDate` `DATE_IN_PAST`; `version` `REQUIRED`. | `errors[]` with `field`, `code`, `message` |
| `401` | `UNAUTHORIZED` | No token or invalid token. | |
| `403` | `FORBIDDEN` | Not an Operations administrator. | |
| `404` | `NOT_FOUND` | The project does not exist. | |
| `409` | `PROJECT_NAME_TAKEN` | Another project of the same client has that name. | `existingProjectId` |
| `409` | `CONCURRENT_MODIFICATION` | `version` is not the current one. | `currentVersion`, `updatedAt`, `updatedBy` |
| `409` | `PROJECT_INACTIVE` (new) | The project is inactive (reactivation is not defined yet, CA-05). | |
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

The team check and the update run in one transaction, so a person assigned between the check and the
write cannot leave an inactive project with people: the deactivation re-counts the team inside the
transaction before writing.

### New error codes

| `ErrorCode` | Status | Spanish message (`messages.properties`) |
|-------------|--------|------------------------------------------|
| `PROJECT_HAS_TEAM` | 409 | «El proyecto tiene {0} colaboradores. Quítalos del proyecto antes de desactivarlo.» |
| `PROJECT_INACTIVE` | 409 | «El proyecto está inactivo y no se puede editar.» |

## Security

`SecurityConfig`: `PUT /api/*/planner/projects/*` and `POST /api/*/planner/projects/*/deactivation`
only for the Operations administrator. Keep the matchers specific: SCRUM-127 adds
`POST /api/*/planner/projects/*/members` and `.../members/removal` with the same role.

## Tests

- Edit: rename; change and remove the contract end; past date rejected only when it changes; name taken
  in the same client (409) and allowed in another client; same name with other case; invalid fields all
  at once; stale `version` (409 with `currentVersion`); inactive project (409); nothing changed (same
  `version`, no audit); audit rows of each field; 403 for the other three roles; 401; 404.
- Deactivate: without team; with team (409 `PROJECT_HAS_TEAM`, `teamSize`); only people on Baja (allowed);
  already inactive (200, no audit); stale `version`; audit row `active`; 403; 404.
- The 201 of "Crear proyecto" returns `version` 0.
