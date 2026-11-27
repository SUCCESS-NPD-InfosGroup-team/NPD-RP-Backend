# SCRUM-224 — PR plan

PRs in merge order, each under 400 changed lines with its own tests. What and why: [spec.md](spec.md).
How: [design.md](design.md).

| # | Branch | Depends on | Estimate | Status |
|---|--------|------------|----------|--------|
| 0 | `docs/SCRUM-224-status-bar-spec` | — | docs (~250) | Merged ([#94](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/94)) |
| 1 | `feature/SCRUM-224-notice-schedule` | PR 0 | 182 | Merged ([#95](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/95)) |
| 2 | `feature/SCRUM-224-status-sources` | PR 0 | 193 | Merged ([#97](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/97)) |
| 3 | `feature/SCRUM-224-operations-status` | PRs 1 and 2 | 323 | Merged ([#98](https://github.com/SUCCESS-NPD-InfosGroup-team/NPD-RP-Backend/pull/98)) |

PRs 1 and 2 are independent: both branch from `develop` after PR 0 and can be merged in any order. The
frontend (SCRUM-225 / SCRUM-207) works against the contract of `design.md` until PR 3 is merged.

## 0. Spec, design and plan — docs

This folder.

## 1. Notice schedule — part 1 of 3

Design: Decision 4.

- `NoticeScheduleProperties` (rejects an invalid cron at startup), `NoticeScheduleConfig` and
  `NoticeScheduleService.nextSend()` in `notifications.service`.
- `app.notifications.notice-cron=${NOTICE_CRON:-}` in `application.properties` (disabled by default).
- Tests: `NoticeScheduleServiceTest` — disabled, send of today in Bogota time, send of tomorrow once
  today's passed, invalid expression rejected.

## 2. Status sources — part 2 of 3

Design: Decisions 2 and 3.

- `AuditEntryRepository.findFirstByOrderByOccurredAtDescIdDesc()`, `LatestChange` and
  `AuditQueryService.findLatestChange()`.
- `AppUserRepository.findByEmail(...)` and `UserQueryService.displayNameOf(...)`.
- `EmployeeRepository.countByStatus_Code`, `ProjectRepository.countByActiveTrue`,
  `ClientRepository.countByActiveTrue`.
- Tests: `StatusBarSourcesTest` (Testcontainers) — latest row of the whole log with ties by id, the
  author by name ignoring case and the username kept when it is not a user, the counts of active rows.

## 3. Operations status endpoint — part 3 of 3

Design: the contract and Decision 1.

- `OperationsStatusResponse`, `OperationsStatusService`, `OperationsStatusController` with its OpenAPI
  description.
- Tests: `OperationsStatusServiceTest` (unit: every piece, and the empty values with no change and no
  send) and `OperationsStatusControllerTest` (JSON shape, every role 200, 401 without or with an invalid
  token).
