# LoadBalancerPro Repo Truth Audit

Audit run: 2026-05-02, America/Los_Angeles

Scope: audit only. No production code was changed, no branches were merged, no files were deleted, no fetch/merge was run, and no cloud mutation logic was touched. The only intended repository change from this work is this audit file.

## Executive Finding

The local repository is not the same repository state as the public GitHub repo at `richmond423/LoadBalancerPro`.

The current local branch, `loadbalancerpro-clean`, is a much more complete integrated branch than the public repo. It is clean, tracks the configured `origin/loadbalancerpro-clean`, contains Spring Boot 3.x, AWS SDK v2, Docker, CI, LASE shadow/replay/evaluation code, observability, cloud-sandbox profile, and evidence/SBOM documentation. It also contains the local and configured-origin `v1.0.0` tag.

However, the configured remote is not `richmond423/LoadBalancerPro`; it is `https://github.com/cs-olympic/finalcs2-richmond423.git`. The public `richmond423/LoadBalancerPro` repo advertises only a `main` branch at `d09701dc31a5cedaab8c6b21ee85bac31b0930ab`, has 3 commits on the GitHub page, and does not advertise tags/releases. That public commit is not present in this local repository.

The safest conclusion is: `loadbalancerpro-clean` is the most complete integrated local branch, but the public repo is not carrying that completed state.

## Repo Identity And Remotes

Current branch:

```text
loadbalancerpro-clean
```

Working tree before this audit file was written:

```text
On branch loadbalancerpro-clean
Your branch is up to date with 'origin/loadbalancerpro-clean'.
nothing to commit, working tree clean
```

Configured remote:

```text
origin  https://github.com/cs-olympic/finalcs2-richmond423.git (fetch)
origin  https://github.com/cs-olympic/finalcs2-richmond423.git (push)
```

Important mismatch:

```text
Expected public repo named by user: https://github.com/richmond423/LoadBalancerPro.git
Configured origin:                 https://github.com/cs-olympic/finalcs2-richmond423.git
```

Current HEAD:

```text
812edbe30beb946206fd8391c0a0a38479218a3a
```

Configured origin branch check:

```text
origin/loadbalancerpro-clean = 812edbe30beb946206fd8391c0a0a38479218a3a
```

Public repo check:

```text
https://github.com/richmond423/LoadBalancerPro.git
refs/heads/main = d09701dc31a5cedaab8c6b21ee85bac31b0930ab
```

`git cat-file -t d09701dc31a5cedaab8c6b21ee85bac31b0930ab` failed locally, so the public repo commit is not present in the local object database.

## Branches

Local branches include many Codex/history branches. The current checked-out branch is:

```text
* loadbalancerpro-clean
```

Remote-tracking branches currently present locally:

```text
origin/codex/loadbalancer-api-layer
origin/codex/loadbalancer-responsibility-split
origin/codex/production-readiness-cleanup
origin/loadbalancerpro-clean
origin/main
origin/master
origin/repo-hygiene-and-canonical-layout
```

Branches not merged into `loadbalancerpro-clean`:

```text
codex/loadbalancer-responsibility-split
codex/robust-safety-test-expansion
codex/supply-chain-pinning
main
```

Interpretation:

- `loadbalancerpro-clean` is the latest and most complete integrated branch by commit date and feature surface.
- `codex/robust-safety-test-expansion` contains four additional safety-test commits not merged into `loadbalancerpro-clean`, but it lacks the later evidence/SBOM/supply-chain documentation commits.
- `codex/supply-chain-pinning` contains one unmerged supply-chain-pinning commit, but it lacks later auth/telemetry/evidence documentation work.
- `codex/loadbalancer-responsibility-split` appears to be an older divergent branch with generated docs, vendored libraries, and a very different layout; it is not the clean completed branch.
- Local `main` is old/divergent and is not the completed state.

## Latest Commits

Latest commits from `git log --oneline --decorate --graph --all -25`:

