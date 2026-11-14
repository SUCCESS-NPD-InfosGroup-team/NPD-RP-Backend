# SCRUM-159 — Design

How the API stops accepting access tokens after logout. What and why: [spec.md](spec.md). PRs:
[plan.md](plan.md).

## 1. Cause

The API is an OAuth2 resource server (`spring-boot-starter-security-oauth2-resource-server`). Because
`spring.security.oauth2.resourceserver.jwt.issuer-uri` is set, Spring Boot's `JwtDecoderConfiguration`
builds a `NimbusJwtDecoder`: it downloads the realm's public keys once (`jwks_uri` from the issuer's
OpenID configuration) and then checks each token **locally**: signature, `exp`/`nbf` (60 s of clock
skew) and `iss`. `SecurityConfig` uses that decoder through `.oauth2ResourceServer(o -> o.jwt(...))`.

Logout ends the session inside Keycloak only. Nothing in the token changes, and the decoder never asks
Keycloak, so the token stays valid for the API until `exp`. No property of the decoder changes this: a
JWT is self-contained, and knowing about a logout needs a call to Keycloak (or a call from it).

## 2. Decision: how the API learns that the session ended

**Chosen: keep the JWT validation and add a token introspection check (C), plus a short token
lifespan.**

| Option | Why not / why |
|--------|---------------|
| A. Short access token lifespan only | No code, but the token still works for up to the lifespan after logout: CA-01 is not met. Kept as a second barrier (CA-04). |
| B. Opaque token introspection instead of JWT (`.opaqueToken()`) | Also calls Keycloak per request, and replaces the `Jwt` principal everywhere: the role converter of `SecurityConfig`, `LastAccessInterceptor` (reads `JwtAuthenticationToken`), `CurrentUser` and `TestSecurityConfiguration` would all change. |
| **C. JWT + introspection validator** | **Chosen.** An `OAuth2TokenValidator<Jwt>` asks Keycloak's introspection endpoint whether the token is still active. The signature is first checked locally as today, so forged tokens never reach Keycloak. The principal, roles, last access and test setup stay the same. Cost: one call to Keycloak per authenticated request. |
| D. Back-channel logout | Keycloak posts a logout token to the API, which stores the ended sessions and rejects their tokens. No call per request, but a new public endpoint, logout-token validation, storage of the ended sessions (in the DB to survive restarts) and a Keycloak setting per client. Much more code for the same result. |

### No cache of the introspection answers

Each request asks Keycloak again. A cache (token → active for N seconds) would save calls but let a
logged-out token work for N seconds, which CA-01 does not allow, and the API runs in the same network
as Keycloak. To revisit only if the latency becomes a problem.

## 3. The validator

```
Authorization: Bearer <jwt>
  → NimbusJwtDecoder: signature                         (as today; fails → 401, Keycloak not asked)
  → validators, all of them run and their errors add up:
       exp/nbf, iss                                       (as today; fail → 401)
       KeycloakSessionValidator: POST .../token/introspect (new)
       active: true        → request goes on
       active: false       → 401 UNAUTHORIZED
       Keycloak unreachable, timeout, 5xx, or our client rejected
                           → 503 SESSION_CHECK_UNAVAILABLE
```

- **Where:** `access/client/KeycloakSessionValidator`, a bean registered in a new
  `access/client/KeycloakIntrospectionConfig`. It is an `OAuth2TokenValidator<Jwt>` bean, and Spring
  Boot's `JwtDecoderConfiguration` adds every such bean to the auto-configured decoder (constructor
  argument `ObjectProvider<OAuth2TokenValidator<Jwt>> additionalValidators`, checked in Boot 4.1.1), so
  **`SecurityConfig` does not change** and `shared` does not depend on `access`.
- **Call:** `POST {keycloak}/realms/{realm}/protocol/openid-connect/token/introspect`, form body
  `token=<jwt>&token_type_hint=access_token`, HTTP Basic with the existing service account
  (`npd-rp-api-admin` and its secret). Only the `active` field of the answer is read.
- **URL:** `KeycloakAdminProperties.introspectionUri()`, built like `tokenUri()`: from
  `KEYCLOAK_ADMIN_URL` when set (`http://keycloak:8080` in the infra compose), otherwise from the issuer.
  No new environment variable.
