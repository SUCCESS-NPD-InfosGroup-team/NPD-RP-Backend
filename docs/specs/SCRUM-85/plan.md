# SCRUM-85 — PR plan

PRs in merge order, each under 400 changed lines with its own tests. What and why: [spec.md](spec.md).
How: [design.md](design.md).

| # | Branch | Status |
|---|--------|--------|
| 0 | `docs/SCRUM-85-resource-detail-spec` | Pushed, PR pending |
| 1 | `feature/SCRUM-85-resource-detail-fields` | Pushed, PR pending |
| 2 | `feature/SCRUM-85-last-change` | Pushed, PR pending |

PRs 1 and 2 are independent of each other: both branch from `develop` and can be reviewed and merged
in any order after PR 0.

## 0. Spec, design and plan

This folder.

## 1. New fields in "Ver recurso" — part 1 of 2

- `ResourceSituation` maps `assignment_start_date`; `ResourceQueryService.situationOf` returns the row.
- `ResourceMapper` receives the row and the `Clock`; `AssignmentResponse` gains `leaderEmployeeNumber`,
  `startDate` and `daysToEndDate`; `ResourceResponse` gains `vacationMonth` (design §2).
- Registration and edit services pass the row; OpenAPI descriptions of the new fields.
- Tests: `ResourceControllerTest` for each new field (positive and negative days, indefinite, without
  lead).
- 97 lines.

## 2. Last change endpoint — part 2 of 2

- `AuditQueryService.findResourceLastChange`, `AuditMapper`, `LastChangeResponse`, `FieldChangeResponse`,
  `AuditController` with its OpenAPI (design §3).
- Spanish labels `audit.field.<field>` in `messages.properties`.
- Tests: `AuditControllerTest` end to end (create, update with several fields, only the last operation,
  204, any role, 401, 400) and the label fallback.
- 363 lines.
