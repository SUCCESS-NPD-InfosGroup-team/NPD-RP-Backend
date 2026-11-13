# SCRUM-91 — Design

How the backend serves the resource audit log described in [spec.md](spec.md).

## Overview

One new request in the `history` module, read-only and for the Operations administrator only:

`GET /api/v1/history/resources/{id}/audit-log`

It reads the rows that "Registrar recurso" and "Editar recurso" (SCRUM-51) already write in `audit_log`.
No migration, no new setting and no change outside the repository (Keycloak, Railway).

## Decision 1 — Route and module

The log is read in `history`, next to "Último cambio" (`/api/v1/history/resources/{id}/last-change`,
SCRUM-85), because it reads the audit log and the route names its module (AGENTS.md). `resources` is the
name the API gives to `employee` records.

**Unknown resource → empty page, not 404.** Answering 404 would make `history` ask `planner` whether the
employee exists, while `planner` already uses `history` to write the log: a dependency cycle between
modules. This is the same decision as "Último cambio". The screen is opened from a resource the frontend
already read with "Ver recurso", so the 404 already reaches the user there. Documented in OpenAPI.

## Decision 2 — What one entry is

**Options considered**

- **A. One entry per row of `audit_log`.** A registration writes one row per field (about 25), so "Alta"
  would fill a whole page, against the mockup and the agreed detail.
- **B. One entry per operation.** An edit of three fields would be one entry, but the mockup shows one
  row per changed field (CA-01).
- **C. Edits per field, registration as one entry (chosen).** Each `UPDATE` row is an entry with its one
  field. The `CREATE` rows of a resource are one entry, "Alta", that carries every registered value.

**How C is paginated.** The page is counted in the database, so totals and page numbers are exact. The
query selects every `UPDATE` row plus only the **first** `CREATE` row of the resource (lowest `id`),
which stands for the registration. When that row is on the page, the service reads the other rows of the
same registration (the existing `findByEntityTypeAndEntityIdAndOccurredAtOrderByIdAsc`, one extra query)
and puts them in the entry.

## Decision 3 — Filters

| Parameter | Type | Meaning |
|-----------|------|---------|
| `actor` | string, optional | Exact username, as returned in `actors`. |
| `field` | string, optional | Technical field name, as returned in `fields` (for example `assignment.endDate`). With a field, "Alta" is not returned: it is not a change of that field. |
| `from` | date `yyyy-MM-dd`, optional | First day included. |
| `to` | date `yyyy-MM-dd`, optional | Last day included. |
| `page`, `size` | integers | Spring paging: `size` 25 by default, at most 100 (`spring.data.web.pageable.*`, existing). Sorting is fixed (Decision 4), so `sort` is ignored. |

- **Days in Bogotá.** `from` and `to` are calendar days of the business zone (the application `Clock`,
  `America/Bogota`): `occurredAt >= from 00:00` and `occurredAt < (to + 1 day) 00:00`.
- **`from` after `to` → 400 `VALIDATION_FAILED`** on the field `to` (`CONFLICTING_VALUES`), like every
  other invalid field, so the frontend shows the message next to "Hasta".
- **Built with a `Specification`** in `history/persistence/query/AuditEntrySpecifications`, like the
  roster search (`RosterEntrySpecifications`): each filter is added only when present. The "first `CREATE`
  row" rule is a JPA Criteria subquery (`min(id)`). No native SQL (AGENTS.md).

**Filter lists.** The answer also carries the users and the fields that appear in the resource's log,
so the frontend fills "Usuario" and "Campo" without guessing. Two JPQL `select distinct` queries on the
same resource, ignoring the filters (the lists do not shrink as the user filters). Fields come only from
`UPDATE` rows, each with its Spanish label.

## Decision 4 — Order

Most recent first: `occurredAt` descending, then `id` ascending. Every row of one edit shares its
`occurredAt`, so `id` keeps the order of the form inside an edit (CA-02).

## Answer

```json
{
  "items": [
    {
      "id": 812,
      "action": "UPDATE",
      "actor": "maria.perez",
      "occurredAt": "2026-10-02T15:14:00Z",
      "fields": [
        { "field": "assignment.endDate", "label": "Fecha final", "oldValue": "2026-09-30", "newValue": "2026-10-31" }
      ]
    },
    {
      "id": 640,
      "action": "CREATE",
      "actor": "maria.perez",
      "occurredAt": "2026-08-03T13:15:00Z",
      "fields": [
        { "field": "employeeNumber", "label": "N° de empleado", "oldValue": null, "newValue": "1045" }
      ]
    }
  ],
  "page": { "number": 0, "size": 25, "totalElements": 2, "totalPages": 1 },
  "actors": ["jorge.rios", "maria.perez"],
  "fields": [ { "field": "assignment.endDate", "label": "Fecha final" } ]
}
```

