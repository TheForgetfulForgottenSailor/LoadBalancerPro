# LoadBalancerPro

LoadBalancerPro is a Java 17 / Spring Boot load-balancing simulator and cloud-safety demo with guarded AWS mutation paths, hardened API contracts, robust import/export handling, CLI workflows, observability endpoints, CI release gates, Docker runtime hardening, and comprehensive mocked test coverage.

It is built as a polished portfolio and enterprise-demo system: the code demonstrates production-minded boundaries, tests, packaging, and cloud guardrails, but it is not a drop-in production cloud load balancer.

The API and CLI are safe by default: allocation endpoints do not call AWS, CLI cloud integration is disabled unless requested, Docker/local runs do not require AWS credentials, and cloud mutation stays disabled unless every live-mode guardrail is configured explicitly.

## Architecture Overview

- Core load-balancing engine: `core.LoadBalancer`, `core.Server`, and related strategy/result types model server health, capacity, weighted distribution, predictive allocation, and failure handling.
- LASE telemetry/scoring/routing foundation: `core.ServerStateVector`, `core.ServerScoreCalculator`, `core.RoutingDecision`, and `core.TailLatencyPowerOfTwoStrategy` provide an internal foundation for tail-latency-aware, queue-aware, explainable routing decisions. This foundation is intentionally not wired into the public allocation flows yet.
- ServerMonitor / health monitoring: `core.ServerMonitor` tracks local and mocked cloud health paths, emits health events, and coordinates with load balancer state without requiring real cloud resources in the default test suite.
- API layer: the Spring Boot API exposes calculation-only allocation endpoints, request validation, browser CORS behavior, security headers, request-size limits, structured error envelopes, Swagger/OpenAPI docs, and Actuator health/metrics endpoints.
- CLI workflow: `cli.LoadBalancerCLI` provides interactive local workflows and optional cloud integration while retaining ownership of monitor lifecycle cleanup.
- CSV/JSON import/export utilities: parser and utility code validate schema, reject malformed input, neutralize CSV injection risk, and keep import/export contracts aligned.
- CloudManager / AWS safety boundary: `core.CloudManager` is the only AWS mutation boundary. Live ASG creation, scaling, registration, and deletion paths are guarded, dry-run by default, and covered with mocked AWS clients.
- Docker/CI/release gates: GitHub Actions runs dependency resolution, tests, packaging, packaged-JAR smoke checks, and Docker image builds. The Docker runtime uses a non-root user and a container healthcheck.

## Roadmap: LoadBalancer Adaptive Systems Engine

The LoadBalancer Adaptive Systems Engine (LASE) is the north-star direction for this repository: a research-grade adaptive systems engine for telemetry-driven routing, overload protection, failure modeling, cloud-safety simulation, and explainable load-balancing decisions.

The internal telemetry-driven routing foundation now exists through immutable server state vectors, deterministic score calculation, power-of-two candidate sampling, and routing decision explanations. It is deliberately kept internal until the existing allocation behavior can be integrated safely.

Planned LASE work includes adaptive concurrency limits, load shedding and priority classes, shadow autoscaling, failure scenario simulation, richer tail-latency-aware routing, and cloud-safety simulation. These are roadmap items, not claims of fully implemented production behavior.

Roadmap backlog:

- Tail-latency-aware routing that accounts for p95/p99 service behavior, not only average utilization.
- Adaptive concurrency limits to keep overloaded servers from accepting more work than they can drain.
- Load shedding and priority classes for graceful degradation under stress.
- Shadow autoscaling mode that compares simulated scale decisions against actual traffic without mutating infrastructure.
- Failure scenario simulator for repeatable demos of degraded servers, region constraints, and guarded cloud paths.
- AWS SDK v2 migration before expanding live cloud behavior.
- Optional auth and deployment profile for demos that need controlled browser/API access.

## Safety Boundaries

- Default tests use mocks for cloud-facing behavior and do not create, modify, or delete real AWS resources.
- Docker and local API runs do not require AWS credentials by default.
- Live AWS behavior requires explicit configuration, operator intent, capacity/account/region guardrails, and dry-run opt-out.
- This repository is intended as a portfolio/enterprise-demo implementation, not production cloud infrastructure ready to operate unmanaged traffic.

