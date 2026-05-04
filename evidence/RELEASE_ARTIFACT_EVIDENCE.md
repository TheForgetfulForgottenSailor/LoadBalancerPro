# LoadBalancerPro Release Artifact Evidence

Date: 2026-05-04
Release documented: `v1.9.0`

## Purpose And Scope

This document records release artifact evidence for LoadBalancerPro.

It is enterprise-demo release evidence, not production certification. It documents `v1.9.0` because that release completed the JAR/SBOM/checksum/attestation chain without changing the project's safety boundaries.

This document does not create new release artifacts, alter workflows, add signing, publish Docker images, publish to Maven Central, or create GitHub Release assets.

## v1.9.0 Release Evidence Summary

The public Release Artifacts workflow passed for `v1.9.0`.

Artifact bundle:

```text
loadbalancerpro-release-1.9.0
```

Manual bundle verification confirmed these files:

- `LoadBalancerPro-1.9.0.jar`
- `LoadBalancerPro-1.9.0-bom.json`
- `LoadBalancerPro-1.9.0-bom.xml`
- `LoadBalancerPro-1.9.0-SHA256SUMS.txt`

The release workflow chain for `v1.9.0` included:

- executable JAR build,
- CycloneDX SBOM JSON/XML generation,
- SHA-256 checksum generation,
- checksum verification,
- deterministic artifact bundle upload,
- release JAR provenance attestation,
- JAR/SBOM JSON relationship attestation.

## Known Historical Workflow Failure: v1.9.1

`v1.9.1` was a docs/evidence-only patch release.

The `Release Artifacts` workflow failed before artifact upload at the `Verify Maven version matches tag` step:

```text
Tag version: 1.9.1
Maven version: 1.9.0
```

This failure was expected for the repository history because Maven/app metadata was not aligned to `1.9.1` before the docs-only tag was created. The version-alignment guard worked as intended and prevented misleading release artifacts from being published for a tag whose version did not match the Maven project version.

`v2.0.0` passed the `Release Artifacts` workflow and is the current release artifact baseline. Do not move, rewrite, or rerun the `v1.9.1` tag to change historical behavior.

## Checksum Role

`LoadBalancerPro-1.9.0-SHA256SUMS.txt` records SHA-256 hashes for the release JAR and SBOM files.

The checksum file supports downloaded artifact integrity verification against the files in the bundle. It helps detect accidental corruption or mismatch after download.

The checksum file does not prove builder identity, does not prove dependencies are vulnerability-free, and does not replace artifact attestations or signing.

## Attestation Evidence

GitHub artifact attestations were created for the `v1.9.0` release artifacts:

- `https://github.com/richmond423/LoadBalancerPro/attestations/26304650`
- `https://github.com/richmond423/LoadBalancerPro/attestations/26304653`

GitHub artifact attestations provide build provenance evidence. One attestation covers release JAR provenance. One attestation covers the JAR/SBOM JSON relationship.

Attestations help reviewers verify where and how artifacts were built. They also help connect the generated SBOM JSON to the release JAR it describes.

## What This Evidence Does Not Prove

This evidence is intentionally conservative. It is not:

- PGP signing.
- Notarization.
- A vulnerability scan.
- Proof that dependencies are vulnerability-free.
- Proof that runtime deployment is secure.
- Production certification.
- GitHub Release asset publication.
- Container signing.
- Docker image publishing.
- Maven Central publishing.

Trivy, GitHub dependency review, CodeQL, SBOM inventory, checksums, and attestations are separate controls. None of them alone proves complete supply-chain security or production readiness.

## Browser Verification Guidance

To review the `v1.9.0` release artifact evidence in the browser:

1. Open the public GitHub repository: `https://github.com/richmond423/LoadBalancerPro`.
2. Go to `Actions`.
3. Open the `Release Artifacts` workflow.
4. Open the `v1.9.0` workflow run.
5. Confirm the workflow run passed.
6. Download `loadbalancerpro-release-1.9.0`.
7. Confirm the four expected files are present:
   - `LoadBalancerPro-1.9.0.jar`
   - `LoadBalancerPro-1.9.0-bom.json`
   - `LoadBalancerPro-1.9.0-bom.xml`
   - `LoadBalancerPro-1.9.0-SHA256SUMS.txt`
8. Open the attestation links:
   - `https://github.com/richmond423/LoadBalancerPro/attestations/26304650`
   - `https://github.com/richmond423/LoadBalancerPro/attestations/26304653`

## Optional GitHub CLI Verification

If GitHub CLI is available and the release JAR has been downloaded locally, verify the artifact attestation with:

```sh
gh attestation verify LoadBalancerPro-1.9.0.jar --repo richmond423/LoadBalancerPro
```

This command requires GitHub CLI, a compatible GitHub authentication/environment, and the artifact file on the local filesystem.

## Remaining Future Work

- GitHub Release assets still do not exist.
- Container signing still does not exist.
- Docker image publishing still does not exist.
- Maven Central publishing still does not exist.
- Deployment evidence remains separate future work.

This document records an enterprise-demo release evidence milestone. It does not certify production deployment readiness.
