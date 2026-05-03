# v1.2.0 Routing API Review

## Current State

- Branch: `release/v1.2.0-routing-engine`
- Implementation commit reviewed: `368e465b11b5d818fe0cc1a33cc63d7a535a1eb6`
- Planning doc commit on branch: `1bc7c0d`
- Endpoint added: `POST /api/routing/compare`
- Prior verification reported:
  - `mvn -q test`: 529 tests, 0 failures, 0 errors, 0 skipped
  - `mvn -q -DskipTests package`: passed
  - JAR smoke checks: passed, with invalid LASE scenario failing safely
  - Docker build and `/api/health` smoke: passed

## Summary of What Was Added

The slice adds a read-only API layer over the existing core routing comparison foundation:

- `api.RoutingController`
- `api.RoutingComparisonService`
- `api.RoutingComparisonRequest`
- `api.RoutingServerStateInput`
- `api.NetworkAwarenessInput`
- `api.RoutingComparisonResponse`
- `api.RoutingComparisonResultResponse`
- OAuth2 authorization for `POST /api/routing/**`
- API/security tests for local, prod API-key, cloud-sandbox API-key, and OAuth2 behavior

The endpoint supports `TAIL_LATENCY_POWER_OF_TWO` for the first public API release and defaults to registry-registered strategies when the request omits `strategies`.

## Read-Only / Recommendation-Only Review

The endpoint is read-only and recommendation-only.

The controller delegates to `RoutingComparisonService`, which:

- parses requested strategy IDs
- validates request telemetry
- maps DTOs to immutable core `ServerStateVector` candidates
- calls `RoutingComparisonEngine`
- flattens the core report into API response DTOs

The service does not persist state, mutate server state, change routing defaults, or perform live allocation.

## CloudManager / AWS Review

The implementation does not call `CloudManager` or AWS mutation logic.

The only `CloudManager` reference in the new routing API test surface is a `MockedConstruction<CloudManager>` assertion proving that the routing comparison request does not construct it.

The production implementation files for the routing endpoint reference `RoutingComparisonEngine`, `RoutingStrategyRegistry`, `ServerStateVector`, and `NetworkAwarenessSignal`; they do not reference `CloudManager` or AWS SDK clients.

## LoadBalancer State Review

The endpoint does not mutate `LoadBalancer` state.

`RoutingComparisonService` does not instantiate `LoadBalancer`, does not call `capacityAwareWithResult`, does not call `predictiveLoadBalancingWithResult`, and does not append LASE allocation events. Existing `AllocatorService` remains the only API service path that constructs a temporary `LoadBalancer` for allocation endpoints.

## Existing Allocation Endpoint Review

Existing `/api/allocate/**` behavior appears unchanged.

The implementation adds a new `RoutingController` under `/api/routing` instead of modifying `AllocatorController` or `AllocatorService`. The only shared production change outside the new routing API classes is the OAuth2 matcher in `ApiSecurityConfiguration`.

Existing allocation tests still passed as part of the full 529-test run.

## Request / Response DTO Review

The request DTOs are intentionally separate from the core model and use boxed request fields for required validation:

- `Boolean healthy`
- `Integer inFlightRequestCount`
- `Double averageLatencyMillis`
- `Double p95LatencyMillis`
- `Double p99LatencyMillis`
- `Double recentErrorRate`

That avoids silent Java primitive defaults for missing JSON fields.

The response DTOs flatten core `Optional` values into API-friendly nullable fields, especially `chosenServerId`. This keeps Java `Optional` out of the JSON contract and makes the response easier for clients to consume.

The DTO design is narrow enough for v1.2.0 and leaves room for additional strategies later.

## Validation Review

Validation is split cleanly:

- Bean validation handles required `servers`, non-empty `servers`, non-null server entries, non-blank `serverId`, and required boxed fields.
- `RoutingComparisonService` handles semantic validation that is awkward for annotations:
  - duplicate strategy IDs
  - unknown strategy IDs
  - duplicate server IDs
  - finite/non-negative numeric checks
  - latency ordering: `averageLatencyMillis <= p95LatencyMillis <= p99LatencyMillis`
  - rate bounds from `0.0` to `1.0`
  - optional capacity, concurrency, queue, and network-awareness bounds

