# Production Auth Hardening Plan

## Baseline

- Version: v2.4.2
- Baseline branch: loadbalancerpro-clean
- Baseline HEAD: 1a63999
- Purpose: document current auth behavior, guardrails, and future hardening before changing route security semantics

## 1. Current Auth Model

- The local/default profile is demo-friendly.
- `ProdApiKeyFilter` is inactive in the local/default profile.
- In `prod` and `cloud-sandbox` API-key mode, `ProdApiKeyFilter` protects `POST`/`PUT`/`PATCH /api/**` and `GET /api/lase/**`.
- OAuth2/JWT mode activates Spring Security resource-server behavior.
- OAuth2/JWT mode requires bearer tokens and route-appropriate roles.
- OAuth2/JWT mode fails startup if both issuer URI and JWK set URI are blank.
- Secrets are not logged.
- Missing API key configuration logs only generic warnings.

## 2. Endpoint Access Matrix

| Endpoint | Local/default | Prod/cloud-sandbox API-key mode | OAuth2/JWT mode | Notes |
| --- | --- | --- | --- | --- |
| `GET /api/health` | Public | Public | Public | Health remains intentionally available for basic readiness checks. |
| `GET /api/lase/shadow` | Public/demo-accessible | Requires `X-API-Key` | Requires `observer` or `operator` role | LASE shadow output is recommendation/evidence oriented. |
| `POST /api/allocate/capacity-aware` | Public/demo-accessible | Requires `X-API-Key` | Requires `operator` role | Calculation-only allocation endpoint. |
| `POST /api/allocate/predictive` | Public/demo-accessible | Requires `X-API-Key` | Requires `operator` role | Calculation-only predictive allocation endpoint. |
| `POST /api/routing/compare` | Public/demo-accessible | Requires `X-API-Key` | Requires `operator` role | Request-level strategy comparison endpoint. |
| `/v3/api-docs` and `/swagger-ui.html` | Public | Public by current API-key-mode behavior; prod tests explicitly cover `/v3/api-docs` availability | Gated by default unless `loadbalancerpro.auth.docs-public=true` | API-key docs privacy changes need focused tests. |
| Actuator health/info | Exposed | Exposed by profile configuration | Requires read role for configured Spring Security paths | Keep network exposure private outside demos. |
| Actuator metrics/prometheus | Exposed in local/default | Not exposed by prod/cloud-sandbox profile defaults | Not exposed by prod/cloud-sandbox profile defaults unless profile config changes | Metrics can reveal operational details. |

## 3. Existing Test Coverage

- API-key prod protection.
- Wrong-key rejection.
- No key leakage in error responses.
- Missing-key fail-closed behavior.
- Cloud-sandbox missing-key fail-closed behavior.
- Protected mutation, routing, and LASE behavior.
- API-key mode aliases: `API_KEY` and `api_key`.
- OAuth2 missing and invalid token handling.
- OAuth2 role gates.
- OAuth2 docs gating.
- OAuth2 `loadbalancerpro.auth.docs-public=true` behavior with mocked JWT authorization configuration.
- OAuth2 custom `loadbalancerpro.auth.required-role.*` override behavior.
- PR #24 added test-only coverage for unauthenticated OpenAPI/Swagger access when `docs-public=true`, confirmed `docs-public=true` does not open protected mutation/allocation routes, and covered custom role overrides for `/api/lase/shadow` and allocation endpoints.
- CORS behavior.
- Request-size ordering.
- Prod/local actuator exposure.
- CORS profile behavior.

Note: `ProdApiKeySnakeCaseModeAliasProtectionTest` exists as a class inside `ProdApiKeyModeAliasProtectionTest.java`, not as a separate file.

## 4. Current Guardrails

- `GET /api/health` remains public.
- `prod` and `cloud-sandbox` fail closed when required API key configuration is missing.
- API keys, JWTs, bearer tokens, and secrets must never be logged.
- Actuator exposure is profile-controlled.
- Cloud mutation safety is separate from auth and must remain independently guarded.
- LASE shadow endpoints are recommendation/evidence oriented, not direct production mutation.

## 5. Known Gaps

Low-risk:

- README/security docs can be aligned after this plan.
- Additional negative-path/security regression tests can be added where new auth settings, error paths, or profile combinations become important.

Medium-risk:

- Making API-key mode Swagger/OpenAPI private by default.
- Changing DTO/schema/auth docs if tests currently expect public docs.
- Stronger deployment identity integration beyond demo API-key and JWT role checks.
- Trusted proxy or edge enforcement guidance for production-like deployments.

High-risk:

- Changing route auth semantics.
- Changing actuator auth behavior.
- Merging API-key and OAuth2 enforcement paths without design.
- Changing cloud mutation/auth coupling without separate guardrails.
- Claiming production-grade authorization without deployment-specific identity, secret rotation, edge controls, and operational policy.

## 6. Recommended Hardening Sequence

1. Keep this plan as the source of truth.
2. Keep PR #24 OAuth2 `docs-public=true` and custom-role override tests as characterization coverage before changing auth semantics.
3. Align README/security docs with actual behavior when auth behavior or operator guidance changes.
4. Only then consider OpenAPI docs/privacy changes.
5. Add deployment-specific secret management and rotation guidance before any production-readiness claim.
6. Add trusted proxy, edge enforcement, and stronger deployment identity integration only with focused design and tests.
7. Do not change route behavior without focused tests.
8. Keep cloud mutation guardrails independent from auth.

## 7. Do Not Do Yet

- Do not make API-key Swagger docs private by default without a focused PR.
- Do not change actuator access behavior casually.
- Do not alter OAuth2 role semantics without tests.
- Do not log secrets, tokens, JWTs, or API keys.
- Do not claim production-grade auth is complete without deployment-specific identity, secret rotation, trusted edge enforcement, and operator controls.
- Do not weaken fail-closed behavior.
- Do not merge API-key and OAuth2 paths casually.
- Do not treat auth as the only cloud safety gate.