```text
812edbe (HEAD -> loadbalancerpro-clean, origin/loadbalancerpro-clean) Merge manual CycloneDX SBOM docs
f21d250 (codex/cyclonedx-sbom-audit) Document manual CycloneDX SBOM generation
ea89e7f Merge resilience score supply chain note
1bb5073 (codex/resilience-score-supply-chain-note) Update resilience score supply chain note
ad1164f Merge supply chain evidence docs
6326632 (codex/supply-chain-evidence) Add supply chain evidence docs
5351309 Merge resilience scorecard evidence
ec3a241 (codex/resilience-score) Add resilience scorecard evidence
a4af723 Merge residual risk register
2a408d5 (codex/residual-risk-register) Upgrade residual risk register
e0340c5 Merge safety invariants evidence
0864bb2 (codex/safety-invariants) Add safety invariants evidence
09b5afc Merge threat model evidence
559d043 (codex/threat-model) Add threat model evidence
db76e1d Merge evidence foundation docs
e0ac685 (codex/evidence-foundation) Add evidence foundation docs
f4f4e92 (codex/robust-safety-test-expansion) Add input API hardening tests
32b1b76 Add replay cloud isolation tests
b837e82 Add telemetry guardrail edge tests
928be4a Add robust safety tests batch 1
fa3bd9e Merge project hardening audit event
ae332c1 (codex/project-hardening-audit-event) Document hardening audit results
b300c79 Harden audit safety guardrails
1b57a13 Merge OpenTelemetry metrics guardrails
69bbd26 (codex/opentelemetry-metrics) Add telemetry endpoint guardrails
```

Commits after the local `v1.0.0` tag on current HEAD:

```text
27 commits after v1.0.0
```

The `v1.0.0` tag target is:

```text
8992364352954c8b48fc09545e7e6ec0e30f2dd3
Bump metadata for v1.0.0
```

## Tags And Releases

Local tags:

```text
v1.0-rc1
v1.0-rc2
v1.0-rc3
v1.0-rc4
v1.0-rc5
v1.0-rc6
v1.0-rc7
v1.0-rc8
v1.0-rc9
v1.0.0
```

Local tag refs:

```text
3758844c3575fa06ff27c818c65ce52c9b647a9e refs/tags/v1.0-rc1
0b763c93ff267d28c2409e36160dd9e7d26cf9f8 refs/tags/v1.0-rc2
a50879b9ea9eac5ff643f4365b0e550158fe0250 refs/tags/v1.0-rc3
7e173f14620582d549da03f1da21e648e46bace2 refs/tags/v1.0-rc4
20cd6b61d4b2d02bc23c48408bcbf8c243515e56 refs/tags/v1.0-rc5
1a835f4d0c41cc21964e0c6f51e6b7b3fddd275a refs/tags/v1.0-rc6
b9dbe2075cd061ca9b1db8a05e1ac03482386be7 refs/tags/v1.0-rc7
8b9139084f2fcf9ec50a663c1b9d7aa698add1e6 refs/tags/v1.0-rc8
eba8abdc87ff9d92d66421005b9d09f8ac5a3cc4 refs/tags/v1.0-rc9
1bd01e60bd8f066ee67d6d9127e8a27425ad1a0b refs/tags/v1.0.0
```

Configured `origin` remote tag check:

- `v1.0.0` exists on `origin`.
- `v1.0-rc1`, `v1.0-rc3`, `v1.0-rc4`, `v1.0-rc5`, `v1.0-rc6`, `v1.0-rc7`, `v1.0-rc8`, and `v1.0-rc9` exist on `origin`.
- `v1.0-rc2` exists locally but was not advertised by `origin` during `git ls-remote`.

Public `richmond423/LoadBalancerPro` tag/release check:

- `git ls-remote --heads --tags https://github.com/richmond423/LoadBalancerPro.git` returned only `refs/heads/main`.
- The GitHub page reports "No releases published".
- Therefore `v1.0.0` exists locally and on configured `origin`, but not on the public repo named in the prompt.

## Project Structure

`tree /f` succeeded. Relevant top-level structure:

```text
.dockerignore
.gitattributes
.gitignore
.trivyignore
Dockerfile
HARDENING_AUDIT.md
README.md
pom.xml
.github/workflows/ci.yml
evidence/
examples/
GUI/
postman/
resources/
scripts/
src/main/java/api/
src/main/java/cli/
src/main/java/core/
src/main/java/gui/
src/main/java/util/
src/main/resources/
src/test/java/api/
src/test/java/cli/
src/test/java/core/
src/test/java/util/
target/
```

Notes:

- `target/` exists locally with build/test artifacts and previous JAR artifacts, but is not tracked.
- No `docker-compose.yml`, `docker-compose.yaml`, or similarly named compose file was found.
- No generated SBOM file such as `bom.xml`, `bom.json`, or `cyclonedx*.json` was found.

