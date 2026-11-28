# SCRUM-231 — Design

How the API deletes a user. What and why: [spec.md](spec.md). PRs: [plan.md](plan.md).

## 1. Overview

```
DELETE /api/v1/access/users/{id}?version=3
  → version missing: 400
  → DB transaction:
      lock the active administrators (only if the user is one), then the user
      check: exists (404), version (409), CA-04 (409)
      Keycloak: delete the account (404 = already deleted)   ← sessions end here
      delete app_user + audit DELETE rows (actor = the administrator)
  → 204
```

Code in `access` (`web`, `service`); `KeycloakAdminClient.deleteAccount` and
`AuditService.recordDeletion` already exist (first version of this story).

## 2. Decision: Keycloak inside the transaction

Creating and editing a user (SCRUM-93 §3, SCRUM-53 §3) call Keycloak with no transaction open and undo
the change if the database then fails. A deletion cannot be undone in Keycloak, so it goes the other way.

| Option | Why not / why |
|--------|---------------|
| A. Keycloak first, then the transaction | Between both, another administrator may edit the user or delete another administrator: the transaction then rejects it, but the account is already gone. Two administrators deleting each other could both pass CA-04 and leave none. |
| B. Disable in Keycloak, save, then delete in Keycloak | Undoable, but three calls and a disabled account left behind if the last one fails. |
| **C. Call Keycloak with the rows locked, inside the transaction** | **Chosen.** The version and CA-04 are checked with the rows locked and stay true until the commit. If Keycloak fails, the transaction rolls back and nothing changed. If the commit fails after Keycloak deleted the account, the row stays; deleting it again finds the 404 and completes. The lock lasts at most the client timeouts (2 s connect + 5 s read). |

- **Lock order.** Administrators first, then the user, the same order as the SCRUM-53 edit, so an edit
  and a deletion that both touch administrators wait for each other instead of deadlocking.
- **Whether to lock the administrators** is decided by role on an unlocked read: a `PENDING`
  administrator may become `ACTIVE` before the lock without a new version.
- **Sessions.** Deleting a Keycloak user removes its sessions, and introspection answers inactive for its
  tokens: no separate logout call.

## 3. Data model

Migration `V14__drop_app_user_deactivation.sql`:

| Change | Notes |
|--------|-------|
| `DISABLED` users | `DELETE` audit rows (`fullName`, `email`, `role`, actor `system`), then the rows are deleted (spec, agreed details) |
| `app_user_disabled_at_ck`, `app_user.disabled_at` | Dropped |
| `app_user.state` CHECK | `PENDING`, `ACTIVE` only. V8 declared it inline: `app_user_state_check` |
| `audit_log.action` CHECK | Keeps `DELETE` (V13) |

`AppUser.disabledAt` and `AppUserState.DISABLED` go away.

### Audit (CA-06)

| Action | Rows (`entity_type = app_user`) |
|--------|----------------------------------|
| Delete | `DELETE fullName`, `DELETE email`, `DELETE role` with the old value and no new value; actor = the administrator |

## 4. API

`DELETE /api/v1/access/users/{id}`, Operations administrator only (`/api/*/access/**` in
`SecurityConfig`), query parameter `version`, answer 204.

| Status / code | When |
|---------------|------|
| 400 `VALIDATION_FAILED` | `version` missing (`errors[0].field = version`) |
| 401 / 403 | No token / not Operations administrator (CA-05) |
| 404 `NOT_FOUND` | No such user (also once deleted) |
| 409 `CONCURRENT_MODIFICATION` | `version` differs, with `currentVersion` |
| 409 `LAST_OPERATIONS_ADMINISTRATOR` | CA-04; its message now says "eliminar" instead of "desactivar" |
| 503 `IDENTITY_PROVIDER_UNAVAILABLE` | Keycloak down; nothing deleted |

`UserResponse` loses `disabledAt` and `deletesAt`, and `state` is only `PENDING` or `ACTIVE`.

## 5. What is removed

| Code | Removed |
|------|---------|
| `POST /users/{id}/deactivation`, `/reactivation`, `UserVersionRequest` | Endpoints |
| `AccountActivationService`, `AccountActivationCommand` | Deactivate, reactivate |
| `AccountDeletionScheduler`, `AccountStateChanged`, `SchedulingConfig` | The job; nothing else schedules tasks |
| `AccountDeletionService`, `AccountDeletionProperties`, `AccountDeletionConfig`, `findByState` | Deletion after one day |
| `app.access.account-deletion-delay`, `-retry-delay` | Settings (`application.properties`, `.env.example`) |
| `ACCOUNT_DELETION_DUE`, `ACCOUNT_DISABLED` | Error codes and messages; editing no longer checks the state |
| `KeycloakAdminClient.setEnabled` | Only deactivation used it; `rename` keeps the shared representation update |

Nothing to change in Railway or Keycloak: if `ACCOUNT_DELETION_DELAY` or `ACCOUNT_DELETION_RETRY_DELAY`
were set, they are ignored.
