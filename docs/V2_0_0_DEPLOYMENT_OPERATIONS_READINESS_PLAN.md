# LoadBalancerPro v2.0.0 Deployment Operations Readiness Plan

Date: 2026-05-04

## A. Current Release State

`v1.9.1` is shipped and pushed to `origin` and `public`.

`v1.9.1` documented the release artifact evidence chain from `v1.9.0`. The project now has repository governance, CI-generated SBOM artifacts, CodeQL SAST, tag-triggered release artifacts, SHA-256 checksums, GitHub artifact attestations, and release evidence documentation.

Existing tags must remain immutable. Public `main` remains untouched.

The current release branch is `loadbalancerpro-clean`. The next milestone should preserve the same release discipline: plan first, implement in focused slices, verify before tagging, and avoid changing historical tags.

## B. Why v2.0.0 Should Be Deployment/Operations Readiness

The `v1.x` line hardened the repository and release posture: governance files, dependency review, SBOM generation, Docker and JAR smoke checks, CodeQL static analysis, release artifact bundles, checksums, attestations, and evidence docs now exist.

The remaining enterprise-demo gap is no longer the core routing or release chain. The major gap is safe deployment and operations readiness:

- how to deploy the app behind trusted infrastructure,
- how to manage secrets,
- how to keep actuator and telemetry endpoints private,
- how to define rollback and incident response,
- how to avoid unsafe live cloud assumptions,
- how to document realistic monitoring and SLO expectations,
- how to produce performance evidence without turning it into a production capacity claim.

`v2.0.0` should therefore become a deployment and operations readiness milestone. It should not claim LoadBalancerPro is production-certified or ready for unmanaged production traffic. The right posture is conservative: the repository can document safer deployment patterns and operational boundaries while keeping production responsibility with the deployment environment.

Before any future `v2.0.0` tag is created, release metadata should be aligned to the tag version because the Release Artifacts workflow verifies Git tag version against the Maven project version before uploading artifacts.

## C. Proposed v2.0.0 Sub-Slices

### Phase 1: Deployment Hardening Guide

Recommended file:

```text
docs/DEPLOYMENT_HARDENING_GUIDE.md
```

Recommended contents:

- State that LoadBalancerPro remains an enterprise-demo/lab system unless deployed with external production controls.
- Explain that the `prod` profile is a production-like starting point, not complete production readiness.
- Recommend TLS termination at a trusted reverse proxy, ingress, or managed load balancer.
- Recommend HSTS at the trusted edge, not inside the app unless the deployment owns HTTPS end to end.
- Recommend API gateway or reverse-proxy rate limiting.
- Recommend private network binding for the app and public exposure only through the trusted edge.
- Document actuator endpoint exposure guidance:
  - `/api/health` can remain public only where intentionally allowed.
  - `/actuator/health` and `/actuator/info` should be private or deployment-authenticated outside local demos.
  - `/actuator/metrics` and `/actuator/prometheus` should not be publicly exposed.
- Document production CORS guidance:
  - configure explicit origins,
  - avoid wildcard origins for credentialed or protected routes,
  - keep browser access intentional.
- Recommend OAuth2 over API-key mode for stronger enterprise demos.
- Explain API-key mode as compatibility/demo-friendly auth, not full enterprise identity.
- Recommend secret handling through environment variables, orchestrator secret injection, or cloud secret managers.
- Warn that OTLP and Prometheus can expose operational metadata.
- State that live AWS remains disabled by default and should not be enabled without sandbox-specific planning.

### Phase 2: Secret-Management Guide

Recommended file:

```text
docs/SECRET_MANAGEMENT_GUIDE.md
```

Recommended contents:

- API key handling and rotation expectations.
- OAuth2 issuer and JWK configuration.
- AWS credential handling.
- OTLP endpoint, header, and collector credential handling.
- CORS origin configuration.
- Local config file cautions.
- Shell history, terminal logs, CI logs, and proxy logs as leakage paths.
- Environment variables versus orchestrator or cloud secret managers.
- Rules against committing real secrets, `.env` files, customer data, private account IDs, tokens, or sensitive logs.
- Guidance for sanitized examples in docs and evidence.

