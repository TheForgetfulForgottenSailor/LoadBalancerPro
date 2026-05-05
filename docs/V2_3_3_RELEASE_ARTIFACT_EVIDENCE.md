# v2.3.3 Release Artifact Evidence

## Summary

- Release version: v2.3.3
- Release purpose: Gson dependency maintenance
- Dependency update: `com.google.code.gson:gson` 2.10.1 -> 2.14.0
- Release commit: `27718d5807f7c5cf52b2b81574c570255068917d`
- Tag object: `45e74e2a2435685d20a0259883272f1e3a80e5bd`
- Artifact workflow: Release Artifacts #12
- Artifact bundle: `loadbalancerpro-release-2.3.3`

## Expected Artifact Files

- `LoadBalancerPro-2.3.3.jar`
- `LoadBalancerPro-2.3.3-bom.json`
- `LoadBalancerPro-2.3.3-bom.xml`
- `LoadBalancerPro-2.3.3-SHA256SUMS.txt`

## Verification Summary

- `mvn -q test` passed
- `mvn -q -DskipTests package` passed
- JAR `--version` reported 2.3.3
- LASE healthy demo passed
- LASE overloaded demo passed
- LASE invalid-name exited 2 as expected
- CycloneDX SBOM generation passed
- `git diff --check` passed

## Safety Notes

- No behavior changes intended
- No namespace migration started
- No risky PRs bundled
- `public/main` untouched
