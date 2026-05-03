# LoadBalancerPro v1.5.0 Supply-Chain Provenance Plan

Date: 2026-05-03

## A. Current Release State

- `v1.4.0` is shipped and pushed to `origin/loadbalancerpro-clean`.
- Remote release branch hash: `af036f643abc0c4c0086f09002324b3e28b12231`.
- Remote `v1.4.0` annotated tag object: `5cafd2f138829f9404fa5c8d3e3a004b4c059d25`.
- Remote `v1.4.0` tag target: `af036f643abc0c4c0086f09002324b3e28b12231`.
- Governance basics are present:
  - `LICENSE`
  - `SECURITY.md`
  - `CONTRIBUTING.md`
  - `.github/CODEOWNERS`
  - `.github/dependabot.yml`
- Dependabot config exists for Maven, GitHub Actions, and Docker.
- CI already runs dependency tree resolution, tests, packaging, LASE JAR smoke checks, packaged JAR health smoke, Docker build, Docker runtime health, Trivy image scan, and pull-request dependency review.
- GitHub Actions in `.github/workflows/ci.yml` are pinned to commit SHAs with comments preserving the upstream action names and versions.
- Docker build and runtime base images are pinned by digest in `Dockerfile`.
- Existing tags must remain immutable.
- Public `main` remains untouched.

## B. Current Supply-Chain Baseline

Current confirmed controls:

- GitHub dependency review is present for pull requests through `actions/dependency-review-action`, failing on high severity findings.
- Trivy image scanning is present in CI for fixed high/critical OS and library vulnerabilities.
- CI resolves the Maven dependency tree before test/package.
- CI runs full Maven tests with `mvn -B test`.
- CI packages the executable JAR with `mvn -B package`.
- CI runs deterministic LASE demo smoke checks.
- CI runs a packaged JAR `/api/health` smoke check.
- CI builds the Docker image and validates Docker runtime health.
- GitHub Actions are pinned to commit SHAs:
  - `actions/checkout`
  - `actions/setup-java`
  - `aquasecurity/trivy-action`
  - `actions/dependency-review-action`
- Docker base images are pinned by digest:
  - `maven:3.9-eclipse-temurin-17@sha256:...`
  - `eclipse-temurin:17-jre-jammy@sha256:...`
- `.github/dependabot.yml` exists and covers Maven, GitHub Actions, and Docker on a weekly schedule.
- `evidence/SBOM_GUIDE.md` documents manual CycloneDX SBOM generation.

Current gaps:

- SBOM artifacts are not generated or archived automatically in CI.
- Generated SBOM files are not published as workflow artifacts.
- `pom.xml` does not include a CycloneDX plugin configuration, which is acceptable for now.
- No CodeQL or equivalent SAST workflow is present.
- No GitHub artifact attestation workflow is present.
- Release JAR artifacts are not yet published through a release-artifact workflow.
- No release artifact provenance or attestation policy is implemented.
- No container signing is implemented.
- No container registry or release image naming policy is defined.
- `evidence/SUPPLY_CHAIN_EVIDENCE.md` has stale baseline statements from before v1.4.0; it still says Dependabot is absent and refers to tag-pinned Actions and Docker images. That evidence should be updated when Phase 1 is implemented.

## C. Recommended Implementation Phases

### Phase 1: SBOM Artifact In CI

Add CI-generated CycloneDX SBOM artifacts without changing `pom.xml` if possible.

Recommended approach:

- Invoke the CycloneDX Maven plugin directly in CI using a pinned plugin version.
- Generate:
  - `target/bom.json`
  - `target/bom.xml`
- Upload both SBOM files as GitHub Actions artifacts.
- Do not commit generated SBOM files.
- Keep SBOM inventory separate from vulnerability analysis.
- Update `evidence/SBOM_GUIDE.md` to explain the new CI artifact path.
- Update `evidence/SUPPLY_CHAIN_EVIDENCE.md` to reflect current controls and remaining gaps.

SBOM inventory should be treated as component inventory, not a guarantee that dependencies are vulnerability-free.

### Phase 2: CodeQL/SAST Workflow

Add a separate SAST workflow after SBOM artifact generation is stable.

Recommended approach:

- Add `.github/workflows/codeql.yml`.
- Use CodeQL for Java/Kotlin.
- Run on:
  - push to `loadbalancerpro-clean`,
  - pull requests targeting `loadbalancerpro-clean`,
  - a weekly schedule.
- Keep CodeQL independent from the existing CI workflow.
- Do not weaken or replace existing test/package/Docker/Trivy/dependency-review gates.
- Define triage expectations for findings before treating CodeQL as a blocking release gate.

### Phase 3: GitHub Artifact Attestations

Plan release artifact provenance after the SBOM and JAR artifact publishing path is stable.

Recommended approach:

- Decide which artifacts are release artifacts:
  - executable JAR,
  - SBOM JSON/XML,
  - possibly Docker image digest metadata.
- Use GitHub artifact attestations if repository permissions support it.
- Define required workflow permissions, likely including `attestations: write` and `id-token: write`.
- Keep attestations tied to generated release artifacts, not source-tree files.
- Do not implement attestations until artifact naming, retention, and release workflow boundaries are stable.

