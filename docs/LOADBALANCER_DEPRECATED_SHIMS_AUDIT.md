# LoadBalancer Deprecated Shims Audit

## Purpose

Document deprecated compatibility shims before any removal work. This note records the current callers, intended replacement APIs, and removal risks so future cleanup can be planned without surprising tests, CLI/GUI behavior, or public Java API consumers.

Current baseline: `loadbalancerpro-clean` at `3ed5fa03066c63dc33a4abc17daa21a22213f043` or later.

## Current Decision

No deprecated shim should be removed immediately. Local production caller cleanup appears complete for the deprecated `LoadBalancer` shims, and no production caller was found for `ServerMonitor.isAlive()`. Current tests intentionally call the shims as compatibility characterization. The remaining decision is public compatibility and removal policy, not urgent code cleanup.

| Method | Replacement or intended API | Current behavior | Known callers | Removal risk | Recommendation |
| --- | --- | --- | --- | --- | --- |
| `LoadBalancer.getCloudManager()` | `getCloudManagerOptional()` or `hasCloudManager()` | Returns the nullable `CloudManager` field. | No direct production callers found. `LoadBalancerTest` intentionally covers the nullable legacy accessor before cloud initialization. | Medium: the method is public Java API and nullable accessor removal can break external consumers. | Keep for compatibility until a public deprecation/removal policy is chosen. |
| `LoadBalancer.updateMetricsFromCloud()` | `updateCloudMetricsIfAvailable()` | Compatibility shim that delegates to `updateCloudMetricsIfAvailable()`. The retry, delay, logging, interrupt handling, and `IOException` behavior now live behind the non-deprecated wrapper. | No direct production callers found; `ServerMonitor` calls `updateCloudMetricsIfAvailable()`. `LoadBalancerCloudMetricsTest` and `LoadBalancerTest` intentionally cover shim compatibility. | Medium: public API compatibility still matters even though implementation no longer lives in the deprecated method. | Keep for compatibility; no urgent code cleanup remains. |
| `LoadBalancer.handleFailover()` | `checkServerHealth()` | Delegates to `checkServerHealth()`. | No direct production callers found. `LoadBalancerTest` intentionally covers shim redistribution/callability compatibility. | Low-to-medium: no production callers were found, but the method is public Java API. | Keep for compatibility until a public deprecation/removal policy is chosen. |
| `LoadBalancer.balanceLoad()` | `rebalanceExistingLoad()` or explicit strategy methods. | Delegates to `rebalanceExistingLoad()`. | No direct production callers found; CLI and GUI use non-shim distribution/rebalance paths. `LoadBalancerTest` intentionally covers shim equivalence. | Low-to-medium: no production callers were found, but it is public Java API and historically tied to legacy rebalance behavior. | Do not remove now; document a public compatibility and removal timeline. |
| `LoadBalancer.getServerMonitor()` | Public behavior checks or a narrower monitor-status API. | Returns the internal `ServerMonitor` instance. | No direct production callers found. `LoadBalancerTest` intentionally covers legacy accessor callability without expanding monitor internals. | Medium/high: the method exposes internals and external callers may depend on it. | Keep for compatibility until a public deprecation/removal policy is chosen. |
| `ServerMonitor.isAlive()` | `isRunning()` | Deprecated compatibility alias for callers that treated `ServerMonitor` like a thread. | No production caller found. `Thread.isAlive()` usage in monitor lifecycle code/tests is separate. `ServerMonitorTest` intentionally covers alias compatibility with `isRunning()` and status state. | Low-to-medium: local compatibility is characterized, but external callers may still rely on the alias. | Keep for compatibility until a public deprecation/removal policy is chosen. |

## Current Compatibility Evidence

- Local production caller cleanup appears complete for the deprecated `LoadBalancer` shims.
- No production caller was found for `ServerMonitor.isAlive()`; `Thread.isAlive()` checks are separate thread lifecycle checks.
- Tests intentionally call deprecated shims as compatibility characterization.
- Moved the `updateMetricsFromCloud()` implementation behind `updateCloudMetricsIfAvailable()`.
- Kept all deprecated shims in place.

## Remaining Pre-Removal Work

- Treat shim removal as a public compatibility decision, not routine cleanup.
- Keep current compatibility tests unless a replacement policy explicitly changes the public contract.
- Decide the public compatibility policy and removal timeline for `balanceLoad()`.
- Decide whether deprecated shims should remain through the next major compatibility release.
- Add release-note guidance before removing any public shim.
- Use a separate source PR for any future shim removal or behavior change.

## Safety Notes

- Audit note only.
- No behavior changes.
- No deprecated methods removed.
- Namespace migration is complete as of `v2.4.0`; this note now uses post-migration source paths.
- `public/main` untouched.
