# LoadBalancerPro Hardening Audit

Audit date: 2026-05-01

Branch: `codex/project-hardening-audit-event`

Test command: `mvn -q test`

Latest branch result: passed on 2026-05-01.

## Scope

This hardening event reviewed LoadBalancerPro as an enterprise control-plane/SRE lab preparing for a portfolio release. The guiding principle was: shadow first, replay first, prove first, execute last.

The audit intentionally avoided broad refactors, new product features, auth weakening, telemetry weakening, cloud guardrail weakening, and cloud mutation logic changes. The only cloud-adjacent code change was a GUI configuration pass-through for an already documented safety guardrail.

## Areas Checked

- Security/Auth: API-key mode, OAuth2 mode, RBAC route protection, Swagger/OpenAPI gating, actuator exposure, CORS preflight, request-size filtering, and sensitive logging.
- Telemetry/Observability: OTLP disabled-by-default behavior, private endpoint guardrails, sanitized startup summary, Prometheus exposure by profile, and documentation warnings.
- Cloud Safety: dry-run defaults, AWS destructive-operation gates, cloud-sandbox defaults, placeholder credential failure behavior, delete/scale/mutate guardrails, and mocked AWS boundaries.
- Replay/Evaluation Lab: offline replay behavior, deterministic replay/report surfaces, line-length limits, malformed replay handling, and no live cloud execution.
- LASE Shadow Advisor: shadow-only behavior, fail-safe recording, no CloudManager construction, and allocation-response isolation.
- Input Hardening: CSV/JSON parsing, validation envelopes, malformed request bodies, oversized requests, invalid server inputs, and CSV injection handling.
- Dependency/Profile Hygiene: Maven dependency shape, profile-specific actuator/telemetry exposure, Docker runtime posture, and CI gates.
- Documentation Truthfulness: README claims against implementation and test evidence.

## Risks Found

- LASE shadow fail-safe paths sanitized control characters but did not redact token/API-key-shaped exception text before logging or storing shadow observability failure reasons.
- Telemetry endpoint validation rejected malformed and fragment-containing OTLP endpoints in code, but those cases did not have isolated regression tests.
- CORS allowed methods did not fully match the protected mutation policy: auth and request-size rules cover `POST`, `PUT`, and `PATCH`, while CORS only allowed `POST` mutations.
- GUI cloud setup did not pass `CLOUD_RESOURCE_NAME_PREFIX` into `CloudConfig`, even though the CLI and docs support the sandbox resource-name prefix guardrail.
- README used an exact test-count claim that can become stale as regression tests are added.

## Risks Fixed

- LASE shadow failure reasons now use a single redaction path before both warning logs and stored shadow observability output.
- Telemetry guardrail tests now prove malformed endpoint rejection and fragment endpoint rejection.
- CORS now allows `GET`, `POST`, `PUT`, `PATCH`, and `OPTIONS` for configured `/api/**` origins, matching the protected mutation policy.
- GUI cloud setup now passes `CLOUD_RESOURCE_NAME_PREFIX` to `CloudConfig`.
- README no longer depends on a brittle exact test count.

## Residual Risks

- The prod profile remains a production-like baseline, not a complete production IAM, TLS, proxy, rate-limit, or secret-rotation system.
- OTLP private endpoint validation is a lab-grade heuristic. It does not prove collector security, TLS trust, network reachability, IAM policy, firewall posture, or egress-policy enforcement.
- Live AWS validation is intentionally outside the default Maven lifecycle and must only be run in disposable, explicitly guarded sandbox infrastructure.
- GUI cloud config pass-through is small and inspectable, but this branch does not add a JavaFX GUI unit test harness.
- Dependency upgrades and vulnerability triage are reported through CI/dependency review and are not broadened in this audit branch.

## Verified By Tests

- API-key mode protects prod/cloud-sandbox mutation and LASE observability endpoints while keeping `/api/health` public.
- OAuth2 mode fails closed without issuer/JWK config, gates Swagger/OpenAPI by default, maps common JWT role claim shapes, returns 401 for missing/invalid tokens, returns 403 for insufficient roles, and authenticates before request-size rejection.
- Prod and cloud-sandbox profiles keep Prometheus and OTLP metrics disabled by default and expose only actuator health/info.
- OTLP metrics export is disabled by default, does not require a collector when disabled, requires explicit opt-in, rejects unsafe endpoints, and logs only sanitized startup summaries.
- CloudManager defaults to dry-run, avoids AWS client construction in dry-run mode, denies unsafe scale/update/delete paths, requires operator intent and guardrails for live mutation, and verifies ASG ownership before deletion.
- Replay mode is offline/read-only, handles malformed inputs safely, enforces maximum line length, and avoids raw replay content in parse errors.
- LASE shadow advisor is disabled by default, is advisory when enabled, does not construct CloudManager, preserves allocation responses, and records fail-safe events.
- API input handling returns structured safe error envelopes for validation failures, malformed JSON, and oversized requests.
- CSV/JSON import/export tests cover malformed rows, unexpected fields, non-finite values, CSV injection, schema rejection, and round-trip behavior.

## Documented But Not Technically Enforced

- Production TLS termination, reverse-proxy hardening, external rate limiting, firewall policy, IAM policy, secret rotation, log retention, and collector trust are deployment responsibilities.
- `/actuator/health` and `/actuator/info` should be private-network or deployment-gated outside local validation, but the app profile itself intentionally leaves them reachable.
- OpenAPI docs remain public in current API-key prod mode for portfolio review; OAuth2 mode gates docs by default.
- Live AWS validation requires a controlled sandbox and disposable resources; the default test suite uses mocks and does not prove behavior against a real AWS account.
- Telemetry credentials and collector authentication must be supplied by deployment secret management, not README examples or source-controlled properties.