## Hardened Foundation Checklist

- Cloud mutation guardrails fail closed for unsafe ASG creation, describe failures before scaling, and non-owned instance registration.
- CSV/JSON handling validates schemas, handles robust CSV quoting, rejects malformed records, and neutralizes spreadsheet formula injection.
- API hardening includes request-size enforcement, safe JSON error envelopes, validation response consistency, CORS coverage, and security headers.
- Concurrency and lifecycle cleanup removed unsafe shared hashing state, bounded cache risk, and clarified CLI monitor shutdown ownership.
- The default Maven test suite currently covers 360 tests with zero skipped tests and uses mocked cloud clients for cloud-adjacent coverage.
- CI release gates verify tests, packaging, packaged-JAR smoke startup, dependency review on pull requests, and Docker image builds.
- Docker runtime hardening runs the app as a non-root user and exposes a Docker healthcheck backed by `/api/health`.
- The internal LASE telemetry-driven routing foundation models server state, scores tail-latency and pressure signals, samples candidates deterministically in tests, and emits explainable routing decisions.

## Requirements

- Java 17+
- Maven 3.9+
- Docker, optional

Never commit AWS credentials, account IDs that should remain private, local config files containing secrets, or generated logs that may contain operational details.

## Build, Test, And Package

Run the default test suite:

```bash
mvn test
```

Build the executable Spring Boot JAR:

```bash
mvn package
```

Use `mvn clean package` when you want to remove stale local build artifacts before creating the JAR.

Run the packaged API locally:

```bash
java -jar target/LoadBalancerPro-1.0.0-rc2.jar --server.address=127.0.0.1 --server.port=18080 --spring.profiles.active=local
```

Verify the health endpoint:

```bash
curl http://127.0.0.1:18080/api/health
```

Run the API from Maven during development:

```bash
mvn spring-boot:run
```

## Quick Demo Commands

```bash
mvn test
mvn package
java -jar target/LoadBalancerPro-1.0.0-rc2.jar --server.address=127.0.0.1 --server.port=18080 --spring.profiles.active=local
curl http://127.0.0.1:18080/api/health
docker build -t loadbalancerpro:local .
docker run --rm --name loadbalancerpro-demo -p 127.0.0.1:8080:8080 loadbalancerpro:local
```

## Local Load-Test Evidence

Local load testing is a reproducible sanity check for the API contract and JVM packaging path. It is not production benchmarking, capacity planning, or a universal performance claim; results depend on the local machine, JDK, OS, background load, and network loopback behavior.

Start the local/demo API first:

```bash
mvn package
java -jar target/LoadBalancerPro-1.0.0-rc2.jar --server.address=127.0.0.1 --server.port=18080 --spring.profiles.active=local
```

The commands below use `hey` against `127.0.0.1` only and do not require AWS credentials, live cloud resources, or CloudManager configuration.

Health endpoint steady-load check:

```bash
hey -z 30s -c 10 http://127.0.0.1:18080/api/health
```

Allocation endpoint steady-load check:

```bash
hey -z 30s -c 10 \
  -m POST \
  -H "Content-Type: application/json" \
  -D examples/capacity-aware-request.json \
  http://127.0.0.1:18080/api/allocate/capacity-aware
```

Allocation endpoint burst/spike check:

```bash
hey -n 1000 -c 50 \
  -m POST \
  -H "Content-Type: application/json" \
  -D examples/capacity-aware-request.json \
  http://127.0.0.1:18080/api/allocate/capacity-aware
```

PowerShell helper:

```powershell
.\scripts\load-test.ps1 -BaseUrl http://127.0.0.1:18080
```

Capture these metrics from each run:

- Requests/sec
- p50 latency
- p95 latency
- p99 latency
- Error rate, derived from non-2xx/3xx status codes and reported errors

Sample local results should be labeled with machine, OS, JDK, command, and timestamp before being compared or shared. No committed result should be treated as a production SLO.

## Deployment Profiles

The default/local profile is for development, CI smoke tests, and portfolio demos. It keeps the demo-friendly behavior documented above: localhost browser CORS origins, `/api/health`, Swagger/OpenAPI, Actuator health/info/metrics/prometheus, and no live AWS mutation by default.

Run the local/demo profile explicitly:

```bash
java -jar target/LoadBalancerPro-1.0.0-rc2.jar --server.address=127.0.0.1 --server.port=18080 --spring.profiles.active=local
```

The `prod` profile is an explicit opt-in production-like starting point, not full production readiness. It keeps `cloud.liveMode=false`, does not require AWS credentials just to start, exposes only Actuator health/info by default, leaves browser CORS origins empty unless configured through `LOADBALANCERPRO_CORS_ALLOWED_ORIGINS`, and protects API mutation/allocation endpoints with the `X-API-Key` header.

Run the production-like profile locally for validation:

```bash
LOADBALANCERPRO_API_KEY=replace-with-random-local-test-value \
LOADBALANCERPRO_CORS_ALLOWED_ORIGINS=https://app.example.com \
java -jar target/LoadBalancerPro-1.0.0-rc2.jar --server.address=127.0.0.1 --server.port=18080 --spring.profiles.active=prod
```

Call protected prod-profile API endpoints with the configured key:

```bash
curl -H "X-API-Key: $LOADBALANCERPRO_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"requestedLoad":10,"servers":[{"id":"api-1","cpuUsage":10,"memoryUsage":20,"diskUsage":30,"capacity":100,"weight":1,"healthy":true}]}' \
  http://127.0.0.1:18080/api/allocate/capacity-aware
```

If `LOADBALANCERPRO_API_KEY` is missing or blank, protected prod-profile API requests fail closed with HTTP 401. `/api/health`, Actuator health/info, and OpenAPI docs remain public for local validation and portfolio review.

The prod-profile API key is a minimal client-auth gate. It is not full user identity, RBAC, OAuth, production authorization, or secret rotation. Before using the prod profile beyond a local demo, add deployment-specific auth, TLS or trusted proxy termination, secret management, actuator/network lockdown, logging retention, and live-cloud change controls. This profile is a safer baseline for review, not a claim that the app is ready for unmanaged production traffic.

## Production Deployment Considerations

LoadBalancerPro is designed as a portfolio/enterprise-demo system. The `prod` profile is a safer deployment starting point, not a complete production security system.

Recommended deployment boundary:

- Terminate TLS at a trusted reverse proxy or ingress such as nginx, Traefik, or a managed load balancer.
- Keep the app bound to a private interface or container network; expose only the proxy publicly.
- Configure the proxy to pass `Forwarded` or `X-Forwarded-*` headers, then enable `server.forward-headers-strategy=framework` through deployment config or by uncommenting the documented prod-profile setting.
- Add external rate limiting and request filtering at the proxy or gateway layer.
- Keep `/actuator/health` and `/actuator/info` behind private networking, firewall rules, or deployment-specific auth when running outside a local demo.
- Send logs and metrics to your normal monitoring stack, with retention and access controls appropriate for operational data.
- Store secrets in environment variables, a secret manager, or orchestrator-managed secret injection. Do not commit secrets, `.env` files, shell history, or generated logs containing sensitive values.

Example local validation behind a trusted proxy configuration:

```bash
LOADBALANCERPRO_API_KEY=replace-with-random-deployment-secret \
SERVER_FORWARD_HEADERS_STRATEGY=framework \
java -jar target/LoadBalancerPro-1.0.0-rc2.jar --server.address=127.0.0.1 --server.port=18080 --spring.profiles.active=prod
```

The API key is passed through `LOADBALANCERPRO_API_KEY`, mapped to `loadbalancerpro.api.key`, and is never documented as a real value. Rotate it outside the application and avoid logging request headers at the proxy.

## Safe LASE Synthetic Demo

The packaged JAR can print deterministic, synthetic LASE evaluation reports without starting the API server:

```bash
mvn package
java -jar target/LoadBalancerPro-1.0.0-rc2.jar --lase-demo=healthy
```

This is a safe internal control-plane demo only. It is recommendation-only, uses synthetic inputs, does not touch live AWS resources, does not call `CloudManager`, does not mutate real routing state, does not require the API server, does not require AWS credentials, and does not require network access.

Available demo commands:

```bash
java -jar target/LoadBalancerPro-1.0.0-rc2.jar --lase-demo
java -jar target/LoadBalancerPro-1.0.0-rc2.jar --lase-demo=all
java -jar target/LoadBalancerPro-1.0.0-rc2.jar --lase-demo=healthy
java -jar target/LoadBalancerPro-1.0.0-rc2.jar --lase-demo=overloaded
java -jar target/LoadBalancerPro-1.0.0-rc2.jar --lase-demo=error-storm
java -jar target/LoadBalancerPro-1.0.0-rc2.jar --lase-demo=partial-outage
java -jar target/LoadBalancerPro-1.0.0-rc2.jar --lase-demo=low-sample
java -jar target/LoadBalancerPro-1.0.0-rc2.jar --lase-demo=invalid-name
```

`--lase-demo` and `--lase-demo=all` print every scenario. Named scenarios print only that scenario. Invalid names fail safely with exit code `2`, print valid scenario names, and do not emit a raw stack trace.

Scenario coverage:

| Scenario | Demonstrates |
| --- | --- |
| `HEALTHY` | Normal low-risk evaluation, routing allowed, no scale-up pressure, and low failure severity. |
| `OVERLOADED` | High utilization, queue, and latency signals with low-priority shedding, shadow scale-up recommendation, and high-pressure failure signals. |
| `ERROR_STORM` | High error rate without matching scale-up pressure; recommends `INVESTIGATE` instead of blind scale-up. |
| `PARTIAL_OUTAGE` | Reduced healthy-server ratio, critical failure severity, and route-around / investigate style mitigation. |
| `LOW_SAMPLE` | Insufficient telemetry with conservative `HOLD` / `LOW` behavior. |

Sample excerpt:

```text
=== LoadBalancerPro LASE Synthetic Demo ===
Mode: synthetic demo, recommendation-only evaluation.
Safety: No live AWS resources touched. No real routing mutation. No CloudManager calls.
Runtime: deterministic local inputs; no AWS keys, network access, or API server required.

Evaluation ID: lase-demo-overloaded
Routing Decision:
Adaptive Concurrency:
Load Shedding:
Shadow Autoscaling:
Failure Scenario:
Summary:
```

This command demonstrates the internal LASE control-plane lab: telemetry-driven routing decisions, adaptive concurrency, load shedding, shadow autoscaling, failure scenario evaluation, and a consolidated explanation report. It does not claim production autoscaling, production chaos engineering, production cloud load-balancing behavior, or live AWS integration.

## Continuous Integration

GitHub Actions verifies the default release gates on every push and pull request:

```bash
mvn -B -DskipTests dependency:tree
mvn -B test
mvn -B package
JAR="$(ls -t target/LoadBalancerPro-*.jar | grep -Ev '(-sources|-javadoc|-tests)\.jar$' | head -n 1)"
java -jar "$JAR" --lase-demo=healthy
java -jar "$JAR" --lase-demo=overloaded
java -jar "$JAR" --lase-demo=invalid-name
java -jar "$JAR" --server.address=127.0.0.1 --server.port=18080 --spring.profiles.active=local
docker build -t loadbalancerpro:ci .
docker run --rm -d --name loadbalancerpro-ci -p 127.0.0.1:18081:8080 loadbalancerpro:ci
curl -fsS http://127.0.0.1:18081/api/health
docker inspect --format='{{.State.Health.Status}}' loadbalancerpro-ci
docker stop loadbalancerpro-ci
```

The LASE demo smoke checks run deterministic synthetic reports, verify safe failure for an invalid scenario name, and confirm the demo path does not emit Spring startup markers. The packaged JAR smoke test binds the app to `127.0.0.1`, waits for `GET /api/health` to return HTTP 200, then stops the local process. CI builds the Docker image, starts the container on a loopback-bound host port, verifies `/api/health`, waits for the Docker healthcheck to become healthy, stops the container, and runs an informational Trivy image scan. CI does not use AWS credentials, does not require live cloud resources, and does not create, modify, or delete AWS infrastructure. Pull requests also run GitHub's dependency review action for changed dependencies and fail on high-severity findings. Broader dependency lifecycle work, such as the AWS SDK v2 migration noted below, remains tracked separately.

## Docker

The repository includes a multi-stage `Dockerfile` that builds the packaged Spring Boot JAR and runs it from a Java 17 JRE image as a non-root user. The runtime image includes `curl` for the Docker `HEALTHCHECK`.

Build the image:

```bash
docker build -t loadbalancerpro:local .
```

The Docker build is self-contained and creates the packaged JAR inside the build stage; no local AWS credentials or prebuilt JAR are required.

Run the API for a local demo:

```bash
docker run --rm --name loadbalancerpro-demo -p 127.0.0.1:8080:8080 loadbalancerpro:local
```

The container binds the Spring Boot process to `0.0.0.0` inside the container so Docker port publishing works predictably. The command above binds the published host port to `127.0.0.1` for local-only access.

Verify the API health endpoint:

```bash
curl -fsS http://127.0.0.1:8080/api/health
```

For detached runs, Docker also evaluates the image healthcheck:

```bash
docker run --rm -d --name loadbalancerpro-demo -p 127.0.0.1:8080:8080 loadbalancerpro:local
docker inspect --format='{{.State.Health.Status}}' loadbalancerpro-demo
docker stop loadbalancerpro-demo
```

Docker mode starts the local/demo-safe API and does not require AWS credentials. Pass cloud settings only through your runtime secret/config system, do not bake credentials into the image, and enable live AWS behavior only with the explicit CloudManager guardrails described below.

## REST API

Run the Spring Boot API, then call:

```text
GET  /api/health
POST /api/allocate/capacity-aware
POST /api/allocate/predictive
```

Example request:

```bash
curl -X POST http://localhost:8080/api/allocate/capacity-aware \
  -H "Content-Type: application/json" \
  -d '{
    "requestedLoad": 75.0,
    "servers": [
      {
        "id": "api-1",
        "cpuUsage": 90.0,
        "memoryUsage": 90.0,
        "diskUsage": 90.0,
        "capacity": 100.0,
        "weight": 1.0,
        "healthy": true
      }
    ]
  }'
```

The allocation APIs are calculation-only. Scaling recommendations are simulations and do not call `CloudManager` or AWS.

