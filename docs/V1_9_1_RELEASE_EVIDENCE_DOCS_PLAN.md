# LoadBalancerPro v1.9.1 Release Evidence Docs Plan

Date: 2026-05-04

## A. Current v1.9.0 State

- `v1.9.0` is shipped and pushed to `origin` and `public`.
- The public Release Artifacts workflow passed for `v1.9.0`.
- The downloaded `loadbalancerpro-release-1.9.0` artifact bundle was manually verified.
- The verified artifact bundle contains:
  - `LoadBalancerPro-1.9.0.jar`
  - `LoadBalancerPro-1.9.0-bom.json`
  - `LoadBalancerPro-1.9.0-bom.xml`
  - `LoadBalancerPro-1.9.0-SHA256SUMS.txt`
- The Release Artifacts workflow created two GitHub artifact attestations:
  - `https://github.com/TheForgetfulForgottenSailor/LoadBalancerPro/attestations/26304650`
  - `https://github.com/TheForgetfulForgottenSailor/LoadBalancerPro/attestations/26304653`
- Existing tags remain immutable.
- Public `main` remains untouched.

## B. Why v1.9.1 Is Useful

`v1.9.0` created the release evidence chain:

- executable JAR build,
- CycloneDX SBOM JSON/XML generation,
- SHA-256 checksum generation,
- checksum verification,
- deterministic artifact bundle upload,
- release JAR provenance attestation,
- JAR/SBOM JSON relationship attestation.

`v1.9.1` should document how reviewers can find and verify that evidence without changing application behavior or release workflows. That documentation helps enterprise-demo reviewers understand what the release chain proves, where the evidence lives, and what the evidence does not prove.

The main value is clarity. The project should avoid overclaiming production readiness, signing, notarization, or vulnerability freedom while still making the actual v1.9.0 provenance evidence easy to review.

## C. Proposed Docs To Add Or Update

Recommended new file:

```text
evidence/RELEASE_ARTIFACT_EVIDENCE.md
```

Recommended contents:

- Purpose and scope for release artifact evidence.
- `v1.9.0` workflow run status:
  - public Release Artifacts workflow passed,
  - artifact bundle was downloaded and manually verified.
- Artifact bundle name:
  - `loadbalancerpro-release-1.9.0`
- Artifact contents:
  - `LoadBalancerPro-1.9.0.jar`
  - `LoadBalancerPro-1.9.0-bom.json`
  - `LoadBalancerPro-1.9.0-bom.xml`
  - `LoadBalancerPro-1.9.0-SHA256SUMS.txt`
- Checksum file role:
  - records SHA-256 hashes for the JAR and SBOM files,
  - supports downloaded artifact integrity verification against the checksum file,
  - does not prove builder identity or dependency safety.
- Attestation links:
  - `https://github.com/TheForgetfulForgottenSailor/LoadBalancerPro/attestations/26304650`
  - `https://github.com/TheForgetfulForgottenSailor/LoadBalancerPro/attestations/26304653`
- What attestations prove:
  - GitHub artifact attestations provide build provenance evidence,
  - the JAR provenance attestation helps verify where and how the release JAR was built,
  - the JAR/SBOM JSON attestation helps verify the SBOM JSON relationship to the release JAR.
- What attestations do not prove:
  - not PGP signing,
  - not notarization,
  - not a vulnerability scan,
  - not proof dependencies are vulnerability-free,
  - not proof the runtime deployment is secure,
  - not production certification,
  - not GitHub Release asset publication,
  - not container signing.
- Browser verification guidance:
  - open the public GitHub repository,
  - inspect the `v1.9.0` tag and Release Artifacts workflow run,
  - download `loadbalancerpro-release-1.9.0`,
  - confirm the four expected files are present,
  - inspect the two attestation links in GitHub.
- Optional GitHub CLI verification command:

  ```sh
  gh attestation verify LoadBalancerPro-1.9.0.jar --repo TheForgetfulForgottenSailor/LoadBalancerPro
  ```

- Note that GitHub CLI verification requires a downloaded artifact and compatible GitHub authentication/environment.
- State that GitHub Release assets still do not exist.
- State that container signing still does not exist.
- State that Docker image publishing, Maven Central publishing, and deployment evidence are outside this release evidence slice.
- State that this is enterprise-demo release evidence, not production certification.

## D. Possible README Update

Add only a short link under `Evidence and Hardening`:

```text
[`RELEASE_ARTIFACT_EVIDENCE.md`](evidence/RELEASE_ARTIFACT_EVIDENCE.md) documents the v1.9.0 release artifact bundle, checksum, and GitHub attestation evidence.
```

Avoid adding a large README section. The README should remain a discoverability index, not the canonical release-evidence record.

## E. What Not To Change

- No Java code.
- No tests.
- No `pom.xml`.
- No workflows.
- No new release artifacts.
- No signing.
- No Docker publishing.
- No Maven Central publishing.
- No GitHub Release assets.
- No public `main`.
- No tag movement.
- No remote changes.
- No CloudManager/AWS behavior.
- No routing behavior.
- No allocation endpoint behavior.
- No CLI behavior.

## F. Verification Plan

After implementation:

```text
mvn -q test
mvn -q -DskipTests package
git diff --check
git diff -- src/main/java src/test/java pom.xml .github/workflows
```

Expected:

- No source changes.
- No test changes.
- No `pom.xml` changes.
- No workflow changes.
- Docs/evidence only.
- Expected changed files should be limited to:
  - `README.md`
  - `docs/V1_9_1_RELEASE_EVIDENCE_DOCS_PLAN.md`
  - `evidence/RELEASE_ARTIFACT_EVIDENCE.md`

Optional read-only checks:

```text
rg -n "RELEASE_ARTIFACT_EVIDENCE|26304650|26304653|loadbalancerpro-release-1.9.0|LoadBalancerPro-1.9.0-SHA256SUMS.txt" README.md evidence docs
```

## G. Recommendation

Proceed with `v1.9.1` before `v2.0.0` planning.

The v1.9.0 release chain is a meaningful enterprise-demo milestone. A narrow v1.9.1 evidence documentation patch should capture the successful workflow, artifact bundle, checksum, and attestation links while the release details are fresh. Keep it docs/evidence-only, avoid any workflow or code changes, and preserve the conservative language already used in the evidence set.
