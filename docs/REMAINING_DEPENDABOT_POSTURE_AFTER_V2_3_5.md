# Remaining Dependabot Posture After v2.4.0

## Current Checkpoint

- `v2.4.0` is closed and released.
- Safe GitHub Actions workflow maintenance is complete.
- The `actions/upload-artifact@v7` release artifact upload path is proven.
- Package namespace migration to `com.richmond423.loadbalancerpro.*` is complete.
- Maven `groupId` is now `com.richmond423`.
- `public/main` was untouched.

## Completed Safe PRs

- #12: Caffeine `3.1.8` -> `3.2.4`, released in `v2.3.4`.
- #4: `actions/checkout` `4.3.1` -> `6.0.2`, included in `v2.3.5`.
- #8: `actions/setup-java` `4.8.0` -> `5.2.0`, included in `v2.3.5`.
- #6: `actions/upload-artifact` `4.6.2` -> `7.0.1`, included in `v2.3.5`.

## Remaining Warning

`actions/dependency-review-action` `v4.9.0` still uses Node.js 20. No clean Node.js 24 update exists today. Wait for a Dependabot PR or upstream Node.js 24-capable release before changing it.

## Remaining Open PR Posture

- #2: `eclipse-temurin` `17` -> `25`: defer; Java runtime major jump.
- #3: `maven` Java `17` -> `26`: defer; Java build/runtime major jump and floating image concerns.
- #7: `springdoc` `2` -> `3`: defer; framework/API-doc compatibility risk and prior build failure.
- #9: JavaFX `17` -> `26`: defer; JavaFX major jump and prior build/CodeQL failure.
- #14: Spring Boot `3.5` -> `4.0`: defer; major framework/BOM migration.

## Recommended Next Strategy

- No remaining PR is safe to merge as routine maintenance.
- Treat each remaining PR as its own compatibility project.
- Consider Dependabot grouping/noise-reduction later.
- Keep future deferred dependency upgrades separate from namespace-migration follow-up or deprecated-shim cleanup.

## Safety Notes

- This note is documentation-only and intends no behavior changes.
- No `public/main` work is included.
- No namespace migration implementation is included in this note; the migration itself was completed separately in `v2.4.0`.
