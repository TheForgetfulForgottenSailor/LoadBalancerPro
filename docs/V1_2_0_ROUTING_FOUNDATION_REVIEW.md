# LoadBalancerPro v1.2.0 Routing Foundation Review

Date: 2026-05-03

## Scope

This review covers the first core-only v1.2.0 routing comparison implementation on branch `release/v1.2.0-routing-engine`.

Reviewed commit:

- `fb9a295199e69b2d60ebcf899402ec74bcec2b77`

Reviewed files:

- `src/main/java/core/RoutingStrategy.java`
- `src/main/java/core/RoutingStrategyId.java`
- `src/main/java/core/RoutingStrategyRegistry.java`
- `src/main/java/core/RoutingComparisonEngine.java`
- `src/main/java/core/RoutingComparisonReport.java`
- `src/main/java/core/RoutingComparisonResult.java`
- `src/main/java/core/TailLatencyPowerOfTwoStrategy.java`
- `src/test/java/core/RoutingComparisonEngineTest.java`
- `docs/V1_2_0_ROUTING_ENGINE_PLAN.md`

No implementation files were changed as part of this review document.

## Summary Of What Was Added

The slice adds a small routing comparison foundation under `core`:

- `RoutingStrategy`
  - Narrow interface with `id()` and `choose(List<ServerStateVector>)`.
- `RoutingStrategyId`
  - Stable enum ID currently containing `TAIL_LATENCY_POWER_OF_TWO`.
  - Includes tolerant parsing through `fromName`.
- `RoutingStrategyRegistry`
  - Deterministic registry backed by insertion-ordered `LinkedHashMap`.
  - Rejects duplicate strategy IDs.
  - Exposes optional and required lookup paths.
- `RoutingComparisonEngine`
  - Runs requested strategies against the same copied, unmodifiable candidate list.
  - Produces one result per requested strategy.
  - Isolates strategy runtime failures into failed results.
- `RoutingComparisonReport`
  - Immutable report record with requested strategies, candidate count, results, and timestamp.
- `RoutingComparisonResult`
  - Immutable per-strategy result with strategy ID, status, optional decision, and reason.
- `TailLatencyPowerOfTwoStrategy`
  - Now implements `RoutingStrategy`.
  - Adds `id()` returning `RoutingStrategyId.TAIL_LATENCY_POWER_OF_TWO`.
  - Keeps existing `choose(...)` behavior intact.
- `RoutingComparisonEngineTest`
  - Focused tests for registry behavior, P2C behavior preservation, comparison output, deterministic result ordering, empty/all-unhealthy candidates, unregistered strategies, and per-strategy failure isolation.

## Core-Only And Non-Invasive Design

The design is core-only and non-invasive.

No public API endpoints were added or changed. No CLI behavior was changed. No `CloudManager` or AWS mutation logic was touched. Existing `LoadBalancer` allocation methods and defaults were not changed.

The only existing production class changed is `TailLatencyPowerOfTwoStrategy`, and the change is additive: it implements a narrow interface and returns a stable strategy ID.

This matches the v1.2.0 plan: establish a comparison backbone before exposing it through API, CLI, or replay workflows.

## TailLatencyPowerOfTwoStrategy Behavior Preservation

Behavior appears preserved.

The existing constructor shape, random injection, clock injection, health filtering, two-candidate sampling, score calculation, tie-breaking, explanation format, and no-healthy-candidate behavior remain unchanged.

The new `id()` method is metadata only. Existing tests still pass, and the new test verifies the same chosen server, strategy name, candidate list, and timestamp behavior for a seeded two-candidate scenario.

## Immutability And Safety Of Result Types

The result types are safe enough for this first core slice.

Positive points:

- Records are used for immutable data carriers.
- `RoutingComparisonReport` copies requested strategies and results using `List.copyOf`.
- `RoutingComparisonResult` wraps decisions in `Optional`.
- `RoutingDecision` and `RoutingDecisionExplanation` already validate and defensively copy their contents.
- The comparison engine copies the candidate list and wraps it in an unmodifiable list before handing it to strategies.

Important nuance:

- The candidate list is shallow-copied, not deeply copied. This is acceptable because `ServerStateVector` is itself an immutable record.
- The result contains the original `RoutingDecision`, which is also immutable enough for current use because the explanation defensively copies lists/maps.

