# LoadBalancerPro Performance Baseline

Date: not captured yet

## Purpose And Scope

This document is a template for repeatable local benchmark evidence for LoadBalancerPro.

It is local evidence only. It is not a production SLO, production SLA, production capacity-planning result, universal performance claim, high-availability proof, or proof of cloud or live AWS performance.

No measured results are included yet because the preferred `hey` load-test tool was not available in the planning and implementation environment for this slice.

## Environment Metadata

| Field | Value |
| --- | --- |
| Date | not captured yet |
| Commit/tag | not captured yet |
| Machine/CPU/RAM | not captured yet |
| OS | not captured yet |
| Java version | not captured yet |
| Maven version | not captured yet |
| Docker version | not used / not captured yet |
| Spring profile | `local` |
| Packaged JAR or Docker | packaged JAR planned |
| Background load notes | not captured yet |

Record environment details before comparing runs. Local background load, OS, JDK, Maven, CPU, memory, and loopback behavior can materially affect results.

## Build And Startup Commands

Build and start the local packaged JAR:

```powershell
mvn -q test
mvn -q -DskipTests package
java -jar target/LoadBalancerPro-${version}.jar --server.address=127.0.0.1 --server.port=18080 --spring.profiles.active=local
curl http://127.0.0.1:18080/api/health
```

Bind to `127.0.0.1` and use the `local` profile for this baseline. The local baseline does not require AWS credentials, live cloud resources, Docker Compose, Kubernetes, Terraform, or external services.

## Endpoint Baseline Plan

Baseline these local endpoints:

- `GET /api/health`
- `POST /api/allocate/capacity-aware`
- `POST /api/allocate/predictive`
- `POST /api/routing/compare`

Use existing local example payloads where available:

- `examples/capacity-aware-request.json`
- `examples/predictive-request.json`

Routing comparison can use the README request body or a future `examples/routing-compare-request.json`. Do not fake routing comparison results if no stable request file exists.

## Load Tool Guidance

`hey` is preferred when installed because it reports latency distribution and supports duration-based tests with `-z`, fixed request counts with `-n`, and concurrency with `-c`.

`hey` was not available during this planning and implementation environment.

Manual `curl` smoke checks verify startup and endpoint reachability only. They are not latency baselines.

No measured results are included until a supported tool is available.

## Example Commands

Health endpoint:

```powershell
hey -z 30s -c 10 http://127.0.0.1:18080/api/health
```

Capacity-aware allocation:

```powershell
hey -z 30s -c 10 -m POST -H "Content-Type: application/json" -D examples/capacity-aware-request.json http://127.0.0.1:18080/api/allocate/capacity-aware
```

Predictive allocation:

```powershell
hey -z 30s -c 10 -m POST -H "Content-Type: application/json" -D examples/predictive-request.json http://127.0.0.1:18080/api/allocate/predictive
```

Routing comparison:

```powershell
hey -z 30s -c 10 -m POST -H "Content-Type: application/json" -D examples/routing-compare-request.json http://127.0.0.1:18080/api/routing/compare
```

Only run the routing comparison command after a stable routing comparison request file exists. Until then, use the README body in a temporary local file that is not committed, or record routing comparison as not captured yet.

## Metrics To Record

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
- warmup and background-load notes

Prefer a short warmup pass before recording results, or state clearly that the recorded run includes cold endpoint or JIT effects.

## Result Tables

### Health Endpoint

| Command | Duration | Concurrency | Requests/sec | p50 | p95 | p99 | Error rate | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| not captured yet | TBD | TBD | TBD | TBD | TBD | TBD | TBD | `hey` unavailable in implementation environment |

### Capacity-Aware Allocation

| Command | Duration | Concurrency | Requests/sec | p50 | p95 | p99 | Error rate | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| not captured yet | TBD | TBD | TBD | TBD | TBD | TBD | TBD | `hey` unavailable in implementation environment |

### Predictive Allocation

| Command | Duration | Concurrency | Requests/sec | p50 | p95 | p99 | Error rate | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| not captured yet | TBD | TBD | TBD | TBD | TBD | TBD | TBD | `hey` unavailable in implementation environment |

### Routing Comparison

| Command | Duration | Concurrency | Requests/sec | p50 | p95 | p99 | Error rate | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| not captured yet | TBD | TBD | TBD | TBD | TBD | TBD | TBD | stable request file not committed yet; `hey` unavailable |

## Interpretation Guidance

Compare only similar machines, JDK versions, OS versions, Spring profiles, and request payloads.

Percentiles matter because averages hide tail behavior. Local loopback results do not represent networked or cloud deployments.

Results can change after dependency updates, JVM updates, OS updates, configuration changes, endpoint changes, or background-load changes.

Production SLOs require real deployment requirements, real user traffic, reliability objectives, and infrastructure constraints.

## What Not To Claim

Do not claim:

- production SLO
- production SLA
- cloud capacity
- high-availability behavior
- live AWS performance
- replacement for managed load balancers
- universal benchmark
- production readiness

## Future Work

Possible later additions:

- add `examples/routing-compare-request.json` if useful
- add `scripts/performance-baseline.ps1`
- add `scripts/performance-baseline.sh`
- add CI artifact upload for benchmark evidence
- consider `k6` or JMeter later only if needed

Keep future performance work conservative until benchmark tooling, request payloads, and review expectations are stable.
