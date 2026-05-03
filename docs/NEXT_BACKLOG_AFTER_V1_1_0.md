# LoadBalancerPro Backlog After v1.1.0

Date: 2026-05-03

## Current Release State

- Public release: `v1.1.0`
- `v1.1.0` tag target: `b57bec688bf08f19718022016d580f3f484af8d7`
- Release notes commit after tag: `bfa845fe9bbf68fcce2686b0fdd1a2b9117ae47c`
- Current branch: `loadbalancerpro-clean`
- Public default branch: `loadbalancerpro-clean`
- Public `main`: preserved and intentionally not used for release publication
- GitHub Actions: passed on `loadbalancerpro-clean`
- Trivy: passed after updating `org.json:json` to `20231013`
- Existing release tags `v1.0.0`, `v1.0.1`, and `v1.1.0`: preserve; do not move

## Version Mismatch Issue

The GitHub release is `v1.1.0`, but the Maven project, generated JAR name, API health response, CLI version output, telemetry resource attributes, and documentation still report or reference `1.0.0`.

Observed current version sources and references:

- `pom.xml`
  - Project version is `<version>1.0.0</version>`.
  - This drives generated artifacts such as `target\LoadBalancerPro-1.0.0.jar`.
- `src/main/java/api/AllocatorController.java`
  - `/api/health` uses `private static final String VERSION = "1.0.0"`.
  - Response currently reports `{"status":"ok","version":"1.0.0"}`.
- `src/main/java/cli/LoadBalancerCLI.java`
  - CLI uses `private static final String VERSION = "1.0.0"`.
  - `--version` prints `LoadBalancerCLI version 1.0.0`.
- `src/main/resources/application.properties`
  - `management.opentelemetry.resource-attributes[service.version]=1.0.0`
  - `info.app.version=1.0.0`
- Tests
  - `src/test/java/api/AllocatorControllerTest.java` expects `/api/health` version `1.0.0`.
  - `src/test/java/api/OpenTelemetryMetricsConfigurationTest.java` expects telemetry `service.version` `1.0.0`.
- README
  - Multiple examples reference `target/LoadBalancerPro-1.0.0.jar`.
- Docker and CI
  - Dockerfile and CI mostly discover `target/LoadBalancerPro-*.jar` dynamically.
  - Docker healthcheck and CI smoke scripts hit `/api/health` but do not appear to assert a specific version string.

## Recommended v1.1.1 Patch Scope

Recommendation: use `v1.1.1` for the cleanup patch because `v1.1.0` is already published and must not be moved. The Maven project version should become `1.1.1` for this patch release so generated artifacts, API health, CLI output, telemetry metadata, and documentation line up with the next immutable Git tag.

Patch scope:

- Update Maven project version from `1.0.0` to `1.1.1`.
- Make `/api/health` version derive from project/application metadata instead of a hard-coded controller constant.
- Make CLI `--version` derive from the same source if practical; otherwise update it in the same patch and document the remaining source-of-truth limitation.
- Update `application.properties` so `info.app.version` and telemetry `service.version` align with the project version.
- Prefer a single source of truth if practical:
  - Option A: Maven resource filtering for `application.properties` using project version tokens.
  - Option B: Spring Boot build info metadata and `BuildProperties`.
  - Option C: a small application version properties class shared by API and CLI.
- Update tests that assert `1.0.0` to assert the new metadata-driven version.
- Update README examples and release/backlog docs that should point to the current JAR name.
- Keep Dockerfile and CI dynamic JAR discovery unless final implementation proves a hard-coded path remains.
- Run full verification:
  - `mvn -q test`
  - `mvn -q -DskipTests package`
  - inspect newest `target\LoadBalancerPro-*.jar`
  - LASE healthy, overloaded, and invalid-name smokes
  - Docker build and `/api/health` smoke
  - dependency tree check for `org.json:json`
  - GitHub Actions and Trivy after pushing a review branch
- Create and push `v1.1.1` only after local verification and GitHub Actions pass.

Do not retag `v1.1.0`, do not move `v1.0.1`, and do not alter public `main`.

## Recommended v1.2.0 Feature Scope

Use `v1.2.0` for visible product/demo improvements and larger feature work after the version cleanup patch is complete.

Candidate scope:

- Real demo polish:
  - cleaner guided demo path
  - more portfolio-friendly sample data
  - clearer local run commands
  - predictable screenshots or demo scripts
- README screenshots/GIF:
  - API health response
  - LASE demo output
  - Docker health smoke
  - optional UI/CLI flow if stable