- **HTTP:** its own `RestClient`, without the OAuth2 interceptor of the admin client (introspection
  authenticates with the client's id and secret, not with a bearer token), with the same timeouts
  (`connect-timeout=2s`, `read-timeout=5s`).
- **Logging:** a failed call logs a warning with the exception class only. The token is never logged.

## 4. Decision: answer when Keycloak cannot be reached

**Chosen: refuse the request with 503 `SESSION_CHECK_UNAVAILABLE` (fail closed).**

| Option | Why not / why |
|--------|---------------|
| Accept the token unchecked (fail open) | A Keycloak outage would bring back the bug (CA-03). |
| 401 | Simple, but the frontend treats 401 as "session ended" and sends the user to the login page during a short Keycloak hiccup, and the user cannot tell an outage from a logout. |
| 503 `IDENTITY_PROVIDER_UNAVAILABLE` | The code the user creation answers when Keycloak is down, but its Spanish text says the account was not created, which is wrong on any other request. |
| **503 `SESSION_CHECK_UNAVAILABLE` (new)** | **Chosen.** Its own text ("no se pudo verificar tu sesión… intenta de nuevo"); the frontend can show "try again" and keep the session. Changed during PR 2: the first version of this design reused `IDENTITY_PROVIDER_UNAVAILABLE`. |

How it reaches the response: the validator fails with an `OAuth2Error` whose code is
`temporarily_unavailable` (an OAuth2 code, not one of ours). Spring Security wraps the decoder's
`JwtValidationException` in an `InvalidBearerTokenException` and calls the authentication entry point.
`shared/security/ProblemDetailsSecurityHandler.commence` looks for that error in the cause and answers
503 `SESSION_CHECK_UNAVAILABLE` instead of 401, but only when it is the **only** error: the
validators do not stop at the first failure, so an expired token is also sent to Keycloak, and while
Keycloak is down it must still answer 401. It only uses Spring Security types and the shared
`ErrorCode`, so `shared` still does not depend on `access`.

Every secured endpoint can now answer this 503. `shared/config/SessionCheckOpenApiConfig` adds it to
every operation of the OpenAPI specification that documents a 401 (or extends the description of its
existing 503), instead of repeating it on each controller method.

## 5. Tests

- `KeycloakSessionValidator` with `MockRestServiceServer`: the request (URL, Basic credentials, form
  body); `active: true` → success; `active: false` → `invalid_token`; 5xx, timeout, connection refused
  and 401 from Keycloak → `temporarily_unavailable`.
- `KeycloakAdminProperties.introspectionUri()`: from the issuer and from the admin URL.
- `ProblemDetailsSecurityHandler`: 503 when `temporarily_unavailable` is the only error in the cause;
  401 when it comes with another error (expired token) and in every other case.
- Wiring: with `WebApplicationContextRunner`, the decoder Spring Boot builds (RSA public key from a test
  file) refuses a correctly signed, unexpired token with `temporarily_unavailable` when Keycloak is on a
  closed port. This guards the reliance on Boot adding validator beans.
- The `@SpringBootTest` tests keep `TestSecurityConfiguration`, which replaces the whole `JwtDecoder`,
  so they never call Keycloak.

## 6. Settings and environment variables

No new variables. The validator uses the existing `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_ADMIN_URL`,
`KEYCLOAK_ADMIN_CLIENT_ID`, `KEYCLOAK_ADMIN_CLIENT_SECRET` and the timeouts of `app.keycloak.*`
(see [SCRUM-93 design §7](../SCRUM-93/design.md#7-settings-and-environment-variables)).

## 7. Changes outside the repository

In the Keycloak realm `npd` of **each environment** (local and production):

| # | Where | Change | Needed by |
|---|-------|--------|-----------|
| 1 | Realm settings → Tokens → Access Token Lifespan | 5 minutes or less (CA-04). The frontend already refreshes the token. **Done:** already 5 minutes locally and in production (checked Nov 8, 2026). | Any time; independent of the code |
| 2 | Client `npd-rp-api-admin` | Nothing to change: a confidential client can introspect the realm's tokens. Check before deploying: `curl -u npd-rp-api-admin:<secret> -d token=<access token> {keycloak}/realms/npd/protocol/openid-connect/token/introspect` answers `"active": true`, and `"active": false` after logging out. | PR 1 |
