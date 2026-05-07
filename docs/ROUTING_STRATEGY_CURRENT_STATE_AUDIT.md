# LoadBalancerPro Routing Strategy Current-State Audit

## Baseline

- Version: v2.4.2
- Branch baseline: loadbalancerpro-clean
- HEAD baseline: b29abf47491231b68fd42ee1c1ef183c0bf4c1d7
- Audit purpose: document existing routing surfaces before adding more algorithms.

## Routing Layers

LoadBalancerPro currently has two routing layers with different responsibilities. Keeping them separate is important because they answer different questions and have different behavior contracts.

### A. Legacy Batch Distribution

Primary files:

- `src/main/java/com/richmond423/loadbalancerpro/core/LoadBalancer.java`
- `src/main/java/com/richmond423/loadbalancerpro/core/LoadDistributionEngine.java`
- `src/main/java/com/richmond423/loadbalancerpro/core/LoadDistributionPlanner.java`
- `src/main/java/com/richmond423/loadbalancerpro/core/ConsistentHashRing.java`

This surface distributes a total amount of load across multiple servers. It is the older load-distribution path used by the CLI, GUI, allocation logic, and existing compatibility behavior.
These legacy batch algorithms are not currently registered in `RoutingStrategyRegistry` and are not exposed through `POST /api/routing/compare`.

Existing legacy batch behavior includes:

- Legacy round robin
- Least loaded / equal-allocation behavior preserved by tests
- Weighted proportional distribution
- Consistent hashing with synthetic `data-N` keys
- Capacity-aware allocation
- Predictive allocation

### B. Request-Level Strategy Comparison

Primary files:

- `src/main/java/com/richmond423/loadbalancerpro/core/RoundRobinRoutingStrategy.java`
- `src/main/java/com/richmond423/loadbalancerpro/core/TailLatencyPowerOfTwoStrategy.java`
- `src/main/java/com/richmond423/loadbalancerpro/core/WeightedLeastLoadStrategy.java`
- `src/main/java/com/richmond423/loadbalancerpro/core/WeightedRoundRobinRoutingStrategy.java`
- `src/main/java/com/richmond423/loadbalancerpro/core/RoutingStrategy.java`
- `src/main/java/com/richmond423/loadbalancerpro/core/RoutingStrategyId.java`
- `src/main/java/com/richmond423/loadbalancerpro/core/RoutingStrategyRegistry.java`
- `src/main/java/com/richmond423/loadbalancerpro/core/RoutingComparisonEngine.java`
- `src/main/java/com/richmond423/loadbalancerpro/api/RoutingController.java`
- `src/main/java/com/richmond423/loadbalancerpro/api/RoutingComparisonService.java`

This surface chooses or recommends one server from telemetry candidates. It is exposed through `POST /api/routing/compare` and is read-only/recommendation-only.

## Strategy Inventory

| Strategy / Capability | Current state | Notes |
| --- | --- | --- |
| Round Robin | Exists in both layers | Legacy batch allocation exists through `LoadBalancer.roundRobin(double)`. Request-level routing exists as `RoutingStrategyId.ROUND_ROBIN` and `RoundRobinRoutingStrategy.choose(...)`. |
| Least Connections | Missing by name | Legacy `leastLoaded(double)` exists, but it preserves the current least-loaded/equal-allocation behavior and is not a true least-connections strategy. |
| Weighted Round Robin | Exists in both layers with different semantics | Legacy proportional `weightedDistribution(double)` exists for batch load distribution. Request-level `WEIGHTED_ROUND_ROBIN` exists as a smooth weighted round-robin strategy in `RoutingStrategyRegistry`; it chooses one healthy candidate and does not mutate legacy batch allocation state. |
| Weighted Least Connections | Adjacent but not strict WLC | Request-level `WEIGHTED_LEAST_LOAD` exists and considers in-flight load, queue depth, latency, tail latency, error rate, capacity, and weight. It should not be described as strict weighted least connections. |
| Power of Two Choices / P2C | Exists as tail-latency P2C | `TAIL_LATENCY_POWER_OF_TWO` exists through `TailLatencyPowerOfTwoStrategy`; it samples two healthy candidates and chooses by score. |
| Consistent Hashing | Partial legacy-only support | Legacy batch consistent hashing exists through synthetic `data-N` keys and `ConsistentHashRing`. It is not exposed as a request-level registry/API strategy and does not accept caller-provided request keys. |
| Sticky/session-aware routing | Missing | No standalone sticky or session-affinity strategy was found. |
| Random routing | Missing as standalone strategy | Random sampling exists inside the P2C strategy, but there is no plain random routing strategy. |
| LASE recommendation / shadow routing | Exists, shadow-only | LASE shadow advice observes and recommends without mutating live routing, allocation, CloudManager, or AWS behavior. |
| Replay-lab comparison | Partial | Offline LASE shadow event replay exists, but full strategy-vs-strategy replay comparison is not implemented. |

