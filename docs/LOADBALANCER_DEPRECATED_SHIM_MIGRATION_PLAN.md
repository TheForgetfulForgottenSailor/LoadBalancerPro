# LoadBalancer Deprecated Shim Migration Plan

## Purpose

Plan caller migration for deprecated `LoadBalancer` compatibility shims before any removal or behavior change.

Current decision: do not remove deprecated shims in patch or minor maintenance without an explicit compatibility notice. Local production caller cleanup appears complete for the listed `LoadBalancer` shims, compatibility tests intentionally remain, `updateMetricsFromCloud()` delegates to the non-deprecated wrapper, and `balanceLoad()` is intentionally left untouched as public legacy compatibility API. The v2.4.0 namespace migration is complete and should not be combined with shim removals.

## Current Caller Table

| Shim | Definition | Production callers | Test callers | Docs/README callers | Current replacement | Migration readiness |
| --- | --- | --- | --- | --- | --- | --- |
| `LoadBalancer.getCloudManager()` | `src/main/java/com/richmond423/loadbalancerpro/core/LoadBalancer.java` | None found. | `LoadBalancerTest` intentionally covers nullable legacy accessor compatibility before cloud initialization. | Audit and migration notes only. | `getCloudManagerOptional()` or `hasCloudManager()`. | Production caller cleanup appears complete; removal still requires public API compatibility policy. |
| `LoadBalancer.updateMetricsFromCloud()` | `src/main/java/com/richmond423/loadbalancerpro/core/LoadBalancer.java` | None found for the deprecated method; `ServerMonitor` calls `updateCloudMetricsIfAvailable()`. | `LoadBalancerCloudMetricsTest` and `LoadBalancerTest` intentionally cover shim delegation/no-op compatibility. | Audit and migration notes only. | `updateCloudMetricsIfAvailable()`. | Implementation move complete; deprecated method remains as compatibility delegate. |
| `LoadBalancer.handleFailover()` | `src/main/java/com/richmond423/loadbalancerpro/core/LoadBalancer.java` | None found. | `LoadBalancerTest` intentionally covers shim redistribution/callability compatibility. | Audit and migration notes only. | `checkServerHealth()` or explicit health-coordinator behavior where appropriate. | Production caller cleanup appears complete; removal still requires public API compatibility policy. |
| `LoadBalancer.balanceLoad()` | `src/main/java/com/richmond423/loadbalancerpro/core/LoadBalancer.java` | No direct `LoadBalancer.balanceLoad()` callers found. `LoadBalancerCLI` has a private same-name helper, and GUI rebalances through `rebalanceExistingLoad()`. | `LoadBalancerTest` intentionally covers shim equivalence with `rebalanceExistingLoad()`. | Audit note only. | `rebalanceExistingLoad()` or explicit distribution strategies. | Do not remove now; public compatibility and legacy rebalance semantics need a removal policy. |
| `LoadBalancer.getServerMonitor()` | `src/main/java/com/richmond423/loadbalancerpro/core/LoadBalancer.java` | None found. | `LoadBalancerTest` intentionally covers legacy accessor callability without expanding monitor internals. | Audit and migration notes only. | Public lifecycle/status checks where possible, or a narrow monitor-status API if needed. | Production caller cleanup appears complete; removal still requires public API compatibility policy. |
| `ServerMonitor.isAlive()` | `src/main/java/com/richmond423/loadbalancerpro/core/ServerMonitor.java` | None found; `Thread.isAlive()` checks are separate thread lifecycle checks. | `ServerMonitorTest` intentionally covers alias compatibility with `isRunning()` and status state. | Audit and migration notes only. | `isRunning()`. | Compatibility characterized; removal still requires public API compatibility policy. |

## Replacement API Table

