# LoadBalancerPro v2.2.0 Performance Baseline Plan

Date: 2026-05-04

## A. Current Release State

v2.1.0 is shipped and pushed.

`docs/OPERATIONS_GUIDE.md` exists and documents startup checks, health verification, metrics guidance, alerting examples, incident response, rollback, dependency/security update flow, and release evidence review cadence.

Deployment hardening and secret-management guides exist:

- `docs/DEPLOYMENT_HARDENING_GUIDE.md`
- `docs/SECRET_MANAGEMENT_GUIDE.md`

Release artifacts, SHA-256 checksums, GitHub artifact attestations, SBOMs, CodeQL SAST, Dependabot, CODEOWNERS, pinned actions, pinned Docker digests, and governance evidence exist.

Existing tags must remain immutable.

Public `main` remains untouched.

## B. Why Performance Baseline Evidence Is Next

The operations guide explains how to run, verify, monitor, respond, and roll back. The next useful evidence slice is a repeatable local performance baseline that shows basic response-time characteristics for the packaged application under controlled local conditions.

This should help reviewers understand local behavior for health checks, allocation endpoints, and routing comparison without claiming production capacity.

The evidence must stay conservative. It is local benchmark evidence only. It is not a production SLO, SLA, capacity-planning result, cloud-performance result, high-availability proof, or universal performance claim.

## C. Proposed File

Create:

`evidence/PERFORMANCE_BASELINE.md`

## D. Recommended Contents For `PERFORMANCE_BASELINE.md`

### 1. Purpose And Scope

Document that the file captures repeatable local benchmark evidence for LoadBalancerPro.

State clearly:

- local benchmark evidence only
- not a production SLO
- not production capacity planning
- not a universal performance claim
- not proof of cloud or live AWS performance

### 2. Environment Metadata

Capture:

- date
- commit and tag
- machine model, or generic CPU/RAM if exact model should not be published
- OS
- Java version
- Maven version
- Docker version, if Docker is used
- active Spring profile
- whether the packaged JAR or Docker image was used
- background load caveat

Recommended table:

| Field | Value |
| --- | --- |
| Date | TBD |
| Commit/tag | TBD |
| Machine/CPU/RAM | TBD |
| OS | TBD |
| Java version | TBD |
| Maven version | TBD |
| Docker version | Not used, or TBD |
| Spring profile | `local` |
| Background load notes | TBD |

### 3. Build And Startup Commands

Include exact commands:

```powershell
mvn -q test
mvn -q -DskipTests package
java -jar target/LoadBalancerPro-${version}.jar --server.address=127.0.0.1 --server.port=18080 --spring.profiles.active=local
curl http://127.0.0.1:18080/api/health
```

The startup command should bind to `127.0.0.1` and use the `local` profile. Do not require AWS credentials, live cloud resources, Docker Compose, Kubernetes, Terraform, or external services.

### 4. Endpoint Baseline Plan

Baseline these local endpoints:

- `GET /api/health`
- `POST /api/allocate/capacity-aware`
- `POST /api/allocate/predictive`
- `POST /api/routing/compare`

The allocation endpoints should use existing safe local example payloads when possible:

- `examples/capacity-aware-request.json`
- `examples/predictive-request.json`

The routing comparison endpoint can initially use the README request body. If the implementation slice chooses to add `examples/routing-compare-request.json`, that should be treated as a separate explicitly reviewed file addition, not an accidental behavior change.

### 5. Load Tool Recommendation

Use `hey` if available because it is simple and reports latency distribution. `hey` supports fixed request count with `-n` and concurrency with `-c`, and it can run duration-based tests with `-z`.

If `hey` is not installed, document that manual `curl` smoke checks are acceptable for startup verification but are not a latency baseline.

The current repository already has local load-test guidance in the README and a PowerShell helper in `scripts/load-test.ps1`. That script currently covers health and capacity-aware allocation. Do not extend scripts in the first v2.2.0 slice unless separately approved.

### 6. Example Commands

Safe local-only examples:

```powershell
hey -z 30s -c 10 http://127.0.0.1:18080/api/health
```

```powershell
hey -z 30s -c 10 -m POST -H "Content-Type: application/json" -D examples/capacity-aware-request.json http://127.0.0.1:18080/api/allocate/capacity-aware
```

```powershell
hey -z 30s -c 10 -m POST -H "Content-Type: application/json" -D examples/predictive-request.json http://127.0.0.1:18080/api/allocate/predictive
```

For routing comparison, use the README routing comparison body or a future `examples/routing-compare-request.json` if that example file is added in the implementation slice:

```powershell
hey -z 30s -c 10 -m POST -H "Content-Type: application/json" -D examples/routing-compare-request.json http://127.0.0.1:18080/api/routing/compare
```

If no routing example file exists yet, do not fake results. Record routing comparison as not captured or use a temporary local file that is not committed.

### 7. Metrics To Record

Record:

- requests/sec
- p50 latency
- p95 latency
- p99 latency
- fastest response
- slowest response
- non-2xx/3xx count or error rate
- command duration
- concurrency
- notes about warmup and background load

Prefer a short warmup pass before recording results, or clearly state that the first measured run includes cold endpoint/JIT effects.

### 8. Result Tables

Plan result tables for each endpoint.

Health endpoint:

| Command | Duration | Concurrency | Requests/sec | p50 | p95 | p99 | Error rate | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD |

Capacity-aware allocation:

| Command | Duration | Concurrency | Requests/sec | p50 | p95 | p99 | Error rate | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD |

Predictive allocation:

| Command | Duration | Concurrency | Requests/sec | p50 | p95 | p99 | Error rate | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD |

Routing comparison:

| Command | Duration | Concurrency | Requests/sec | p50 | p95 | p99 | Error rate | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD |

If tooling is unavailable, use `not captured yet` rather than invented values.

### 9. Interpretation Guidance

State:

- Compare only similar machines, JDK versions, OS versions, Spring profiles, and request payloads.
- Percentiles matter because averages hide tail behavior.
- Local loopback results do not represent networked or cloud deployment behavior.
- Results can change after dependency updates, JVM changes, OS updates, endpoint changes, or background load changes.
- Any production SLO must come from real deployment requirements, real user traffic, reliability objectives, and infrastructure constraints.

### 10. What Not To Claim

Do not claim:

- production SLO
- production SLA
- cloud capacity
- high-availability behavior
- live AWS performance
- replacement for managed load balancers
- universal benchmark status
- production readiness

## E. Possible Future Helper Files

Possible later additions, not required for the first v2.2.0 slice:

- `examples/routing-compare-request.json`, if missing and useful
- `scripts/performance-baseline.ps1`
- `scripts/performance-baseline.sh`
- CI artifact upload for benchmark evidence
- `k6` or JMeter scenarios if later needed

Do not add scripts or CI performance gates in the first slice unless separately approved.

## F. Recommended First Implementation Slice

Recommended first implementation:

- Add `evidence/PERFORMANCE_BASELINE.md` as a conservative template.
- Add measured local results only if Codex can start the local API and `hey` is available.
- If `hey` is unavailable, create the evidence template and leave result rows as `not captured yet`.
- Add a tiny README link under Evidence and Hardening.
- Include this planning doc.
- Do not add scripts yet.
- Do not add a CI performance gate.
- Do not add Docker Compose, Kubernetes, Helm, Terraform, or IAM samples.
- Do not make production performance claims.

## G. Verification Plan

For implementation:

```powershell
mvn -q test
mvn -q -DskipTests package
git diff --check
git diff -- src/main/java src/test/java pom.xml .github/workflows Dockerfile
```

Expected:

- docs/evidence only
- no production code changes
- no test changes
- no `pom.xml` changes
- no workflow changes
- no `Dockerfile` changes
- empty protected diff

Optional local measurement verification:

- package the JAR
- start the API on `127.0.0.1:18080` with the `local` profile
- run `curl http://127.0.0.1:18080/api/health`
- run `hey` commands only if `hey` is installed
- stop the API process cleanly
- record exact commands, environment metadata, and caveats

## H. Risks

- Benchmarks can be mistaken for production capacity.
- Background processes can skew local results.
- Windows and Linux results can differ.
- Cold start and warmed endpoint results can differ.
- `hey` may not be installed.
- Request JSON examples can drift from API contracts.
- Performance gates can become flaky if added too early.
- Results can become stale after code, dependency, JVM, OS, or configuration changes.
- Local loopback avoids real network, TLS, proxy, and cloud overhead.

## I. What Not To Change

- No production code.
- No tests.
- No `pom.xml`.
- No workflows.
- No `Dockerfile`.
- No CI performance gate.
- No scripts in the first slice unless separately approved.
- No Docker Compose.
- No Kubernetes.
- No Helm.
- No Terraform.
- No IAM samples.
- No public `main`.
- No tag movement.

## J. Recommendation

Proceed with a performance baseline evidence template before Docker Compose, Kubernetes, IAM samples, or broader deployment assets.

Capture measured results only if the local API can be run safely and `hey` is available. Otherwise, ship the template first with `not captured yet` placeholders and keep the slice conservative, local-only, and docs/evidence-only.
