# LoadBalancerPro v1.1.0 Hardening Review

Prepared: 2026-05-03

Scope: review only. This document was prepared on a local review branch. No hardening branch was merged, no tags were created, no remotes were changed, and nothing was pushed.

## Baseline

Baseline branch:

```text
loadbalancerpro-clean
```

Baseline commit:

```text
3e6aa007afab8e5792ce6808f31b54a83b955d2d
Document next release review
```

Baseline release tag:

```text
v1.0.1
```

Review branch:

```text
release/v1.1.0-hardening-review
```

## Branch Under Review

Reviewed branch:

```text
codex/robust-safety-test-expansion
```

Commits not merged into `loadbalancerpro-clean`:

```text
928be4a Add robust safety tests batch 1
b837e82 Add telemetry guardrail edge tests
32b1b76 Add replay cloud isolation tests
f4f4e92 Add input API hardening tests
```

Merge base:

```text
fa3bd9e2053e690f32e13f48b36a8f9c5e837194
```

Important diff note:

- `git diff loadbalancerpro-clean..codex/robust-safety-test-expansion` compares the current baseline directly to an older branch tip, so it shows deletions of newer docs/evidence files that were added after this branch diverged.
- The meaningful branch delta from the merge base is 15 files changed, 760 insertions, and 7 deletions.
- `git merge-tree` did not show conflict markers for merging the branch into the current baseline.
- If this branch is applied, use a normal merge or reviewed cherry-picks. Do not reset the release branch to the older branch tip.

## Files Changed

Production code:

```text
src/main/java/api/ApiErrorResponse.java
src/main/java/api/RestExceptionHandler.java
src/main/java/core/CloudManager.java
src/main/java/core/LaseShadowAdvisor.java
src/main/java/core/LoadBalancer.java
```

Tests:

```text
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

No CI, Docker, dependency, evidence-doc, or README changes are part of the meaningful robust-safety branch delta.

## Production Behavior Changes

### API Error Envelopes

`ApiErrorResponse` adds explicit helpers for:

```text
405 method_not_allowed
415 unsupported_media_type
```

`RestExceptionHandler` maps:

```text
HttpRequestMethodNotSupportedException -> structured 405 JSON
HttpMediaTypeNotSupportedException -> structured 415 JSON
```

Expected impact:

- Safer, more consistent API error responses.
- Less chance of framework-default error bodies leaking implementation detail.
- Public API behavior changes for unsupported methods and unsupported content types.

### Cloud Sandbox Guardrail

`CloudManager` adds a required sandbox resource-name prefix:

```text
lbp-sandbox-
```

For sandbox live scaling, `cloud.resourceNamePrefix` must start with that value. Otherwise the scale decision is denied with:

```text
SANDBOX_RESOURCE_PREFIX_INVALID
```

Expected impact:

- Stronger protection against accidentally scaling non-sandbox resources while using sandbox settings.
- Existing sandbox live-mode tests or manual sandbox configs that use another prefix will fail closed until updated.
- Default dry-run/local behavior should remain unaffected.

### LASE Shadow Failure Sanitization

`LaseShadowAdvisor.safeFailureReason` becomes package-visible/static and accepts `Throwable`, including null.

`LoadBalancer` uses the sanitizer when catching outer LASE shadow observation failures.

Expected impact:

- LASE shadow failures continue to fail safe.
- Null, blank, control-character-only, token-bearing, and bearer-token-bearing messages are sanitized before reaching event logs or outer warning logs.
- Diagnostic detail is intentionally reduced when it resembles a secret.

## Test Additions

API hardening coverage:

- Null and empty server-list validation.
- Missing request-body error shape.
- Missing numeric field/default behavior documentation through tests.
- Very large finite load returns finite JSON.
- Invalid HTTP method returns structured 405.
- Invalid content type returns structured 415.
- Prod API-key protection and alias coverage.
- Prod CORS/auth hardening cases.
- OAuth2 observer/operator authorization edge cases.
- Swagger UI gate behavior under OAuth2 default docs policy.

Telemetry guardrail coverage:

- Additional OTLP endpoint validation edge cases.
- Safer startup behavior around malformed, public, localhost, blank, credential-bearing, query-bearing, or fragment-bearing metrics endpoints.

Replay/cloud isolation coverage:

- LASE replay does not construct `CloudManager`.
- Replay does not require AWS credentials.
- Replay does not mutate system properties.
- Empty replay file reports zero-event summary.
- Identical replay input produces deterministic output.
- Scale-up recommendations remain advisory-only during replay.

CloudManager guardrail coverage:

- Sandbox live scale without resource prefix denies updates.
- Sandbox live scale with incorrect resource prefix denies updates.
- Sandbox live scale with `lbp-sandbox-` prefix can pass existing guardrails.

LASE shadow advisor coverage:

- Null and blank failure messages map to safe fallback.
- Sensitive token/bearer content is redacted.
- Failure followed by success does not leak stale failure reason.
- Control-character-only messages are neutralized.
- Outer LoadBalancer shadow-observation catch does not log raw sensitive message.

Import/API utility coverage:

- JSON import rejects non-finite numeric values without partial import.

## Risks

The branch is safety-positive, but it is not test-only.

Primary risks:

- `CloudManager` behavior changes for sandbox live-mode resource prefixes. This is desirable, but it is a production guardrail change.
- If any private sandbox workflow intentionally used a different prefix, it will now fail closed.
- API clients that relied on default Spring 405/415 response bodies will see structured LoadBalancerPro error envelopes instead.
- LASE shadow failure logs may contain less raw detail after sanitization, which is safer but can slightly reduce debugging detail.
- The robust-safety branch was cut before later docs/evidence commits, so reviewers must merge normally from the current baseline rather than replacing the tree with the branch tip.

Residual risk:

- The branch does not include Docker/CI supply-chain pinning. That remains separate in `codex/supply-chain-pinning`.
- The branch does not clean the historical large JavaFX file from Git history.

## Recommendation

Recommendation:

```text
Merge codex/robust-safety-test-expansion into release/v1.1.0-hardening-review as the first v1.1.0 hardening slice after this review is accepted.
```

Rationale:

- The commits are cohesive: small production guardrail changes are paired with focused tests.
- Cherry-picking only tests would fail or create misleading coverage because some tests depend on the production behavior changes.
- Cherry-picking only production changes would skip the safety evidence that makes the branch valuable.
- The branch appears mergeable without textual conflicts.

Do not merge into public/default branch directly. Merge first on the local review branch, verify, then decide whether to push a `release/v1.1.0-hardening-review` branch for PR-style review.

Defer only if:

- The project owner is not ready to enforce the `lbp-sandbox-` prefix for sandbox live-mode scaling.
- The project owner wants v1.1.0 to include supply-chain pinning first, before production guardrail changes.
- A fresh merge produces unexpected conflicts not shown by `git merge-tree`.

## Exact Verification Plan If Merged

After merging the robust safety branch into `release/v1.1.0-hardening-review`, run:

```bash
mvn -q test
mvn -q -DskipTests package
```

Inspect the packaged JAR name:

```bash
Get-ChildItem -Path target -Filter 'LoadBalancerPro-*.jar' | Sort-Object LastWriteTime -Descending | Select-Object FullName,Name,Length,LastWriteTime
```

Run LASE demo smoke checks against the newest executable JAR:

```bash
java -jar target\LoadBalancerPro-1.0.0.jar --lase-demo=healthy
java -jar target\LoadBalancerPro-1.0.0.jar --lase-demo=overloaded
java -jar target\LoadBalancerPro-1.0.0.jar --lase-demo=invalid-name
```

Run focused API/JUnit checks if a fast targeted pass is desired before the full suite:

```bash
mvn -q -Dtest=api.AllocatorControllerTest test
mvn -q -Dtest=api.OAuth2AuthorizationTest test
mvn -q -Dtest=api.config.TelemetryConfigurationTest test
mvn -q -Dtest=cli.LaseReplayCommandTest test
mvn -q -Dtest=core.CloudManagerGuardrailTest test
mvn -q -Dtest=core.LaseShadowAdvisorTest test
```

Manual review checklist:

```text
Confirm CloudManager denies sandbox live scale when resourceNamePrefix is missing or not lbp-sandbox-.
Confirm documented cloud-sandbox defaults still use lbp-sandbox-.
Confirm API 405 and 415 responses are structured JSON and do not leak stack traces.
Confirm LASE replay remains offline/read-only and does not instantiate CloudManager.
Confirm LASE shadow failure logs redact token-like and bearer-like content.
Confirm no docs/evidence files disappear in the merge result.
```

Before any push:

```bash
git status
git log --oneline --decorate -8
git diff --stat loadbalancerpro-clean..HEAD
git diff --name-status loadbalancerpro-clean..HEAD
```

Do not create `v1.1.0` until robust safety and any chosen supply-chain hardening are merged, pushed for review, and freshly verified.
