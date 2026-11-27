# SCRUM-231 — Design

How the API deactivates, reactivates and deletes a user account. What and why: [spec.md](spec.md). PRs:
[plan.md](plan.md).

## 1. Overview

```
POST /api/v1/access/users/{id}/deactivation   { "version": 3 }
  → checks: user exists, version, CA-04 (no writes)
  → Keycloak: disable the account → close its sessions
  → DB transaction: lock, recheck version and CA-04, state DISABLED + disabled_at, audit
  → after commit: schedule the deletion at disabled_at + 1 day
  → on failure after the Keycloak change: enable the account again (best effort)

POST /api/v1/access/users/{id}/reactivation   { "version": 4 }
  → checks: version, not past the deletion time
  → Keycloak: enable the account
  → DB transaction: lock, recheck, state ACTIVE or PENDING, disabled_at NULL, audit
  → after commit: cancel the scheduled deletion
  → on failure after the Keycloak change: disable it again (best effort)

Deletion task (at disabled_at + 1 day, one per account)
  → DB: lock the user; skip unless still DISABLED and due
  → Keycloak: delete the account (404 = already deleted)
  → DB: delete app_user + audit DELETE rows (actor "system")
```

Same shape as creating and editing a user (SCRUM-93 §3, SCRUM-53 §3): Keycloak first, undone on failure,
and no transaction open while the request waits for Keycloak.

Code in `access` (`web`, `service`, `client`, `job`, `persistence`) plus a `DELETE` action in
`history/AuditService`.

## 2. Decision: what "deactivated" means in Keycloak (CA-01)

**Chosen: `enabled = false` on the account + `POST /users/{id}/logout`.**

| Option | Why not / why |
|--------|---------------|
| A. Only close the sessions | The user logs in again right away: breaks CA-01. |
| B. Remove the realm role | The user still logs in (with no role), and CA-02 needs the role back: we would have to remember it. |
| **C. Disable + close the sessions** | **Chosen.** Keycloak refuses the login of a disabled account and its action tokens (invitation link), the role stays, and reactivating is `enabled = true`. Disabling alone does not end existing sessions, so the logout is still needed. |

## 3. Decision: how the deletion runs (CA-08)

**Chosen: one task per account at its exact deletion time, on Spring's `TaskScheduler`, rebuilt from the
database when the API starts** (decided in the spec).

| Option | Why not / why |
|--------|---------------|
| A. Periodic job (`@Scheduled`, every N minutes) | Deletes up to N minutes after the time the confirmation announced, and a reactivation in that gap would have to be told apart. |
| B. Quartz or db-scheduler with a JDBC store | Exact and persistent, but a new dependency and its own tables for one task. |
| **C. `TaskScheduler.schedule(task, instant)` per account + startup recovery** | **Chosen.** Exact to the second, no new dependency. The tasks live in memory, so `disabled_at` in the database is the source of truth: when the API starts it schedules every `DISABLED` account again (an overdue one runs at once), so a restart or deploy never loses a deletion, it only delays it until the API is up. |

Details:

- `AccountDeletionScheduler` (`access/job`) keeps `Map<Long, ScheduledFuture<?>>` by user id: `schedule`
  replaces any earlier task for the user, `cancel` cancels and removes it.
- It is called **after the commit** of the deactivation or reactivation: the service publishes
  `AccountStateChanged` inside its transaction and the scheduler listens with
  `@TransactionalEventListener`, so a rolled-back deactivation never schedules a deletion and `service`
  does not depend on `job`.
- The deletion itself (`AccountDeletionService.deleteIfDue`) runs in one transaction with the user
  locked, Keycloak call included, so a reactivation saved at the same moment waits and then finds no
  user (404).
- `ApplicationReadyEvent` → `AppUserRepository.findByState(DISABLED)` → `schedule` each one.
- The task itself never trusts memory: it locks the row and deletes only if it is still `DISABLED` and
  `disabled_at + delay <= now`. A stale task (the user was reactivated, or deactivated again later) does
  nothing.
- Keycloak unavailable → reschedule the task after `retry-delay`. Any other failure → logged with the user
  id and rescheduled too; the next start also retries.
- One API instance on Railway (as today). With more instances two tasks could run for the same account;
  the row lock and the 404-tolerant delete make the second one a no-op, so it stays correct.
- `@EnableScheduling` in `shared/config/SchedulingConfig` so Spring Boot provides the `TaskScheduler`.

## 4. Keycloak Admin REST calls

Same client and service account (`npd-rp-api-admin`, `manage-users`): **no change in the realm**.

| # | Call | When |
|---|------|------|
| 1 | `GET /admin/realms/{realm}/users/{id}` | Deactivate, reactivate: whole representation |
| 2 | `PUT /admin/realms/{realm}/users/{id}` with the same representation and `enabled` changed | Deactivate (`false`), reactivate (`true`), and their compensations |
| 3 | `POST /admin/realms/{realm}/users/{id}/logout` (exists, SCRUM-53) | Deactivate, after 2 |
| 4 | `DELETE /admin/realms/{realm}/users/{id}` | Deletion task |

