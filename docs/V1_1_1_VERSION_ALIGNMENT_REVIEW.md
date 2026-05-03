# LoadBalancerPro v1.1.1 Version Alignment Review

Date: 2026-05-03

## Scope

This review plans a small v1.1.1 patch to align project and runtime version metadata after the published v1.1.0 release. No production code, tests, README, Maven configuration, tags, remotes, or public branches were changed as part of this review.

## Baseline

- Baseline branch: `loadbalancerpro-clean`
- Baseline commit: `2eda5c65d3cf3c4d009a58cbed17cbc316836a99`
- Review branch: `release/v1.1.1-version-alignment`
- Existing release tag: `v1.1.0`
- `v1.1.0` tag target: `b57bec688bf08f19718022016d580f3f484af8d7`
- Public default branch: `loadbalancerpro-clean`
- Public `main`: preserved and must remain untouched
- Existing tags must not be moved

## Problem

The public GitHub release is `v1.1.0`, but runtime and project metadata still report `1.0.0`.

Known mismatches:

- Generated JAR name is `LoadBalancerPro-1.0.0.jar`.
- `/api/health` reports `{"status":"ok","version":"1.0.0"}`.
- CLI `--version` reports `LoadBalancerCLI version 1.0.0`.
- OpenTelemetry resource attribute `service.version` is `1.0.0`.
- Actuator/info metadata `info.app.version` is `1.0.0`.
- README examples still use `target/LoadBalancerPro-1.0.0.jar`.

Because `v1.1.0` is already published and immutable, this should be fixed as `v1.1.1`, not by moving or replacing `v1.1.0`.

## Discovered Version References

### Maven

- `pom.xml`
  - `<version>1.0.0</version>`
  - This controls artifact names such as `target\LoadBalancerPro-1.0.0.jar`.

### Application Source

- `src/main/java/api/AllocatorController.java`
  - `private static final String VERSION = "1.0.0"`
  - `/api/health` returns this hard-coded value.
- `src/main/java/cli/LoadBalancerCLI.java`
  - `private static final String VERSION = "1.0.0"`
  - `--version` prints this hard-coded value.
- `src/main/java/api/LoadBalancerApiApplication.java`
  - The packaged Spring Boot JAR entrypoint previously did not handle `--version`.
  - `java -jar target\LoadBalancerPro-1.1.1.jar --version` should print the app version and exit instead of starting the API server.

### Resources

- `src/main/resources/application.properties`
  - `management.opentelemetry.resource-attributes[service.version]=1.0.0`
  - `info.app.version=1.0.0`

### Tests

- `src/test/java/api/AllocatorControllerTest.java`
  - Expects `/api/health` JSON `version` to equal `1.0.0`.
- `src/test/java/api/OpenTelemetryMetricsConfigurationTest.java`
  - Expects OpenTelemetry `service.version` to equal `1.0.0`.

### README

`README.md` contains multiple direct JAR examples using:

- `target/LoadBalancerPro-1.0.0.jar`

Examples appear in local API run commands, quick demo commands, load-test setup, profile examples, LASE demo commands, and replay commands.

### Docker and CI

- `Dockerfile`
  - Uses wildcard dynamic artifact discovery: `target/LoadBalancerPro-*.jar`.
  - Docker healthcheck calls `/api/health` but does not assert the version string.
- `.github/workflows/ci.yml`
  - Uses dynamic artifact discovery for JAR smokes.
  - API and Docker health checks verify HTTP 200 from `/api/health` but do not assert the version string.

### Historical Docs

Several historical planning/review docs mention `v1.0.0` or `LoadBalancerPro-1.0.0.jar`. Many should remain historical records and should not be rewritten. The new backlog and release notes can mention the mismatch as historical context.

## Recommended Source-of-Truth Strategy

Recommendation: implement the smallest safe v1.1.1 alignment, without a broad build-system refactor.

Preferred implementation:

- Update Maven project version to `1.1.1`.
- Add one application property, for example `loadbalancerpro.app.version=1.1.1`, in `application.properties`.
- Have `/api/health` read that property through Spring property injection with a safe fallback.
- Set `info.app.version` and telemetry `service.version` to the same value.
- Keep CLI simple by updating its constant to `1.1.1` for this patch, unless a tiny shared constant can be introduced without coupling CLI startup to Spring.

Why this is preferred:

- It avoids enabling Maven resource filtering across all resources, which can have surprising side effects.
- It avoids making the CLI depend on Spring Boot metadata during early flag handling.
- It keeps the patch readable and low-risk.
- It fixes the user-visible mismatch in the API, JAR name, CLI, telemetry, tests, and README.

Alternative for later:

- Spring Boot build-info metadata with `BuildProperties` could become the long-term API source of truth, but it needs Maven plugin configuration and careful behavior for tests and non-Spring CLI paths. That is reasonable but slightly broader than necessary for a patch release.

## Exact Files That Should Change

Recommended v1.1.1 implementation files:

- `pom.xml`
  - Change project version from `1.0.0` to `1.1.1`.
- `src/main/resources/application.properties`
  - Add or update app version property.
  - Update `management.opentelemetry.resource-attributes[service.version]`.
  - Update `info.app.version`.
- `src/main/java/api/AllocatorController.java`
  - Remove the hard-coded `VERSION = "1.0.0"`.
  - Inject a version property, for example `@Value("${loadbalancerpro.app.version:${info.app.version:unknown}}")`.
  - Return the injected value from `/api/health`.
- `src/main/java/cli/LoadBalancerCLI.java`
  - Update `VERSION` to `1.1.1`, or replace it with a minimal shared constant only if that can be done without dragging Spring into CLI startup.