- `id`: the id of the row (for `CREATE`, the first row), stable, for the frontend's list keys.
- `action`: `CREATE` ("Alta") or `UPDATE` ("Edición"). The frontend shows the Spanish names.
- `fields`: one field for `UPDATE`; every registered value for `CREATE` (`oldValue` always `null`).
  It reuses `FieldChangeResponse` of "Último cambio": same labels (`audit.field.<field>` in
  `messages.properties`) and the same fallback to the technical name.
- `actor`: the Keycloak username stored in `audit_log.actor`, as it is (CA-07).
- Values as the log stores them (dates `yyyy-MM-dd`, catalogs by name). Formatting is the frontend's job.
- `items` + `page` is the shape of `PageResponse` used by every list of the API. `actors` and `fields`
  are added next to them, so the answer is a new record `AuditLogResponse` and not `PageResponse` itself.

## Components

| Package | Class | Change |
|---------|-------|--------|
| `history/web` | `AuditController` | New method for the route above, with its OpenAPI description. |
| `history/service` | `AuditQueryService.findResourceLog(id, criteria, pageable)` | New method, read-only transaction. Validates the dates, runs the page query, completes "Alta" and reads the filter lists. |
| `history/dto/request` | `AuditLogCriteria` (record) | The four optional filters, like `ResourceSearchCriteria`. |
| `history/persistence/query` | `AuditEntrySpecifications` | New: resource, entries (UPDATE or first CREATE), actor, field, date range. |
| `history/persistence/repository` | `AuditEntryRepository` | Extends `JpaSpecificationExecutor`; two `select distinct` queries for the lists. |
| `history/service/mapper` | `AuditMapper` | New `toLogEntry(...)`; shares the label lookup with `toLastChange`. |
| `history/dto/response` | `AuditLogResponse`, `AuditLogEntryResponse`, `AuditFieldOption` | The answer. |
| `shared/security` | `SecurityConfig` | One rule: `GET /api/*/history/resources/*/audit-log` requires the Operations administrator role. |
| `resources` | `messages.properties` | `validation.auditLog.to.CONFLICTING_VALUES`, the message of the `from` after `to` error. |

## Performance

Every query filters by `entity_type` and `entity_id` and sorts by `occurred_at`, so it uses
`audit_log_entity_idx (entity_type, entity_id, occurred_at)` from V5. The log of one resource is small
(tens of rows), so no new index is needed.

## Security

CA-06: only the Operations administrator, the same role that registers and edits resources. The other
roles (NPD lead, Sales / Customer Success, Invitado) get 403. The rule goes in `SecurityConfig` before
`.anyRequest().authenticated()`, so "Último cambio" stays open to every role.

CA-05: the module exposes no request that creates, changes or deletes rows. The log is only written by
`AuditService`, inside the transactions of "Registrar recurso" and "Editar recurso".

## Errors

| Status | Code | When |
|--------|------|------|
| 400 | `INVALID_REQUEST` | `{id}` is not a number, or a date is not `yyyy-MM-dd`. |
| 400 | `VALIDATION_FAILED` | `from` is after `to` (field `to`, `CONFLICTING_VALUES`). |
| 401 | `UNAUTHORIZED` | No token, invalid token, or the session ended (SCRUM-159). |
| 403 | `FORBIDDEN` | The user is not an Operations administrator. |
| 503 | `SESSION_CHECK_UNAVAILABLE` | Keycloak could not check the session. |

All in the ProblemDetails format of the global handler (CA-08).

## Tests

- `AuditQueryServiceTest` / repository level (Testcontainers): "Alta" is one entry with every registered
  value; edits are one entry per field; order (newest first, form order inside an edit); each filter
  alone and combined; `field` hides "Alta"; `from` / `to` are Bogotá days and both included; paging
  totals count "Alta" once; rows of another resource or table are ignored; filter lists.
- `AuditControllerTest` (`@SpringBootTest`, random port): 200 for the Operations administrator, 403 for
  the other roles, 401 without a token, empty page for a resource without changes and for an unknown id,
  400 for a bad date and for `from` after `to`.
