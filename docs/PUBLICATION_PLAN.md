# LoadBalancerPro Public Publication Plan

Prepared: 2026-05-03

Scope: planning only. Do not run the command sequence in this document until it has been reviewed. This plan does not change remotes, push branches, merge branches, delete files, or overwrite public history.

## Current Canonical Local Branch

Current best local branch:

```text
loadbalancerpro-clean
```

Current local HEAD:

```text
812edbe30beb946206fd8391c0a0a38479218a3a
Merge manual CycloneDX SBOM docs
```

The repo-truth audit found that `loadbalancerpro-clean` is clean, tracks `origin/loadbalancerpro-clean`, and is the most complete integrated local branch.

## Remote Situation

Current configured remote:

```text
origin  https://github.com/cs-olympic/finalcs2-richmond423.git
```

Intended public repository:

```text
https://github.com/richmond423/LoadBalancerPro.git
```

Important mismatch:

- The completed local branch is not currently published to `richmond423/LoadBalancerPro`.
- The public repo appears to contain a smaller `main` branch with no public `v1.0.0` release/tag.
- The configured `origin` should not be replaced yet. Add the public repo as a second remote first so publication is deliberate and reversible.

## Unmerged Branch Review

### `codex/robust-safety-test-expansion`

Commits not merged into `loadbalancerpro-clean`:

```text
928be4a Add robust safety tests batch 1
b837e82 Add telemetry guardrail edge tests
32b1b76 Add replay cloud isolation tests
f4f4e92 Add input API hardening tests
```

Files changed by the branch commits:

```text
src/main/java/api/ApiErrorResponse.java
src/main/java/api/RestExceptionHandler.java
src/main/java/core/CloudManager.java
src/main/java/core/LaseShadowAdvisor.java
src/main/java/core/LoadBalancer.java
src/test/java/api/AllocatorControllerTest.java
src/test/java/api/OAuth2AuthorizationTest.java
src/test/java/api/ProdApiKeyModeAliasProtectionTest.java
src/test/java/api/ProdApiKeyProtectionTest.java
src/test/java/api/ProdCorsOverrideConfigurationTest.java
src/test/java/api/config/TelemetryConfigurationTest.java
src/test/java/cli/LaseReplayCommandTest.java
src/test/java/core/CloudManagerGuardrailTest.java
src/test/java/core/LaseShadowAdvisorTest.java
src/test/java/util/UtilsTest.java
```

Change type:

- Mostly test expansion.
- Also production-code changes in API error handling, REST exception handling, CloudManager sandbox-prefix guardrails, LASE shadow failure sanitization, and LoadBalancer logging behavior.
- Not docs-only and not build-only.

Safety assessment:

- Valuable hardening branch, but not test-only.
- Safe to review for merge or cherry-pick after publication planning, because it strengthens fail-closed behavior and adds tests around sensitive paths.
- Because it touches `CloudManager`, it should be reviewed as production behavior before inclusion.
- Do not blindly publish it as part of public `main` without review.

Conflict assessment:

- `git merge-tree` simulation against `loadbalancerpro-clean` showed no conflict markers for this branch.
- A normal merge should preserve later evidence/SBOM docs even though a direct endpoint diff makes the branch tip look older.
- Still review the resulting diff carefully because production Java files change.

Test recommendation:

- Yes. Run `mvn -q test` after applying this branch.
- Because it touches CloudManager guardrails and API error handling, also consider package/JAR smoke checks before public release.

### `codex/supply-chain-pinning`

Commit not merged into `loadbalancerpro-clean`:

```text
eb29627 Pin supply-chain inputs
```

Files changed by the branch commit:

```text
.github/workflows/ci.yml
Dockerfile
README.md
```

Change type:

- Build/CI supply-chain hardening.
- Documentation update.
- No production Java code changes.

What it does:

- Pins GitHub Actions to reviewed commit SHAs while preserving action names/version comments.
- Pins Docker base images by digest.
- Documents the pinning/update policy in README.

Safety assessment:

- Safe to merge or cherry-pick after review, but verify the pinned action SHAs and Docker image digests before relying on them.
- This can break CI or Docker builds if any pinned digest is stale, unavailable for the target platform, or intentionally superseded.
- It is not required to publish the completed local branch as a new public branch.

Conflict assessment:

- `git merge-tree` simulation showed an auto-merged README area marked `changed in both`, but no conflict markers.
- Review the README hunk after applying because current README has later evidence/SBOM text and the branch adds supply-chain pinning notes near the CI/Docker sections.

Test recommendation:

- Yes. Run `mvn -q test` after applying it, even though it is not production Java.
- Also run Docker build/runtime smoke and inspect the GitHub Actions workflow after applying, because the main risk is build/CI behavior rather than unit-test behavior.

## Publish Branch Choice

Recommended first public publication target:

```text
public/loadbalancerpro-clean
```

Do not publish directly over public `main` first.

Recommended sequence:

1. Add the intended public repo as a second remote named `public`.
2. Push `loadbalancerpro-clean` to the public repo as a new branch named `loadbalancerpro-clean`.
3. Verify on GitHub that the branch, files, README, CI workflow, evidence docs, and tags look correct.
4. Only later decide whether to make `loadbalancerpro-clean` the default branch, merge it into `main`, or replace `main`.

Whether to publish as `main`, `loadbalancerpro-clean`, or both:

- First publish as `loadbalancerpro-clean`.
- Do not overwrite `main` until the smaller public repo has been reviewed and backed up.
- Later, either make `loadbalancerpro-clean` the default branch in GitHub settings or open a PR into `main`.
- Only use `--force-with-lease` to replace public `main` after explicit review and consent.

## Tag Recommendation

Do not move or replace the existing local/configured-origin `v1.0.0` tag.

