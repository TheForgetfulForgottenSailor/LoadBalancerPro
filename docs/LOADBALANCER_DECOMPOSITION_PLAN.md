# LoadBalancer Decomposition Plan

## Baseline

- Version: v2.4.2
- Baseline branch: loadbalancerpro-clean
- Baseline HEAD: 86db110
- Purpose: document completed LoadBalancer.java decomposition work and conservative future follow-ups after PR #22

## Current Responsibility Map

`LoadBalancer.java` is now mostly a public facade/orchestrator around:

- public constructors and public API entry points
- server registry coordination through `ServerRegistry`
- consistent hash ring delegation through `ConsistentHashRing`
- load distribution delegation through `LoadDistributionEngine`
- health/failover delegation through `ServerHealthCoordinator`
- legacy batch rebalance strategy selection state
- cloud initialization, cloud metric retries, and cloud scale request boundaries
- monitor/executor lifecycle orchestration
- import/export orchestration
- deprecated public compatibility shims
- LASE shadow observation boundaries for capacity/predictive allocation

## Critical Boundary

Legacy batch routing in `LoadBalancer.java` must remain separate from request-level `RoutingStrategy`, `RoutingStrategyRegistry`, `RoutingComparisonEngine`, and API routing unless a future dedicated design explicitly unifies them.

## Completed Decomposition

| PR | Completed scope | Notes |
| --- | --- | --- |
| PR #18 | LoadBalancer characterization tests | Established behavior coverage before extracting private responsibilities. |
| PR #19 | `ConsistentHashRing` extraction | Package-private helper; `LoadBalancer` still owns the public `consistentHashing` entry point. |
| PR #20 | `ServerRegistry` extraction | Package-private helper; public snapshots and lookups still flow through `LoadBalancer`. |
| PR #21 | `LoadDistributionEngine` extraction | Package-private helper; legacy batch distribution APIs remain on `LoadBalancer`. |
| PR #22 | `ServerHealthCoordinator` extraction | Package-private helper; `LoadBalancer` keeps the public health/failover facade. |

## Future Candidates

| Candidate | Current code involved | Risk | Timing |
| --- | --- | --- | --- |
| CloudMetricsCoordinator / cloud-facing compatibility cleanup | `initializeCloud`, `cloudManager`, retry constants, `updateCloudMetricsIfAvailable`, `scaleCloudServers`, cloud shutdown boundary | High | Only after additional characterization; no live cloud behavior changes |
| LaseShadowAdvisorBridge | LASE constructors, test hooks, `observeLaseShadow` | Medium | After behavior tests confirm fail-safe shadow behavior |
| LoadBalancerLifecycleCoordinator | `monitor`, `executor`, `importServerLogs`, `exportReport`, `shutdown` | Medium/High | Later; keep lifecycle semantics unchanged |
| Deprecated shim policy | `getCloudManager`, `updateMetricsFromCloud`, `handleFailover`, `balanceLoad`, `getServerMonitor` | Low docs / High removal | Do not remove yet |
| Characterization-only follow-up | Cloud metrics retries, lifecycle, import/export, LASE bridge, deprecated shims | Low | Preferred before any production refactor |

## Audit Notes

- `ConsistentHashRing`, `ServerRegistry`, `LoadDistributionEngine`, and `ServerHealthCoordinator` have already been extracted.
- The extracted helpers are package-private and focused; do not re-list them as future extraction candidates unless a new, separate responsibility is identified.
- `loadQueue` is maintained on add/remove but appears unread locally.
- This is an audit note only, not a removal recommendation.
- Deprecated public shims are intentionally preserved.
- `balanceLoad()` remains a compatibility shim.
- `updateMetricsFromCloud()` remains a deprecated shim behind `updateCloudMetricsIfAvailable()`.
- This document does not claim any behavior change beyond already-merged PRs.

## Additional Characterization Needed Before Future Extraction

- cloud metrics retry behavior without live AWS calls
- monitor shutdown idempotency
- import/export behavior
- LASE shadow observation and fail-safe behavior without live behavior changes
- deprecated shim compatibility behavior
- public API snapshot and lookup behavior if registry coordination changes
- additional tests before any production refactor

## Recommended Sequence

1. Treat PRs #18 through #22 as the completed low-risk decomposition baseline.
2. Keep this plan current before selecting the next refactor.
3. Prefer docs-only or test-only work when stale claims or missing characterization are found.
4. Add or refresh characterization tests for the next specific responsibility before changing production code.
5. Extract at most one remaining helper per PR.
6. Do not remove deprecated shims.
7. Do not change cloud mutation behavior.
8. Do not merge legacy batch routing with request-level strategy routing without a separate design.

## Do Not Do Yet

- Do not remove deprecated shims.
- Do not change `balanceLoad()` behavior.
- Do not alter least-loaded/equal-allocation legacy behavior.
- Do not change cloud mutation or dry-run guardrails.
- Do not change monitor lifecycle semantics.
- Do not add dependencies.
- Do not merge legacy batch routing into `RoutingStrategyRegistry`/API.
