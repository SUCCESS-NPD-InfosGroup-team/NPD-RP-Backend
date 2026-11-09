# SCRUM-159 — PR plan

PRs in merge order, each under 400 changed lines with its own tests. What and why: [spec.md](spec.md).
How: [design.md](design.md).

| # | Branch | Status |
|---|--------|--------|
| 0 | `docs/SCRUM-159-token-revocation-spec` | This PR |
| 1 | `fix/SCRUM-159-introspection-validator` | Pending |
| 2 | `fix/SCRUM-159-identity-provider-unavailable` | Pending |

## 0. Spec, design and plan

This folder. 181 lines.

## 1. Introspection validator — part 1 of 2

- `KeycloakAdminProperties.introspectionUri()`.
- `KeycloakIntrospectionConfig` (its `RestClient` with Basic credentials and the timeouts) and
  `KeycloakSessionValidator` (design §3). Keycloak unreachable fails with `temporarily_unavailable`,
  which answers 401 until PR 2.
- Tests: validator with `MockRestServiceServer` for each answer and error; the new URI; the wiring test
  with the auto-configured decoder (design §5).
- ~320 lines. Needs Keycloak check 2 before it is deployed (design §7).

## 2. 503 when Keycloak cannot be reached — part 2 of 2

- `ProblemDetailsSecurityHandler.commence`: 503 `IDENTITY_PROVIDER_UNAVAILABLE` when the cause is the
  `temporarily_unavailable` validation error, 401 otherwise (design §4).
- Tests of both answers.
- ~80 lines. Depends on 1.
