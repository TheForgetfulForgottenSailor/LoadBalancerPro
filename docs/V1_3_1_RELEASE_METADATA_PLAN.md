# LoadBalancerPro v1.3.1 Release Metadata Plan

Date: 2026-05-03

## A. Current v1.3.0 Release State

- `v1.3.0` is shipped and pushed to `origin/loadbalancerpro-clean`.
- Release branch: `loadbalancerpro-clean`.
- Remote release branch hash: `2dfdc113a210e89dfb2de56b1ac0c1dde24bf1ea`.
- Annotated tag: `v1.3.0`.
- Remote tag object: `ac564e3e37410ee4cbc71000b2a64763a90355e0`.
- Remote tag target: `2dfdc113a210e89dfb2de56b1ac0c1dde24bf1ea`.
- `v1.3.0` added `WEIGHTED_LEAST_LOAD`, optional routing-only weight support, README routing documentation, and focused tests.
- `CloudManager`, AWS mutation paths, existing allocation endpoints, CLI workflows, and `TAIL_LATENCY_POWER_OF_TWO` behavior were intentionally unchanged.
- The `v1.3.0` tag must remain immutable. Do not move, delete, or replace it.
- Public `main` remains untouched and should stay untouched.

## B. Audit Findings

The audit confirms that `v1.3.0` shipped with active release metadata and active README examples still reporting `1.2.1`. These are active release-facing mismatches and should be corrected in a new patch release rather than by moving `v1.3.0`.

Audit command used:

```text
rg -n "1\.2\.1|LoadBalancerPro-1\.2\.1|1\.3\.0|LoadBalancerPro-1\.3\.0|service.version|info.app.version|loadbalancerpro.app.version|FALLBACK_VERSION|VERSION" pom.xml src/main/resources src/main/java README.md src/test/java docs
```

Active release metadata still reporting `1.2.1`:

- `pom.xml`
  - Maven project version is still `<version>1.2.1</version>`.
- `src/main/resources/application.properties`
  - `management.opentelemetry.resource-attributes[service.version]=1.2.1`
  - `loadbalancerpro.app.version=1.2.1`
  - `info.app.version=1.2.1`
- `src/main/java/api/LoadBalancerApiApplication.java`
  - `FALLBACK_VERSION` is still `1.2.1`.
  - This affects packaged `--version` fallback behavior when implementation metadata is unavailable.
- `src/main/java/cli/LoadBalancerCLI.java`
  - CLI `VERSION` is still `1.2.1`.
  - This affects `LoadBalancerCLI --version`.
- `README.md`
  - Active packaged JAR examples still use `LoadBalancerPro-1.2.1.jar`.
  - Affected examples include local API startup, quick demo commands, local load-test startup, local/prod/cloud-sandbox profile startup, LASE demo commands, and offline LASE replay commands.
- `src/test/java/api/AllocatorControllerTest.java`
  - `/api/health` version expectation is still `1.2.1`.
- `src/test/java/api/OpenTelemetryMetricsConfigurationTest.java`
  - OpenTelemetry `service.version` expectation is still `1.2.1`.
- `src/test/java/api/LoadBalancerApiApplicationTest.java`
  - API fallback version expectation is still `1.2.1`.

Active `1.3.0` references:

- `docs/V1_3_0_WEIGHTED_LEAST_LOAD_PLAN.md` describes the already shipped `v1.3.0` feature plan.
- Active Maven, application properties, API fallback, CLI version, and README JAR examples do not currently report `1.3.0`; they still report `1.2.1`.

Historical docs with older version references:

- `docs/ENTERPRISE_READINESS_AUDIT.md`
- `docs/NEXT_BACKLOG_AFTER_V1_1_0.md`
- `docs/V1_1_1_VERSION_ALIGNMENT_REVIEW.md`
- `docs/V1_2_1_VERSION_AND_ROUTING_DOCS_PLAN.md`
- `docs/V1_3_0_WEIGHTED_LEAST_LOAD_PLAN.md`

These historical planning/review docs describe prior release states and should generally preserve old version numbers unless a future task explicitly updates release-facing docs. The v1.3.1 patch should not rewrite historical records merely because they mention older versions.

## C. Recommended Patch Release

A narrow `v1.3.1` patch is needed because active metadata still reports `1.2.1` after the shipped `v1.3.0` release.

