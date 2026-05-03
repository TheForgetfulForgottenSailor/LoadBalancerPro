# LoadBalancerPro v1.1.0 CI Failure Review

Date: 2026-05-03

## Failed CI Run

- Branch: `release/v1.1.0-hardening-review`
- Failed commit: `30e81f31a9a40abe0b7a4dfb727804c2d35f659f`
- Failed step: `Scan Docker image`
- Scanner: Trivy
- Finding type: Java package inside packaged JAR

## Vulnerability

- CVE: `CVE-2023-5072`
- Severity: HIGH
- Title: JSON-java parser confusion leads to OOM
- Vulnerable dependency: `org.json:json`
- Vulnerable version: `20230227`
- Fixed version required by the CI finding: `20231013`

## Chosen Fix

Updated the direct Maven dependency `org.json:json` from `20230227` to `20231013`.

Maven Central has newer `org.json:json` releases after `20231013`, including 2025 releases, but this branch uses the smallest safe update because the goal is to fix the real Trivy vulnerability without unrelated dependency churn. `20231013` is the first fixed version for `CVE-2023-5072` and should remove the high-severity finding while keeping behavioral change scope small.

## Why `.trivyignore` Was Not Used

`.trivyignore` was not changed because this is a real fixable dependency vulnerability. Ignoring the CVE would leave the vulnerable parser version inside the Docker image and weaken the release gate. The CI scan should continue to fail on high or critical fixed vulnerabilities.

## Verification Results

- Resolved dependency check: `org.json:json:jar:20231013:compile`
- `mvn -q test`: passed, 503 tests, 0 failures, 0 errors, 0 skipped
- `mvn -q -DskipTests package`: passed
- Packaged JAR: `target\LoadBalancerPro-1.0.0.jar`
- LASE healthy smoke: passed, exit code 0
- LASE overloaded smoke: passed, exit code 0
- LASE invalid-name smoke: safe expected failure, exit code 2
- Docker build: passed for `loadbalancerpro:v1.1.0-cve-2023-5072-fix`
- Docker `/api/health` smoke: passed with `{"status":"ok","version":"1.0.0"}`
- Docker healthcheck: `healthy`
- Local Trivy: not installed locally

## Remaining CI Verification Needed

After this fix is committed and pushed to the review branch, verify GitHub Actions again. The required CI confirmation is that the pinned GitHub Action SHAs still resolve and the Trivy `Scan Docker image` step no longer reports `org.json:json` `CVE-2023-5072`.
