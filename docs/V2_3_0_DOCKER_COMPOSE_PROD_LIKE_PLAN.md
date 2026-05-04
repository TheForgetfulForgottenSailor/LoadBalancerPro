# LoadBalancerPro v2.3.0 Docker Compose Prod-Like Plan

## A. Current Release State

v2.2.0 is shipped and pushed to origin and public.

Deployment hardening, secret-management, operations, and performance baseline docs exist.

The release artifact workflow, SHA-256 checksums, GitHub artifact attestations, SBOMs, CodeQL SAST, CI SBOM artifacts, and governance docs exist.

Existing tags must remain immutable.

Public main remains untouched.

## B. Why Docker Compose Prod-Like Example Is Next

The deployment hardening docs explain safer deployment boundaries and controls. A minimal Docker Compose example gives reviewers a runnable local/private production-like validation path.

The example should demonstrate:

- starting the packaged container with the `prod` profile,
- binding the published host port to `127.0.0.1`,
- using an API key placeholder through environment configuration,
- keeping live AWS behavior disabled,
- checking `/api/health`.

This must not imply complete production readiness. Compose is a convenient local validation shape, not a substitute for TLS termination, IAM review, network policy, secret rotation, centralized logging, production monitoring, or incident response.

## C. Proposed Files

Recommended implementation files:

- `deploy/docker-compose.prod-like.yml`
- `docs/DOCKER_COMPOSE_PROD_LIKE_GUIDE.md`

The first slice should add only these files plus a small README link if useful.

## D. Docker Compose Goals

The example should:

- build from the local `Dockerfile`, or use the local image name produced by that build,
- run the app with the `prod` Spring profile,
- bind the published host port to `127.0.0.1` only,
- use an environment placeholder for `LOADBALANCERPRO_API_KEY`,
- avoid real credentials,
- keep cloud live mode disabled,
- avoid AWS credentials,
- include a healthcheck if feasible,
- avoid exposing Prometheus or metrics publicly,
- avoid a reverse proxy in the first slice unless a placeholder note is safer,
- stay small enough for local validation and review.

## E. Proposed Compose Shape

Planned service shape:

```yaml
services:
  loadbalancerpro:
    build: ..
    image: loadbalancerpro:prod-like
    ports:
      - "127.0.0.1:18080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      LOADBALANCERPRO_API_KEY: replace-with-random-local-test-value
      CLOUD_LIVE_MODE: "false"
      CLOUD_ALLOW_LIVE_MUTATION: "false"
      CLOUD_ALLOW_RESOURCE_DELETION: "false"
      MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED: "false"
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://127.0.0.1:8080/api/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 20s
    restart: "no"
```

Implementation should verify the exact build context relative to the final file location. If the Compose file lives under `deploy/`, `build: ..` may be clearer than `build: .`; the implementation should choose the smallest working path after running `docker compose -f deploy/docker-compose.prod-like.yml config`.

The current `prod` profile uses `cloud.liveMode=false` in `application-prod.properties`. The cloud-sandbox profile also has explicit `cloud.allowLiveMutation=false` and `cloud.allowResourceDeletion=false` properties. The Compose example can keep explicit false-like environment placeholders for reviewer clarity, but implementation should verify whether relaxed Spring binding maps the proposed uppercase names exactly as intended. If any names do not bind cleanly, prefer documented property-style environment names or omit redundant keys instead of implying protection that is not actually wired.

The existing Dockerfile already includes a container-local healthcheck against `http://127.0.0.1:8080/api/health`. The Compose file may rely on the image healthcheck or restate it if doing so improves review clarity.

## F. Guide Contents

`docs/DOCKER_COMPOSE_PROD_LIKE_GUIDE.md` should include:

- purpose and scope,
- clear language that this is local/private production-like validation, not production,
- prerequisites, including Docker with Compose v2,
- build and run command,
- health check command,
- protected API call example using `X-API-Key`,
- stop and cleanup commands,
- secret handling warnings,
- confirmation that live AWS remains disabled,
- what this does not prove.

The guide should state that the example does not prove:

- TLS,
- WAF behavior,
- IAM least privilege,
- secret rotation,
- central logging,
- production monitoring,
- production SLOs,
- production security certification.

Recommended command examples:

```bash
docker compose -f deploy/docker-compose.prod-like.yml config
docker compose -f deploy/docker-compose.prod-like.yml up --build -d
curl -fsS http://127.0.0.1:18080/api/health
curl -H "X-API-Key: replace-with-random-local-test-value" \
  -H "Content-Type: application/json" \
  -d @examples/capacity-aware-request.json \
  http://127.0.0.1:18080/api/allocate/capacity-aware
docker compose -f deploy/docker-compose.prod-like.yml ps
docker compose -f deploy/docker-compose.prod-like.yml down
```

Use placeholders only. Do not commit `.env` files or real secrets.

## G. README Update Recommendation

Add one short link under Evidence and Hardening or the Deployment section:

- `docs/DOCKER_COMPOSE_PROD_LIKE_GUIDE.md`

Do not add a large README section. Keep runtime/API instructions unchanged unless a small discoverability link is needed.

## H. Verification Plan For Implementation

After implementation:

```bash
mvn -q test
mvn -q -DskipTests package
docker compose -f deploy/docker-compose.prod-like.yml config
docker compose -f deploy/docker-compose.prod-like.yml up --build -d
curl -fsS http://127.0.0.1:18080/api/health
docker compose -f deploy/docker-compose.prod-like.yml ps
docker compose -f deploy/docker-compose.prod-like.yml down
git diff --check
git diff -- src/main/java src/test/java pom.xml .github/workflows Dockerfile
```

Expected result:

- no Java code changes,
- no test changes,
- no `pom.xml` changes,
- no workflow changes,
- no Dockerfile changes,
- only docs and the Compose file added.

Docker Compose was available in this planning environment as Docker Compose v2.34.0, but Docker reported a local config access warning. Implementation should report any local Docker access or permission warnings honestly.

## I. Risks

Users may mistake Docker Compose for production readiness.

The API key placeholder might be copied into a real environment.

Binding to `0.0.0.0` would expose the local service more broadly than intended.

The healthcheck command could be wrong inside the container if paths, networking, or curl availability change.

Environment variable names must match Spring Boot and application configuration. Redundant cloud-safety environment variables should not be used as evidence unless they actually bind.

Compose v2 and older `docker-compose` behavior differ.

Docker build can be slow or fail due to local Docker Desktop, filesystem, or network/cache state.

Live AWS flags must stay off.

Secrets must not be committed, baked into images, pasted into examples, or stored in source-controlled `.env` files.

## J. What Not To Change

- No production code.
- No tests.
- No `pom.xml`.
- No workflows.
- No Dockerfile.
- No Kubernetes.
- No Helm.
- No Terraform.
- No IAM samples.
- No live AWS.
- No Docker image publishing.
- No Maven Central publishing.
- No GitHub Release assets.
- No public main.
- No tag movement.

## K. Recommendation

Proceed with the minimal Docker Compose production-like example before Kubernetes or IAM samples.

Keep it local/private and conservative:

- add `deploy/docker-compose.prod-like.yml`,
- add `docs/DOCKER_COMPOSE_PROD_LIKE_GUIDE.md`,
- add only a small README link,
- verify Compose config and health locally,
- do not add reverse proxy, Kubernetes, IAM, Helm, Terraform, or live AWS behavior in this slice.

Defer Kubernetes, IAM samples, reverse proxy examples, Terraform, and container publishing to later focused slices.
