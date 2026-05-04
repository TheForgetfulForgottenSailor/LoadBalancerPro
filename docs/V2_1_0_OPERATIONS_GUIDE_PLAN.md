# LoadBalancerPro v2.1.0 Operations Guide Plan

Date: 2026-05-04

## A. Current Release State

v2.0.1 is shipped and pushed.

Deployment hardening documentation exists in `docs/DEPLOYMENT_HARDENING_GUIDE.md`.

Secret-management documentation exists in `docs/SECRET_MANAGEMENT_GUIDE.md`.

Release evidence, SHA-256 checksums, GitHub artifact attestations, SBOMs, CodeQL SAST, Dependabot, CODEOWNERS, and governance basics are already documented as part of the v1.4.0 through v2.0.1 hardening line.

Existing tags must remain immutable.

Public `main` remains untouched.

## B. Why Operations Docs Are Next

The v2.0.0 deployment hardening docs explain safer deployment boundaries: edge controls, authentication posture, actuator exposure, telemetry handling, secret handling, and cloud-sandbox cautions.

The next enterprise-readiness gap is operational discipline: how an operator starts the system, verifies it, monitors it, responds to incidents, handles dependency and security updates, and rolls back without damaging release evidence.

This should remain enterprise-demo and lab guidance. It should not claim production certification, production SLOs, managed service support, or validated live-cloud readiness.

## C. Proposed File

Create:

`docs/OPERATIONS_GUIDE.md`

## D. Recommended Contents For `OPERATIONS_GUIDE.md`

### 1. Purpose And Scope

The guide should state that it provides lab and enterprise-demo operations guidance for LoadBalancerPro.

It should clarify that it is not production certification and that operators remain responsible for deployment environment controls, identity, network security, TLS, secret management, monitoring, incident response, and compliance.

### 2. Operating Modes Support Matrix

Include a compact table covering:

| Mode | Intended use | Auth expectation | AWS behavior | Actuator/metrics posture | Recommended exposure | Support status |
| --- | --- | --- | --- | --- | --- | --- |
| local/default | Development and demos | API key or demo-safe auth | Live AWS disabled | Local/private only | Localhost or private network | Supported for demos |
| prod profile | Production-like validation | Prefer OAuth2 for stronger demos | Live AWS still off unless explicitly configured elsewhere | Private or authenticated | Behind trusted edge controls | Production-like, not certified |
| cloud-sandbox profile | Controlled sandbox validation | Deliberate auth and operator controls | Sandbox-only mutation boundary | Private, monitored, restricted | Private sandbox network | Controlled validation only |
| future live sandbox | Future explicitly approved live-cloud exercise | Strong auth and reviewed IAM | Disposable sandbox resources only | Private, audited, alerting enabled | Restricted sandbox edge | Not implemented unless separately approved |

### 3. Startup/Preflight Checklist

Include a checklist for:

- profile selected
- auth mode configured
- API key or OAuth2 config present
- CORS reviewed
- actuator exposure reviewed
- OTLP and Prometheus exposure reviewed
- cloud live flags reviewed
- secrets externalized
- release artifact, checksum, and attestation reviewed
- rollback target known

### 4. Health/Readiness Verification

Document checks for:

- `/api/health`
- `/actuator/health`
- `/actuator/info`
- readiness endpoint if enabled by the deployment profile

Explain expected local, prod-profile, and cloud-sandbox differences.

Warn that actuator endpoints should not be exposed publicly without trusted edge controls or deployment-level authentication.

### 5. Metrics And Dashboards

Recommend dashboard signals for:

- JVM memory
- garbage collection
- thread counts
- HTTP request rates
- HTTP 4xx and 5xx rates
- allocation request counts
- routing comparison request counts
- LASE shadow fail-safe counts
- cloud mutation denial and failure counts

Include Prometheus and OTLP privacy caveats, especially because telemetry can expose operational metadata and should be routed only to trusted collectors.

### 6. Alerting Examples

Include example alerts for:

- health endpoint failures
- sustained 5xx responses
- high latency
- elevated LASE fail-safe counts
- cloud mutation denial spikes
- telemetry export failures
- JVM memory pressure

State clearly that these examples are not production SLAs.

### 7. Incident Response Runbook

Recommend a concise runbook:

- identify the active profile
- confirm whether live AWS mutation is enabled
- preserve logs and release evidence
- disable live mutation if needed
- rotate secrets if exposure is suspected
- review release artifact evidence
- document timeline, symptoms, and actions
- avoid destructive actions unless they are part of an approved response plan

### 8. Rollback Guide

Include rollback steps:

- identify the last known good tag
- verify the artifact, checksum, and attestation for that tag
- deploy the previous artifact through the environment's normal mechanism
- confirm health endpoints
- monitor metrics and logs
- do not move tags
- document the rollback reason and result

### 9. Dependency/Security Update Workflow

Recommend:

- review Dependabot PRs one at a time
- avoid batching risky updates
- run tests and package verification
- review CI, CodeQL, Trivy, and dependency review results
- update evidence docs when security posture changes
- preserve release evidence and tag immutability

### 10. Release Evidence Review Cadence

Recommend evidence review:

- before semantic release tags
- after workflow changes
- after dependency updates
- after deployment documentation changes
- after security findings or incident-response exercises

### 11. Logs And Retention Guidance

Include:

- avoid secrets in logs
- centralize logs for serious deployments
- define retention outside the app
- protect log access
- review support bundles and pasted issue reports for sensitive values

### 12. What This Guide Does Not Provide

State that the guide does not provide:

- production SLOs
- a full incident-management program
- SIEM setup
- WAF rules
- cloud account governance
- production certification

## E. README Update Recommendation

The implementation slice should add one short link under the README Evidence and Hardening section:

`docs/OPERATIONS_GUIDE.md`

The README update should stay small and should not expand runtime, API, deployment, or release instructions.

## F. What Not To Change

- No Java code.
- No tests.
- No `pom.xml`.
- No workflows.
- No `Dockerfile`.
- No deployment manifests.
- No Docker Compose.
- No Kubernetes.
- No Helm.
- No Terraform.
- No IAM samples.
- No live AWS enablement.
- No public `main`.
- No tag movement.

## G. Verification Plan

After implementation:

```powershell
mvn -q test
mvn -q -DskipTests package
git diff --check
git diff -- src/main/java src/test/java pom.xml .github/workflows Dockerfile
```

Expected result:

- docs only
- no Java source changes
- no test changes
- no `pom.xml` changes
- no workflow changes
- no `Dockerfile` changes
- empty protected diff

## H. Recommendation

Proceed with v2.1.0 as an operations guide milestone before Docker Compose, Kubernetes, IAM policy samples, or performance baseline evidence.

Recommended sequence:

1. Implement `docs/OPERATIONS_GUIDE.md` and a tiny README link.
2. Add a performance baseline evidence slice after operational checks are documented.
3. Add Docker Compose, Kubernetes/Helm planning, and IAM samples only in separate later slices with conservative warnings and no production-readiness overclaims.
