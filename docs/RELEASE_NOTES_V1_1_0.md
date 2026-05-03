# LoadBalancerPro v1.1.0 Release Notes

## Release Title

LoadBalancerPro v1.1.0: Robust Safety and Supply-Chain Hardening

## Summary

LoadBalancerPro v1.1.0 is a hardening release built on the verified v1.0.1 public baseline. It adds stronger safety guardrails, broader failure-mode test coverage, pinned supply-chain inputs, and a Trivy-verified dependency fix for `CVE-2023-5072`.

The release was verified on `loadbalancerpro-clean` at commit `b57bec688bf08f19718022016d580f3f484af8d7`. Public `main` remains preserved, while the public default branch is `loadbalancerpro-clean`.

## Major Improvements

- Expanded robust safety coverage for API input handling, production authentication guardrails, telemetry startup validation, replay/cloud isolation, LASE shadow advisor behavior, and utility import/export edge cases.
- Hardened structured API error handling for unsupported methods and content types.
- Strengthened cloud sandbox safety by requiring sandbox live-scaling resource names to use the `lbp-sandbox-` prefix.
- Added review documentation for v1.1.0 hardening, supply-chain pinning, and the CI Trivy failure remediation.

## Robust Safety Hardening

- API `405 Method Not Allowed` and `415 Unsupported Media Type` responses now return structured JSON error bodies.
- Cloud sandbox live scaling now requires `cloud.resourceNamePrefix` to start with `lbp-sandbox-`.
- LASE shadow advisor behavior was tightened around validation, redaction, low-sample handling, and deterministic recommendations.
- Load balancer safety behavior and edge-case validation received expanded tests.
- Robust test expansion increased coverage to 503 passing tests.

## Supply-Chain Hardening

- GitHub Actions are pinned to reviewed commit SHAs while preserving comments for upstream action names and version tags.
- Docker build and runtime base images are pinned by digest.
- README guidance now documents the update process for pinned actions and Docker digests.
- CI continues to run Docker build, container health/API smoke checks, dependency review, and Trivy scanning.

## CVE Fix

- Fixed Trivy finding: `CVE-2023-5072`
- Vulnerable package: `org.json:json`
- Previous version: `20230227`
- Fixed version: `20231013`
- Change: direct Maven dependency updated from `org.json:json:20230227` to `org.json:json:20231013`
- `.trivyignore` was not used because the vulnerability was real and fixable.

## Verification

- `mvn -q test`: passed, 503 tests, 0 failures, 0 errors, 0 skipped
- `mvn -q -DskipTests package`: passed
- LASE healthy smoke: passed
- LASE overloaded smoke: passed
- LASE invalid-name smoke: safe expected failure
- Docker build: passed
- Docker `/api/health` smoke: passed
- Docker healthcheck: healthy
- GitHub Actions on `release/v1.1.0-hardening-review`: passed
- GitHub Actions on `loadbalancerpro-clean`: passed
- Trivy: passed after the `org.json:json` update to `20231013`
- Resolved dependency: `org.json:json:jar:20231013:compile`

## Upgrade Notes

- Sandbox live scaling now requires `cloud.resourceNamePrefix` to start with `lbp-sandbox-`. Existing sandbox configs using another live-scaling prefix must be updated before live scaling will proceed.
- API `405` and `415` errors now return structured JSON responses instead of unstructured/default error handling.
- Supply-chain updates should refresh pinned GitHub Action SHAs and Docker digests intentionally, with CI and Trivy verification.

## Known Notes

- Public `main` remains preserved at its historical smaller state.
- The public default branch is `loadbalancerpro-clean`.
- The historical large JavaFX binary remains in Git history. History cleanup is deferred to a separate reviewed maintenance task.
- The Maven project version and generated JAR name remain `1.0.0` / `LoadBalancerPro-1.0.0.jar` in this release; the Git release tag is `v1.1.0`.

## Suggested GitHub Release Body

```markdown
## LoadBalancerPro v1.1.0: Robust Safety and Supply-Chain Hardening

LoadBalancerPro v1.1.0 is a hardening release built on the verified v1.0.1 public baseline. It adds stronger safety guardrails, broader failure-mode test coverage, pinned supply-chain inputs, and a Trivy-verified dependency fix for `CVE-2023-5072`.

### Major Improvements

- Expanded robust safety tests across API input handling, production auth guardrails, telemetry startup validation, replay/cloud isolation, LASE shadow advisor behavior, and utility import/export edge cases.
- Structured JSON responses for API `405 Method Not Allowed` and `415 Unsupported Media Type` errors.
- Stronger cloud sandbox live-scaling guardrail: `cloud.resourceNamePrefix` must start with `lbp-sandbox-`.
- Pinned GitHub Actions to reviewed commit SHAs.
- Pinned Docker build/runtime base images by digest.
- Fixed `org.json:json` `CVE-2023-5072` by updating `20230227 -> 20231013`.

### Verification

- `mvn -q test`: 503 tests passed, 0 failures, 0 errors, 0 skipped
- `mvn -q -DskipTests package`: passed
- LASE healthy and overloaded smokes: passed
- LASE invalid-name smoke: safe expected failure
- Docker build: passed
- Docker `/api/health` smoke and healthcheck: passed
- GitHub Actions: passed
- Trivy: passed

### Upgrade Notes

- Sandbox live scaling now requires `cloud.resourceNamePrefix` to start with `lbp-sandbox-`.
- API `405` and `415` errors now return structured JSON.
- Supply-chain pin refreshes should be handled as focused changes with CI and Trivy verification.

### Known Notes

- Public `main` remains preserved; the public default branch is `loadbalancerpro-clean`.
- The historical large JavaFX binary remains in Git history and is deferred to a separate reviewed maintenance task.
- The Maven project version and generated JAR name remain `1.0.0` / `LoadBalancerPro-1.0.0.jar`; the Git release tag is `v1.1.0`.
```