Do not move, replace, or retag `v1.3.0`. The correct fix is a new patch release that aligns active release metadata and README examples to `1.3.1`.

## D. Exact Patch Scope For v1.3.1

The v1.3.1 implementation should be limited to release metadata, active README examples, and version-sensitive tests:

- Update Maven/project version to `1.3.1`.
- Update `/api/health` version properties to `1.3.1`.
- Update telemetry/app metadata to `1.3.1`:
  - `management.opentelemetry.resource-attributes[service.version]`
  - `info.app.version`
  - `loadbalancerpro.app.version`
- Update API fallback version to `1.3.1`.
- Update CLI `--version` metadata to `1.3.1`.
- Update README active JAR examples to `LoadBalancerPro-1.3.1.jar`.
- Update version-sensitive tests to expect `1.3.1`.
- Optionally add a short README or release-note sentence that `v1.3.1` is a metadata/docs patch after `v1.3.0`.

Patch should not include any routing algorithm, cloud, allocation, governance, deployment, SBOM, signing, or ops work.

## E. What Not To Change

- Do not move `v1.3.0`.
- Do not move or rewrite any existing tag.
- Do not change `WEIGHTED_LEAST_LOAD` behavior.
- Do not change optional routing-only weight behavior.
- Do not change `TAIL_LATENCY_POWER_OF_TWO` behavior.
- Do not change `RoutingComparisonService` behavior.
- Do not change `RoutingController` behavior.
- Do not change `CloudManager` or AWS behavior.
- Do not change existing allocation endpoints.
- Do not change CLI workflows beyond version metadata if needed.
- Do not touch public `main`.
- Do not broaden into governance, deployment, ops, SBOM, signing, or supply-chain work.

## F. Tests To Update If Patch Is Needed

- `src/test/java/api/AllocatorControllerTest.java`
  - Update `/api/health` version expectation from `1.2.1` to `1.3.1`.
- `src/test/java/api/OpenTelemetryMetricsConfigurationTest.java`
  - Update `service.version` expectation from `1.2.1` to `1.3.1`.
- `src/test/java/api/LoadBalancerApiApplicationTest.java`
  - Update fallback version expectation from `1.2.1` to `1.3.1`.
- CLI version test
  - No CLI `--version` test was identified by the audit search. If a CLI version test exists outside the searched paths or is added during implementation, update it to expect `1.3.1`.

## G. Verification Plan

Before implementation, run:

```text
rg -n "1\.2\.1|LoadBalancerPro-1\.2\.1|service.version|info.app.version|loadbalancerpro.app.version|FALLBACK_VERSION|VERSION" pom.xml src/main/resources src/main/java README.md src/test/java
```

After implementation, run:

```text
mvn -q test
mvn -q -DskipTests package
java -jar target\LoadBalancerPro-1.3.1.jar --version
java -jar target\LoadBalancerPro-1.3.1.jar --lase-demo=healthy
java -jar target\LoadBalancerPro-1.3.1.jar --lase-demo=overloaded
java -jar target\LoadBalancerPro-1.3.1.jar --lase-demo=invalid-name
git diff --check
```

The invalid-name LASE demo is expected to fail safely with valid-scenario guidance and no raw stack trace.

Protected-area check:

```text
git diff -- src/main/java/cloud src/main/java/core/CloudManager.java src/main/java/core/TailLatencyPowerOfTwoStrategy.java src/main/java/api/AllocatorController.java src/main/java/api/RoutingComparisonService.java src/main/java/api/RoutingController.java src/main/java/core/WeightedLeastLoadStrategy.java
```

Expected active metadata search after implementation:

- No active `1.2.1` or `LoadBalancerPro-1.2.1.jar` matches in `pom.xml`, `src/main/resources`, `src/main/java`, `README.md`, or `src/test/java`.
- Any remaining `1.2.1` references should be historical docs only.

## H. Recommendation

`v1.3.1` is needed.

Implement `v1.3.1` before any new feature work. Keep it narrow: align active release metadata, README JAR examples, and version-sensitive tests to `1.3.1`, then verify with the package and CLI/JAR smoke checks. Do not move `v1.3.0`, do not alter routing behavior, and do not expand the patch into enterprise hardening or operations work.