- Classic algorithms:
  - power of two choices (P2C)
  - weighted routing
  - consistent hashing
  - comparison examples and tests
- Stronger observability examples:
  - Prometheus scrape example
  - sample metrics output
  - OTLP collector example for safe local use
  - dashboards or documented queries if lightweight
- Optional branch protection:
  - require CI on `loadbalancerpro-clean`
  - require signed or reviewed release tags if desired
  - protect public `main` from accidental writes
- Deferred large JavaFX history cleanup plan:
  - audit current repository size and largest blobs
  - decide whether history rewrite is worth the disruption
  - coordinate backup, branch freeze, and force-push policy only if explicitly approved later

## Risks

- Version cleanup is simple conceptually but touches cross-cutting surfaces: Maven artifact naming, API response, CLI output, telemetry metadata, tests, README examples, Docker smoke expectations, and release docs.
- Retagging `v1.1.0` would damage release provenance; create `v1.1.1` instead.
- Maven resource filtering can accidentally modify resource behavior if applied too broadly.
- Spring Boot build-info metadata is clean but may require plugin configuration and test updates.
- CLI version sourcing can be awkward because CLI/demo paths may run before Spring context startup.
- README examples may become stale again if JAR names are hard-coded; consider documenting wildcard discovery commands where possible.
- Large JavaFX history cleanup would require history rewrite and coordination; defer until it is a planned maintenance task, not part of a patch release.

## Proposed Command Plan

DO NOT RUN YET:

```powershell
git status
git switch loadbalancerpro-clean
git pull --ff-only origin loadbalancerpro-clean
git switch -c release/v1.1.1-version-alignment

rg -n "1\.0\.0|LoadBalancerPro-1\.0\.0|service.version|info.app.version|VERSION" pom.xml src/main/java src/main/resources src/test/java README.md Dockerfile .github docs

# Implement the smallest reviewed version-alignment patch:
# - Update pom.xml project version to 1.1.1.
# - Replace hard-coded API health version with project/application metadata.
# - Align CLI --version if practical.
# - Align application.properties info.app.version and service.version.
# - Update version-sensitive tests.
# - Update README/JAR references where appropriate.

git diff -- pom.xml src/main/java src/main/resources src/test/java README.md docs

mvn -q test
mvn -q -DskipTests package

Get-ChildItem -Path target -Filter 'LoadBalancerPro-*.jar' | Sort-Object LastWriteTime -Descending | Select-Object FullName,Name,Length,LastWriteTime
java -jar target\LoadBalancerPro-1.1.1.jar --lase-demo=healthy
java -jar target\LoadBalancerPro-1.1.1.jar --lase-demo=overloaded
java -jar target\LoadBalancerPro-1.1.1.jar --lase-demo=invalid-name

docker build -t loadbalancerpro:v1.1.1-version-alignment .
docker run --rm -d --name loadbalancerpro-v111-check -p 127.0.0.1:18081:8080 loadbalancerpro:v1.1.1-version-alignment
curl.exe -fsS http://127.0.0.1:18081/api/health
docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' loadbalancerpro-v111-check
docker stop loadbalancerpro-v111-check

mvn dependency:tree "-Dincludes=org.json:json" "-DoutputType=text"

git status
git add pom.xml src/main/java src/main/resources src/test/java README.md docs/NEXT_BACKLOG_AFTER_V1_1_0.md
git commit -m "Align project version metadata for v1.1.1"

git push origin release/v1.1.1-version-alignment
git push public release/v1.1.1-version-alignment:release/v1.1.1-version-alignment

# After GitHub Actions and Trivy pass on the review branch:
git switch loadbalancerpro-clean
git merge --no-ff release/v1.1.1-version-alignment -m "Merge v1.1.1 version alignment"
mvn -q test
mvn -q -DskipTests package
docker build -t loadbalancerpro:v1.1.1-final-check .

git push origin loadbalancerpro-clean
git push public loadbalancerpro-clean:loadbalancerpro-clean

# After branch CI passes:
git tag -a v1.1.1 -m "LoadBalancerPro v1.1.1: version metadata alignment"
git push origin v1.1.1
git push public v1.1.1
```

## Recommendation

Do a conservative `v1.1.1` patch first for version metadata alignment. Keep the scope narrow, preserve all existing tags, and avoid public `main`. After `v1.1.1` is verified and tagged, start a separate `v1.2.0` branch for demo polish, screenshots/GIFs, classic routing algorithm examples, observability examples, branch protection planning, and the deferred large-history cleanup decision.