Missing or empty `strategies` default to the registry's deterministic strategy list, currently `TAIL_LATENCY_POWER_OF_TWO`.

All-unhealthy candidates are handled as a safe no-decision comparison result instead of becoming a request failure.

## Error Behavior Review

The endpoint uses existing structured error behavior through `RestExceptionHandler` and existing filters:

- validation failures return `400 validation_failed`
- semantic validation failures return `400 bad_request`
- unknown/duplicate strategy IDs return structured `400`
- duplicate server IDs return structured `400`
- unsupported media type returns structured `415`
- wrong method returns structured `405`
- oversized API mutation requests remain covered by `RequestSizeLimitFilter`

The behavior is consistent with the existing allocation endpoints.

## Auth / Security Review

### Local

Local/default mode is allowed through the existing non-OAuth2 security configuration. The new local test confirms `POST /api/routing/compare` succeeds without an API key.

### Prod API-Key

`ProdApiKeyFilter` already protects every `POST`, `PUT`, or `PATCH` request under `/api/**`. Because the new endpoint is `POST /api/routing/compare`, it inherits API-key protection automatically.

New tests confirm:

- missing API key is rejected
- wrong API key is rejected without leaking secrets
- correct API key is accepted

### Cloud-Sandbox API-Key

Cloud-sandbox uses the same API-key filter path. The new test confirms routing compare is rejected without the sandbox key and accepted with the configured sandbox key.

### OAuth2 Operator Role

The implementation adds:

`POST /api/routing/**` requires the allocation/operator role.

This matcher is placed before the fallback `/api/**` authenticated matcher, so viewer or observer tokens cannot use the recommendation endpoint merely by being authenticated.

New OAuth2 tests confirm:

- viewer token is rejected with `403`
- observer token is rejected with `403`
- operator token is accepted

## Test Coverage Review

Coverage is strong for the intended first API slice:

- valid local request
- default strategy behavior
- response shape with strategy/status/reason/chosen server/scores
- empty server list
- duplicate server IDs
- unknown strategy ID
- duplicate strategy IDs after normalization
- invalid metric ordering
- all-unhealthy safe no-decision response
- unsupported media type
- wrong HTTP method
- prod API-key protection
- cloud-sandbox API-key protection
- OAuth2 role protection
- no `CloudManager` construction from the routing compare path

Existing allocation and core routing tests remain in the suite and passed.

## Design Concerns

No blocking design concerns were found.

Non-blocking notes:

- `RoutingComparisonService` currently owns its default registry and clock internally. That is fine for this slice, but a package-private or bean-injection constructor could make future strategy/clock tests easier once more strategies are added.
- Validation coverage is broad enough for v1.2.0, but future tests could add individual negative cases for network-awareness fields, negative queue depth, missing `healthy`, and non-finite numeric values if the API grows.
- The API returns `null` for `chosenServerId` on safe no-decision results. This is acceptable and easier than exposing `Optional`, but release notes should describe it so clients do not treat null as a server ID.

## Recommended Cleanup Before Push

No cleanup is required before pushing for CI/Trivy review.

Optional future cleanup, not a push blocker:

- Add OpenAPI path assertion for `/api/routing/compare` if the team wants explicit API-doc contract coverage.
- Add a small service-level unit test with an injected fixed clock if constructor visibility is widened in a later strategy slice.

## Push Readiness

This branch is safe to push for CI/Trivy review.

Recommended next step:

1. Push `release/v1.2.0-routing-engine` to origin and public for CI review.
2. Confirm GitHub Actions and Trivy pass on the public branch.
3. Only then merge into `loadbalancerpro-clean`, rerun final verification, push `loadbalancerpro-clean`, and consider the v1.2.0 tag.