## Build And Test Verification

Command requested:

```text
mvn -q test
```

Initial sandboxed attempt failed before running tests:

```text
Could not create local repository at C:\Users\CodexSandboxOffline\.m2\repository
Access is denied.
```

Rerun with approved access to the normal Maven local repository passed:

```text
Exit code: 0
```

Surefire report totals from `target/surefire-reports/TEST-*.xml`:

```text
Test suites: 46
Tests:       464
Failures:   0
Errors:     0
Skipped:    0
```

Maven warnings observed during the passing run:

- SpringDoc warns that `/v3/api-docs` and `/swagger-ui.html` are enabled by default in tested contexts.
- Mockito/Byte Buddy emits a future-JDK dynamic-agent warning.
- Some tests intentionally validate fail-closed startup behavior and therefore log expected Spring context startup failures.

## Advanced Feature Checks

| Expected feature | Actual state |
| --- | --- |
| AWS SDK v2 dependencies | Present. `pom.xml` uses `software.amazon.awssdk:bom` version `2.42.35` and SDK v2 modules `autoscaling`, `cloudwatch`, and `ec2`. Source imports are SDK v2. |
| Spring Boot 3.x | Present. `pom.xml` has `spring-boot.version` = `3.5.14`. |
| Dockerfile | Present. Root `Dockerfile` exists and README/CI describe Docker build and healthcheck behavior. |
| docker-compose | Missing. No compose file found. README does not appear to claim one. |
| CI workflows | Present locally. `.github/workflows/ci.yml` runs dependency tree, tests, package, LASE demo smoke checks, packaged JAR smoke, Docker build/runtime smoke, Trivy, and dependency review. |
| Security scan configuration | Present but scoped. CI uses Trivy image scan and GitHub dependency review. `.trivyignore` exists with comments only. No Maven OWASP dependency-check, SpotBugs, Semgrep, Snyk, or SARIF config was found. |
| LASE shadow advisor classes/endpoints | Present. Core classes include `LaseShadowAdvisor`, event log/snapshot/summary classes, and `GET /api/lase/shadow` exists in `AllocatorController`. Shadow evaluation is disabled by default in properties. |
| Observability/metrics endpoints | Present. Actuator, Prometheus registry, and OTLP metrics registry are configured. Local profile exposes health/info/metrics/prometheus. Prod and cloud-sandbox profiles expose health/info by default. |
| Replay/evaluation lab | Present. Classes and CLI commands exist for LASE demo, evaluation engine, replay reader/metrics/report formatting, and README documents offline replay. |
| Cloud sandbox profile | Present. `src/main/resources/application-cloud-sandbox.properties` exists with dry-run/live-mutation-disabled defaults, API-key protection expectations, capacity caps, and sandbox resource prefix defaults. |
| SBOM docs | Present as documentation only. `evidence/SBOM_GUIDE.md` documents a manual CycloneDX path. No generated SBOM artifact or automated CycloneDX build/CI gate was found. |
| Evidence docs | Present. `evidence/` contains hardening audit, residual risks, resilience score, safety invariants, SBOM guide, security posture, supply-chain evidence, test evidence, and threat model. |
| Total test count | Current branch reports 464 tests, 0 failures, 0 errors, 0 skipped. |

## Actual Major Features Present

- Spring Boot REST API with `/api/health`, `/api/allocate/capacity-aware`, `/api/allocate/predictive`, and `/api/lase/shadow`.
- Request validation, structured error response path, request size limit filter, CORS/security-header configuration, API-key auth, and OAuth2 resource-server configuration.
- Core load allocation, server state, capacity-aware and predictive allocation behavior.
- AWS SDK v2 guarded CloudManager integration with dry-run defaults, mutation guardrails, account/region allow-list logic, resource prefix checks, and deletion ownership checks.
- LASE internal lab pieces: adaptive concurrency, load shedding, shadow autoscaling, failure scenario modeling, telemetry-aware scoring/routing foundations, shadow advisor, observability snapshot, and offline replay.
- Actuator and metrics support through Spring Boot Actuator, Micrometer Prometheus, and Micrometer OTLP registry.
- Docker runtime support through a root `Dockerfile`.
- CI workflow with Maven tests/package, smoke checks, Docker smoke, Trivy image scan, and dependency review.
- Evidence documentation under `evidence/`.

## Major Features Missing Or Not Proven

