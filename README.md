# LoadBalancerPro

LoadBalancerPro is a Java load balancing project with:

- Core allocation strategies and server health models
- A command-line interface
- A Spring Boot REST API for calculation-only allocation requests
- Micrometer/Actuator observability, including Prometheus metrics
- Guardrailed AWS Auto Scaling integration that is dry-run by default

The API and CLI are safe by default: allocation endpoints do not call AWS, CLI cloud integration is disabled unless requested, and cloud mutation stays disabled unless every live-mode guardrail is configured explicitly.

## Requirements

- Java 17+
- Maven 3.9+
- Docker, optional

Never commit AWS credentials, account IDs that should remain private, local config files containing secrets, or generated logs that may contain operational details.

## Build, Test, And Package

Run the unit and integration test suite:

```bash
mvn test
```

Build the executable Spring Boot JAR:

```bash
mvn clean package
```

Run the packaged API locally:

```bash
java -jar target/LoadBalancerPro-1.0-SNAPSHOT.jar --spring.profiles.active=local
```

Run the API from Maven during development:

```bash
mvn spring-boot:run
```

## Docker

The repository includes a `Dockerfile`.

Build the image:

```bash
docker build -t loadbalancerpro:local .
```

Run the API:

```bash
docker run --rm -p 8080:8080 loadbalancerpro:local
```

Pass cloud settings only through your runtime secret/config system. Do not bake credentials into the image.

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

Invalid request bodies return HTTP 400 with a structured validation response. Browser CORS is enabled for `/api/**` from `http://localhost:3000` and `http://localhost:8080`, with credentials disabled. Responses include lightweight security headers such as `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `X-XSS-Protection: 1; mode=block`, and `Cache-Control: no-store`.

OpenAPI UI is available at:

```text
GET /swagger-ui.html
```

## Actuator And Metrics

Actuator exposes these endpoints:

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
- Run `mvn clean package`.
- Start the JAR with the intended Spring profile and verify `/actuator/health`.
- Verify `/actuator/metrics` and `/actuator/prometheus` are reachable only where intended.
- Confirm no credentials are stored in Git, Docker images, shell history, or committed config files.
- Confirm cloud mode is dry-run unless a live change is scheduled.
- For live scale/update, confirm operator intent, capacity caps, account ID, environment, and region allow-list.
- For autonomous scale-up, confirm `cloud.allowAutonomousScaleUp=true` is intentional.
- For deletion, confirm the ASG ownership tag and both deletion gates.
- Review cloud audit logs and metrics after any live operation.