## Existing Request-Level Strategies

- `TAIL_LATENCY_POWER_OF_TWO`
- `WEIGHTED_LEAST_LOAD`
- `WEIGHTED_ROUND_ROBIN`
- `ROUND_ROBIN`

## Existing Legacy/Batch Algorithms

- Round robin
- Least loaded / equal-allocation behavior preserved by tests
- Weighted proportional distribution
- Consistent hashing with synthetic `data-N` keys
- Capacity-aware allocation
- Predictive allocation

## Existing Support

- Health-aware filtering
- Capacity-aware allocation
- Network-aware scoring for telemetry/LASE
- LASE shadow recommendations
- Strategy registry/discovery
- API strategy comparison endpoint
- Offline LASE replay lab for saved shadow events

## Missing From Request-Level Strategy Registry/API

- Least connections
- Weighted least connections
- Plain power of two choices
- Consistent hashing with caller-provided keys
- Sticky/session-affinity routing
- Random routing

## Partially Present But Not Unified

- Consistent hashing exists only in legacy batch mode with synthetic keys.
- Weighted routing exists in legacy proportional distribution, weighted least-load request strategy, and request-level smooth weighted round robin. These are separate behaviors and should not be described as interchangeable.
- Replay lab summarizes LASE shadow events but does not run full strategy-vs-strategy replay comparison.
- Round robin exists in both layers, but the legacy batch method splits a total load while the request-level strategy rotates among request candidates.

## Do Not Overclaim

- `README.md` currently says `POST /api/routing/compare` supports `ROUND_ROBIN`, `TAIL_LATENCY_POWER_OF_TWO`, `WEIGHTED_LEAST_LOAD`, and `WEIGHTED_ROUND_ROBIN`. That matches the current request-level registry/API surface.
- LASE should remain described as shadow/research-grade unless a future change explicitly wires it into public allocation flows.
- Legacy batch algorithms should not be described as request-level API strategies unless they are explicitly added to `RoutingStrategyRegistry` and covered by API tests.

## Existing Tests

- `src/test/java/com/richmond423/loadbalancerpro/core/RoutingComparisonEngineTest.java`
- `src/test/java/com/richmond423/loadbalancerpro/core/RoundRobinRoutingStrategyTest.java`
- `src/test/java/com/richmond423/loadbalancerpro/core/WeightedLeastLoadStrategyTest.java`
- `src/test/java/com/richmond423/loadbalancerpro/core/WeightedRoundRobinRoutingStrategyTest.java`
- `src/test/java/com/richmond423/loadbalancerpro/core/ServerTelemetryRoutingTest.java`
- `src/test/java/com/richmond423/loadbalancerpro/api/RoutingControllerTest.java`
- `src/test/java/com/richmond423/loadbalancerpro/core/LoadBalancerTest.java`
- `src/test/java/com/richmond423/loadbalancerpro/core/LaseShadowReplayMetricsTest.java`
- `src/test/java/com/richmond423/loadbalancerpro/cli/LaseReplayCommandTest.java`

## Missing Tests

- Registry/API tests for least connections strategy
- Registry/API tests for weighted least connections strategy
- Registry/API tests for consistent hashing strategy
- Registry/API tests for sticky routing/session affinity
- Replay tests for strategy-vs-strategy comparisons

## Risk Assessment

- Low risk: docs/test polish, README alignment, OpenAPI/example polish, replay documentation
- Medium risk: adding one new request-level `RoutingStrategy` with focused tests
- High risk: unifying legacy batch distribution with request-level strategy routing, changing preserved legacy behavior, adding sticky/consistent hashing semantics without a design doc
- Split terminology between legacy batch distribution and request-level routing can make support claims ambiguous.
- Duplicate implementation risk rises if new algorithms are added independently to both layers.
- Docs/code drift is easy here because older planning docs describe future strategy work that later landed partially.
- `LoadBalancer.java` can become too large again if new strategies are added there instead of using the request-level strategy abstractions.

## Recommended Next Steps

1. Keep this audit as the source of truth before adding routing algorithms.
2. Add tests first if any existing strategy behavior is under-covered.
3. Keep request-level weighted round robin separate from legacy batch `weightedDistribution(double)` in docs and tests.
4. Use `RoundRobinRoutingStrategy` as the template for adding one request-level strategy at a time.
5. Do not refactor `LoadBalancer.java` into the request-level strategy system yet.
6. Defer sticky routing, full consistent hashing registry/API support, and full replay-lab comparison until after separate planning.

## Do Not Do Yet

- Do not start Spring Boot 4.
- Do not start JavaFX 26.
- Do not add Spring AI/LangChain4j/Neo4j/Bedrock/eBPF/OPA.
- Do not merge legacy batch routing and request-level routing without a separate design.