Invalid request bodies return HTTP 400 with a structured validation response. In the local/demo profile, browser CORS is enabled for `/api/**` from `http://localhost:3000` and `http://localhost:8080`, with credentials disabled. In the `prod` profile, configure allowed origins explicitly with `LOADBALANCERPRO_CORS_ALLOWED_ORIGINS`. Responses include lightweight security headers such as `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `X-XSS-Protection: 1; mode=block`, and `Cache-Control: no-store`.

OpenAPI UI is available at:

```text
GET /swagger-ui.html
```

## Actuator And Metrics

The local/demo profile exposes these Actuator endpoints:

```text
GET /actuator/health
GET /actuator/metrics
GET /actuator/prometheus
```

Additional configured endpoints include:

```text
GET /actuator/info
GET /actuator/health/readiness
```

Prometheus scraping target:

```text
http://localhost:8080/actuator/prometheus
```

Domain metrics include allocation counters/gauges, parsing failures, and cloud scale decisions with source and reason tags.

The `prod` profile exposes only `/actuator/health` and `/actuator/info` by default. Keep metrics and Prometheus behind deployment-specific network and authentication controls before enabling them outside a demo environment.

## CLI

Run the interactive CLI:

```bash
mvn -q exec:java "-Dexec.mainClass=cli.LoadBalancerCLI"
```

For a local allocation demo, run the interactive CLI and choose the balance-load workflow. The CLI does not currently expose a `--allocator-demo` flag.

Enable cloud integration for the CLI only when explicitly needed:

```bash
mvn -q exec:java "-Dexec.mainClass=cli.LoadBalancerCLI" "-Dexec.args=--cloud-enabled"
```

CLI general settings may be supplied in `cli.config` or with `--config <file>`. Cloud credentials and guardrails are loaded from system properties or environment variables.

## CLI Cloud Configuration

Use system properties:

```text
-Daws.accessKeyId=...
-Daws.secretAccessKey=...
-Daws.region=us-east-1
-Dcloud.liveMode=false
-Dcloud.launchTemplateId=...
-Dcloud.subnetId=...
-Dcloud.maxDesiredCapacity=3
-Dcloud.maxScaleStep=1
-Dcloud.allowLiveMutation=false
-Dcloud.operatorIntent=
-Dcloud.allowAutonomousScaleUp=false
-Dcloud.environment=dev
-Dcloud.allowedAwsAccountIds=123456789012
-Dcloud.currentAwsAccountId=123456789012
-Dcloud.allowedRegions=us-east-1,us-west-2
-Dcloud.allowResourceDeletion=false
-Dcloud.confirmResourceOwnership=false
```

Or environment variables:

```text
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
AWS_REGION
AWS_DEFAULT_REGION
CLOUD_LIVE_MODE
CLOUD_LAUNCH_TEMPLATE_ID
CLOUD_SUBNET_ID
CLOUD_MAX_DESIRED_CAPACITY
CLOUD_MAX_SCALE_STEP
CLOUD_ALLOW_LIVE_MUTATION
CLOUD_OPERATOR_INTENT
CLOUD_ALLOW_AUTONOMOUS_SCALE_UP
CLOUD_ENVIRONMENT
CLOUD_ALLOWED_AWS_ACCOUNT_IDS
CLOUD_CURRENT_AWS_ACCOUNT_ID
CLOUD_ALLOWED_REGIONS
CLOUD_ALLOW_RESOURCE_DELETION
CLOUD_CONFIRM_RESOURCE_OWNERSHIP
```

Required credentials are rejected if they are blank or placeholder values. Missing required cloud config disables CLI cloud mode safely and prints an operator-facing error.

## Dependency Lifecycle Notes

LoadBalancerPro currently uses AWS SDK for Java 1.x modules for the guarded CloudManager integration. AWS announced that SDK v1 entered maintenance mode on July 31, 2024 and reached end-of-support on December 31, 2025. This project should track a future migration to AWS SDK for Java 2.x before expanding cloud features, but this production-readiness sweep intentionally does not migrate SDK major versions.

Reference: https://aws.amazon.com/blogs/developer/announcing-end-of-support-for-aws-sdk-for-java-v1-x-on-december-31-2025/

## Test Notes

The default Maven test suite uses mocked cloud clients for CloudManager and ServerMonitor cloud-path coverage. It does not create, modify, or delete real AWS resources, and `mvn test` is expected to complete with zero skipped tests. Live AWS validation is intentionally outside the default Maven lifecycle; run it only in a controlled AWS sandbox with explicit cloud guardrails, operator intent, and disposable resources.

## Cloud Safety Modes

Dry-run is the default because `cloud.liveMode=false` unless set otherwise. In dry-run mode, CloudManager logs decisions and does not perform live AWS mutation.

Live ASG scale/update requires all of the following:

- `cloud.liveMode=true`
- `cloud.allowLiveMutation=true`
- `cloud.operatorIntent=LOADBALANCERPRO_LIVE_MUTATION`
- `cloud.maxDesiredCapacity` set high enough for the requested desired capacity
- `cloud.maxScaleStep` set high enough for the requested scale step
- `cloud.environment` set to a non-blank environment name
- `cloud.allowedAwsAccountIds` containing `cloud.currentAwsAccountId`
- `cloud.allowedRegions` either empty or containing `aws.region`
- `cloud.launchTemplateId` and `cloud.subnetId` when live mode is requested through the CLI

Autonomous scale-up from background sources is denied by default. Set `cloud.allowAutonomousScaleUp=true` only when predictive, preemptive, or unknown-source live scale-up is intended.

Live deletion has additional gates:

- `cloud.liveMode=true`
- `cloud.allowResourceDeletion=true`
- `cloud.confirmResourceOwnership=true`
- the ASG can be described successfully
- the ASG has the ownership tag `LoadBalancerPro=<auto-scaling-group-name>`

If any deletion gate or ownership validation fails, deletion is skipped.

## Deployment Checklist

- Run `mvn test`.
- Run `mvn package`.
- Start the JAR with the intended Spring profile and verify `/actuator/health`.
- Verify `/actuator/metrics` and `/actuator/prometheus` are reachable only where intended.
- Confirm no credentials are stored in Git, Docker images, shell history, or committed config files.
- Confirm cloud mode is dry-run unless a live change is scheduled.
- For live scale/update, confirm operator intent, capacity caps, account ID, environment, and region allow-list.
- For autonomous scale-up, confirm `cloud.allowAutonomousScaleUp=true` is intentional.
- For deletion, confirm the ASG ownership tag and both deletion gates.
- Review cloud audit logs and metrics after any live operation.
