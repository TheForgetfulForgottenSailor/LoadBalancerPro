# LoadBalancerPro v1.2.0 Routing Engine Plan

Date: 2026-05-03

## Scope

This document plans `v1.2.0` as a product capability upgrade focused on smarter routing strategies and side-by-side strategy comparison. No production code, tests, remotes, tags, or public branches were changed as part of this planning pass.

## Baseline

- Baseline branch: `loadbalancerpro-clean`
- Planning branch: `planning/v1.2.0-routing-engine`
- Baseline commit: `11ebc766fb2fc5a93c5bf84f0552fc231f2d46f1`
- Published tags: `v1.0.1`, `v1.1.0`, `v1.1.1`
- Public default branch: `loadbalancerpro-clean`
- Public `main`: preserved and must remain untouched
- Latest verified release: `v1.1.1`

## Current Routing And Allocation State

LoadBalancerPro currently has two related but separate routing surfaces.

The legacy allocation surface lives in `LoadBalancer` and `LoadDistributionPlanner`. It distributes a total amount of synthetic load/data across healthy `Server` instances and updates `currentDistribution`.

Existing allocation methods:

- `roundRobin(double totalData)`
- `leastLoaded(double totalData)`
- `weightedDistribution(double totalData)`
- `consistentHashing(double totalData, int numKeys)`
- `capacityAware(double totalData)` / `capacityAwareWithResult(double totalData)`
- `predictiveLoadBalancing(double totalData)` / `predictiveLoadBalancingWithResult(double totalData)`
- `rebalanceExistingLoad()`, controlled only by `LoadBalancer.Strategy.ROUND_ROBIN` or `LoadBalancer.Strategy.LEAST_LOADED`

The telemetry routing surface is newer and lives around LASE:

- `ServerStateVector`
- `ServerScoreCalculator`
- `RoutingDecision`
- `RoutingDecisionExplanation`
- `TailLatencyPowerOfTwoStrategy`
- `LaseEvaluationEngine`
- LASE demo, shadow advisor, event log, and replay support

This newer surface makes a single routing decision from telemetry-rich server state. It considers health, in-flight requests, configured or estimated capacity, latency percentiles, error rate, queue depth, and network awareness signals.

## Strategies Already Present

Public or semi-public allocation methods already present:

- Round robin: equal split across healthy servers.
- Least loaded: sorts by `Server.getLoadScore()`, but current tests intentionally preserve an equal-allocation contract for common cases.
- Weighted distribution: splits by `Server.getWeight()`, with all-zero weights falling back to equal allocation.
- Consistent hashing: maps synthetic `data-N` keys onto a hash ring with configured replicas and skips unhealthy servers.
- Capacity-aware: allocates by available capacity and returns unallocated load.
- Predictive: allocates by predicted available capacity using a predictive load factor.

Telemetry decision strategy already present:

- `TAIL_LATENCY_POWER_OF_TWO`: samples two eligible healthy `ServerStateVector` candidates and picks the lower `ServerScoreCalculator` score.

## Internal-Only Vs Public Exposure

Public API exposure today:

- `GET /api/health`
- `GET /api/lase/shadow`
- `POST /api/allocate/capacity-aware`
- `POST /api/allocate/predictive`

The public API does not currently expose round robin, weighted, consistent hashing, least-loaded, or telemetry-based P2C strategy selection.

CLI exposure today:

- Interactive menu option `Balance Load` exposes six allocation strategies:
  - Round Robin
  - Least Loaded
  - Weighted
  - Consistent Hashing
  - Capacity-Aware
  - Predictive

Internal-only or shadow/demo exposure today:

- `TailLatencyPowerOfTwoStrategy` is used by `LaseEvaluationEngine`, demos, shadow advisor, and telemetry-routing tests.
- LASE replay evaluates saved shadow events and metrics, but it does not yet run multiple routing strategies against the same input and compare recommendations.
- `ServerScoreCalculator` and `RoutingDecisionExplanation` are strong foundations but are not yet generalized behind a strategy interface.

