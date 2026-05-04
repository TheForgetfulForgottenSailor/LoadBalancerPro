# LoadBalancerPro Docker Compose Prod-Like Guide

Date: 2026-05-04

## Purpose And Scope

This guide documents a minimal Docker Compose example for local/private production-like validation of LoadBalancerPro.

It is not a production deployment and it is not production certification.

The example uses the `prod` Spring profile, but it does not provide TLS, IAM, WAF, secret rotation, centralized logging, production monitoring, production SLOs, or a complete security boundary.

Use it to validate that the existing Dockerfile can build and run the application with a production-like profile on a loopback-bound host port.

## Prerequisites

- Docker with Compose v2.
- No AWS credentials.
- No real secrets.
- The existing repository `Dockerfile`.

The Dockerfile builds the app image and includes a container-local healthcheck for `http://127.0.0.1:8080/api/health`.

## Files

This slice adds:

- `deploy/docker-compose.prod-like.yml`

The Compose file builds from the repository root, runs the `prod` profile, publishes only `127.0.0.1:18080`, and uses a placeholder API key.

## Run Commands

Validate the Compose file:

```bash
docker compose -f deploy/docker-compose.prod-like.yml config
```

Build and start the service:

```bash
docker compose -f deploy/docker-compose.prod-like.yml up --build -d
```

Check the public health endpoint on the loopback-bound host port:

```bash
curl -fsS http://127.0.0.1:18080/api/health
```

Inspect service and health status:

```bash
docker compose -f deploy/docker-compose.prod-like.yml ps
```

Stop and remove the Compose service:

```bash
docker compose -f deploy/docker-compose.prod-like.yml down
```

## Protected API Example

The Compose file uses the placeholder API key `replace-with-random-local-test-value`.

Example protected allocation call:

```bash
curl -H "X-API-Key: replace-with-random-local-test-value" \
  -H "Content-Type: application/json" \
  -d @examples/capacity-aware-request.json \
  http://127.0.0.1:18080/api/allocate/capacity-aware
```

Replace the placeholder API key for any serious environment. Do not commit `.env` files, real API keys, AWS credentials, OAuth tokens, or private telemetry credentials.

## Safety Boundaries

- The published host port binds to `127.0.0.1` only.
- The `prod` Spring profile is used.
- Live AWS remains disabled by the `prod` profile default.
- No AWS credentials are supplied.
- Prometheus and metrics ports are not separately exposed.
- OTLP metrics export remains disabled through the profile variable `LOADBALANCERPRO_OTLP_METRICS_ENABLED=false`.
- The Dockerfile healthcheck is used instead of adding a second Compose healthcheck.
- Release artifact, checksum, SBOM, and attestation evidence remain separate from this Compose example.

This example deliberately avoids reverse proxy, TLS, Kubernetes, Helm, Terraform, IAM samples, Docker image publishing, Maven Central publishing, and GitHub Release asset publishing.

## Cleanup

Stop and remove the Compose service:

```bash
docker compose -f deploy/docker-compose.prod-like.yml down
```

Optional local image cleanup:

```bash
docker image rm loadbalancerpro:prod-like
```

Only remove the image if no other local validation run needs it.

## What This Does Not Prove

This Compose example does not prove:

- TLS.
- WAF behavior.
- IAM least privilege.
- Secret rotation.
- Central logging.
- Production monitoring.
- Production SLOs.
- Production security certification.
- Kubernetes or Helm readiness.
- Live AWS readiness.
- High availability.
- Cloud capacity.

Treat this as a local/private validation aid for an enterprise-demo repository, not as production deployment evidence.

## Local Docker Warning Note

If Docker reports local config access warnings, resolve the local Docker Desktop or Docker config permissions for the workstation.

Do not treat local Docker config warnings as application failures unless the container fails to build, start, or pass health checks.
