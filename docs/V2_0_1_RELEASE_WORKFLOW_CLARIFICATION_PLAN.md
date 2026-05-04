# LoadBalancerPro v2.0.1 Release Workflow Clarification Plan

Date: 2026-05-04

## A. Current Release State

`v2.0.0` is shipped to `origin` and `public`.

The public `Release Artifacts` workflow passed for `v2.0.0`. That confirms the current release tag, Maven project version, JAR build, SBOM generation, checksum generation, artifact upload, and attestation flow are working for the current shipped release.

The historical `v1.9.1` `Release Artifacts` workflow failed at the `Verify Maven version matches tag` step because the semantic tag version and Maven project version did not match:

```text
Tag version: 1.9.1
Maven version: 1.9.0
```

That failure is expected for the current repository history. `v1.9.1` was a docs/evidence-only release, and Maven/app metadata was not aligned to `1.9.1` before the tag was created.

Existing tags remain immutable. Public `main` remains untouched.

## B. Why Clarification Is Needed

The `v1.9.1` workflow failure is not evidence that the current release is broken. It shows that the release-artifact guard is working as designed.

The workflow uses semantic tags matching `v*.*.*` as release-artifact triggers. For those tags, it extracts the tag version, reads the Maven project version, and refuses to upload release artifacts when the two values differ. That prevents a misleading artifact bundle such as a `v1.9.1` release artifact containing a `1.9.0` Maven/JAR version.

Future docs-only patch releases need a clear policy:

- either align Maven/app metadata before pushing a semantic release tag,
- or define a separate non-artifact tag or branch policy later for documentation-only markers.

Until a separate policy exists, every semantic tag should be treated as an artifact-producing release tag.

## C. Recommended Docs Update

Recommended file to update in the implementation slice:

```text
evidence/RELEASE_ARTIFACT_EVIDENCE.md
```

Add a short section:

```text
Known Historical Workflow Failure: v1.9.1
```

Recommended contents:

- `v1.9.1` was a docs/evidence-only patch release.
- The `Release Artifacts` workflow failed before artifact upload.
- The failure reason was tag/Maven metadata mismatch: tag `1.9.1` versus Maven `1.9.0`.
- The version-alignment guard prevented misleading release artifacts.
- `v2.0.0` passed the `Release Artifacts` workflow and is the current release artifact baseline.
- Do not move, rewrite, or rerun the `v1.9.1` tag to change historical behavior.

Optional supporting update:

```text
evidence/SUPPLY_CHAIN_EVIDENCE.md
```

Add one sentence explaining that semantic release tags matching `v*.*.*` require Maven/app metadata alignment before push because the release workflow intentionally fails on mismatches.

No README update is recommended unless reviewers are repeatedly confused by the historical `v1.9.1` run. Keep the first implementation focused on evidence docs.

## D. Future Release Policy

Recommended release policy:

- Every semantic tag matching `v*.*.*` should align Maven project version, app metadata, CLI version, and active README JAR examples before it is pushed.
- Semantic tags should be treated as release-artifact tags because the current workflow triggers on `v*.*.*`.
- Docs-only releases should still align metadata if they use a semantic release tag.
- If a future docs-only marker should not produce release artifacts, define a separate tag convention or branch/PR policy in a dedicated planning slice.
- Do not move historical tags to fix failed workflow runs.
- Do not rerun or alter `v1.9.1`; the failure is useful evidence that the guard refused mismatched artifacts.

## E. What Not To Change

- No production code.
- No tests.
- No `pom.xml`.
- No workflows.
- No release artifact workflow trigger changes.
- No new release artifacts.
- No tag movement.
- No rerun or alteration of `v1.9.1`.
- No remote changes.
- No public `main`.
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

Expected result:

- Docs/evidence only.
- No source changes.
- No test changes.
- No `pom.xml` changes.
- No workflow changes.
- No generated artifacts committed.

Recommended text check:

```text
rg -n "Known Historical Workflow Failure|v1.9.1|Tag version: 1.9.1|Maven version: 1.9.0|v2.0.0" evidence/RELEASE_ARTIFACT_EVIDENCE.md evidence/SUPPLY_CHAIN_EVIDENCE.md
```

## G. Recommendation

Proceed with `v2.0.1` as a narrow docs-only clarification before further deployment or operations implementation.

The patch should document that the `v1.9.1` workflow failure is historical, expected, and evidence that the tag/Maven version guard worked. It should also make the future semantic-tag policy explicit: if a tag matches `v*.*.*`, align Maven/app metadata before pushing it.

