# LoadBalancerPro v1.9.0 Release Artifact Attestations Plan

Date: 2026-05-04

## A. Current Release State

- `v1.8.0` is shipped and pushed to `origin` and `public`.
- The public Release Artifacts workflow passed for `v1.8.0`.
- The downloaded `loadbalancerpro-release-1.8.0` artifact bundle was manually verified to include:
  - `LoadBalancerPro-1.8.0.jar`
  - `LoadBalancerPro-1.8.0-bom.json`
  - `LoadBalancerPro-1.8.0-bom.xml`
  - `LoadBalancerPro-1.8.0-SHA256SUMS.txt`
- Existing tags must remain immutable.
- Public `main` remains untouched.

## B. Current Artifact Integrity And Provenance Baseline

Current baseline:

- The tag-triggered Release Artifacts workflow builds the executable JAR.
- The tag-triggered workflow generates CycloneDX SBOM JSON and XML.
- Git tag and Maven project version alignment is enforced before artifact upload.
- SHA-256 checksums are generated and verified with `sha256sum -c`.
- Deterministic artifact names exist for the JAR, SBOM files, and checksum file.
- Artifacts are GitHub Actions artifacts, not GitHub Release assets.
- Release artifact bundle retention is 90 days.

Current gaps:

- No GitHub artifact attestations exist yet.
- No release signing exists yet.
- No container signing exists yet.
- No GitHub Release asset publication policy exists yet.
- No container registry or release image naming policy exists yet.

## C. Proposed Attestation Approach

Update only:

```text
.github/workflows/release-artifacts.yml
```

The Release Artifacts workflow should stay tag-triggered and continue to:

- enforce semantic tag format,
- verify Git tag version equals Maven project version,
- build the executable JAR,
- run JAR smoke checks,
- generate CycloneDX SBOM JSON/XML,
- generate and verify SHA-256 checksums,
- upload the deterministic artifact bundle.

Likely workflow permissions:

```yaml
permissions:
  contents: read
  id-token: write
  attestations: write
```

The current `actions/attest` documentation also describes `artifact-metadata: write` for creating artifact storage records. The implementation slice should verify whether that permission is required for the selected action version and selected attestation modes in this repository instead of guessing. If required, add it with a narrow comment explaining why.

Recommended action:

- Use GitHub's official `actions/attest@v4` if compatible with the repository and the desired provenance/SBOM flows.
- Pin the action by reviewed commit SHA, following the repository's existing GitHub Actions policy.
- Add a comment preserving the upstream action name/version tag, matching existing workflow style.
- Do not invent a commit SHA. Resolve and verify the current action SHA during implementation.

Recommended attestation candidates:

1. Build provenance attestation for:

   ```text
   release-artifacts/LoadBalancerPro-${RELEASE_VERSION}.jar
   ```

2. SBOM attestation for the JAR/SBOM JSON relationship:

   ```yaml
   subject-path: release-artifacts/LoadBalancerPro-${RELEASE_VERSION}.jar
   sbom-path: release-artifacts/LoadBalancerPro-${RELEASE_VERSION}-bom.json
   ```

3. Consider separate provenance attestations for:

   ```text
   release-artifacts/LoadBalancerPro-${RELEASE_VERSION}-bom.json
   release-artifacts/LoadBalancerPro-${RELEASE_VERSION}-bom.xml
   release-artifacts/LoadBalancerPro-${RELEASE_VERSION}-SHA256SUMS.txt
   ```

Recommended conservative first implementation:

- Attest JAR build provenance.
- Attest the JAR plus SBOM JSON relationship.
- Defer SHA256SUMS attestation unless implementation confirms `actions/attest@v4` behavior is clean for that subject and the resulting evidence is useful.
- Do not attempt custom predicates in the first slice.

## D. Documentation Language

Evidence docs must say:

- GitHub artifact attestations provide build provenance evidence.
- Attestations help consumers verify where and how artifacts were built.
- Attestations are not PGP signing.
- Attestations are not notarization.
- Attestations are not a vulnerability scan.
- Attestations are not a production-readiness guarantee.
- SBOM remains component inventory.
- Checksums remain integrity checks.
- Trivy, dependency review, and CodeQL remain separate controls.
- GitHub Release assets still do not exist unless implemented in a later slice.

Avoid saying that attestations make the project production-ready or prove dependencies are vulnerability-free.

## E. Verification Guidance

After implementation:

- The tag-triggered Release Artifacts workflow must pass on the next semantic version tag.
- The GitHub Actions run should show successful attestation step output.
- If the repository supports the feature, the GitHub UI may show attestations in Actions or repository security/provenance views.
- The left-side GitHub Actions management or Attestations area may show records, depending on the current GitHub UI and repository plan.
- If GitHub CLI is available, verify a downloaded artifact with:

  ```sh
  gh attestation verify LoadBalancerPro-1.9.0.jar --repo TheForgetfulForgottenSailor/LoadBalancerPro
  ```

