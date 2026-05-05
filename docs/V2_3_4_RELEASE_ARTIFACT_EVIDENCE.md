# v2.3.4 Release Artifact Evidence

## Summary

- Release version: v2.3.4
- Release purpose: Caffeine dependency maintenance
- Dependency update: `com.github.ben-manes.caffeine:caffeine` 3.1.8 -> 3.2.4
- Release commit: `a7a21e24f8e678c641ab7810bd6b62f48e18e1f5`
- Tag object: `5d31424c6ef88fdf66a062d62be253a1abf2d47b`
- Workflow run: Release Artifacts #13
- Artifact bundle: `loadbalancerpro-release-2.3.4`
- Artifact ID: `6797060530`
- Artifact size: 81,221,315 bytes
- Artifact expired: false
- Artifact contents manually verified through GitHub UI ZIP download

## Expected Artifact Files

- `LoadBalancerPro-2.3.4.jar`
- `LoadBalancerPro-2.3.4-bom.json`
- `LoadBalancerPro-2.3.4-bom.xml`
- `LoadBalancerPro-2.3.4-SHA256SUMS.txt`

## Verification Summary

- `mvn -q test` passed
- `mvn -q -DskipTests package` passed
- JAR `--version` reported 2.3.4
- LASE healthy demo passed
- LASE overloaded demo passed
- LASE invalid-name exited 2 as expected
- CycloneDX SBOM generation passed
- SHA-256 checksum generation and verification passed
- JAR provenance attestation passed
- JAR SBOM attestation passed
- `git diff --check` passed

## License Note

- Dependency Review had a non-blocking unknown-license warning for Caffeine.
- Authoritative Caffeine metadata confirms Apache License 2.0.
- Dependency Review reported 0 vulnerabilities, 0 incompatible licenses, and 0 invalid SPDX license definitions.

## Workflow Warning Note

- Release Artifacts #13 showed a Node.js 20 deprecation warning for GitHub Actions.
- This does not invalidate v2.3.4.
- Future workflow maintenance should review `actions/checkout`, `actions/setup-java`, and `actions/upload-artifact` separately.

## Safety Notes

- No behavior changes intended
- No risky PRs bundled
- No namespace migration started
- `public/main` untouched
