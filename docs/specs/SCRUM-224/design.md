# SCRUM-224 — Design

How the backend serves the data of the status bar, described in [spec.md](spec.md).

> **Contract.** Shared with the frontend (SCRUM-225 / SCRUM-207). The route and the response body below
> are the contract. The first three fields keep the names the frontend already reads in `MainLayout.tsx`.

## Overview

One read request in the `planner` module, for every role (T-1).

| # | Request | What it does |
|---|---------|--------------|
| 1 | `GET /api/v1/planner/operations-status` | Last data update and its author, counts of active resources, projects and clients, and the next notice send (CA-01). |

```json
{
  "lastUpdatedAt": "2026-10-07T14:12:00Z",
  "lastUpdatedBy": "María Pérez",
  "nextNotificationAt": "2026-11-28T12:00:00Z",
  "activeResources": 142,
  "activeProjects": 31,
  "activeClients": 12
}
```

| Field | Type | Empty when | Source |
|-------|------|------------|--------|
| `lastUpdatedAt` | `Instant` (UTC) or `null` | the audit log is empty | latest `audit_log.occurred_at` (ties: highest `id`) |
| `lastUpdatedBy` | `string` or `null` | the audit log is empty | full name of the `actor` of that row; the username when it is not a user (Decision 3) |
| `nextNotificationAt` | `Instant` (UTC) or `null` | the notice schedule is disabled | next moment of `app.notifications.notice-cron` (Decision 4) |
| `activeResources` | `long` | never | employees with status `ACTIVO` |
| `activeProjects` | `long` | never | projects with `active = true` |
| `activeClients` | `long` | never | clients with `active = true` |

Responses: **200** with the body above; **401** without a valid token (`UNAUTHORIZED`, existing handler).
There is no 403: every authenticated role reads it.

## Decision 1 — One endpoint in `planner`, not one per module

**Options considered**

- **A. Each module serves its own piece** (`history` the last update, `notifications` the next send, the
  counts from the existing searches). The bar would keep making four requests, as it does today.
- **B. One `planner` endpoint that asks each module through its `service` (chosen).** The bar is part of
  the planner screens, the frontend already calls `apiFetch('planner', '/operations-status')`, and the
  rule "a module uses another one through its `service` layer" is kept.

Classes:

| Class | Package | Role |
|-------|---------|------|
| `OperationsStatusController` | `planner.web` | `GET /operations-status`, OpenAPI description. |
| `OperationsStatusService` | `planner.service` | Builds the answer; `@Transactional(readOnly = true)`. |
| `OperationsStatusResponse` | `planner.dto.response` | The record of the contract. |
| `AuditQueryService.findLatestChange()` | `history.service` | New method: latest row of the whole log. |
| `UserQueryService.displayNameOf(String)` | `access.service` | New method: full name of a username (Decision 3). |
| `NoticeScheduleService` + `NoticeScheduleProperties` | `notifications.service` | Next send of the notice schedule (Decision 4). |

`planner` → `access` is a new dependency (`access` already uses `planner` through
`NpdLeadEmployeeService`). It is between services only and creates no bean cycle: `UserQueryService`
does not depend on the new `OperationsStatusService`.

## Decision 2 — Counts with derived JPA queries

Three derived counts, no `@Query` needed:

```java
long EmployeeRepository.countByStatus_Code(String statusCode);   // "ACTIVO", ProjectQueryService.ACTIVE_STATUS
long ProjectRepository.countByActiveTrue();
long ClientRepository.countByActiveTrue();
```

`activeResources` uses the same status code as the resource search default filter «Estado = Activo»
(HU-03) and the «Colaboradores sin proyecto» KPI, so the three numbers agree. `activeProjects` matches
`activeProjects` of the project KPIs without filters. The counts never take the screen filters.

## Decision 3 — Author by full name

`audit_log.actor` keeps the `preferred_username` of the token, which in this Keycloak realm is the user's
email (the users list already relies on it to show «Creado por», `UserQueryService.creatorNames`).

`UserQueryService.displayNameOf(username)` looks the user up by email, in lower case like it is stored,
and returns `fullName`; when no `app_user` has that email (an account created only in the Keycloak
console, or a deleted user) it returns the username unchanged. Deleted users keep their audit rows, so
the fallback is needed. New repository method:

```java
Optional<AppUser> AppUserRepository.findByEmail(String email);
```

**Options considered:** store the name in `audit_log` (a migration plus a change in every writer, and
old rows would stay without name), or resolve it in the frontend (a second request and the users API is
only for administrators). Resolving on read is the smallest change and works for old rows too.

## Decision 4 — Next notice send from a cron setting

The notice job does not exist yet (SCRUM-27). The schedule is a typed setting in the `notifications`
module, read now by this endpoint and later by the job, so both always agree:

```properties
# Daily send of the e-mail notices (HE-04). Spring cron, America/Bogota time. "-" disables it.
app.notifications.notice-cron=${NOTICE_CRON:-}
```

```java
@Validated
@ConfigurationProperties("app.notifications")
public record NoticeScheduleProperties(@NotBlank String noticeCron) { ... }
```

- `"-"` is Spring's `Scheduled.CRON_DISABLED`, so the future job can use
  `@Scheduled(cron = "${app.notifications.notice-cron}", zone = "America/Bogota")` with the same value
  and stays off while it is `-`.
- `NoticeScheduleService.nextSend()` returns `Optional.empty()` when the value is `-`; otherwise
  `CronExpression.parse(cron).next(ZonedDateTime.now(clock))` with the application `Clock`
  (America/Bogota, `ClockConfig`), converted to `Instant`.
- An invalid expression stops the application at startup (validated in the properties' compact
  constructor with `CronExpression.isValidExpression`), so a typo never reaches the bar.
- Default `-`: no environment (Railway, local, tests) changes until the job exists. When SCRUM-27 is
  merged, `NOTICE_CRON` is set in Railway (for example `0 0 7 * * *`, every day at 07:00).

## Tests

- `OperationsStatusServiceTest` (unit, mocks): all fields filled; empty log → `lastUpdatedAt` and
  `lastUpdatedBy` `null`; schedule disabled → `nextNotificationAt` `null`.
- `NoticeScheduleServiceTest` (unit, fixed `Clock`): disabled; next run later today; next run tomorrow
  when today's time passed; invalid cron rejected by the properties.
- `OperationsStatusControllerTest` (Testcontainers, like `UpcomingEventControllerTest`): JSON shape with
  real rows — the latest audit row wins (ties by `id`), the author comes back by name with the email's
  case ignored, an author who is not a user keeps the username (covers `displayNameOf`), inactive
  projects and non-Activo employees are not counted; the four roles get 200; no token or an invalid one
  gets 401. Other tests share the database, so counts are checked as the difference the fixture makes.

## Changes outside the repository

- None now. When SCRUM-27 is merged: set `NOTICE_CRON` in Railway.
- Frontend (SCRUM-225): read the three counts from this answer and drop the three extra requests;
  `lastUpdatedBy` is now a name.
