# LoadBalancerPro v1.3.0 Weighted Least-Load Plan

Date: 2026-05-03

## A. Current v1.2.1 State

- `v1.2.1` is shipped and pushed to `origin/loadbalancerpro-clean`.
- Current release branch state inspected from `loadbalancerpro-clean`:
  - Remote branch hash: `3be016ed82d83b08a4b00e128e7f3c47a82fd639`
  - Annotated tag: `v1.2.1`
  - Tag target: `3be016ed82d83b08a4b00e128e7f3c47a82fd639`
- `v1.2.0` added the routing comparison engine and `POST /api/routing/compare`.
- `v1.2.1` aligned Maven/JAR/API/CLI/telemetry/README release metadata to `1.2.1`.
- `v1.2.1` documents `POST /api/routing/compare` in the README.
- Current real routing strategy set contains only `TAIL_LATENCY_POWER_OF_TWO`.
- `POST /api/routing/compare` is structurally ready for multi-strategy comparison, but the product value is still limited while only one real strategy is registered.

Relevant current architecture:

- `RoutingStrategy` is the core strategy interface.
- `RoutingStrategyId` currently has one ID: `TAIL_LATENCY_POWER_OF_TWO`.
- `RoutingStrategyRegistry.defaultRegistry()` currently registers `TailLatencyPowerOfTwoStrategy`.
- `RoutingComparisonEngine.compare(candidates)` uses `registry.registeredIds()`.
- `RoutingComparisonService` also uses `registry.registeredIds()` when API callers omit `strategies`.
- Therefore, adding a second strategy to the default registry should make omitted API strategy requests compare both strategies, unless the implementation deliberately chooses a more conservative registration path.

Important model note:

- Capacity and estimated concurrency are already present in `ServerStateVector`.
- Queue depth, latency, health, in-flight requests, and error rate are already present in `ServerStateVector`.
- Weight is present on allocation/API `ServerInput` and core `Server`, but is not currently present in `RoutingServerStateInput` or `ServerStateVector`.
- If `WEIGHTED_LEAST_LOAD` is expected to truly respect caller-provided weight in `POST /api/routing/compare`, the v1.3.0 implementation needs a narrow, backward-compatible optional weight field in the routing comparison API/state model. If that is considered too much API shape change for the first slice, the strategy can default weight to `1.0`, but that would weaken the stated product goal.

## B. Why A Second Strategy Is Needed

Adding `WEIGHTED_LEAST_LOAD` makes the comparison endpoint useful in a way a single strategy cannot:

- It lets callers see strategy agreement and disagreement.
- It makes `POST /api/routing/compare` demonstrate actual comparison instead of a one-result recommendation.
- It gives a better fit for heterogeneous server pools where backends have different capacity or intended traffic share.
- It helps explain tradeoffs between tail-latency sampling and whole-pool normalized load.
- It adds product value without touching `CloudManager`, AWS mutation paths, CLI behavior, or existing allocation endpoints.

This is safer than consistent hashing for v1.3.0 because it uses telemetry and capacity concepts already represented by the project. Consistent hashing would introduce stickiness, key-space behavior, ring maintenance, and a different set of API inputs.

## C. Proposed New Strategy

- Strategy ID: `WEIGHTED_LEAST_LOAD`
- Class name proposal: `WeightedLeastLoadStrategy`
- Registration location: `RoutingStrategyRegistry`
- API selection: callers should be able to pass `"WEIGHTED_LEAST_LOAD"` in `POST /api/routing/compare`.
- Default comparison behavior: if `strategies` is omitted, the default registry should include both:
  - `TAIL_LATENCY_POWER_OF_TWO`
  - `WEIGHTED_LEAST_LOAD`

Recommended default registration order:

```text
TAIL_LATENCY_POWER_OF_TWO
WEIGHTED_LEAST_LOAD
```

Keeping the existing strategy first should reduce README/test churn and preserve current response ordering expectations where practical. Tests that currently assert omitted strategies return exactly one result will need to be updated to expect both strategies after registration.

## D. Proposed Behavior

`WEIGHTED_LEAST_LOAD` should choose the healthiest and least-loaded server after normalizing pressure signals by server capacity and weight.

The strategy must:

- Use only caller-provided `ServerStateVector` telemetry.
- Exclude unhealthy servers from selection.
- Evaluate the full eligible candidate set, not a random sample.
- Produce deterministic scores and deterministic tie-breaking.
- Return a safe no-decision result for empty or all-unhealthy candidate lists.
- Include `strategyUsed`, `candidateServersConsidered`, `chosenServerId`, `scores`, `reason`, and timestamp in the same response shape as existing routing decisions.
- Not mutate `LoadBalancer` state.
- Not call `CloudManager`.
- Not call AWS.
- Not change allocation endpoint behavior.
- Not alter `TAIL_LATENCY_POWER_OF_TWO` behavior.

The strategy should be read-only and recommendation-only, just like the existing routing comparison endpoint.

## E. Exact Scoring Formula Proposal

Lower score is better.

Initial formula to evaluate and refine:

```text
effectiveCapacity = max(configuredCapacity, estimatedConcurrencyLimit, 1.0)
effectiveWeight = max(weight, 0.1)

loadPressure = inFlightRequestCount / effectiveCapacity
queuePressure = queueDepth / effectiveCapacity
latencyPressure = averageLatencyMillis / max(p95LatencyMillis, averageLatencyMillis, 1.0)
tailPressure = p95LatencyMillis / max(p99LatencyMillis, p95LatencyMillis, 1.0)
errorPressure = recentErrorRate

weightedScore =
    (loadPressure * 0.45)
  + (queuePressure * 0.20)
  + (latencyPressure * 0.15)
  + (tailPressure * 0.10)
  + (errorPressure * 0.10)

finalScore = weightedScore / effectiveWeight
```

Formula discussion:

- This formula intentionally emphasizes normalized current load.
- Capacity normalization lets larger servers absorb more in-flight requests before being penalized.
- Queue pressure catches backlog even when in-flight counts look acceptable.
- Latency and tail pressure prevent a high-capacity but degraded server from winning too easily.
- Error pressure is included directly and should remain bounded by existing `ServerStateVector` validation.
- Dividing by weight lets a higher-weight server be preferred when telemetry pressure is otherwise similar.

Potential refinement before implementation:

- `effectiveCapacity = max(configuredCapacity, estimatedConcurrencyLimit, 1.0)` may not be the best semantic choice. If estimated concurrency limit is lower than configured capacity, using the max could overstate safe capacity. A more conservative option is:

```text
effectiveCapacity =
    estimatedConcurrencyLimit if present
    else configuredCapacity if present
    else 1.0
```

- That conservative option matches `ServerScoreCalculator.capacityBasis`, which currently prefers estimated concurrency over configured capacity.
- `ServerScoreCalculator` already provides capacity normalization, queue ratio, error scoring, latency scoring, network risk scoring, and unhealthy penalty behavior. However, it is tuned for tail-latency risk with absolute millisecond weights, not a normalized weighted least-load strategy.
- Recommended implementation direction: do not blindly reuse `ServerScoreCalculator.score()` as the full formula, because that would make the second strategy too similar to `TAIL_LATENCY_POWER_OF_TWO`. Instead:
  - Consider extracting or reusing a tiny capacity-basis helper if doing so stays simple.
  - Consider reusing `networkRiskScore` only if network awareness is deliberately included in `WEIGHTED_LEAST_LOAD`.
  - Keep the weighted least-load formula isolated enough that strategy disagreement is possible and explainable.

Network-awareness note:

- The proposed formula above does not include `NetworkAwarenessSignal`.
- That keeps v1.3.0 narrower and easier to explain.
- A future revision could add a small network penalty after tests prove the base strategy is stable.

## F. Missing Capacity And Weight Handling

Capacity handling:

- Missing capacity should fall back safely to estimated concurrency limit if present.
- Missing capacity and missing estimated concurrency should fall back to `1.0`.
- Zero capacity should fall back to `1.0`.
- Negative capacity is already rejected by `ServerStateVector` validation when present.
- Estimated concurrency limit is already rejected when zero or negative.

Weight handling:

- Current routing comparison state does not carry weight.
- Recommended v1.3.0 API/state addition:
  - Add optional `Double weight` to `RoutingServerStateInput`.
  - Add optional or defaulted weight to `ServerStateVector`.
  - Map missing weight to `1.0`.
  - Reject non-finite and negative weight at the API boundary if a weight field is added.
  - Treat zero weight as `1.0` or clamp it to the minimum, depending on the final product semantics.