## Failure Isolation

Strategy failure isolation is handled safely for runtime strategy failures.

The engine catches `RuntimeException` thrown by an individual strategy and converts it into a `RoutingComparisonResult` with:

- `Status.FAILED`
- empty decision
- safe reason text

This means one bad strategy does not crash the whole comparison report.

Unregistered strategy IDs also produce a failed result instead of crashing.

This is the right behavior for future API/CLI/replay output, where partial results are more useful than a total comparison failure.

## Registry Behavior

Registry behavior is deterministic and safe.

Positive points:

- Uses `LinkedHashMap`, preserving registration order.
- `registeredIds()` returns a copied list.
- Duplicate strategy IDs are rejected at registry construction time.
- Null registry collections, null strategy instances, and null strategy IDs are rejected.
- `find(null)` safely returns `Optional.empty`.
- `require(...)` provides a clear exception for missing registrations.

The default registry currently contains only `TAIL_LATENCY_POWER_OF_TWO`, which is exactly right for this first slice.

## Test Coverage Review

The focused test coverage is strong for the current scope.

Covered:

- Existing P2C behavior still works after implementing `RoutingStrategy`.
- Default registry returns `TAIL_LATENCY_POWER_OF_TWO`.
- Unknown string strategy IDs parse as absent.
- Empty registry lookup is absent or rejected safely.
- Comparison returns one result per requested strategy.
- Result ordering follows requested strategy ordering.
- Empty candidates produce a safe no-candidate decision.
- All-unhealthy candidates produce a safe no-candidate decision.
- Runtime strategy failure is isolated into a failed result.
- Unregistered strategy request is isolated into a failed result.
- Result includes strategy ID, status, decision, and reason.

Existing broader tests also continue to protect:

- P2C candidate sampling and deterministic seeded behavior.
- Health filtering.
- Score explanation contents.
- LASE integration paths that use `TailLatencyPowerOfTwoStrategy`.
- Existing `LoadBalancer` allocation behaviors.

## Design Concerns

No release-blocking issues were found.

Small concerns to keep in mind:

- `RoutingComparisonEngine.compare(...)` fails fast on null entries in `requestedStrategies` because `List.copyOf` rejects nulls. That is reasonable for a core API, but future API/CLI exposure should convert malformed user input into structured validation errors before calling this core method.
- `RoutingComparisonResult.failed(...)` requires a non-null `RoutingStrategyId`. This is fine if IDs are parsed before comparison, but future external input should never pass null IDs into the engine.
- The default registry uses a default `TailLatencyPowerOfTwoStrategy` with an unseeded `Random`. That is normal for real P2C behavior, but deterministic demo/replay output should keep injecting seeded strategies.
- Duplicate requested strategies are allowed and return duplicate result rows. The current behavior is deterministic in ordering, but with stateful random strategies the second decision may sample different candidates. This is acceptable for a core comparison primitive, but API/CLI design should decide whether duplicate strategy IDs are useful or should be de-duplicated at the edge.
- `RoutingStrategyId.fromName` normalizes hyphens but not spaces. That is fine for now; external input handling can stay strict.

## Recommended Small Cleanup Before Push

No mandatory cleanup is needed before pushing for CI review.

Optional small cleanup before or after CI:

- Add one test for duplicate registry ID rejection.
- Add one test documenting null requested strategy handling, either as clear fail-fast validation or as an edge-layer responsibility.
- Consider adding Javadoc to `RoutingStrategy` once an API or CLI surface starts depending on it.

These are polish-level improvements, not blockers.

## CI Push Readiness

This branch is safe to push for CI review.

Reasons:

- Scope is core-only.
- API behavior is unchanged.
- CLI behavior is unchanged.
- Cloud/AWS mutation logic is unchanged.
- Existing `LoadBalancer` allocation behavior/defaults are unchanged.
- Strategy comparison failure isolation is in place.
- Result and report types are immutable enough for the current use case.
- Registry behavior is deterministic.
- Local verification already passed:
  - `mvn -q test`: 512 tests, 0 failures, 0 errors, 0 skipped
  - `mvn -q -DskipTests package`: passed

Recommendation: push `release/v1.2.0-routing-engine` to origin/public for CI and Trivy review. Defer public API/CLI exposure to the next v1.2.0 slice after this core foundation passes CI.
