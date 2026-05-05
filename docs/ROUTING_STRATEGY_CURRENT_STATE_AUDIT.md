# LoadBalancerPro Routing Strategy Current-State Audit

## Baseline

- Version: v2.4.2
- Branch baseline: loadbalancerpro-clean
- HEAD baseline: 06dd909129d7bbe24716a3d251dbd2102fa153b3
- Audit purpose: document existing routing surfaces before adding more algorithms.

## Routing Surfaces

LoadBalancerPro currently has two routing surfaces with different responsibilities. Keeping them separate is important because they answer different questions and have different behavior contracts.

### A. Legacy Batch Distribution

Primary files:

- `src/main/java/com/richmond423/loadbalancerpro/core/LoadBalancer.java`
- `src/main/java/com/richmond423/loadbalancerpro/core/LoadDistributionPlanner.java`

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

- `src/main/java/com/richmond423/loadbalancerpro/core/RoutingStrategy.java`
- `src/main/java/com/richmond423/loadbalancerpro/core/RoutingStrategyId.java`
- `src/main/java/com/richmond423/loadbalancerpro/core/RoutingStrategyRegistry.java`
- `src/main/java/com/richmond423/loadbalancerpro/core/RoutingComparisonEngine.java`
- `src/main/java/com/richmond423/loadbalancerpro/api/RoutingController.java`
- `src/main/java/com/richmond423/loadbalancerpro/api/RoutingComparisonService.java`

This surface chooses or recommends one server from telemetry candidates. It is exposed through `POST /api/routing/compare` and is read-only/recommendation-only.

## Existing Request-Level Strategies

- `TAIL_LATENCY_POWER_OF_TWO`
- `WEIGHTED_LEAST_LOAD`

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

- Plain round robin
- Least connections
- Weighted round robin
- Weighted least connections
- Plain power of two choices
- Consistent hashing with caller-provided keys
- Sticky/session-affinity routing

## Partially Present But Not Unified

- Consistent hashing exists only in legacy batch mode with synthetic keys.
- Weighted routing exists in legacy proportional distribution and weighted least-load request strategy.
- Replay lab summarizes LASE shadow events but does not run full strategy-vs-strategy replay comparison.

## Existing Tests

- `src/test/java/com/richmond423/loadbalancerpro/core/RoutingComparisonEngineTest.java`
- `src/test/java/com/richmond423/loadbalancerpro/core/WeightedLeastLoadStrategyTest.java`
- `src/test/java/com/richmond423/loadbalancerpro/core/ServerTelemetryRoutingTest.java`
- `src/test/java/com/richmond423/loadbalancerpro/api/RoutingControllerTest.java`
- `src/test/java/com/richmond423/loadbalancerpro/core/LoadBalancerTest.java`
- `src/test/java/com/richmond423/loadbalancerpro/core/LaseShadowReplayMetricsTest.java`
- `src/test/java/com/richmond423/loadbalancerpro/cli/LaseReplayCommandTest.java`

## Missing Tests

- Registry/API tests for round robin strategy
- Registry/API tests for least connections strategy
- Registry/API tests for weighted round robin strategy
- Registry/API tests for weighted least connections strategy
- Registry/API tests for consistent hashing strategy
- Registry/API tests for sticky routing/session affinity
- Replay tests for strategy-vs-strategy comparisons

## Risk Assessment

- Low risk: docs/test polish, README alignment, OpenAPI/example polish, replay documentation
- Medium risk: adding one new request-level `RoutingStrategy` with focused tests
- High risk: unifying legacy batch distribution with request-level strategy routing, changing preserved legacy behavior, adding sticky/consistent hashing semantics without a design doc

## Recommended Next Steps

1. Keep this audit as the source of truth before adding routing algorithms.
2. Add one request-level strategy at a time.
3. Prefer plain `RoundRobinRoutingStrategy` as first implementation if implementation begins.
4. Add tests before exposing any new strategy through the registry/API.
5. Do not refactor `LoadBalancer.java` into the request-level strategy system yet.
6. Plan strategy-vs-strategy replay comparison separately.

## Do Not Do Yet

- Do not start Spring Boot 4.
- Do not start JavaFX 26.
- Do not add Spring AI/LangChain4j/Neo4j/Bedrock/eBPF/OPA.
- Do not merge legacy batch routing and request-level routing without a separate design.