- Recommended scoring semantics:
  - Missing weight: `1.0`
  - Zero weight: `1.0` for safe compatibility, unless product explicitly wants zero-weight candidates to be avoided.
  - Negative weight: reject before strategy scoring if exposed through the API/state model.
  - Very small positive weight: clamp to `0.1` to avoid explosive scores.

Telemetry defaults:

- Missing queue depth already maps to `OptionalInt.empty()` and should score as `0`.
- Missing network awareness already maps to neutral signal.
- Latency and error values are required by the current routing comparison API, so the strategy can rely on validated non-negative finite latency and bounded error rate.
- Unhealthy servers should be excluded rather than scored with a penalty, matching `TailLatencyPowerOfTwoStrategy` selection behavior.

## G. Tie-Breaking

Tie-breaking must be deterministic:

1. Lowest `finalScore` wins.
2. If final scores are equal, choose the lexicographically smallest `serverId`.
3. If a future input path allows missing or blank `serverId`, use stable input ordering as a final fallback. Current `ServerStateVector` rejects missing/blank IDs, so this is only a defensive note.
4. Empty candidate lists should return a successful no-decision result with no chosen server, no candidate IDs, empty scores, and a reason such as `No healthy eligible servers were available.`
5. All-unhealthy candidate lists should behave the same as empty eligible lists.

Implementation should avoid depending on `HashMap` iteration order. Use `List` ordering, `LinkedHashMap` for score output, and an explicit comparator:

```text
score ascending, then serverId ascending
```

## H. Files Likely To Change During Implementation

Core:

- `src/main/java/core/RoutingStrategyId.java`
- `src/main/java/core/WeightedLeastLoadStrategy.java`
- `src/main/java/core/RoutingStrategyRegistry.java`
- Possibly `src/main/java/core/ServerScoreCalculator.java`
- Likely `src/main/java/core/ServerStateVector.java` if weight becomes first-class routing telemetry.

API:

- Likely `src/main/java/api/RoutingServerStateInput.java` if API callers can provide weight.
- Likely `src/main/java/api/RoutingComparisonService.java` if it maps optional weight into `ServerStateVector`.
- `src/main/java/api/RoutingController.java` should not need behavior changes.

Tests:

- `src/test/java/core/WeightedLeastLoadStrategyTest.java`
- `src/test/java/core/RoutingStrategyRegistryTest.java`
- `src/test/java/core/RoutingComparisonEngineTest.java`
- `src/test/java/api/RoutingControllerTest.java`

Docs:

- `README.md` or release notes only after the v1.3.0 behavior is implemented and verified.

Do not touch `CloudManager`, AWS integrations, CLI behavior, or allocation endpoint behavior.

## I. Core Tests Needed

Add focused tests for `WeightedLeastLoadStrategy`:

- Selects lower normalized load.
- Favors higher capacity when raw in-flight counts are similar.
- Respects server weight.
- Excludes unhealthy servers.
- Handles missing capacity safely.
- Handles zero capacity safely.
- Handles missing weight safely if weight is added to `ServerStateVector`.
- Handles zero weight safely.
- Handles negative weight safely, either through validation or fallback semantics chosen in implementation.
- Handles latency pressure.
- Handles queue pressure.
- Handles error-rate pressure.
- Uses deterministic tie-breaking by `serverId`.
- Returns safe no-decision for an empty candidate list.
- Returns safe no-decision for an all-unhealthy candidate list.
- Does not mutate input state.
- Produces a complete decision explanation with strategy name, candidates, chosen server, scores, reason, and timestamp.

Registry and engine tests:

- `RoutingStrategyId.fromName("WEIGHTED_LEAST_LOAD")` resolves correctly.
- Kebab/lowercase aliases such as `weighted-least-load` resolve if the existing normalization continues to support that style.
- `RoutingStrategyRegistry.defaultRegistry()` exposes both `TAIL_LATENCY_POWER_OF_TWO` and `WEIGHTED_LEAST_LOAD`.
- Duplicate strategy registration remains rejected.
- `RoutingComparisonEngine` can compare `TAIL_LATENCY_POWER_OF_TWO` and `WEIGHTED_LEAST_LOAD` together.
- Result order follows requested strategy order.
- Default comparison order follows registry order.
- Existing `TAIL_LATENCY_POWER_OF_TWO` tests continue to pass unchanged.

## J. API Tests Needed

Update or add API tests for `POST /api/routing/compare`:

- Accepts `WEIGHTED_LEAST_LOAD` explicitly.
- Omitted `strategies` include both registered real strategies if the default registry includes both.
- Response contains separate result entries for `TAIL_LATENCY_POWER_OF_TWO` and `WEIGHTED_LEAST_LOAD`.
- Response includes distinct `strategyId` values and per-strategy score maps.
- If weight is added to the request model, API accepts valid weight and applies it to weighted least-load scoring.
- If weight is added to the request model, missing weight remains backward-compatible.
- Unsupported strategy still returns structured validation/error behavior.
- Duplicate strategies still return structured error behavior.
- Read-only behavior remains unchanged; tests should continue to prove no `CloudManager` construction for routing comparison.
- Prod API-key protection remains unchanged.
- Cloud-sandbox API-key protection remains unchanged.
- OAuth2 operator-role protection remains unchanged.
- `/api/health` remains public.

Existing tests that should be adjusted carefully:

- `RoutingControllerTest.missingStrategiesDefaultsToRegisteredTailLatencyPowerOfTwoStrategy` should become a two-strategy default assertion if the registry defaults include `WEIGHTED_LEAST_LOAD`.
- Any tests that assume one default result should be updated to assert ordering and both strategy IDs rather than removing coverage.

## K. Risks

- The score formula may overweight or underweight one telemetry dimension.
- Capacity semantics must be clear: configured capacity and estimated concurrency are related but not identical.
- Weight semantics must be explicit because routing comparison currently lacks a weight field.
- Normalization must avoid division by zero.
- Very small weights can inflate scores dramatically unless clamped.
- Deterministic tie-breaking must not accidentally depend on `HashMap` iteration order.
- Adding the strategy to the default comparison set may affect README examples and tests that assume one default result.
- If the formula reuses `ServerScoreCalculator.score()` wholesale, `WEIGHTED_LEAST_LOAD` may be too similar to `TAIL_LATENCY_POWER_OF_TWO`.
- If the formula ignores existing scoring helpers completely, duplicated capacity-basis or risk logic may drift.
- Must not change existing `TAIL_LATENCY_POWER_OF_TWO` behavior.
- Must not accidentally connect routing recommendation to allocation, `LoadBalancer`, `CloudManager`, or AWS mutation paths.
- Adding a new API request field for weight should remain backward-compatible and should not affect allocation endpoint DTOs.

## L. What Not To Change

- Do not change `CloudManager` or AWS behavior.
- Do not change existing allocation methods.
- Do not change `TAIL_LATENCY_POWER_OF_TWO` behavior.
- Do not change CLI behavior.
- Do not move existing tags.
- Do not touch public `main`.
- Do not implement consistent hashing yet.
- Do not broaden into governance, deployment, ops, SBOM, signing, or supply-chain work.
- Do not change release metadata outside the planned v1.3.0 implementation and docs.
- Do not make `POST /api/routing/compare` mutate live routing or cloud state.

## M. Recommended First Implementation Slice

Recommended order:

1. Add the enum/id only:
   - Add `WEIGHTED_LEAST_LOAD` to `RoutingStrategyId`.
   - Add parsing tests first.

2. Decide weight representation:
   - Preferred: add optional weight to routing comparison state/API in a backward-compatible way.
   - Fallback: implement v1.3.0 with implicit weight `1.0` and document that caller-provided routing weights are deferred.

3. Add `WeightedLeastLoadStrategy` with isolated unit tests:
   - Implement deterministic full-candidate scoring.
   - Add no-candidate handling.
   - Add tie-breaking tests.

4. Register the strategy:
   - Add it to `RoutingStrategyRegistry.defaultRegistry()`.
   - Preserve deterministic registry order.

5. Add registry and comparison engine tests:
   - Prove default comparison includes both real strategies.
   - Prove explicit strategy order is respected.

6. Add API tests:
   - Explicit `WEIGHTED_LEAST_LOAD`.
   - Omitted strategies include both registered strategies.
   - Existing auth/read-only protections remain unchanged.

7. Update README/release notes only after behavior is verified:
   - Document the new strategy ID.
   - Show a two-strategy comparison example.
   - State that the endpoint remains read-only and recommendation-only.

This first slice gives v1.3.0 meaningful strategy comparison while staying narrow: one new routing strategy, no allocation behavior changes, no cloud changes, no CLI changes, and no production-readiness expansion.
