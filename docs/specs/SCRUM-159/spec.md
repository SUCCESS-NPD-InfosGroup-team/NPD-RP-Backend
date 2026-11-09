# SCRUM-159 — Access token keeps working after logout (BUG-11)

Jira: [SCRUM-159](https://infosgroup-sucess.atlassian.net/browse/SCRUM-159) · Related story: SCRUM-54
HU-05 "Log out" (task SCRUM-68) · Source: system test report of Oct 31, 2026, case SYS-O02.

## Problem

A user signs in, copies the access token, and logs out. The frontend ends the session in Keycloak, but
the API keeps accepting the copied token until it expires. Anyone holding that token can still read and
change data after the user has left.

## Expected behavior

HU-05 CA-02: after logout, the old token is rejected.

## Acceptance criteria

| ID | Criterion | What the backend guarantees |
|----|-----------|-----------------------------|
| CA-01 | Token rejected after logout | A request with an access token whose Keycloak session has ended (logout, or session removed by an administrator) answers 401, from the first request after the logout. |
| CA-02 | Valid tokens keep working | A request with the token of a user who is still signed in works as before, with the same roles and permissions. |
| CA-03 | Identity provider unavailable | If the API cannot check the token because Keycloak does not answer, the request is refused (never accepted unchecked) with a specific error that tells it apart from an expired session. |
| CA-04 | Short-lived tokens | The access tokens of the realm last 5 minutes or less, so a token that escapes every other check is still useless soon. |

## Scope

**Included**

- Checking on every authenticated request that the token's session is still active in Keycloak.
- The answer when Keycloak cannot be reached.
- The access token lifespan of the realm `npd` in each environment (Keycloak setting, not code).

**Out of scope**

- The frontend logout itself: it already ends the Keycloak session (checked by the developer).
- Refresh tokens: Keycloak already invalidates them on logout.
- Back-channel logout (Keycloak notifying the API) and any token blocklist stored by the API.
- Revoking the tokens of a disabled or deleted user other than through their session (no user disabling
  exists yet).

## Open questions

None.
