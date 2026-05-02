# LoadBalancerPro Test Evidence

Default verification command:

```bash
mvn -q test
```

## OAuth/API-Key Tests

- Prod and cloud-sandbox API-key mode reject missing or wrong API keys and allow correct keys for protected endpoints.
- OAuth2 mode rejects missing or invalid bearer tokens with 401 and valid tokens with insufficient role with 403.
- OAuth2 mode allows observer access to LASE shadow observability and operator access to allocation mutations.
- OAuth2 mode gates Swagger/OpenAPI by default while keeping `/api/health` public.
- CORS preflight coverage verifies configured origins and auth-related headers.

## Telemetry Guardrail Tests

- OTLP metrics export is disabled by default.
- Prod and cloud-sandbox profiles keep OTLP disabled and Prometheus endpoint exposure disabled by default.
- OTLP startup validation covers missing, malformed, unsafe, credential-bearing, query-bearing, and fragment-bearing endpoint shapes.
- Startup summaries are sanitized and do not include credentials, query strings, fragments, bearer tokens, API keys, or auth headers.

## LASE Redaction/Shadow Tests

- LASE shadow mode remains advisory and does not construct or call `CloudManager`.
- Shadow recommendations do not alter allocation responses.
- Evaluator failures are captured as fail-safe shadow events.
- Sensitive-looking failure text is redacted while useful non-sensitive context is preserved.
- Control characters are neutralized in stored failure reasons.

## Replay/Cloud Isolation Tests

- Replay mode is offline and does not require AWS credentials.
- Replay input errors fail safely without raw content exposure.
- CloudManager dry-run mode avoids AWS mutation calls.
- Live mutation paths are guarded by operator intent, capacity limits, account/region checks, and ownership checks.
- Cloud-sandbox behavior is constrained by documented sandbox resource-name prefix guardrails.

## Input/API Hardening Tests

- Allocation API validation rejects malformed JSON, invalid server fields, negative load, empty server lists, and oversized request bodies with safe JSON envelopes.
- Safe error envelopes avoid stack traces and exception-class leakage.
- CSV/JSON import tests cover malformed rows, unexpected fields, non-finite values, empty input, trailing JSON data, and CSV formula-injection handling.
- Primitive DTO numeric-field omission remains documented as a residual risk rather than claimed fixed.

## Current Evidence Set

- [Hardening Audit 001](HARDENING_AUDIT_001.md)
- [Security Posture](SECURITY_POSTURE.md)
- [Residual Risks](RESIDUAL_RISKS.md)
