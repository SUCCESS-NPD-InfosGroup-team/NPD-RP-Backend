# SCRUM-231 — Deactivate and reactivate a user account (HU-09, backend)

Jira: [SCRUM-217](https://infosgroup-sucess.atlassian.net/browse/SCRUM-217) (HU-09) · Backend subtask:
[SCRUM-231](https://infosgroup-sucess.atlassian.net/browse/SCRUM-231) · Frontend subtask: SCRUM-232 ·
Epic: SCRUM-7 Users management · Split from: SCRUM-53 (HU-03)

Mockups: Figma "NPD-InfosGroup", page "08 · Usuarios y roles": "Usuarios · Editar usuario", "Usuarios ·
Editar · Desactivar cuenta", "Usuarios · Cuenta desactivada" and "Usuarios · Editar NPD Lead · Desactivar
cuenta".

## Context

A platform user lives in Keycloak (account, password, role) and in `app_user` (what the user list shows)
(SCRUM-93). `app_user.state` already allows `DISABLED` (V8) but nothing uses it. SCRUM-53 added the edit
of name and role, the closing of a user's sessions through the Keycloak Admin API, and the rule that at
least one active Operations administrator must remain. Every request is checked against Keycloak's
introspection (SCRUM-159), so a closed session is refused on its next request.

## User story

As an **Operations administrator**, I want to **deactivate a user account, which is deleted one day later
unless I reactivate it**, so that **access is cut at once when someone leaves and I can still undo it if it
was a mistake**.

## Acceptance criteria

Only the part the API serves. The modal, the separate red zone (CA-03), the confirmation texts and buttons
(CA-07) are frontend (SCRUM-232); the API gives them the data they need.

| ID | Criterion | What the backend guarantees |
|----|-----------|-----------------------------|
| CA-01 | Deactivation cuts access | The Keycloak account is disabled and its sessions are closed at once: the next request with an old token gets 401 and the user cannot log in again. `app_user.state` becomes `DISABLED` and the moment is kept; the deletion is scheduled exactly one day later. The audit log keeps the user's history. |
| CA-02 | Reactivation | Before the deletion time, reactivating enables the Keycloak account again with the same role (it was never removed), cancels the deletion and puts the state back: `ACTIVE` if the user had ever accessed the platform, `PENDING` otherwise. No confirmation is asked. |
| CA-03 | Edit modal with a separate zone | The user read by the modal carries its state and, when deactivated, the deletion time, so the modal can show the zone or the "Cuenta desactivada" state. |
| CA-04 | Last Operations administrator protected | Deactivating an active Operations administrator when no other active Operations administrator remains (in practice, the last one deactivating their own account) is rejected with the same error as SCRUM-53 (`LAST_OPERATIONS_ADMINISTRATOR`), and nothing changes. |
| CA-05 | Only Operations administrators | Any other role gets 403 (no token: 401) from both endpoints, and nothing changes. |
| CA-06 | Change is audited | Each deactivation and reactivation writes an `audit_log` row for `app_user` with who, when, the previous state and the new state. |
| CA-07 | Confirmation | The deletion time is one day after the deactivation; after deactivating, the response gives the exact deletion time the API will use. Before deactivating the frontend shows "now + 1 day". |
| CA-08 | Deletion after 1 day | At the deletion time the account is deleted from Keycloak and its `app_user` row is removed, so it leaves the user list. The audit log keeps all earlier rows and gets a deletion record with the user's name and email. |
| CA-09 | Deactivating an NPD lead | Only the user is deactivated and later deleted. The linked `employee` row is never changed or deleted, so the person stays in Recursos and can still be chosen as NPD lead. |

## Agreed details

- **What a deactivated account is.** `app_user.state = DISABLED` with the moment of the deactivation, and
  the Keycloak account disabled (`enabled = false`), which also stops new logins. Its sessions are closed
  through the Admin API (as in SCRUM-53).
- **Own account.** An administrator may deactivate their own account while another active Operations
  administrator remains; their sessions end as any other's.
- **Pending accounts.** A `PENDING` account (invitation sent, never used) can be deactivated too. The
  invitation link stops working while the account is disabled.
- **Deletion time.** Deactivation moment + 1 day (exact instant). The deletion runs at that time, not on a
  periodic sweep (decided 2026-11-26): each deactivation schedules its own deletion, and reactivating
  cancels it.
- **Reactivating after the deletion time.** Rejected (`ACCOUNT_DELETION_DUE`) even if the deletion has not
  run yet (for example, the API was restarting at that moment): the account is deleted as announced.
- **Repeated calls.** Deactivating an account that is already deactivated, or reactivating one that is not,
  answers 200 with the user and changes nothing (same as projects, HU-14).
- **Concurrent edits.** Both calls send the user's `version`; if the user was saved after it was read, 409
  `CONCURRENT_MODIFICATION` and nothing changes (same as the edit of SCRUM-53).
- **Editing a deactivated account.** Changing the name or role of a deactivated account is rejected with
  409 `ACCOUNT_DISABLED`: it must be reactivated first. Reading it still works.
- **Who deletes.** The deletion has no user behind it; its audit rows have the actor `system`. Who
  deactivated the account is in the deactivation row.
- **What stays after the deletion.** The audit rows of the user, and those where the user is the actor
  (by username). Users the deleted user created show the username as "created by", as for any creator that
  is not in `app_user` (SCRUM-53).
- **Keycloak down.** Deactivating or reactivating answers 503 `IDENTITY_PROVIDER_UNAVAILABLE` and nothing
  is left half-changed. A deletion that cannot reach Keycloak is retried later.
- **Messages.** New user-facing errors get their Spanish text in `messages.properties`.

## Scope

**Included**

- `POST /api/v1/access/users/{id}/deactivation` and `/reactivation` (CA-01, CA-02, CA-04 to CA-07,
  CA-09), with OpenAPI and Spanish messages.
- The deletion time in the user read by the list and the modal (CA-03, CA-07).
- The scheduled deletion of each deactivated account at its time, surviving restarts (CA-08).
- Rejecting the edit of a deactivated account.

**Out of scope**

- The frontend (SCRUM-232): the zone of the modal, the confirmation, the "Cuenta desactivada" state.
- Filters of the user list by state.
- Deleting accounts by hand (immediately), and deleting employee records.
- Notifying the user by email that their account was deactivated.

## Open questions

None. Resolved while writing it (2026-11-26):

1. **Scope:** the whole subtask SCRUM-231: deactivate, reactivate and the deletion.
2. **Pending accounts** can be deactivated; **editing a deactivated account** is rejected.
3. **Reactivating after the deletion time** is rejected, even if the deletion has not run yet.
4. **How the deletion runs:** one task per account at its exact time, rescheduled from the database when
   the API starts, instead of a periodic job that would delete up to one interval late.
