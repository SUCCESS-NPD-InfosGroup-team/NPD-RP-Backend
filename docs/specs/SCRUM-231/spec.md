# SCRUM-231 — Delete a user (HU-09, backend)

Jira: [SCRUM-217](https://infosgroup-sucess.atlassian.net/browse/SCRUM-217) (HU-09) · Backend subtask:
[SCRUM-231](https://infosgroup-sucess.atlassian.net/browse/SCRUM-231) · Frontend subtask: SCRUM-232 ·
Epic: SCRUM-7 Users management · Split from: SCRUM-53 (HU-03)

Mockups: Figma "NPD-InfosGroup", page "08 · Usuarios y roles", "Usuarios · Editar usuario" and its
"Eliminar usuario" confirmation.

> **Changed on 2026-11-27.** The first version of this story deactivated an account and deleted it one
> day later unless it was reactivated (merged in #79 and #85–#92). It is replaced by an immediate
> deletion: no deactivated state, no reactivation and no scheduled job. That code is removed by the PRs
> of [plan.md](plan.md).

## Context

A platform user lives in Keycloak (account, password, role) and in `app_user` (what the user list shows)
(SCRUM-93). SCRUM-53 added the edit of name and role and the rule that at least one active Operations
administrator must remain. Every request is checked against Keycloak's introspection (SCRUM-159), so the
token of a deleted account is refused on its next request.

## User story

As an **Operations administrator**, I want to **delete a user**, so that **access is cut at once when
someone leaves the platform**.

## Acceptance criteria

Only the part the API serves. The red zone of the modal (CA-03) and the confirmation texts and buttons
(CA-07) are frontend (SCRUM-232).

| ID | Criterion | What the backend guarantees |
|----|-----------|-----------------------------|
| CA-01 | Deletion cuts access | The Keycloak account is deleted, which ends its sessions: the next request with an old token gets 401 and the user cannot log in again. The `app_user` row is deleted in the same request. The audit log keeps the user's history. |
| CA-02 | Deleted user leaves the list | The user no longer appears in the list nor in `GET /users/{id}` (404). There is no `DISABLED` state and no reactivation: giving access again means creating a new user, with the same email if wanted. |
| CA-03 | Separate zone in the edit modal | Frontend only. |
| CA-04 | Last Operations administrator protected | Deleting the only active Operations administrator (in practice, the last one deleting their own user) is rejected with SCRUM-53's `LAST_OPERATIONS_ADMINISTRATOR`, and nothing changes. |
| CA-05 | Only Operations administrators | Any other role gets 403 (no token: 401), and nothing changes. |
| CA-06 | Deletion is audited | `audit_log` gets `DELETE` rows for `app_user` with who, when and the user's name, email and role. |
| CA-07 | Confirmation | Frontend only; the API deletes when it is called, with no second step. |
| CA-08 | Deletion after 1 day | Removed: the deletion is immediate (CA-01). |
| CA-09 | Deleting an NPD lead | Only the user is deleted. The linked `employee` row is never changed, so the person stays in Recursos and can still be chosen as NPD lead. |

## Agreed details

- **Endpoint.** `DELETE /api/v1/access/users/{id}?version=<int>`, 204 with no body.
- **Concurrent edits.** The call sends the `version` that was read; if the user was saved after it, 409
  `CONCURRENT_MODIFICATION` and nothing is deleted (same as the edit of SCRUM-53). A missing `version`
  is a 400.
- **Own user.** An administrator may delete their own user while another active Operations administrator
  remains; their sessions end as any other's.
- **Pending users.** A `PENDING` user (invitation sent, never used) can be deleted; the invitation link
  stops working.
- **Keycloak down.** 503 `IDENTITY_PROVIDER_UNAVAILABLE` and nothing is deleted.
- **What stays after the deletion.** The audit rows of the user, and those where the user is the actor
  (by username). Users the deleted user created show the username as "created by", as for any creator
  that is not in `app_user` (SCRUM-53).
- **Users left `DISABLED` by the first version.** The migration deletes them from `app_user` with their
  `DELETE` audit rows (actor `system`). Their Keycloak accounts stay disabled and must be deleted by hand
  in the Keycloak console before the same email can be used again (decided 2026-11-27).

## Scope

**Included**

- `DELETE /api/v1/access/users/{id}` (CA-01, CA-02, CA-04 to CA-06, CA-09), with OpenAPI and Spanish
  messages.
- Removing the deactivation, the reactivation, the scheduled deletion, the `DISABLED` state and
  `disabled_at`, the `ACCOUNT_DELETION_DUE` and `ACCOUNT_DISABLED` errors and the `app.access.*`
  settings.

**Out of scope**

- The frontend (SCRUM-232).
- Deleting employee records.
- Notifying the user by email that their user was deleted.

## Open questions

None. Resolved on 2026-11-27:

1. **Migration:** a new `V14` (V13 has already run), not an edit of V13.
2. **Users already `DISABLED`:** deleted by the migration.
3. **Version:** required, as a query parameter.
4. **PR size:** under 400 changed lines each, deletions included, so the removal goes in several PRs.
