# SCRUM-93 — Create user account with role (HU-02)

Jira: [SCRUM-93](https://infosgroup-sucess.atlassian.net/browse/SCRUM-93) · Epic: SCRUM-7 Users management ·
Backend subtask: SCRUM-146 "Backend - Get and Post" · Frontend subtask: SCRUM-147 (done)

## User story

As an **Operations administrator**, I want to **create a user account together with its role**, so
that **each new platform user gets controlled access from day one**.

## Acceptance criteria

What the backend guarantees for each criterion:

| ID | Criterion | What the backend guarantees |
|----|-----------|-----------------------------|
| CA-01 | Account created with its role | A new account exists in the identity provider with exactly the chosen role, and the person receives an email to set their password. |
| CA-02 | Role is mandatory | A request without a role is rejected and nothing is created. |
| CA-03 | Only the four defined roles | The role must be one of: Operations administrator, NPD lead, Customer Success, Guest. Any other value is rejected. |
| CA-04 | Required fields and format | Full name and email are mandatory; the email must have a valid format. All the errors of the form are returned together. |
| CA-05 | Duplicate email | An email that already has an account is rejected with a specific error, and nothing is created. |
| CA-06 | Only Operations administrators | Only users with the Operations administrator role can manage users: create accounts, see the user list and the roles. Anyone else is refused. |
| CA-07 | Identity provider unavailable | If the identity provider cannot be reached, the request fails with a specific error, nothing is left half-created, and it can be retried. |
| CA-08 | Creation is audited | Who created the account, when, and with which values is recorded in the audit log. |
| CA-09 | User list | Operations administrators see the users, page by page, with four columns: **user** (full name and email), **role**, **state** and **last access**. No filters. |
| CA-10 | Create in a side panel with role cards | Frontend criterion. The backend provides the list of roles, with name and description, to draw the cards. |

## Agreed details

- The form sends **full name**, **email** and **role**, all mandatory. The email is also the username.
- The person sets their own password: the identity provider emails them a link, **valid for 48 hours**.
  After setting the password they land on the **resources page** (`/recursos`) of the frontend, where
  they sign in.
- **State** shows whether the person has already started using the account: *pending* (invitation sent,
  never signed in) or *active*.
- **Last access** is the last time the person used the platform.
- **Everything related to user management is only for Operations administrators.**

## Scope

**Included**

- Create an account with its role, and send the invitation email.
- List the users created through the platform, with their role, state and last access.
- List the four roles with their names and descriptions.
- Errors for each failure in the acceptance criteria, in Spanish for the frontend.

**Out of scope**

- Editing, disabling or deleting users, and changing their role.
- Resending an expired invitation, and password resets (done by the identity provider's own pages).
- Users created directly in the identity provider: they do not appear in the list (there is no
  synchronization).
- More than one role per user.
- Filters or search in the user list.
- The side panel and role cards themselves (frontend, SCRUM-147).