- New `KeycloakAdminClient.setEnabled(id, enabled)` (calls 1–2, like `rename`) and
  `deleteAccount(id)` (call 4; a 404 is "already deleted", not an error). The existing `deleteUser` keeps
  its behaviour for the creation compensation.
- Errors as today: 5xx, timeout → `IdentityProviderUnavailableException` (503); a 404 on calls 1–3 →
  `IllegalStateException` (500), logged with the id.

## 5. Data model

Migration `V13__app_user_deactivation.sql`:

| Change | Notes |
|--------|-------|
| `app_user.disabled_at timestamptz NULL` | Moment of the deactivation. The deletion time is `disabled_at + 1 day`, computed, not stored |
| `CHECK ((state = 'DISABLED') = (disabled_at IS NOT NULL))` | A deactivated account always has its moment, and only it |
| `audit_log.action` CHECK gets `'DELETE'` | Drop and recreate the constraint (V5 named it implicitly: `audit_log_action_check`) |

`AppUser.disabledAt` (`Instant`). The delay is a typed setting, `app.access.account-deletion-delay`
(`Duration`, default `P1D`), so tests can use seconds; `app.access.account-deletion-retry-delay`
(default `PT5M`).

### Transactions

**Deactivate** (after the Keycloak calls):

1. Load the user with `PESSIMISTIC_WRITE` (SCRUM-53's `lockById`) and compare `version` (409).
2. If it is an `ACTIVE` `operations-administrator`: lock the active administrators in id order
   (SCRUM-53's `lockByRoleAndState`) and reject with `LAST_OPERATIONS_ADMINISTRATOR` if it is the only one.
3. `state = DISABLED`, `disabled_at = now` (`Clock`), audit `state`: previous → `DISABLED`.

**Reactivate**: lock, `version`, `disabled_at + delay > now` (else `ACCOUNT_DELETION_DUE`),
`state = last_access_at IS NULL ? PENDING : ACTIVE`, `disabled_at = NULL`, audit `state`:
`DISABLED` → new state.

**Delete** (task): lock, check, Keycloak call 4, then `delete` + `AuditService.recordDeletion` with
`fullName`, `email`, `role` as previous values and actor `system`. If the database step fails after
Keycloak deleted the account, the row stays `DISABLED`; the retry finds the 404 and completes it.

The `employee` row (CA-09) is never read or written by any of the three. `app_user.employee_id` points to
`employee`, so deleting the user does not touch the employee.

### Audit (CA-06, CA-08)

| Action | Rows (`entity_type = app_user`) |
|--------|----------------------------------|
| Deactivate | `UPDATE state` `ACTIVE`/`PENDING` → `DISABLED`, actor = the administrator |
| Reactivate | `UPDATE state` `DISABLED` → `ACTIVE`/`PENDING`, actor = the administrator |
| Delete | `DELETE fullName`, `DELETE email`, `DELETE role` with the old value and no new value, actor `system` |

New `AuditService.recordDeletion(entityType, id, lastValues, actor)`, the mirror of `recordCreation`.

## 6. API

Both under `/api/v1/access/users/{id}`, Operations administrator only (`/api/*/access/**` in
`SecurityConfig`), body `{ "version": <int> }` like the project deactivation (HU-14), answer 200 with the
user as `GET /users/{id}` returns it.

`UserResponse` gets, additively:

| Field | Value |
|-------|-------|
| `state` | Now also `DISABLED` |
| `disabledAt` | Moment of the deactivation, `null` otherwise |
| `deletesAt` | `disabledAt + delay`: the exact time shown in the "Cuenta desactivada" state and returned right after deactivating (CA-07) |

### Errors

| Status / code | When |
|---------------|------|
| 400 `VALIDATION_FAILED` | `version` missing |
| 401 / 403 | No token / not Operations administrator (CA-05) |
| 404 `NOT_FOUND` | No such user (also once deleted) |
| 409 `CONCURRENT_MODIFICATION` | `version` differs, with `currentVersion` |
| 409 `LAST_OPERATIONS_ADMINISTRATOR` | CA-04 (from SCRUM-53) |
| 409 `ACCOUNT_DELETION_DUE` | Reactivating after `deletesAt` (new) |
| 409 `ACCOUNT_DISABLED` | `PUT /users/{id}` (SCRUM-53 edit) on a deactivated account (new) |
| 503 `IDENTITY_PROVIDER_UNAVAILABLE` | Keycloak down; nothing changed |

`ACCOUNT_DISABLED` is checked in `UserEditService.update` before any Keycloak call. A deactivation saved
after that check changes the `version`, so the edit's recheck under the lock already rejects it (409
`CONCURRENT_MODIFICATION`).

## 7. Settings and changes outside the repository

| Setting | Env variable | Default |
|---------|--------------|---------|
| `app.access.account-deletion-delay` | `ACCOUNT_DELETION_DELAY` | `P1D` |
| `app.access.account-deletion-retry-delay` | `ACCOUNT_DELETION_RETRY_DELAY` | `PT5M` |

Nothing to add in Railway (the defaults are the agreed values) and nothing in Keycloak (`manage-users`
covers calls 1–4).