### Phase 3: Minimal Docker Compose Production-Like Example

Recommended files:

```text
deploy/docker-compose.prod-like.yml
docs/DOCKER_COMPOSE_PROD_LIKE_GUIDE.md
```

Recommended contents:

- Private/local host binding by default.
- `prod` profile.
- API key environment variable placeholder only.
- No real credentials.
- No live AWS.
- Healthcheck using `/api/health` or actuator health as appropriate.
- Optional reverse-proxy placeholder only if the first example can remain safe and simple.
- Clear "not production complete" language.
- Warnings that Compose does not solve TLS, secret rotation, IAM, firewalling, observability retention, or incident response by itself.

This should be a later slice, not the first v2.0.0 implementation, unless explicitly approved.

### Phase 4: Kubernetes/Helm Planning

Recommended planning file:

```text
docs/KUBERNETES_DEPLOYMENT_PLAN.md
```

Do not implement Kubernetes manifests or Helm charts until separately approved.

Recommended contents:

- Future Deployment object expectations.
- Future Service and ingress/edge assumptions.
- ConfigMap and Secret placeholders.
- Liveness and readiness probes using Spring Boot health endpoints.
- Resource requests and limits.
- NetworkPolicy expectations.
- Non-root container posture matching the Dockerfile.
- No live AWS by default.
- Guidance against aggressive probes that could create restart storms.
- Clear caveat that Kubernetes is widely used for container orchestration and application lifecycle management, but manifests do not imply production certification.

### Phase 5: Cloud Sandbox IAM Least-Privilege Policy Sample

Recommended file:

```text
docs/CLOUD_SANDBOX_IAM_POLICY.md
```

Recommended contents:

- Sandbox-only IAM policy sketch.
- Region and account constraints.
- `lbp-sandbox-` resource naming expectation.
- Least-privilege permissions for Auto Scaling, EC2, and CloudWatch only as actually needed by `CloudManager`.
- Deletion guardrails and ownership-tag assumptions.
- Disposable resources only.
- No production AWS account usage.
- Manual review requirement before any live use.
- Statement that the policy sample is guidance, not a guarantee of safe AWS operation.

This phase should be reviewed carefully before implementation because broad IAM examples are easy to misuse.

### Phase 6: Operations Guide

Recommended file:

```text
docs/OPERATIONS_GUIDE.md
```

Recommended contents:

- Profile selection checklist.
- Startup verification steps.
- Health and readiness checks.
- Metrics and dashboard review.
- Logs, retention, and access-control expectations.
- Incident response runbook.
- Rollback guide.
- Release artifact verification using the existing release artifact evidence chain.
- Dependency and security update workflow.
- Support matrix for local, prod-like, cloud-sandbox, and future live-sandbox modes.
- Evidence review cadence before releases.

### Phase 7: SLO/Alerting/Dashboard Examples

Recommended file:

```text
docs/SLO_ALERTING_DASHBOARD_GUIDE.md
```

Recommended contents:

- Lab/demo SLO examples only.
- Availability, error-rate, and latency examples.
- Actuator health alerts.
- JVM memory, GC, and thread indicators.
- HTTP 4xx/5xx rates.
- LASE shadow fail-safe counts.
- Cloud mutation denial and failure counts.
- Warnings that these examples are not production SLAs.
- Guidance that a real deployment must derive SLOs from user impact and measured service behavior.

### Phase 8: Performance Baseline Evidence

Recommended file:

```text
evidence/PERFORMANCE_BASELINE.md
```

Recommended contents:

- Machine, JDK, OS, and commit/tag.
- Commands run.
- Health endpoint load test.
- Allocation endpoint load test.
- Routing comparison endpoint load test.
- p50, p95, and p99 latency.
- Error rate.
- Local benchmark caveat.
- Statement that the baseline is not a production SLO, production capacity estimate, or universal performance claim.

## D. Recommended First Implementation Slice

Recommended first implementation:

1. Add `docs/DEPLOYMENT_HARDENING_GUIDE.md`.
2. Add `docs/SECRET_MANAGEMENT_GUIDE.md` only if it naturally fits the same docs-only slice; otherwise defer it to the next slice.
3. Add tiny README links only if they improve discoverability.
4. Do not add deployment manifests yet.
5. Do not add Docker Compose yet.
6. Do not add Kubernetes or Helm yet.
7. Do not change production code, tests, `pom.xml`, workflows, or `Dockerfile`.

The safest first slice is docs-only. It should convert deployment responsibilities already scattered across README and evidence docs into a clear deployment hardening guide without creating runnable deployment artifacts that reviewers might copy into unsafe environments.

## E. Verification Plan

For the first implementation slice:

```text
mvn -q test
mvn -q -DskipTests package
git diff --check
git diff -- src/main/java src/test/java pom.xml .github/workflows Dockerfile
```

Expected result:

- No Java code changes.
- No test changes.
- No `pom.xml` changes.
- No workflow changes.
- No `Dockerfile` changes.
- Docs only.

If README links are added, confirm they are short and do not rewrite runtime/API documentation.

If a future v2.0.0 tag is planned, add a separate release metadata alignment step before tagging so Maven/API/CLI/README version metadata matches `2.0.0` and the Release Artifacts workflow can pass tag/Maven alignment.

## F. Risks

- Deployment docs can overclaim production readiness.
- Examples can be copied into unsafe environments.
- Secret examples can accidentally encourage hardcoding.
- Reverse-proxy guidance can be too specific, stale, or wrong for a user's infrastructure.
- Kubernetes manifests can imply support maturity the project does not provide.
- IAM policy samples can be dangerous if too broad.
- Operations docs can create fake SLOs if not clearly caveated.
- Live AWS guidance can be misused outside a disposable sandbox.
- Performance baselines can be mistaken for production capacity.
- Docker Compose examples can be mistaken for a complete production deployment.
- Alert examples can create noisy or misleading operational signals if used without tuning.
- Documentation can drift from application behavior if profile defaults or endpoint exposure change later.

## G. What Not To Change

- No production code.
- No tests.
- No `pom.xml`.
- No workflows.
- No `Dockerfile`.
- No live AWS.
- No Terraform.
- No Kubernetes manifests in the first slice.
- No Docker Compose in the first slice unless separately approved.
- No container publishing.
- No Maven Central publishing.
- No GitHub Release assets.
- No public `main`.
- No tag movement.
- No routing behavior.
- No allocation endpoint behavior.
- No CLI workflow behavior.
- No CloudManager/AWS behavior.

## H. Open Questions

- Should `v2.0.0` be docs-only, or should it eventually include minimal Docker Compose?
- Should Kubernetes manifests wait until `v2.1.0`?
- Should the performance baseline be generated before or after the operations guide?
- Should the project add production-like Docker Compose before Kubernetes?
- Should OAuth2 production setup receive its own guide?
- Should cloud sandbox IAM policy be reviewed manually before committing?
- Should `v2.0.0` include a final enterprise readiness review?
- Should `docs/SECRET_MANAGEMENT_GUIDE.md` ship with Phase 1 or be the immediate Phase 2?
- Should `evidence/PERFORMANCE_BASELINE.md` be based on `hey`, JMeter, k6, or a minimal repeatable curl-based approach?
- Should v2.0.0 release metadata alignment be a dedicated patch step before tagging, as with prior version-alignment releases?

## I. Recommendation

Proceed with `v2.0.0` planning now and keep the first implementation conservative.

Recommended path:

1. Implement Phase 1 deployment hardening docs first.
2. Add the secret-management guide either in Phase 1 if it stays focused or as the immediate next slice.
3. Add the operations guide after the deployment/secret guidance is stable.
4. Add performance baseline evidence after operational expectations are defined.
5. Add Docker Compose, Kubernetes/Helm planning, and cloud-sandbox IAM samples in separate later slices.
6. Keep claims conservative and explicitly label all deployment guidance as enterprise-demo/lab guidance unless external production controls are in place.

The project has reached a strong repository, release, and supply-chain evidence milestone. The next useful enterprise-demo step is operational clarity: how to deploy, observe, roll back, and review the system without overclaiming that documentation alone makes it production-ready.
