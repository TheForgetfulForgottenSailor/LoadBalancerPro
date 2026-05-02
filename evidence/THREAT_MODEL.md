# LoadBalancerPro Threat Model

Threat model date: 2026-05-01  
Branch: `codex/threat-model`  
Verification command: `mvn -q test`

## Purpose and Scope

This document describes LoadBalancerPro as an enterprise-demo SRE/control-plane lab. It is intended to make the project's security posture, safety boundaries, test evidence, and residual risks explicit. It is not a production certification, penetration test, SOC 2 control mapping, or assurance that a deployment is secure without proper infrastructure controls.

The scope covers the Spring API, API-key and OAuth2 authorization modes, LASE shadow recommendations, replay/evaluation behavior, cloud sandbox and live-mutation gates, telemetry and actuator exposure, CSV/JSON import paths, and README/evidence claims.

The governing principle remains:

> shadow first, replay first, prove first, execute last.

## System Overview

LoadBalancerPro exposes API routes for load-allocation workflows and local/demo experimentation. Local/default behavior is intentionally convenient for lab use. Production-oriented profiles add API-key protection by default, with an opt-in OAuth2 resource-server mode for stronger role-based authorization.

Cloud-facing behavior is guarded by dry-run/no-op defaults, explicit profile and mutation gates, placeholder credential rejection, and cloud-sandbox resource prefix controls. Replay and evaluation paths are intended to operate offline against provided inputs rather than live cloud state. LASE is currently a shadow advisor: it can generate recommendations and observations, but those recommendations are not intended to execute mutations.

Telemetry supports Prometheus/demo behavior locally and guarded OTLP metrics export only when explicitly enabled. Production and cloud-sandbox profiles keep telemetry export and actuator exposure limited by default.

## Assets

| Asset | Why it matters | Protection objective |
| --- | --- | --- |
| API-key and OAuth-protected mutation routes | Allocation routes represent the control-plane mutation surface. | Reject unauthenticated or underprivileged access and keep route policy regression-tested. |
| LASE shadow advisor/recommendation path | Shadow output can influence future operational decisions. | Keep recommendations advisory-only, redact sensitive failure context, and avoid stale failure leakage. |
| Replay/evaluation lab | Replay inputs may model production behavior. | Keep replay isolated from live cloud mutation and deterministic where expected. |
| Cloud sandbox/live mutation gates | Cloud mutation can affect external infrastructure. | Require explicit gates, dry-run defaults, safe prefixes, and non-placeholder credentials. |
| AWS credentials/configuration | Credentials can authorize real infrastructure changes. | Fail closed for placeholders and keep secrets out of logs, docs, and examples. |
| Telemetry/OTLP collector configuration | Telemetry can expose operational metadata. | Disable by default, require explicit opt-in, sanitize startup summaries, and prefer private collectors. |
| Prometheus/actuator exposure | Management endpoints can reveal runtime and service details. | Keep production exposure limited and avoid public exposure of metrics endpoints. |
| CSV/JSON import paths | Imported data can shape routing/evaluation behavior. | Reject malformed or dangerous input safely and avoid stack-trace disclosure. |
| README/evidence claims | Documentation guides deployment and review decisions. | Keep claims tied to tested behavior and clearly identify deployment responsibilities. |

## Trust Boundaries

| Boundary | Risk at the boundary | Expected control |
| --- | --- | --- |
| External API client to Spring API | Untrusted callers may attempt protected mutations or malformed requests. | API-key or OAuth2 auth in hardened modes, validation, safe error envelopes, request-size filtering. |
| Authenticated user/operator to protected routes | Authenticated users may have insufficient role for mutation. | RBAC route checks for observer/operator behavior in OAuth2 mode. |
| App to OAuth issuer/JWK provider | Token validation depends on issuer and key material. | OAuth2 mode fails startup when issuer/JWK configuration is missing; deployment owns issuer trust and key rotation. |
| App to AWS SDK/cloud APIs | Misconfiguration could reach live infrastructure. | Dry-run/no-op defaults, explicit live gates, sandbox prefix checks, placeholder credential rejection. |
| App to OTLP collector | Telemetry may leak service and runtime metadata. | OTLP disabled by default, private-endpoint guardrails, sanitized startup summary. |
| Replay input files to replay engine | Untrusted replay files may be malformed or crafted. | Offline replay behavior, malformed input handling, and no live cloud dependency in replay paths. |
| Local/default profile to prod/cloud-sandbox profile | Demo convenience could be accidentally exposed as production posture. | Profile-specific auth, actuator, telemetry, and cloud-safety defaults. |
| GUI/CLI user input to cloud configuration | Human input could target unsafe cloud resources. | Configuration validation, documented sandbox prefix, and no credential values in documentation examples. |

