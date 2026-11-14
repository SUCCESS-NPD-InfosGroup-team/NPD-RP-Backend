# SCRUM-91 — PR plan

PRs in merge order, each under 400 changed lines with its own tests. What and why: [spec.md](spec.md).
How: [design.md](design.md). Backend subtask: SCRUM-177.

| # | Branch | Depends on | Estimate | Status |
|---|--------|------------|----------|--------|
| 0 | `docs/SCRUM-177-audit-log-spec` | — | 291 lines | Merged ([#44](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/44)) |
| 1 | `feature/SCRUM-177-audit-log-queries` | PR 0 | 253 lines | Merged ([#45](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/45)) |
| 2 | `feature/SCRUM-177-audit-log-service` | PR 1 | 384 lines | In review |
| 3 | `feature/SCRUM-177-audit-log-endpoint` | PR 2 | ~300 lines | Pending |

The PRs are merged in this order: each one uses what the previous one adds.

## 0. Spec, design and plan — docs

This folder (`spec.md`, `design.md`, `plan.md`).

## 1. Audit log queries — part 1 of 3

The database side of the log (design §2, §3).

- `history/persistence/query/AuditEntrySpecifications`: record, entries (every `UPDATE` row plus the
  first `CREATE` row), actor, edits of a field (hides "Alta"), since and before a moment.
- `AuditEntryRepository` extends `JpaSpecificationExecutor<AuditEntry>` and gains the two
  `select distinct` queries of the filter lists (users, and fields of `UPDATE` rows).
- Tests (`AuditEntryRepositoryTest`, Testcontainers): one row per entry with "Alta" counted once, other
  records ignored, each filter, the moment boundaries, the filter lists.

## 2. Audit log service — part 2 of 3

What the endpoint returns, without exposing it yet (design §2, §3, §4, "Answer").

- `history/dto/request/AuditLogCriteria` (record with the four filters).
- `AuditQueryService.findResourceLog(id, criteria, pageable)`: checks `from` is not after `to`, turns the
  Bogotá days into moments with the application `Clock`, runs the page with the fixed order (newest
  first, then form order), completes "Alta" with the rest of its registration, and reads the lists.
- `AuditMapper.toLogEntry(...)`, `toFieldOption(...)` and the label lookup shared with `toLastChange`.
- DTOs: `AuditLogResponse`, `AuditLogEntryResponse`, `AuditFieldOption`.
- `validation.auditLog.to.CONFLICTING_VALUES` in `messages.properties`.
- Tests (`AuditQueryServiceTest`, Testcontainers): "Alta" as one entry with every value; one entry per
  edited field; order; page totals count "Alta" once; `from` / `to` are Bogotá days, both included;
  filter lists sorted by label; empty log; `from` after `to` rejected.

## 3. Audit log endpoint — part 3 of 3

The request the frontend calls (design "Answer", "Security", "Errors").

- `AuditController`: `GET /api/v1/history/resources/{id}/audit-log` with `actor`, `field`, `from`, `to`
  and paging, and its full OpenAPI description (answer, 400, 401, 403, 503, empty page for an unknown id).
- `SecurityConfig`: the rule that limits the route to the Operations administrator, before
  `.anyRequest().authenticated()`.
- Tests (`AuditControllerTest`, `@SpringBootTest`, random port): 200 with the answer for the Operations
  administrator; 403 for NPD lead, Sales / Customer Success and Invitado; 401 without a token; empty page
  without changes and for an unknown id; 400 for a bad date and 400 `VALIDATION_FAILED` for `from` after
  `to`; "Último cambio" still answers to every role.
