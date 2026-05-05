# v2.4.1 Compatibility Maintenance Release

## Purpose

`v2.4.1` is a compatibility-maintenance release after the `v2.4.0` package namespace migration. It keeps the public compatibility shims in place while moving local tests and internal implementation paths toward the non-deprecated APIs.

## Included Maintenance

- Migrated local test callers away from deprecated `LoadBalancer` shims:
  - `handleFailover()` to `checkServerHealth()`
  - `getCloudManager()` to `getCloudManagerOptional()`
  - `getServerMonitor()` to public shutdown behavior
- Moved the cloud metrics implementation behind `updateCloudMetricsIfAvailable()`.
- Kept deprecated `updateMetricsFromCloud()` as a compatibility delegate.
- Left `balanceLoad()` intentionally in place as a public compatibility shim.
- Preserved all deprecated shims; no shim was removed.
- Added README portfolio evidence polish and a compact architecture diagram.
- Updated `CODEOWNERS` namespace paths after the `v2.4.0` migration.
- Updated planning/docs truth after the `v2.4.0` release and shim maintenance work.

## Scope Notes

- No dependency or plugin versions changed.
- No public runtime behavior changes are intended.
- No `public/main` work is included.
- Docker/runtime evidence remains `v2.4.0` post-release evidence, not a new `v2.4.1` runtime feature.