Reason:

- Local `v1.0.0` points to `8992364352954c8b48fc09545e7e6ec0e30f2dd3`.
- `loadbalancerpro-clean` is 27 commits ahead of that tag.
- Moving `v1.0.0` would make the release name mean two different commits and create avoidable provenance confusion.

Recommended tag path:

- Preserve `v1.0.0` as the historical release tag.
- After final branch selection, create a new annotated tag on the chosen publication commit.
- Prefer `v1.0.1` if this is framed as a publication/hardening/evidence patch release.
- Prefer `v1.1.0` if the final published branch includes the unmerged robust safety and supply-chain pinning work and is presented as a broader hardening release.

Conservative default:

```text
Create v1.0.1 after final review, not before.
```

## DO NOT RUN YET: Safe Command Sequence

These commands are the proposed safe sequence. They are intentionally not run as part of this planning step.

### Phase 0: Verify local state

```bash
git status
git branch --show-current
git log --oneline --decorate -10
git tag --list
git remote -v
```

Expected before publication:

```text
branch = loadbalancerpro-clean
working tree clean except reviewed docs/planning files
origin = https://github.com/cs-olympic/finalcs2-richmond423.git
```

### Phase 1: Add public repo as second remote

```bash
git remote add public https://github.com/richmond423/LoadBalancerPro.git
git remote -v
git ls-remote --heads --tags public
```

Do not change `origin` yet.

### Phase 2: Publish completed branch without touching public main

```bash
git switch loadbalancerpro-clean
git push public loadbalancerpro-clean:loadbalancerpro-clean
```

Then verify on GitHub:

```text
https://github.com/richmond423/LoadBalancerPro/tree/loadbalancerpro-clean
```

Check that GitHub shows:

- Root `README.md`
- `pom.xml`
- `Dockerfile`
- `.github/workflows/ci.yml`
- `evidence/`
- `docs/REPO_TRUTH_AUDIT.md`
- `docs/PUBLICATION_PLAN.md`
- LASE source/test files

### Phase 3: Tag only after final review

If publishing current `loadbalancerpro-clean` as a patch publication baseline:

```bash
git switch loadbalancerpro-clean
git tag -a v1.0.1 -m "LoadBalancerPro v1.0.1: public publication baseline"
git push public v1.0.1
```

If first merging reviewed hardening branches and treating it as a broader hardening release:

```bash
git switch loadbalancerpro-clean
# Apply reviewed branches first, then rerun tests and smoke checks.
git tag -a v1.1.0 -m "LoadBalancerPro v1.1.0: public hardening release"
git push public v1.1.0
```

Optional historical tag publication, only if desired:

```bash
git push public v1.0.0
```

Do not retag `v1.0.0`.

### Phase 4: Decide public main later

Preferred low-risk options:

```text
Option A: Keep public main unchanged and make loadbalancerpro-clean the default branch in GitHub settings.
Option B: Open a GitHub PR from loadbalancerpro-clean into main and review the replacement diff.
Option C: Rename branches in GitHub after review so the completed branch becomes main without surprise.
```

High-risk option, only after explicit approval:

```bash
git push --force-with-lease public loadbalancerpro-clean:main
```

This command would replace the smaller public `main` history. Do not run it unless the public repo has been backed up and the overwrite has been reviewed.

## Optional Pre-Publication Branch Integration

Conservative choice:

```text
Publish current loadbalancerpro-clean first as a new public branch, then evaluate the unmerged branches in separate PRs.
```

If including `codex/robust-safety-test-expansion` before publication:

```bash
# DO NOT RUN YET
git switch loadbalancerpro-clean
git merge --no-ff codex/robust-safety-test-expansion
mvn -q test
```

Review the resulting production-code changes before publishing.

If including `codex/supply-chain-pinning` before publication:

```bash
# DO NOT RUN YET
git switch loadbalancerpro-clean
git merge --no-ff codex/supply-chain-pinning
mvn -q test
docker build -t loadbalancerpro:publication-check .
```

Review pinned action SHAs, Docker digests, and README merge result before publishing.

## Risk Notes

- The public repo currently appears much smaller. Pushing to public `main` directly could overwrite or obscure that history.
- Adding a second remote is safer than changing `origin`, because it keeps the current known-good configured origin intact.
- Pushing a new branch is safer than pushing to `main`, because it lets GitHub render the completed work without replacing anything.
- Do not use `--force` or `--mirror`.
- Do not push all local branches. Many local branches are historical, divergent, or incomplete.
- Do not move `v1.0.0`; create a new tag for the current completed branch after review.
- If the public repo has branch protections, secrets, GitHub Pages, Actions settings, or classroom/institutional links, replacing `main` can have side effects outside Git history.
- If the unmerged robust safety branch is applied, treat it as production behavior change because it touches CloudManager and API handling.
- If the supply-chain pinning branch is applied, treat it as CI/build behavior change and verify Docker/Actions behavior.

## Recommended Safest Path

1. Keep `loadbalancerpro-clean` as the canonical local branch for now.
2. Do not merge the two unmerged branches before the first public visibility step unless they are explicitly reviewed.
3. Add `richmond423/LoadBalancerPro` as a second remote named `public`.
4. Push only `loadbalancerpro-clean` to `public/loadbalancerpro-clean`.
5. Verify the branch on GitHub.
6. Create a new release tag after review, preferably `v1.0.1` for the current branch or `v1.1.0` if additional hardening branches are included.
7. Decide later whether to make `loadbalancerpro-clean` the default branch or replace/merge into public `main`.

Short safest summary:

```text
Publish the completed work to the public repo as a new branch first. Do not force-push public main. Do not move v1.0.0. Create a new tag only after final branch review.
```
