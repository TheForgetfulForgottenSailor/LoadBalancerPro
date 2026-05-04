# LoadBalancerPro Operations Guide

Date: 2026-05-04

## Purpose And Scope

This guide provides lab and enterprise-demo operations guidance for LoadBalancerPro.

It is not production certification. It does not prove that any deployment is secure, highly available, compliant, or ready for unmanaged production traffic.

Operators remain responsible for environment controls, identity, network security, TLS, secret management, monitoring, incident response, compliance, and any infrastructure-specific deployment process.

## Operating Modes Support Matrix

| Mode | Intended use | Auth expectation | AWS behavior | Actuator/metrics posture | Recommended exposure | Support status |
| --- | --- | --- | --- | --- | --- | --- |
| local/default | Development, CI smoke tests, portfolio demos | Demo-safe auth; local profile may allow convenient access | Live AWS disabled | Local Actuator and metrics visibility | Localhost or private developer network | Supported for demos |
| prod profile | Production-like validation and enterprise demos | Prefer OAuth2; API-key mode is a compatibility baseline | Live AWS remains disabled unless explicitly configured elsewhere | Health/info only by default; metrics should stay private | Behind trusted reverse proxy, ingress, or gateway | Production-like, not production-certified |
| cloud-sandbox profile | Controlled cloud-safety validation | Deliberate auth and operator controls | Dry-run by default; sandbox guardrails required before live mutation | Private, monitored, and restricted | Private sandbox network or trusted edge | Controlled validation only |
| future live sandbox | Separately approved disposable live-cloud exercise | Strong auth, reviewed IAM, and operator approval | Disposable sandbox resources only | Private, audited, alerting enabled | Restricted sandbox edge | Not implemented unless separately approved |

## Startup/Preflight Checklist

Before running or exposing the application, confirm:

- [ ] Spring profile selected.
- [ ] Auth mode configured.
- [ ] API key or OAuth2 configuration present where required.
- [ ] CORS origins reviewed.
- [ ] Actuator exposure reviewed.
- [ ] OTLP and Prometheus exposure reviewed.
- [ ] Cloud live flags reviewed.
- [ ] Secrets externalized outside Git and images.
- [ ] Release artifact, checksum, and attestation reviewed.
- [ ] Rollback target known.

## Health/Readiness Verification

Use the application health endpoints to confirm startup and basic runtime posture:

- `GET /api/health`
- `GET /actuator/health`
- `GET /actuator/info`
- `GET /actuator/health/readiness`, when readiness probes are enabled by the active profile and Spring Boot actuator configuration

Expected posture differs by mode:

- Local/default mode may expose health, info, metrics, and Prometheus endpoints for local demos.
- The `prod` profile should be treated as production-like and should keep actuator exposure private or deployment-authenticated outside demos.
- The `cloud-sandbox` profile should keep actuator and telemetry endpoints private to the sandbox monitoring boundary.

Do not expose actuator endpoints publicly without trusted edge controls, private networking, or deployment-level authentication.

## Metrics And Dashboards

Useful dashboard signals include:

- JVM memory usage.
- Garbage collection activity.
- Thread counts.
- HTTP request rates.
- HTTP 4xx and 5xx rates.
- Allocation request counts.
- Routing comparison request counts.
- LASE shadow fail-safe counts.
- Cloud mutation denial and failure counts.

Prometheus and OTLP outputs can expose service names, routes, error rates, latency, JVM/runtime details, and operational patterns. Route them only to trusted collectors or monitoring planes. Do not make Prometheus scrape endpoints or OTLP collectors public.

## Alerting Examples

Example lab alerts:

- Health endpoint failures.
- Sustained 5xx responses.
- High latency.
- Elevated LASE fail-safe counts.
- Cloud mutation denial spikes.
- Telemetry export failures.
- JVM memory pressure.

These examples are not production SLAs. Production thresholds depend on real traffic, infrastructure, capacity, dependency behavior, and business requirements.

## Incident Response Runbook

For an incident or suspicious behavior:

1. Identify the active profile.
2. Confirm whether live AWS mutation is enabled.
3. Preserve logs, command history, workflow links, release evidence, and relevant configuration snapshots.
4. Disable live mutation if a cloud-safety issue is suspected and the response plan allows it.
5. Rotate API keys, OAuth-related secrets, AWS credentials, or telemetry credentials if exposure is suspected.
6. Review release artifact evidence, including artifact bundle, checksum, and attestation records.
7. Document the timeline, symptoms, decisions, and actions.
8. Avoid destructive actions unless they are explicitly approved in the response plan.

If AWS credentials or live cloud resources are involved, treat the response as a cloud-account incident and follow the account owner's process.

## Rollback Guide

For rollback:

1. Identify the last known good semantic tag.
2. Verify the release artifact, checksum, and attestation for that tag where available.
3. Deploy the previous artifact through the environment's normal mechanism.
4. Confirm `/api/health` and actuator health endpoints.
5. Monitor metrics, logs, and error rates.
6. Do not move tags.
7. Document the rollback reason, target version, verification result, and follow-up action.

Rollback should preserve release history. If a tag-triggered workflow failed historically, document the reason rather than rewriting the tag.

## Dependency/Security Update Workflow

Recommended dependency and security update flow:

- Review Dependabot PRs one at a time.
- Avoid batching risky dependency, plugin, action, or image updates.
- Run tests and package verification.
- Review CI, CodeQL, Trivy, and dependency review results.
- Update evidence docs when security posture or release evidence changes.
- Preserve release evidence and tag immutability.

Give extra review to updates near Spring Boot, Spring Security/OAuth2, AWS SDK, telemetry libraries, JSON/CSV parsing, Log4j, GitHub Actions, Docker base images, and build plugins.

## Release Evidence Review Cadence

Review release and operations evidence:

- Before semantic release tags.
- After workflow changes.
- After dependency updates.
- After deployment documentation changes.
- After security findings or incident-response exercises.

For semantic release tags, confirm Maven/app metadata alignment before push because the Release Artifacts workflow intentionally fails on tag/Maven mismatches.

## Logs And Retention Guidance

Operational logging guidance:

- Avoid secrets in logs.
- Centralize logs for serious deployments.
- Define log retention outside the application.
- Protect log access.
- Review support bundles and pasted issue reports for sensitive values.

Sensitive values include API keys, bearer tokens, OAuth client secrets, AWS credentials, private account identifiers, OTLP credentials, and private endpoint URLs.

## What This Guide Does Not Provide

This guide does not provide:

- Production SLOs.
- A full incident-management program.
- SIEM setup.
- WAF rules.
- Cloud account governance.
- Production certification.

Treat this as operational guidance for an enterprise-demo repository. A real production deployment still needs environment-specific architecture, security review, monitoring, incident response, and operational ownership.