## Threat Actors

- Unauthenticated external caller probing public API routes.
- Authenticated but underprivileged user attempting mutation.
- Deployment operator who accidentally exposes local/demo behavior or management endpoints.
- Misconfigured OAuth issuer, JWK provider, or token claim mapping.
- Cloud operator or CI job with real AWS credentials and unsafe configuration.
- User supplying malformed replay, CSV, JSON, or GUI/CLI configuration input.
- Telemetry collector or network path that is public, untrusted, or misconfigured.
- Documentation consumer who treats lab evidence as production certification.

## Threat Scenarios

| ID | Scenario | Existing mitigations and evidence | Residual risk or follow-up |
| --- | --- | --- | --- |
| T1 | Unauthorized allocation mutation | Production-oriented API-key mode protects mutation routes, OAuth2 mode requires authenticated roles, and tests cover missing/invalid OAuth tokens and operator access. See `evidence/SECURITY_POSTURE.md`, `evidence/TEST_EVIDENCE.md`, and `evidence/HARDENING_AUDIT_001.md`. | Local/default remains intentionally convenient and must not be exposed as production. |
| T2 | OAuth role confusion or bad claim mapping | OAuth authority extraction recognizes common role shapes including `role`, `roles`, `authorities`, `scope`, `scp`, and `realm_access.roles`; route tests distinguish observer from operator. | Deployment still owns issuer trust, token lifetime, key rotation, and user lifecycle controls. |
| T3 | API-key spelling or configuration alias weakening protection | API-key mode remains the default hardened compatibility mode, and current tests cover default API-key behavior plus an explicit `API_KEY` enum-style spelling. | If `api_key` snake-case spelling is intended as a supported external contract, broader alias regression tests should be added and kept documented. |
| T4 | Swagger/OpenAPI exposure in strong-auth mode | OAuth2 mode gates OpenAPI by default and tests cover `/v3/api-docs` access policy. README/evidence documents public docs as a deployment choice, not an entitlement. | API-key mode may intentionally leave docs public for demo use; production deployments should gate or disable docs when appropriate. |
| T5 | Request-size/auth ordering bypass | OAuth tests prove unauthenticated oversized protected mutation requests return 401 before size rejection, while authenticated oversized requests can receive 413. API-key filter ordering is documented in the hardening evidence. | Keep both auth modes under regression when filter order changes. |
| T6 | Telemetry endpoint leaking operational metadata | OTLP metrics export is disabled by default, startup summary is sanitized, and documentation warns that telemetry can expose service names, route names, errors, latency, host/runtime details, and operational patterns. | Collector trust, network isolation, TLS, IAM, and retention policy remain deployment responsibilities. |
| T7 | Public OTLP collector misconfiguration | OTLP guardrails reject public endpoints by default and require an explicit property override to allow them. Tests cover private IP, localhost, and public endpoint behavior. | Private endpoint detection is heuristic and does not prove the collector is secure. |
| T8 | Replay accidentally touching CloudManager or live cloud paths | Replay/evaluation is documented as offline lab behavior and evidence records replay/cloud isolation checks. | Additional mock-construction tests can further prove no CloudManager construction if replay internals change. |
| T9 | LASE shadow recommendation executing mutation | LASE is documented and tested as shadow/advisory behavior; failure reasons are redacted before stored observability output. | Future promotion from shadow to execution would require a separate guarded design, route policy, and tests. |
| T10 | Cloud-sandbox live mutation using the wrong resource prefix | Cloud-sandbox defaults use the documented `lbp-sandbox-` prefix, GUI pass-through supports that prefix, and live mutation requires explicit configuration. | Custom or incorrect prefixes remain an operator risk unless fixed-prefix enforcement is tested and required for the chosen deployment path. |
| T11 | Placeholder AWS credentials accidentally accepted | Cloud configuration rejects placeholder credential values and evidence records fail-closed behavior for unsafe cloud configuration. | CI evidence does not prove real AWS account IAM policy boundaries or live-account safety. |
| T12 | Malformed CSV/JSON import causing unsafe behavior | Import and replay paths are covered by focused validation tests and safe failure documentation where present. | Very large hostile files, schema evolution, and all third-party parser edge cases remain future hardening areas. |
| T13 | API error envelopes leaking stack traces or secrets | Known API/auth/telemetry/LASE paths use safe envelopes or redaction, and docs warn against logging tokens, API keys, auth headers, and request bodies. | Framework-generated error surfaces such as every possible 404/405/415 variant should be periodically reviewed before hostile exposure. |
| T14 | Documentation overclaiming verified security | Evidence docs separate tested behavior from residual risk and deployment responsibility. This threat model references test evidence instead of claiming production certification. | Documentation must be updated whenever behavior, profiles, or test coverage changes. |