### Phase 4: Container Signing

Plan container signing after image publishing is defined.

Recommended approach:

- Decide the container registry and image name first.
- Decide whether release images are pushed on tags only or also on release branch merges.
- Consider future keyless signing with `cosign`.
- Attach signatures to pushed image digests, not mutable tags alone.
- Do not implement signing before registry, image naming, permissions, and release trigger policy are decided.

## D. Exact Future Implementation Scope For Phase 1

The first implementation slice should add only:

- CI SBOM generation step.
- CI artifact upload step for `target/bom.json` and `target/bom.xml`.
- Updates to `evidence/SBOM_GUIDE.md`.
- Updates to `evidence/SUPPLY_CHAIN_EVIDENCE.md`.
- Optional short README note linking to the updated evidence docs if it improves discoverability.

Recommended CI command shape:

```sh
mvn -B org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom \
  -DoutputFormat=all \
  -DoutputDirectory=target \
  -DoutputName=bom \
  -Dcyclonedx.skipAttach=true
```

Scope boundaries:

- No `pom.xml` plugin changes unless direct invocation fails or cannot reliably produce both JSON/XML artifacts.
- No dependency version changes.
- No generated SBOM files committed.
- No CodeQL workflow in Phase 1.
- No signing or attestation in Phase 1.
- No Docker registry or image publishing changes in Phase 1.

## E. Tests And Verification For Phase 1

Run:

```text
mvn -q test
mvn -q -DskipTests package
mvn -B org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom
```

If using explicit output flags, run:

```text
mvn -B org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom -DoutputFormat=all -DoutputDirectory=target -DoutputName=bom -Dcyclonedx.skipAttach=true
```

Confirm:

```text
dir target\bom.json
dir target\bom.xml
git status
git diff --check
git diff --name-only
git diff -- src/main/java src/test/java pom.xml
```

Expected:

- `target/bom.json` exists locally after generation.
- `target/bom.xml` exists locally after generation.
- Generated SBOM files are not shown by `git status` because `target/` is ignored.
- GitHub Actions syntax is sane if a local or repository-supported check is available.
- No `src/main/java` changes.
- No `src/test/java` changes.
- No `pom.xml` changes unless a deliberate exception is made and reviewed.
- Existing CI gates remain present.

## F. Risks

- CI time can increase, especially if Maven resolves the CycloneDX plugin cold.
- Artifact retention expectations may be misunderstood if GitHub default retention is not documented.
- The pinned CycloneDX plugin version can drift and will need periodic review.
- Direct Maven plugin invocation may behave differently locally and in GitHub Actions.
- SBOM generation is inventory, not vulnerability scanning.
- CodeQL can create noisy findings and needs triage ownership.
- Artifact attestations require repository and workflow permissions that may not be enabled.
- Signing requires a registry and release image naming decision first.
- Accidental `pom.xml` or dependency churn would broaden the slice.
- Generated SBOM files could be accidentally committed if output paths change outside ignored directories.
- Evidence docs can become stale if the CI behavior changes without updating them.

## G. What Not To Change

- Do not change production code.
- Do not change tests.
- Do not change dependencies.
- Do not move tags.
- Do not touch public `main`.
- Do not change `CloudManager` or AWS behavior.
- Do not change routing, allocation, or CLI behavior.
- Do not commit generated SBOMs.
- Do not implement signing before planning registry and image naming.
- Do not modify deployment or live AWS docs in this slice.
- Do not add Kubernetes, Terraform, or operations docs in this slice.

## H. Recommended First Implementation Slice

Proceed with Phase 1 only:

1. Add CI SBOM generation using direct CycloneDX Maven plugin invocation.
2. Upload `target/bom.json` and `target/bom.xml` as workflow artifacts.
3. Update `evidence/SBOM_GUIDE.md`.
4. Update `evidence/SUPPLY_CHAIN_EVIDENCE.md`.
5. Verify Maven tests/package and manual CycloneDX generation.
6. Confirm generated SBOM files remain untracked.
7. Commit as one supply-chain provenance basics commit.

Recommended commit message:

```text
Add CI SBOM provenance artifacts
```

## I. Open Questions

- Should CodeQL be handled as `v1.5.1` or saved for `v1.6.0`?
- Should SBOM artifact retention use GitHub default retention or an explicit retention period?
- Should release JAR artifacts be uploaded in CI before adding attestations?
- Which container registry and image name should be used before container signing?
- Should Apache-2.0 be reconsidered later for patent language, or should the project keep MIT?
- Should dependency tree output also be uploaded as an artifact, or is SBOM enough for Phase 1?
- Should SBOM generation run on all pushes/PRs or only on release branch and tag workflows?

## J. Recommendation

Proceed with Phase 1 before another feature.

Keep the first implementation CI/evidence-only: generate CycloneDX SBOM artifacts in CI, upload them as workflow artifacts, and update evidence docs to reflect the current supply-chain baseline. Defer CodeQL, attestations, release artifact publishing, and container signing to later focused slices after the SBOM artifact path is stable.
