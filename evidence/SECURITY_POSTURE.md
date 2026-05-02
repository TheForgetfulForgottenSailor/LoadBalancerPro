# LoadBalancerPro Security Posture

This document summarizes the current security and safety posture for the portfolio release evidence set. It is evidence for the lab implementation, not a claim of complete production security.

## Auth/RBAC Posture

- Local/default mode remains convenient for demos and does not require an API key.
- Prod and cloud-sandbox API-key mode protect mutation endpoints and LASE shadow observability while keeping `/api/health` public.
- OAuth2 mode is explicit opt-in, validates JWTs through Spring Security resource-server support, fails startup without issuer or JWK configuration, gates Swagger/OpenAPI by default, and applies role checks for observer/operator routes.
- CORS preflight supports the documented browser flow, including `Authorization`, without bypassing protected routes.

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
