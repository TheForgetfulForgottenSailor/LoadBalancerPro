# LoadBalancerPro v1.1.0 Supply-Chain Pinning Review

Date: 2026-05-03

## Scope

This review inspects `codex/supply-chain-pinning` as the second proposed v1.1.0 hardening slice after the local robust safety merge on `release/v1.1.0-hardening-review`.

No merge, push, tag creation, remote change, or history rewrite was performed for this review.

## Current Review Branch State

- Current branch: `release/v1.1.0-hardening-review`
- Current review branch commit: `e3bafc4c94fee2ab5bd79cec0f128c5c1fd723da`
- Current review branch includes the local robust safety hardening merge.
- Working tree before this document was created: clean.
- No v1.1.0 tag exists.
- Nothing from this review branch has been pushed.

## Supply-Chain Branch State

- Reviewed branch: `codex/supply-chain-pinning`
- Supply-chain branch tip: `eb29627abfa02d06f336f8edeb8cfc81b95c20d1`
- Commit reviewed:
  - `eb29627 Pin supply-chain inputs`
- Merge base with current review branch: `6edc79dcc0f1b3452b5289ef1143831b56da6bcd`

Direct comparison from current `HEAD` to `codex/supply-chain-pinning` is noisy because the supply-chain branch was created from an older baseline. That direct branch-tip diff appears to delete later docs, evidence, auth, telemetry, and robust safety work. The meaningful branch delta is the merge-base delta, which changes only three files.

## Meaningful Files Changed

From `6edc79dcc0f1b3452b5289ef1143831b56da6bcd..codex/supply-chain-pinning`:

- Modified `.github/workflows/ci.yml`
- Modified `Dockerfile`
- Modified `README.md`
- Total meaningful delta: 3 files changed, 19 insertions, 7 deletions

No production Java code, tests, Maven configuration, cloud mutation logic, evidence docs, or release planning docs are intentionally changed by this branch.

## Exact CI Changes

`.github/workflows/ci.yml` replaces floating or version-tagged GitHub Actions references with commit SHA pins while keeping comments that preserve the upstream action name and version tag:

- `actions/checkout@v4` becomes `actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5`
- `actions/setup-java@v4` becomes `actions/setup-java@c1e323688fd81a25caa38c78aa6df2d33d3e20d9`
- `aquasecurity/trivy-action@0.35.0` becomes `aquasecurity/trivy-action@57a97c7e7821a5776cebc9bb87c984fa69cba8f1`
- `actions/dependency-review-action@v4` becomes `actions/dependency-review-action@2031cfc080254a8a887f58cffee85186f0e49e48`

Conclusion: GitHub Actions are pinned to full commit SHAs.

## Exact Docker Changes

`Dockerfile` pins both base images by digest and adds comments preserving the tag names:

- Build stage changes from `maven:3.9-eclipse-temurin-17` to `maven:3.9-eclipse-temurin-17@sha256:036d1a6f2965e4368157bb87f02cd31652a96918a26f7eb5ae45a0aa33f2cb8e`
- Runtime stage changes from `eclipse-temurin:17-jre-jammy` to `eclipse-temurin:17-jre-jammy@sha256:642d45bf22d3cb9face159181732ed9fa70873b2681e50445eff7d4785c176bb`

Conclusion: Docker base images are pinned by digest.

## Exact README Changes

`README.md` adds documentation in the CI/security section explaining:

- GitHub Actions are pinned to reviewed commit SHAs.
- Comments preserve upstream action names and version tags for update review.
- Docker base images are pinned by digest.
- Tag and digest updates should be made together in a focused PR after rebuilding, running tests/package/JAR/Docker smokes, and reviewing Trivy output.

`README.md` also adds Docker section guidance explaining:

- Build and runtime base images are pinned by digest for reproducibility.
- Digest refreshes should be treated as supply-chain changes.
- Refreshes should rebuild the image, run the container health smoke, and review the vulnerability scan before merge.

