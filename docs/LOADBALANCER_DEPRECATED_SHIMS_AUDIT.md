# LoadBalancer Deprecated Shims Audit

## Purpose

Document deprecated `LoadBalancer` compatibility shims before any removal work. This note records the current callers, intended replacement APIs, and removal risks so future cleanup can be planned without surprising tests, CLI/GUI behavior, or public Java API consumers.

## Current Decision

No deprecated `LoadBalancer` shim should be removed immediately.

| Method | Replacement or intended API | Current behavior | Known callers | Removal risk | Recommendation |
| --- | --- | --- | --- | --- | --- |
| `getCloudManager()` | `getCloudManagerOptional()` or `hasCloudManager()` | Returns the nullable `CloudManager` field. | `src/test/java/com/richmond423/loadbalancerpro/core/ServerMonitorTest.java` line 387. | Medium: tests break and the method is public Java API. | Keep for compatibility; migrate tests first. |
| `updateMetricsFromCloud()` | `updateCloudMetricsIfAvailable()` | Contains the current cloud metric update implementation, including retry and interrupt handling. `updateCloudMetricsIfAvailable()` delegates to it. | Internal caller in `src/main/java/com/richmond423/loadbalancerpro/core/LoadBalancer.java` line 430. | Medium: public API concern and implementation still lives in the deprecated method. | Keep until the implementation moves behind the non-deprecated wrapper and wrapper behavior tests exist. |
| `handleFailover()` | `checkServerHealth()` | Delegates to `checkServerHealth()`. | `src/test/java/com/richmond423/loadbalancerpro/core/LoadBalancerTest.java` line 766. | Medium: tests break and the method is public Java API. | Migrate test caller to `checkServerHealth()` before removal. |
| `balanceLoad()` | `rebalanceExistingLoad()` or explicit strategy methods. | Delegates to `rebalanceExistingLoad()`. | No direct local callers found. | Low-to-medium: no local callers, but it is public Java API and documented as a legacy GUI entry point. | Do not remove now; document a public compatibility and removal timeline. |
| `getServerMonitor()` | Public behavior checks or a narrower monitor-status API. | Returns the internal `ServerMonitor` instance. | `src/test/java/com/richmond423/loadbalancerpro/core/LoadBalancerTest.java` line 1005. | Medium/high: removal breaks shutdown/lifecycle tests and the method exposes internals. | Keep until tests are redesigned around public behavior or a narrower API exists. |

## Required Pre-Removal Work

- Migrate tests away from `getCloudManager()`.
- Move the `updateMetricsFromCloud()` implementation behind `updateCloudMetricsIfAvailable()`.
- Add or confirm behavior tests for `updateCloudMetricsIfAvailable()`.
- Migrate `handleFailover()` tests to `checkServerHealth()`.
- Decide the public compatibility policy and removal timeline for `balanceLoad()`.
- Redesign the `getServerMonitor()` lifecycle test around public behavior or introduce a narrower monitor-status API.

## Safety Notes

- Audit note only.
- No behavior changes.
- No deprecated methods removed.
- Namespace migration is complete as of `v2.4.0`; this note now uses post-migration source paths.
- `public/main` untouched.