- Public repo publication is missing: the completed local state is not present at `richmond423/LoadBalancerPro`.
- Public `v1.0.0` release/tag is missing from `richmond423/LoadBalancerPro`.
- Docker Compose is not present.
- Generated SBOM artifact is not present; SBOM support is documented as a manual process only.
- Automated CycloneDX/SBOM CI gate is not present.
- Static analysis/security tooling beyond Trivy image scan and GitHub dependency review was not found.
- Live AWS sandbox validation was not run and is not proven by this audit.
- LASE is still mostly shadow/internal/lab behavior; the README correctly says it is not production autoscaling or a production cloud load balancer.
- The unmerged `codex/robust-safety-test-expansion` branch contains additional test work not currently in `loadbalancerpro-clean`.
- The unmerged `codex/supply-chain-pinning` branch contains supply-chain-pinning work not currently in `loadbalancerpro-clean`.

## README Truth Check

Current local README is mostly consistent with the local `loadbalancerpro-clean` codebase:

- Java 17 / Spring Boot claim matches `pom.xml`.
- AWS SDK v2 claim matches `pom.xml` and source imports.
- Guarded AWS mutation and dry-run safety claims are supported by CloudManager/CloudConfig code and tests.
- Docker claim is supported by `Dockerfile` and CI.
- CI claim is supported by `.github/workflows/ci.yml`.
- Observability endpoint claims match Actuator/Micrometer dependencies and property profiles.
- LASE shadow endpoint and replay lab claims match code and tests.
- Test-suite claim is supported by the passing `mvn -q test` run with 464 tests and zero skipped tests.
- SBOM claim is carefully worded as manual documentation only, which matches the repo state.

README issues or stale areas:

- The roadmap section still lists adaptive concurrency, load shedding, shadow autoscaling, and failure scenario simulation as planned/backlog items, while those now exist as internal lab/demo components. The README later documents them as part of the safe LASE synthetic demo. This is inconsistent and should be cleaned up later.
- The README says the internal routing foundation is not wired into public allocation flows. That appears accurate.
- The README does not claim Docker Compose, and no compose file exists.
- The README does not claim generated SBOM artifacts or SBOM CI enforcement, and none were found.
- The README describes local/CI completed state, but that state is not reflected in the public `richmond423/LoadBalancerPro` repository.

Public GitHub README/state mismatch:

- The public GitHub page for `richmond423/LoadBalancerPro` shows only a small repo with 3 commits and top-level `src`, `.gitattributes`, `.gitignore`, `README.md`, and `pom.xml`.
- The public README shown there describes a smaller core/CLI/API project with "100+ tests", not the 464-test completed local state.
- The public page reports no releases published.

## Does This Match Claimed Completed v1.0.0?

Local/configured-origin answer:

```text
Mostly yes, with nuance.
```

The local `v1.0.0` tag exists and points to commit `8992364352954c8b48fc09545e7e6ec0e30f2dd3`. The current `loadbalancerpro-clean` branch is 27 commits ahead of that tag and appears more complete than the tagged release because it includes later auth, telemetry guardrail, evidence, supply-chain, resilience, and SBOM documentation updates.

Public repo answer:

```text
No.
```

The public repo `richmond423/LoadBalancerPro` does not match the completed local state. It advertises only `main` at `d09701dc31a5cedaab8c6b21ee85bac31b0930ab`, no tags, no releases, and a much smaller project surface.

## Recommended Next Safest Action

Do not start feature coding yet.

Recommended next step:

1. Decide the canonical destination repo: either keep using `cs-olympic/finalcs2-richmond423` as canonical, or intentionally publish/mirror the completed branch to `richmond423/LoadBalancerPro`.
2. Before publishing, review the unmerged branches `codex/robust-safety-test-expansion` and `codex/supply-chain-pinning` to decide whether their deltas should be merged/cherry-picked into `loadbalancerpro-clean`.
3. After canonical branch selection, push the selected branch and tags to the intended public repo, especially `loadbalancerpro-clean` and `v1.0.0`, or rewrite the public README to avoid claiming the completed state.
4. Then rerun `mvn -q test` and, if Docker is available, run the Docker/CI smoke path before declaring the public repo release-ready.

Until that is done, the safest wording is:

```text
The completed LoadBalancerPro work exists locally on loadbalancerpro-clean and on the configured cs-olympic origin, but it is not currently published to the public richmond423/LoadBalancerPro repository.
```