- If GitHub CLI is unavailable, document browser-based verification only.
- If repository permissions are not available, the workflow may fail at the attestation step. Fix that in a follow-up patch without moving the failed tag.

Local pre-merge verification should still include:

```text
mvn -q test
mvn -q -DskipTests package
mvn -B org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom -DoutputFormat=all -DoutputDirectory=target -DoutputName=bom -Dcyclonedx.skipAttach=true
git diff --check
git diff -- src/main/java src/test/java pom.xml .github/workflows/ci.yml .github/workflows/codeql.yml
```

Expected local result:

- No Java source changes.
- No test changes.
- No `pom.xml` changes.
- Existing CI workflow unchanged.
- Existing CodeQL workflow unchanged.
- Release workflow updated only for attestations and required permissions.
- Evidence docs updated.

## F. Exact Recommended First Implementation Scope

Recommended implementation scope:

- Update `.github/workflows/release-artifacts.yml`.
- Add required attestation permissions.
- Add pinned official GitHub attestation action step or steps.
- Update `evidence/SUPPLY_CHAIN_EVIDENCE.md`.
- Update `evidence/SBOM_GUIDE.md`.
- Update `evidence/SECURITY_POSTURE.md`.
- Update `evidence/RESIDUAL_RISKS.md` if the existing supply-chain residual risk should mention attestations.
- Include this planning doc.

Do not change:

- Java source.
- Tests.
- `pom.xml`.
- Maven dependencies.
- Existing `.github/workflows/ci.yml`.
- Existing `.github/workflows/codeql.yml`.
- Docker publishing.
- Maven Central publishing.
- GitHub Release asset publishing.
- Container signing.

## G. Risks

- GitHub attestation permissions may fail depending on repository settings or plan.
- The selected attestation action may require additional permission keys such as `artifact-metadata: write`.
- Pinned action SHA must be verified; do not invent a SHA.
- Attestations can be misunderstood as PGP signing, notarization, vulnerability proof, or production-readiness proof.
- SBOM relationship attestation may require different inputs than generic provenance attestation.
- Public/private repository plan limitations may affect availability.
- GitHub Enterprise Server does not support artifact attestations.
- Rerunning workflows for the same tag can create multiple attestations.
- Consumers need GitHub CLI or GitHub UI access to verify attestations.
- GitHub Release assets still do not exist.
- Container signing remains separate and depends on registry/image naming decisions.
- A failed attestation workflow on a release tag should be fixed by a new patch release, not by moving the tag.

## H. What Not To Change

- Do not change production code.
- Do not change tests.
- Do not change dependencies.
- Do not move tags.
- Do not touch public `main`.
- Do not change `CloudManager` or AWS behavior.
- Do not change routing behavior.
- Do not change allocation endpoint behavior.
- Do not change CLI behavior.
- Do not publish Docker images.
- Do not publish to Maven Central.
- Do not create GitHub Release assets.
- Do not implement container signing.
- Do not broaden into deployment, Kubernetes, Terraform, live AWS, or operations docs.

## I. Open Questions

- Should `v1.9.0` attest the JAR only, or the JAR plus SBOM relationship?
- Should `SHA256SUMS.txt` be attested in the first slice?
- Does `actions/attest@v4` support the exact desired SBOM relationship flow for the current release-artifacts layout?
- Is `artifact-metadata: write` required for this repository, action version, and attestation mode?
- Should the first attestation release be `v1.9.0` or a patch `v1.8.1`?
- Should GitHub Release assets be added before or after attestations?
- Should verification commands be added to README, evidence docs only, or both?
- Should workflow rerun behavior be documented before the first attested tag?
- Should implementation add one combined multi-subject attestation or separate attestation steps for clearer audit records?

## J. Recommendation

Proceed with artifact attestations before GitHub Release assets.

Recommended first implementation:

- Keep the slice workflow/evidence-only.
- Prefer JAR provenance plus JAR/SBOM JSON attestation.
- Verify whether `SHA256SUMS.txt` should be attested during implementation.
- Resolve and pin the official GitHub attestation action SHA during implementation.
- Keep GitHub Release assets, release signing, container signing, Docker publishing, Maven Central publishing, and deployment/ops docs out of scope.

This is the next useful provenance step because v1.7.0 established release artifacts, v1.8.0 established checksums, and v1.9.0 can add builder provenance evidence without changing application behavior.

## References For Implementation

- GitHub Docs: Using artifact attestations to establish provenance for builds.
- GitHub official `actions/attest` action documentation.
- GitHub official `actions/attest-build-provenance` and `actions/attest-sbom` repositories, which currently point new implementations toward `actions/attest`.