| Deprecated shim | Preferred API | Notes |
| --- | --- | --- |
| `getCloudManager()` | `hasCloudManager()` for boolean checks; `getCloudManagerOptional()` when the configured manager must be inspected. | Prefer avoiding direct CloudManager inspection in behavior tests unless the test is specifically about cloud configuration or shim compatibility. |
| `updateMetricsFromCloud()` | `updateCloudMetricsIfAvailable()`. | Implementation now lives in the non-deprecated wrapper; the deprecated method remains as a delegating shim. |
| `handleFailover()` | `checkServerHealth()` or explicit health-coordinator behavior where appropriate. | The shim is a direct delegate; keep compatibility tests until a removal policy is accepted. |
| `balanceLoad()` | `rebalanceExistingLoad()` or explicit distribution strategies. | No production callers were found, but public API compatibility remains the blocker. |
| `getServerMonitor()` | Public shutdown/lifecycle/status behavior checks, or a narrow status API. | Avoid exposing the full internal monitor solely for new code. |
| `ServerMonitor.isAlive()` | `isRunning()`. | Keep alias compatibility evidence distinct from `Thread.isAlive()` lifecycle checks. |

## Phased Migration Sequence

### Phase A: Migrate Low-Risk Production/Internal Callers

Status: production caller cleanup appears complete for the listed shims.

1. No direct production callers were found for the deprecated `LoadBalancer` shims.
2. No production caller was found for `ServerMonitor.isAlive()`; `Thread.isAlive()` usage is separate.
3. CLI/GUI paths use explicit distribution/rebalance paths rather than `LoadBalancer.balanceLoad()`.
4. Focused core tests and the full Maven suite pass with compatibility tests in place.

### Phase B: Move Cloud Metrics Implementation

Status: complete.

1. The body of `updateMetricsFromCloud()` now lives in `updateCloudMetricsIfAvailable()`.
2. `updateMetricsFromCloud()` remains as a deprecated delegate to `updateCloudMetricsIfAvailable()`.
3. Retry behavior, delay, log text, `IOException` messages, interrupt handling, and cloud guardrails were preserved.
4. Existing `ServerMonitor` cloud metrics tests exercise the non-deprecated wrapper path, while `LoadBalancerCloudMetricsTest` intentionally keeps deprecated shim delegation coverage.

### Phase C: Maintain Compatibility Evidence Before Any Removal

Status: active compatibility characterization remains intentional.

1. Replacement paths are covered where they are the intended modern API.
2. Deprecated shim tests remain to document public compatibility behavior.
3. Do not treat compatibility tests as accidental callers.
4. `balanceLoad()` remains untouched because it is public legacy compatibility API.
5. Any future source cleanup should be a separate PR after the removal policy is accepted.

### Phase D: Decide Removal Timeline

1. Keep all deprecated shims until a public compatibility/removal policy is approved.
2. Treat removal as a compatibility-affecting change because these methods are public Java API.
3. Prefer keeping the shims through the next minor maintenance line and remove only in a major release or after an explicit compatibility notice.
4. Do not combine shim removals with dependency upgrades, release evidence work, or namespace migration follow-ups.

## Removal Risk Classification

| Shim | Removal risk | Reason |
| --- | --- | --- |
| `handleFailover()` | Low to medium | Production caller cleanup appears complete, but public Java API compatibility still matters. |
| `getCloudManager()` | Medium | Production caller cleanup appears complete, but nullable public API removal can break external callers. |
| `updateMetricsFromCloud()` | Medium | Implementation moved behind the wrapper, but public API compatibility still matters. |
| `balanceLoad()` | Low to medium | No production callers were found, but it is public legacy compatibility API. |
| `getServerMonitor()` | Medium to high | Production caller cleanup appears complete, but the method exposes lifecycle internals and external callers may depend on it. |
| `ServerMonitor.isAlive()` | Low to medium | No production caller was found, but the alias is public and external callers may depend on it. |

## Recommendation

Local production caller cleanup appears complete for the listed shims, and compatibility tests intentionally remain. Leave all deprecated shims in place. The next action is maintaining compatibility evidence and deciding whether/when to remove public deprecated shims later. Any removal should happen only with explicit compatibility notice, preferably in a major compatibility release, and in a separate source PR.