## Functional Weaknesses

- Product users cannot compare strategies through the API.
- The CLI can run individual distribution methods, but it does not produce a side-by-side comparison table or recommendation.
- The API exposes only capacity-aware and predictive allocation, leaving several existing algorithms invisible to API users.
- Existing allocation methods answer "how should this batch of load be distributed?" while `TailLatencyPowerOfTwoStrategy` answers "which server should receive this request?" These concepts are useful but not unified.
- `LoadBalancer.Strategy` only supports round robin and least loaded for rebalance, even though more methods exist.
- Least-loaded behavior is currently intentionally equal-allocation in tests for common cases, so changing it in place would risk breaking existing behavior and expectations.
- Consistent hashing exists, but only with synthetic key generation. There is no API/CLI path where a caller can supply a request key and inspect ring movement or stickiness behavior.
- Weighted routing exists as pure weight split, but not as weighted least-load or weighted capacity scoring.
- Strategy explanations exist for LASE routing but not for legacy allocation methods.
- There is no first-class comparison result model that captures candidates, chosen server, scores, distribution, unallocated load, stability, and reason per strategy.

## Proposed v1.2.0 Goal

Build a strategy comparison routing engine that can run multiple routing strategies against the same server state and return explainable, testable results without changing default public allocation behavior.

The v1.2.0 product goal should be:

- Keep existing allocation endpoints and CLI behavior stable by default.
- Promote telemetry routing foundations into a small strategy abstraction.
- Add side-by-side comparison tooling first.
- Add at least one user-visible comparison path after the engine is tested.
- Make recommendations explainable enough for API, CLI, LASE replay, and docs.

## Proposed Algorithms

### Power Of Two Choices

Purpose:

- Choose one request target by sampling two healthy candidates and selecting the better score.

Current foundation:

- `TailLatencyPowerOfTwoStrategy` already implements a telemetry-rich P2C variant with seeded testability, clock injection, candidate explanations, and `ServerScoreCalculator`.

v1.2.0 direction:

- Generalize the existing strategy behind a small interface.
- Keep the existing tail-latency P2C semantics.
- Add comparison output that shows sampled candidates, chosen server, score map, and reason.
- Consider adding a simpler `POWER_OF_TWO_CHOICES` alias only if it is clearly distinct from the existing `TAIL_LATENCY_POWER_OF_TWO`.

### Weighted Least-Load / Weighted Capacity Routing

Purpose:

- Prefer servers with lower normalized load while respecting configured weight and capacity.

Current foundation:

- `Server` has `weight`, `capacity`, `loadScore`, and health state.
- `LoadDistributionPlanner.weighted` and `capacityAwareResult` already use weight/capacity concepts.
- `ServerScoreCalculator` can normalize in-flight and queue signals by capacity basis.

v1.2.0 direction:

- Add a strategy that scores candidates by load/capacity/weight instead of pure weight ratio.
- Keep current `weightedDistribution` behavior unchanged.
- Provide comparison-only output first, then decide whether to expose as a new API endpoint or CLI strategy.

### Consistent Hashing

Purpose:

- Route requests by key for sticky assignment and lower movement when servers join or leave.

Current foundation:

- `LoadBalancer` maintains `consistentHashRing`.
- `consistentHashing(double totalData, int numKeys)` distributes synthetic `data-N` keys.
- Duplicate replacement updates the hash ring and has tests.

v1.2.0 direction:

- Add request-key based decision support, not just synthetic batch distribution.
- Report chosen server and ring behavior for a caller-supplied key.
- Add comparison metrics for key movement when adding/removing servers.
- Defer public mutation of hash ring behavior until tests cover stability and failover.

## Which Algorithm Should Come First

Start with Power of Two Choices strategy comparison first.

Reasons:

- The repo already has `TailLatencyPowerOfTwoStrategy`, `ServerStateVector`, `ServerScoreCalculator`, `RoutingDecision`, and `RoutingDecisionExplanation`.
- Tests already cover health filtering, deterministic seeded sampling, candidate count, score explanations, network risk scoring, and no-healthy-server behavior.
- P2C is request-routing oriented, which fits the desired product upgrade better than another batch distribution method.
- It can be added behind comparison tooling without changing existing allocation endpoints or CLI default behavior.
- It gives a clean path to compare future strategies because it already returns explainable decisions.

## Exact Classes Likely To Change

Likely core changes:

- `src/main/java/core/TailLatencyPowerOfTwoStrategy.java`
  - Implement a small routing strategy interface if added.
  - Preserve current behavior and strategy name.
- `src/main/java/core/LaseEvaluationEngine.java`
  - Depend on the interface instead of the concrete `TailLatencyPowerOfTwoStrategy`, if the interface remains narrow and safe.
- `src/main/java/core/RoutingDecision.java`
  - Likely unchanged unless comparison needs metadata.
- `src/main/java/core/RoutingDecisionExplanation.java`
  - Likely unchanged unless comparison needs standardized fields.
- `src/main/java/core/ServerScoreCalculator.java`
  - Reuse for P2C.
  - Possibly add a separate calculator for weighted least-load later.
- `src/main/java/core/ServerStateVector.java`
  - Likely unchanged for first slice.
- `src/main/java/core/LoadBalancer.java`
  - Avoid changing existing allocation behavior in first slice.
  - Later add adapters only if needed.
- `src/main/java/core/LoadDistributionPlanner.java`
  - Avoid changing current contracts in first slice.
  - Later add weighted least-load or key-based hashing helpers with separate names.

Likely API changes after the core comparison engine is tested:

- `src/main/java/api/AllocatorController.java`
- `src/main/java/api/AllocatorService.java`
- New request/response records under `src/main/java/api`

Likely CLI changes after the core comparison engine is tested:

- `src/main/java/cli/LoadBalancerCLI.java`
- `src/main/java/cli/LaseReplayCommand.java`, if replay comparison becomes part of the first visible workflow

Likely tests:

- `src/test/java/core/ServerTelemetryRoutingTest.java`
- `src/test/java/core/LaseEvaluationEngineTest.java`
- `src/test/java/core/LoadBalancerTest.java`, only if `LoadBalancer` behavior changes
- New focused tests for comparison engine and strategy registry
- API controller/service tests if an endpoint is exposed
- CLI tests if a command or menu option is exposed

## New Interfaces And Classes To Consider

Recommended minimal core interfaces/classes:

- `RoutingStrategy`
  - Method shape: `RoutingDecision choose(List<ServerStateVector> servers)`
  - Metadata shape: `String name()`
  - Keep it narrow; avoid config sprawl in the first slice.
- `RoutingStrategyId`
  - Enum or value object for stable names such as `TAIL_LATENCY_POWER_OF_TWO`, `WEIGHTED_LEAST_LOAD`, `CONSISTENT_HASH`.
- `RoutingStrategyRegistry`
  - Maps strategy IDs to strategy instances.
  - Useful for API/CLI validation and comparison ordering.
- `RoutingComparisonEngine`
  - Runs selected strategies against the same input.
  - Returns per-strategy decisions and optional recommendation.
- `RoutingComparisonReport`
  - Captures input summary, per-strategy decisions, chosen strategy if any, and timestamp.
- `RoutingComparisonResult`
  - Captures strategy name, decision, status, and reason.

Optional later classes:

- `WeightedLeastLoadStrategy`
- `ConsistentHashRoutingStrategy`
- `RequestRoutingInput`
- `RequestRoutingRequest` / `RequestRoutingResponse` for API
- `RoutingComparisonRequest` / `RoutingComparisonResponse` for API
- `RoutingReplayComparisonEngine` for offline comparison from replay records

## API Exposure Options

Option A: Comparison-only endpoint first.