- `src/main/java/api/LoadBalancerApiApplication.java`
  - Handle `--version` before Spring starts.
  - Read package implementation metadata when available and fall back to `1.1.1` for tests/dev runs.
- `README.md`
  - Replace active usage examples from `target/LoadBalancerPro-1.0.0.jar` to `target/LoadBalancerPro-1.1.1.jar`.
  - Consider using dynamic JAR discovery in longer smoke-test sections to avoid future churn.

## Tests That Should Change

- `src/test/java/api/AllocatorControllerTest.java`
  - Expect `/api/health` version `1.1.1`, or assert it matches the configured property if the test can read the environment cleanly.
- `src/test/java/api/OpenTelemetryMetricsConfigurationTest.java`
  - Expect `service.version` `1.1.1`, or assert it matches the configured app version property.
- `src/test/java/api/LoadBalancerApiApplicationTest.java`
  - Assert `--version` does not start the API server.
  - Assert the dev/test fallback version is `1.1.1`.

Optional tests:

- Add or update CLI version test if there is existing coverage for `--version`.
- Add an API health test that proves overriding the version property changes the response, if that remains low-friction.

## README and Docs References That Should Change

Update active README examples that tell users what command to run:

- Local API run examples.
- Quick demo commands.
- Local load-test setup.
- Local/prod/cloud-sandbox profile examples.
- LASE synthetic demo examples.
- LASE replay example.

Do not rewrite historical audit/review docs unless they are actively misleading. Historical docs that describe previous `v1.0.0`, `v1.0.1`, or `v1.1.0` facts should remain intact.

The existing `docs/RELEASE_NOTES_V1_1_0.md` should keep the note that `v1.1.0` shipped with Maven/JAR `1.0.0`, because that was true for that release. A new `v1.1.1` release note can explain the cleanup.

## Risks

- Retagging `v1.1.0` would break release provenance. Use `v1.1.1`.
- Maven version changes alter generated artifact names, so every command that hard-codes the JAR name must be reviewed.
- Maven resource filtering is tempting but can unexpectedly change resource contents if applied broadly.
- Spring Boot build-info is clean for the API but does not automatically solve CLI early `--version` handling.
- A shared constant is simple, but if it is not generated from Maven it can still drift in future releases.
- README examples can become stale again if they hard-code exact JAR names.
- Docker and CI dynamic JAR discovery should be preserved to avoid brittle release-specific paths.

## Verification Plan

DO NOT RUN YET:

```powershell
git status
git branch --show-current
rg -n "1\.0\.0|LoadBalancerPro-1\.0\.0|service.version|info.app.version|VERSION" pom.xml src/main/java src/main/resources src/test/java README.md Dockerfile .github docs

# After implementation:
git diff -- pom.xml src/main/java/api/AllocatorController.java src/main/java/api/LoadBalancerApiApplication.java src/main/java/cli/LoadBalancerCLI.java src/main/resources/application.properties src/test/java/api/AllocatorControllerTest.java src/test/java/api/LoadBalancerApiApplicationTest.java src/test/java/api/OpenTelemetryMetricsConfigurationTest.java README.md docs

mvn -q test
mvn -q -DskipTests package

Get-ChildItem -Path target -Filter 'LoadBalancerPro-*.jar' | Sort-Object LastWriteTime -Descending | Select-Object FullName,Name,Length,LastWriteTime
java -jar target\LoadBalancerPro-1.1.1.jar --version
java -jar target\LoadBalancerPro-1.1.1.jar --lase-demo=healthy
java -jar target\LoadBalancerPro-1.1.1.jar --lase-demo=overloaded
java -jar target\LoadBalancerPro-1.1.1.jar --lase-demo=invalid-name

Start-Process -FilePath java -ArgumentList '-jar','target\LoadBalancerPro-1.1.1.jar','--server.address=127.0.0.1','--server.port=18080','--spring.profiles.active=local' -WindowStyle Hidden
curl.exe -fsS http://127.0.0.1:18080/api/health

docker build -t loadbalancerpro:v1.1.1-version-alignment .
docker run --rm -d --name loadbalancerpro-v111-check -p 127.0.0.1:18081:8080 loadbalancerpro:v1.1.1-version-alignment
curl.exe -fsS http://127.0.0.1:18081/api/health
docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' loadbalancerpro-v111-check
docker stop loadbalancerpro-v111-check

mvn dependency:tree "-Dincludes=org.json:json" "-DoutputType=text"
git status
```

After local verification:

```powershell
git add pom.xml src/main/java/api/AllocatorController.java src/main/java/api/LoadBalancerApiApplication.java src/main/java/cli/LoadBalancerCLI.java src/main/resources/application.properties src/test/java/api/AllocatorControllerTest.java src/test/java/api/LoadBalancerApiApplicationTest.java src/test/java/api/OpenTelemetryMetricsConfigurationTest.java README.md docs/V1_1_1_VERSION_ALIGNMENT_REVIEW.md
git commit -m "Align version metadata for v1.1.1"
git push origin release/v1.1.1-version-alignment
git push public release/v1.1.1-version-alignment:release/v1.1.1-version-alignment
```

Only after GitHub Actions and Trivy pass should `release/v1.1.1-version-alignment` be merged into `loadbalancerpro-clean`, pushed, verified again, and tagged as `v1.1.1`.

## Recommendation

Implement now as a small `v1.1.1` patch. The mismatch is user-visible in the JAR name, API health response, CLI output, telemetry metadata, and README commands. Fixing it as a patch release is safer than leaving published `v1.1.0` as-is and starting larger v1.2.0 work on top of inconsistent metadata.