## Existing Mitigations

- API-key mode remains the default production-compatible behavior for protected mutation routes.
- OAuth2 mode is opt-in and fails closed when issuer/JWK configuration is missing.
- OAuth2 role mapping normalizes common JWT claim shapes into route-check authorities.
- `/api/health` remains public while mutation and selected observability routes are protected by mode.
- Swagger/OpenAPI is gated by default in OAuth2 mode.
- CORS preflight supports the `Authorization` header for protected methods.
- Request-size filtering is ordered so protected unauthenticated OAuth mutation requests authenticate before body-size rejection.
- OTLP metrics export is disabled by default, with private-endpoint guardrails when enabled.
- Startup telemetry summaries are sanitized and avoid credentials, query strings, fragments, and token-looking values.
- Cloud mutation defaults favor dry-run/no-op behavior and reject placeholder credentials.
- LASE failures are redacted before stored shadow-observability output.
- Evidence docs explicitly distinguish verified test coverage from deployment responsibilities.

## Tests and Evidence That Support Mitigations

The current evidence set is:

- `evidence/SECURITY_POSTURE.md`: concise posture summary for auth/RBAC, telemetry, cloud safety, replay/evaluation, LASE shadow behavior, and input/API hardening.
- `evidence/TEST_EVIDENCE.md`: maps major safety claims to Maven test coverage.
- `evidence/RESIDUAL_RISKS.md`: lists known residual risks and deployment responsibilities.
- `evidence/HARDENING_AUDIT_001.md`: records the first formal hardening event and verified fixes.

Representative Maven test areas include:

- API-key and OAuth2 authorization behavior, including missing/invalid tokens, role-based allow/deny decisions, health access, Swagger/OpenAPI gating, and auth-before-size behavior.
- Telemetry guardrails for OTLP disabled-by-default behavior, endpoint validation, private/public endpoint policy, and sanitized startup summaries.
- LASE shadow behavior and redaction of sensitive-looking failure messages.
- Replay/evaluation safety and malformed-input handling.
- Cloud guardrails for dry-run/default behavior, placeholder credentials, and sandbox/live mutation configuration.
- Input/API validation for safe envelopes and malformed request behavior in core paths.

Run `mvn -q test` to execute the regression suite.

## Residual Risks

- Primitive numeric DTO fields may default to `0.0` when omitted; this is documented as a future hardening decision rather than silently claimed as fixed.
- Real AWS validation and IAM boundary verification are outside default CI.
- Redaction is pattern-based and is not a full DLP system.
- OTLP private-endpoint validation is heuristic and does not prove collector security.
- Production TLS, IAM, firewall rules, rate limiting, secret rotation, monitoring retention, and incident response remain deployment responsibilities.
- Local/default profile is intentionally convenient and should not be exposed as production.
- API-key alias coverage should be expanded if multiple spellings are documented as supported external behavior.
- Cloud-sandbox prefix safety depends on documented configuration and should be strengthened if untrusted operators can set arbitrary prefixes.
- Not every framework-generated error surface is claimed to have identical project JSON-envelope behavior.

See `evidence/RESIDUAL_RISKS.md` for the standing residual-risk register.

## Out-of-Scope Items

- Production certification, SOC 2, ISO 27001, PCI, HIPAA, or formal compliance mapping.
- Live AWS account penetration testing or destructive mutation testing.
- OAuth provider operations, tenant governance, token lifecycle, and key rotation policy.
- Network-layer controls such as TLS termination, WAF, firewall rules, private routing, and service mesh policy.
- Collector hardening, telemetry retention policy, and SIEM integration.
- Full DLP scanning of logs, metrics, traces, files, or request bodies.
- Tracing export, logs export, and collector container deployment.

## Future Hardening Opportunities

- Add explicit regression coverage for every documented API-key mode spelling, including `api_key` if supported.
- Add broader safe-envelope tests for less-common framework-generated API errors.
- Require or test fixed `lbp-sandbox-` prefix enforcement for cloud-sandbox live mutation if sandbox operators are not fully trusted.
- Add explicit replay tests that mock or block CloudManager construction when replay internals evolve.
- Convert ambiguous primitive numeric DTO fields to nullable validated fields in a separate compatibility-aware change.
- Add dependency/SBOM scanning evidence and document any accepted dependency risks.
- Add a deployment hardening guide covering TLS, IAM, firewalling, rate limiting, secret rotation, OAuth issuer operations, and collector security.