- Add `POST /api/routing/compare`.
- Input includes server telemetry candidates and requested strategies.
- Output includes one result per strategy with chosen server, candidates, scores, and explanation.
- Does not mutate `LoadBalancer`.
- Does not alter existing `/api/allocate/capacity-aware` or `/api/allocate/predictive`.

Option B: Explicit route decision endpoint.

- Add `POST /api/routing/decide`.
- Input includes strategy and server telemetry.
- Output is one `RoutingDecision`.
- This is simpler than comparison, but less aligned with the v1.2.0 product goal.

Option C: Extend allocation endpoints.

- Add strategy selection to `/api/allocate`.
- Higher compatibility risk because allocation methods are batch distribution methods, while P2C is request routing.
- Defer until the model distinction is clean.

Recommendation:

- Start with option A after the core engine is tested.
- Keep it read-only and recommendation-only.
- Require the same allocation role if OAuth2 mode is enabled because it influences routing decisions.

## CLI Exposure Options

Option A: Add a non-interactive comparison command.

- Example shape: `--routing-compare=<json-file>` or `--routing-compare-demo`.
- Emits a compact table of strategy, chosen server, score, candidates, and reason.
- Easier to test than interactive menu changes.

Option B: Add interactive menu option.

- Add "Compare Routing Strategies" to the CLI menu.
- Higher friction because it needs prompts for telemetry fields.
- Better for demos after the non-interactive path is stable.

Option C: Extend LASE demo/replay commands.

- Let `--lase-demo` or `--lase-replay` compare P2C against future strategies.
- Good fit because LASE already has telemetry-shaped input and explanation formatting.

Recommendation:

- First expose via a non-interactive command or replay/demo option, not by changing the main menu.

## Replay And Comparison Lab Options

Short-term:

- Add comparison output to synthetic LASE scenarios.
- Use deterministic clocks and seeded strategies for repeatable output.
- Report agreement/disagreement between current LASE P2C and comparison strategies.

Medium-term:

- Extend LASE replay to run selected strategies over saved `ServerStateVector` candidates.
- Produce aggregate metrics:
  - strategy agreement rate
  - chosen server distribution
  - no-candidate count
  - average score of chosen candidate
  - network risk of chosen candidate
  - disagreement examples

Longer-term:

- Add sample replay fixtures for overloaded, partial outage, key stickiness, and capacity-skew scenarios.
- Make comparison output useful in CI without requiring live cloud resources.

## Test Plan

Core unit tests:

- `RoutingStrategy` contract rejects null inputs consistently.
- P2C strategy keeps existing deterministic seeded sampling.
- P2C filters unhealthy candidates.
- P2C returns safe empty decisions for no healthy candidates.
- P2C explanation includes strategy, candidates, chosen server, scores, reason, and timestamp.
- Comparison engine runs multiple strategies against one immutable input.
- Comparison engine isolates strategy failures so one strategy cannot break the entire report.
- Comparison result ordering is deterministic.
- Strategy registry rejects unknown strategy IDs with clear messages.

Weighted least-load tests, when added:

- Lower normalized load wins.
- Capacity and weight affect score in expected directions.
- Zero weight is handled safely.
- Unhealthy servers are excluded.
- Tie-breaking is deterministic.

Consistent hashing tests, when added:

- Same key maps to same server while ring is unchanged.
- Removing a server remaps only affected keys as much as practical.
- Unhealthy chosen ring entries are skipped.
- Empty ring and all-unhealthy ring return safe no-candidate decisions.
- Duplicate server replacement updates routing decisions consistently.

API tests, if endpoint is added:

- Request validation for empty server list, invalid strategy IDs, malformed telemetry, and negative metrics.
- Successful comparison response includes all requested strategy results.
- OAuth2/API key protections match allocation endpoint expectations.
- Structured 400/405/415 behavior remains intact.

CLI/replay tests, if command is added:

- Non-interactive command prints deterministic comparison output.
- Invalid strategy names fail safely.
- LASE demo smoke remains stable.
- Replay comparison handles malformed replay records safely.

Verification commands after implementation:

```powershell
mvn -q test
mvn -q -DskipTests package
java -jar target\LoadBalancerPro-1.1.1.jar --lase-demo=healthy
java -jar target\LoadBalancerPro-1.1.1.jar --lase-demo=overloaded
java -jar target\LoadBalancerPro-1.1.1.jar --lase-demo=invalid-name
docker build -t loadbalancerpro:v1.2.0-routing-engine-review .
docker run --rm -d --name loadbalancerpro-v120-routing -p 127.0.0.1:18081:8080 loadbalancerpro:v1.2.0-routing-engine-review
curl.exe -fsS http://127.0.0.1:18081/api/health
docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' loadbalancerpro-v120-routing
docker stop loadbalancerpro-v120-routing
```

## Risks

- Blurring batch load allocation and request routing could create confusing APIs. Keep models distinct.
- Changing existing `LoadBalancer` methods would risk breaking current CLI/API behavior and tests.
- Random candidate sampling can make tests flaky unless seeded/random injection remains available.
- Strategy comparison output can become noisy if it exposes too much raw telemetry.
- Consistent hashing can be deceptively complex once caller-supplied keys, health skipping, and ring churn are exposed.
- Weighted least-load semantics need clear definitions for load, capacity, and weight or users will misread results.
- API exposure adds auth, validation, OpenAPI, and backward compatibility concerns.
- Live cloud mutation logic must stay out of this work. Routing comparison should be recommendation-only.

## What Not To Do

- Do not change default allocation behavior in the first v1.2.0 slice.
- Do not rename or rewrite existing allocation methods.
- Do not make `leastLoaded` truly least-loaded in place while tests preserve the current equal-allocation contract.
- Do not collapse `capacityAware`, `predictive`, and P2C into one ambiguous endpoint.
- Do not add live AWS mutation or autoscaling side effects.
- Do not use comparison output to automatically route production traffic yet.
- Do not make CLI startup depend on Spring context.
- Do not rewrite historical release/audit docs as part of routing work.

## Recommended First Implementation Slice

Implement a core-only strategy comparison foundation first.

Suggested slice:

1. Add a narrow `RoutingStrategy` interface.
2. Make `TailLatencyPowerOfTwoStrategy` implement it without changing behavior.
3. Add `RoutingStrategyRegistry` with the existing `TAIL_LATENCY_POWER_OF_TWO` strategy.
4. Add `RoutingComparisonEngine` and result records.
5. Add focused tests proving comparison output is deterministic, explainable, and safe for empty/all-unhealthy inputs.
6. Keep public API, CLI menu, `LoadBalancer` allocation methods, and defaults unchanged.
7. After tests pass, consider a second slice that exposes comparison through a recommendation-only API endpoint or non-interactive CLI command.

This gives v1.2.0 a real product capability upgrade while staying conservative: the repo gets a routing-engine backbone and strategy comparison path, but current allocation behavior remains stable until the new surface is verified.

## Proposed Command Plan

DO NOT RUN YET:

```powershell
git status
git switch loadbalancerpro-clean
git pull --ff-only origin loadbalancerpro-clean
git switch -c release/v1.2.0-routing-engine

# First implementation slice:
# - Add RoutingStrategy interface.
# - Adapt TailLatencyPowerOfTwoStrategy without behavior changes.
# - Add RoutingStrategyRegistry.
# - Add RoutingComparisonEngine and result records.
# - Add focused core tests.
# - Do not change public allocation defaults.

git diff -- src/main/java/core src/test/java/core docs
mvn -q test
mvn -q -DskipTests package

git status
git add src/main/java/core src/test/java/core docs/V1_2_0_ROUTING_ENGINE_PLAN.md
git commit -m "Add routing strategy comparison foundation"

git push origin release/v1.2.0-routing-engine
git push public release/v1.2.0-routing-engine:release/v1.2.0-routing-engine

# After GitHub Actions and Trivy pass, decide whether the next v1.2.0 slice
# should expose comparison through API, CLI, replay, or all three.
```