## Merge-Conflict Assessment

`git merge-tree` shows `.github/workflows/ci.yml` and `Dockerfile` would merge cleanly.

`README.md` is reported as "changed in both" because current `release/v1.1.0-hardening-review` has later README edits since the supply-chain branch point. The simulated merge output shows the supply-chain README additions applying without conflict markers, but the README should still be reviewed after merge to confirm the new supply-chain text lands in the intended sections and does not duplicate or contradict later README wording.

## Risks

- SHA-pinned GitHub Actions reduce tag drift risk, but updates become manual and require a recurring review process.
- Pinned action SHAs can become stale if upstream actions release security fixes or platform compatibility updates.
- Pinned Docker digests improve reproducibility, but they can freeze OS and JRE layers that later receive security fixes.
- Docker tag-plus-digest references must be refreshed carefully; changing the tag without the digest, or the digest without review, can create misleading documentation.
- Docker digest compatibility should be verified for the expected build platform before merging.
- CI must prove the pinned action SHAs resolve correctly in GitHub Actions, not only locally.
- The branch is based on an older baseline, so it must be merged into the current review branch, not used as a replacement branch or reset target.

## Merge Timing Recommendation

Recommendation: merge `codex/supply-chain-pinning` into `release/v1.1.0-hardening-review` for v1.1.0 after this review is accepted.

Rationale:

- The meaningful change is focused and appropriate for v1.1.0 hardening.
- It does not touch production Java code or cloud mutation logic.
- It complements the already merged robust safety hardening.
- It should not be backported into the already published v1.0.1 baseline unless a separate urgent supply-chain release is intentionally planned.

Defer only if Docker is unavailable for verification, the pinned Docker digests fail to build, the pinned GitHub Actions SHAs fail to resolve, or the README merge produces unclear duplicate security guidance.

## Verification Plan If Merged

DO NOT RUN YET:

```powershell
git status
git branch --show-current
git merge --no-ff codex/supply-chain-pinning -m "Merge supply-chain pinning for v1.1.0 review"

git status
git diff --name-status loadbalancerpro-clean..HEAD
git diff --stat loadbalancerpro-clean..HEAD
git diff loadbalancerpro-clean..HEAD -- .github/workflows/ci.yml Dockerfile README.md

mvn -q test
mvn -q -DskipTests package

Get-ChildItem -Path target -Filter 'LoadBalancerPro-*.jar' | Sort-Object LastWriteTime -Descending | Select-Object FullName,Name,Length,LastWriteTime
java -jar target\LoadBalancerPro-1.0.0.jar --lase-demo=healthy
java -jar target\LoadBalancerPro-1.0.0.jar --lase-demo=overloaded
java -jar target\LoadBalancerPro-1.0.0.jar --lase-demo=invalid-name

docker build -t loadbalancerpro:v1.1.0-supply-chain-review .
docker run --rm -d --name loadbalancerpro-v110-review -p 127.0.0.1:18081:8080 loadbalancerpro:v1.1.0-supply-chain-review
curl.exe -fsS http://127.0.0.1:18081/api/health
docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' loadbalancerpro-v110-review
docker stop loadbalancerpro-v110-review
trivy image --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 loadbalancerpro:v1.1.0-supply-chain-review
```

After pushing a review branch later, verify GitHub Actions separately:

- CI workflow starts successfully.
- Pinned `checkout`, `setup-java`, `trivy-action`, and `dependency-review-action` SHAs resolve.
- Maven test/package jobs pass.
- Docker build, container smoke, Docker healthcheck, and Trivy scan pass in CI.
- Dependency review job runs and enforces high-severity failure.

## Final Recommendation

Merge this supply-chain pinning branch locally into `release/v1.1.0-hardening-review` as the second v1.1.0 hardening slice, then run the full verification plan before pushing any review branch.

Do not replace public `main`, do not move existing tags, and do not create `v1.1.0` until robust safety plus supply-chain verification has passed and the combined branch has been reviewed.
