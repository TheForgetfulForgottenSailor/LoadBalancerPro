# LoadBalancerPro v1.8.0 Release Checksums And Attestations Plan

Date: 2026-05-04

## A. Current Release State

- `v1.7.0` is shipped and pushed.
- The tag-triggered Release Artifacts workflow exists and passed for `v1.7.0`.
- The downloaded `loadbalancerpro-release-1.7.0` workflow artifact bundle was verified to contain:
  - `LoadBalancerPro-1.7.0.jar`
  - `LoadBalancerPro-1.7.0-bom.json`
  - `LoadBalancerPro-1.7.0-bom.xml`
- Existing tags must remain immutable.
- Public `main` remains untouched.

## B. Current Release Artifact Baseline

Current baseline:

- The tag-triggered workflow builds the executable JAR.
- The tag-triggered workflow generates CycloneDX SBOM JSON and XML.
- Git tag and Maven project version alignment is enforced before artifact upload.
- Deterministic artifact names exist for the JAR and SBOM files.
- Artifacts are GitHub Actions artifacts, not GitHub Release assets.
- The release artifact bundle is retained for 90 days.

Current gaps:

- No SHA-256 checksum file exists yet.
- No GitHub artifact attestations exist yet.
- No release signing exists yet.
- No container signing exists yet.
- No GitHub Release asset publication policy exists yet.
- No container registry or release image naming policy exists yet.

## C. Proposed Phases

### Phase 1: SHA-256 Checksums

Extend `.github/workflows/release-artifacts.yml` after release artifact staging.

Generate SHA-256 checksums for:

- `LoadBalancerPro-${version}.jar`
- `LoadBalancerPro-${version}-bom.json`
- `LoadBalancerPro-${version}-bom.xml`

Output checksum file:

```text
LoadBalancerPro-${version}-SHA256SUMS.txt
```

Recommended behavior:

- Generate the checksum file inside `release-artifacts/`.
- Verify the checksum file in the workflow with `sha256sum -c`.
- Upload the checksum file in the existing `loadbalancerpro-release-${version}` artifact bundle.
- Keep artifact retention at 90 days.
- Update evidence docs.
- Do not add attestations yet.

### Phase 2: Artifact Attestations

Add GitHub artifact attestations after checksum generation is stable.

Use GitHub's official artifact attestation action if repository support and permissions are available.

Likely required workflow permissions:

```yaml
permissions:
  contents: read
  id-token: write
  attestations: write
```

Attest:

- executable JAR
- `bom.json`
- `bom.xml`
- possibly the `SHA256SUMS` file

Keep attestations tied to tag-triggered workflow outputs. Be precise in documentation: attestations are provenance evidence. They are not PGP signing, notarization, vulnerability scanning, release signing, dependency review, or production-readiness proof.

### Phase 3: GitHub Release Assets

Optionally publish release artifacts, checksums, and attestations as GitHub Release assets in a later slice.

Keep this separate until the project defines:

- whether releases are created manually or by workflow,
- whether assets can be overwritten,
- retention expectations,
- release notes policy,
- permissions required for release asset upload.

### Phase 4: Container Signing

Defer container signing until container image publication exists.

Future signing should wait for:

- registry selection,
- image name policy,
- release tag and digest policy,
- keyless versus key-managed signing decision,
- publication workflow design.

## D. Recommended First Implementation Scope

Implement Phase 1 only:

- Update `.github/workflows/release-artifacts.yml` to generate SHA-256 checksums.
- Include the checksum file in the uploaded release artifact bundle.
- Update `evidence/SBOM_GUIDE.md`.
- Update `evidence/SUPPLY_CHAIN_EVIDENCE.md`.
- Update `evidence/SECURITY_POSTURE.md` if a short conservative checksum note improves accuracy.
- Update `evidence/RESIDUAL_RISKS.md` if the existing supply-chain residual risk should mention checksums.
- Include this planning doc.

Do not change:

- Java source.
- Tests.
- `pom.xml`.
- Maven dependencies.
- Existing CI workflow behavior.
- Existing CodeQL workflow behavior.
- Artifact attestation behavior.
- Signing behavior.
- Docker publishing.
- Maven Central publishing.
- GitHub Release asset publishing.

## E. Checksum Implementation Details

The release workflow runs on `ubuntu-latest`, so use Linux shell commands.

Expected staged release workflow outputs:

```text
release-artifacts/LoadBalancerPro-${RELEASE_VERSION}.jar
release-artifacts/LoadBalancerPro-${RELEASE_VERSION}-bom.json
release-artifacts/LoadBalancerPro-${RELEASE_VERSION}-bom.xml
release-artifacts/LoadBalancerPro-${RELEASE_VERSION}-SHA256SUMS.txt
```

Recommended checksum command:

```sh
cd release-artifacts
sha256sum \
  LoadBalancerPro-${RELEASE_VERSION}.jar \
  LoadBalancerPro-${RELEASE_VERSION}-bom.json \
  LoadBalancerPro-${RELEASE_VERSION}-bom.xml \
  > LoadBalancerPro-${RELEASE_VERSION}-SHA256SUMS.txt
```

Recommended workflow verification command:

```sh
cd release-artifacts
sha256sum -c LoadBalancerPro-${RELEASE_VERSION}-SHA256SUMS.txt
```

The upload artifact path should include:

```text
release-artifacts/LoadBalancerPro-${{ env.RELEASE_VERSION }}.jar
release-artifacts/LoadBalancerPro-${{ env.RELEASE_VERSION }}-bom.json
release-artifacts/LoadBalancerPro-${{ env.RELEASE_VERSION }}-bom.xml
release-artifacts/LoadBalancerPro-${{ env.RELEASE_VERSION }}-SHA256SUMS.txt
```

Keep the existing artifact name:

```text
loadbalancerpro-release-${version}
```

Keep retention at 90 days unless a later release-retention policy changes it intentionally.

## F. Evidence Language

Evidence docs should say:

- Checksums help verify downloaded artifact integrity against the uploaded checksum file.
- Checksums do not prove who built the artifact.
- Checksums do not replace signatures or attestations.
- Checksums do not prove dependencies are vulnerability-free.
- Checksums do not prove the CI runner or repository settings were trusted.
- SBOMs remain component inventory, not vulnerability proof.
- Attestations and signing remain future work unless implemented later.

Recommended user verification guidance can include:

Linux/macOS:

```sh
sha256sum -c LoadBalancerPro-1.8.0-SHA256SUMS.txt
```

PowerShell example for a single file:

```powershell
Get-FileHash .\LoadBalancerPro-1.8.0.jar -Algorithm SHA256
```

PowerShell checksum-file verification can be documented later if the project wants a polished cross-platform verification helper.

## G. Verification Plan

Before implementation:

```text
git status
git branch --show-current
```

After implementation:

```text
mvn -q test
mvn -q -DskipTests package
mvn -B org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom -DoutputFormat=all -DoutputDirectory=target -DoutputName=bom -Dcyclonedx.skipAttach=true
git diff --check
git diff --name-only
git diff -- src/main/java src/test/java pom.xml .github/workflows/ci.yml .github/workflows/codeql.yml
```

Expected:

- No `src/main/java` changes.
- No `src/test/java` changes.
- No `pom.xml` changes.
- Existing `.github/workflows/ci.yml` unchanged.
- Existing `.github/workflows/codeql.yml` unchanged.
- `.github/workflows/release-artifacts.yml` updated only for checksums.
- Evidence docs updated.
- Generated JAR and SBOM files remain build outputs and are not committed.

Additional workflow review checks:

```text
findstr /C:"SHA256SUMS" .github\workflows\release-artifacts.yml
findstr /C:"sha256sum" .github\workflows\release-artifacts.yml
findstr /C:"retention-days: 90" .github\workflows\release-artifacts.yml
findstr /C:"loadbalancerpro-release-" .github\workflows\release-artifacts.yml
```

The real checksum upload behavior should be validated by the next tag-triggered Release Artifacts workflow run.

## H. Risks

- A checksum file can be uploaded with compromised artifacts if the workflow or runner is compromised.
- Checksums without attestations prove integrity against the bundle, not builder identity.
- Checksums do not replace signatures or GitHub artifact attestations.
- Artifact names can drift between workflow, docs, and actual upload paths.
- Shell quoting mistakes can break checksum generation.
- Reruns can produce artifacts with the same names but different checksums if build inputs drift.
- Attestations need repository permissions and can fail because of repository settings.
- GitHub artifact attestation language can be overclaimed as signing, notarization, or production-readiness proof.
- GitHub Release asset publication has overwrite and duplication risks.
- Container signing remains blocked by missing registry and image naming policy.

## I. What Not To Change

- Do not change production code.
- Do not change tests.
- Do not change dependencies.
- Do not move tags.
- Do not touch public `main`.
- Do not change `CloudManager` or AWS behavior.
- Do not change routing behavior.
- Do not change allocation endpoint behavior.
- Do not change CLI behavior.
- Do not implement attestations in Phase 1.
- Do not implement signing in Phase 1.
- Do not publish Docker images.
- Do not publish to Maven Central.
- Do not create GitHub Release assets.
- Do not broaden into deployment, Kubernetes, Terraform, live AWS, or operations docs.

## J. Open Questions

- Should checksums ship as `v1.8.0` and attestations as `v1.9.0`?
- Should attestations include the checksum file or only primary artifacts?
- Should the artifact bundle include a manifest file later?
- Should checksums be uploaded as Actions artifacts only or also GitHub Release assets later?
- Should release artifacts have longer retention than 90 days?
- Should provenance docs include polished user verification commands for Windows PowerShell and Linux/macOS?
- Should a future helper script verify the checksum file cross-platform?
- Should release artifact reruns be documented as potentially producing new checksums for the same source tag if dependency resolution or build inputs drift?

## K. Recommendation

Proceed with checksums before attestations.

Recommended sequence:

1. Implement SHA-256 checksums as `v1.8.0`.
2. Validate the checksum file appears in the tag-triggered release artifact bundle.
3. Keep the slice workflow/evidence-only.
4. Implement GitHub artifact attestations later after checksum workflow behavior is stable.

This keeps the next increment small and reviewable while improving release artifact integrity evidence without changing application behavior, tests, dependencies, CI gates, CodeQL behavior, public `main`, or existing tags.
