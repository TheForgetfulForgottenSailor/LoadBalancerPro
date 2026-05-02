# LoadBalancerPro Residual Risks

These risks are intentionally documented so the project does not overclaim production readiness.

## Known Residual Risks

- Primitive DTO numeric fields can default to `0.0` when omitted from JSON input. This is a future DTO hardening decision, not currently treated as fixed.
- Real AWS validation is outside the default CI path. The Maven suite uses mocks and does not prove behavior in a live AWS account.
- Redaction is pattern-based, not full data-loss-prevention. It redacts common token/API-key/bearer-shaped values but cannot guarantee removal of every possible secret form.
- OTLP private endpoint validation is heuristic. It checks obvious private/local host patterns but does not prove collector trust, network reachability, TLS trust, IAM policy, or egress enforcement.
- Production TLS, IAM, firewalling, external rate limiting, secret rotation, deployment identity, log retention, and collector access controls remain deployment responsibilities.

## Operating Guidance

- Treat prod/cloud-sandbox profiles as hardened lab baselines, not complete production deployments.
- Keep real cloud validation in disposable sandbox accounts with explicit guardrails.
- Keep telemetry collectors private and authenticated by deployment infrastructure.
- Keep secrets out of source control, README examples, command history, and logs.
- Revisit primitive DTO numeric fields before exposing the API to untrusted callers.
