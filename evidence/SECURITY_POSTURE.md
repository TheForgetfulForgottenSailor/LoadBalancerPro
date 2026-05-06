# LoadBalancerPro Security Posture

This document summarizes the current security and safety posture for the portfolio release evidence set. It is evidence for the lab implementation, not a claim of complete production security.

## Auth/RBAC Posture

- Local/default mode remains convenient for demos and does not require an API key.
- Prod and cloud-sandbox API-key mode protect mutation endpoints and LASE shadow observability while keeping `/api/health` public.
- OAuth2 mode is explicit opt-in, validates JWTs through Spring Security resource-server support, fails startup without issuer or JWK configuration, gates Swagger/OpenAPI by default, and applies role checks for observer/operator routes.
- CORS preflight supports the documented browser flow, including `Authorization`, without bypassing protected routes.

## CSRF Posture

CodeQL flags the current Spring Security configuration with `java/spring-disabled-csrf-protection`. The current disposition is accepted for the stateless JSON API design, not a claim that CSRF is irrelevant for every future deployment.

- CSRF is intentionally disabled for the current stateless API design.
- The app does not use browser session or form-login authentication.
- Spring Security is configured with `SessionCreationPolicy.STATELESS`, and HTTP Basic, form login, and logout are disabled.
- CORS uses `allowCredentials(false)`.
- Protected mutating routes require `X-API-Key` in prod/cloud-sandbox API-key mode or OAuth2 bearer JWT roles in OAuth2 mode.
- Enabling CSRF would require CSRF token plumbing and could break legitimate header-auth API clients without a meaningful benefit under the current no-cookie auth model.

Revisit this disposition if cookie/session authentication, credentialed CORS, or browser ambient-credential flows are introduced.

## Telemetry Posture

- OTLP metrics export is disabled by default.
- Prod and cloud-sandbox keep Prometheus endpoint exposure disabled by default.
- OTLP opt-in requires an explicit endpoint and startup guardrails reject blank, malformed, credential-bearing, query-bearing, fragment-bearing, and public endpoints unless the private-endpoint requirement is deliberately overridden.
- Startup telemetry summaries are sanitized to host-level OTLP endpoint detail only.

## Cloud Safety Posture

- Cloud mutation is dry-run/no-op by default.
- Live mutation requires explicit live mode, operator intent, account/region guardrails, capacity caps, and mutation-specific guardrails.
- Cloud-sandbox defaults are constrained and use the documented `lbp-sandbox-` resource-name prefix.
- Default tests use mocked AWS clients and are not expected to create, modify, or delete AWS resources.

## Replay/Evaluation Posture

- Replay and evaluation flows are offline/read-only lab paths.
- Replay reports summarize shadow recommendations and do not promote recommendations into live cloud mutation.
- Malformed replay inputs fail safely and avoid exposing raw replay content in errors.

## LASE Shadow Posture

- LASE remains advisory/shadow-only.
- Shadow recommendations do not alter allocation responses or execute cloud mutation paths.
- Shadow failure reasons are sanitized and redacted before stored observability output and related warning logs.
- Fail-safe events keep useful context where possible while redacting token/API-key/bearer-shaped values.

## Input/API Hardening Posture

- API validation returns structured JSON error envelopes for malformed JSON, validation failures, oversized requests, and authentication/authorization failures.
- Request-size filtering is covered by tests and is ordered behind authentication for protected mutation paths in hardened auth modes.
- CSV/JSON import paths validate schema shape, reject dangerous or malformed input, and neutralize spreadsheet formula injection risk.

## Static Analysis Posture

- A separate CodeQL workflow provides Java/Kotlin static-analysis coverage with manual Maven build mode.
- CodeQL is treated as a SAST baseline, not a complete security review, independent audit, or production-readiness claim.
- Initial CodeQL findings should be reviewed and triaged before SAST is treated as a mature release blocker.
- Findings involving CloudManager/AWS guardrails, auth, request validation, deserialization, file parsing, command execution, or telemetry redaction should receive priority review.

## Release Provenance Posture

- A separate tag-triggered Release Artifacts workflow builds the executable JAR and CycloneDX SBOM JSON/XML for semantic version tags.
- The workflow fails before upload if the Git tag version and Maven project version do not match.
- Release artifacts are uploaded as GitHub Actions artifacts with deterministic names and a SHA-256 checksum file.
- Checksums support downloaded artifact integrity checks against the uploaded checksum file, but they do not prove builder identity or replace signatures or attestations.
- GitHub artifact attestations provide build provenance evidence for the release JAR and the JAR/SBOM JSON relationship.
- Attestations help consumers verify where and how the attested artifact was built, but they are not PGP signing, notarization, vulnerability scanning, or production-readiness proof.
- This is release evidence, not GitHub Release asset publication, release signing beyond GitHub artifact attestations, container signing, or a production-readiness claim.

## What Is Verified By Tests

- API-key protection and OAuth2 route policy.
- JWT role extraction for common role claim shapes.
- Swagger/OpenAPI gating in OAuth2 mode.
- CORS preflight behavior for configured origins and headers.
- OTLP disabled-by-default behavior and endpoint safety guardrails.
- Cloud dry-run defaults, mutation gates, and mocked AWS isolation.
- LASE shadow-only behavior, failure redaction, and fail-safe observability.
- Replay isolation and safe malformed-input handling.
- Safe API error envelopes for core malformed, invalid, and oversized request paths.

## What Remains Deployment Responsibility

- TLS termination and certificate policy.
- IAM, network segmentation, firewall rules, and private collector enforcement.
- External rate limiting and abuse protection.
- Secret storage, rotation, and incident response.
- Production log retention, access controls, and monitoring.
- Real AWS sandbox validation in disposable infrastructure.
