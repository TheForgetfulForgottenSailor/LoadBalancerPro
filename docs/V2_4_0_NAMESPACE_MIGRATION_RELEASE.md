# LoadBalancerPro v2.4.0 Namespace Migration Release

## Purpose

`v2.4.0` is a focused package namespace migration release. No application behavior changes are intended.

## Namespace Changes

- Maven `groupId`: `com.example` -> `com.richmond423`.
- Root Java package: `com.richmond423.loadbalancerpro`.
- Package mapping:
  - `api` -> `com.richmond423.loadbalancerpro.api`
  - `api.config` -> `com.richmond423.loadbalancerpro.api.config`
  - `cli` -> `com.richmond423.loadbalancerpro.cli`
  - `core` -> `com.richmond423.loadbalancerpro.core`
  - `gui` -> `com.richmond423.loadbalancerpro.gui`
  - `util` -> `com.richmond423.loadbalancerpro.util`

## Migration Scope

- Moved 115 production Java files.
- Moved 45 test Java files.
- Updated package declarations and imports.
- Updated the Spring Boot `mainClass` to `com.richmond423.loadbalancerpro.api.LoadBalancerApiApplication`.
- Updated active README package and CLI references.

## Unchanged Areas

- Resources unchanged, including `gui.messages` and `server_types` resource loading.
- Workflows unchanged.
- Dependencies and plugin versions unchanged.
- Deprecated `LoadBalancer` compatibility shims unchanged.
- Routing, allocation, cloud/AWS guardrails, and runtime behavior unchanged by intent.

## Downstream Compatibility

External Java consumers using the old flat package imports (`api`, `cli`, `core`, `gui`, or `util`) must update imports to `com.richmond423.loadbalancerpro.*`.
