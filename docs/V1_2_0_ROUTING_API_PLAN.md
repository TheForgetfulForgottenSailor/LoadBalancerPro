# v1.2.0 Routing API Plan

## Current State

- Baseline branch: `loadbalancerpro-clean`
- Planning branch: `planning/v1.2.0-routing-api`
- Baseline commit: `e07e80841b9db05fc2b34b053a3e0089b9e8d713`
- Public `main` remains preserved and is not part of this plan.
- `loadbalancerpro-clean` already includes the core-only routing comparison foundation from the first v1.2.0 slice.

The current routing comparison foundation adds:

- `core.RoutingStrategy`
- `core.RoutingStrategyId`
- `core.RoutingStrategyRegistry`
- `core.RoutingComparisonEngine`
- `core.RoutingComparisonReport`
- `core.RoutingComparisonResult`
- `core.TailLatencyPowerOfTwoStrategy` implementing `RoutingStrategy`

The foundation is internal-only today. It is not exposed through the API or CLI, and it does not change existing `LoadBalancer` allocation behavior.

## Existing Public API Shape

`AllocatorController` currently exposes:

- `GET /api/health`
- `GET /api/lase/shadow`
- `POST /api/allocate/capacity-aware`
- `POST /api/allocate/predictive`

`AllocatorService` builds a temporary `LoadBalancer` for allocation requests and delegates to the existing allocation methods. The routing comparison endpoint should not reuse that mutation-oriented allocation path.

## Proposed Endpoint

Add:

`POST /api/routing/compare`

This endpoint should be recommendation-only and read-only. It should accept caller-provided candidate telemetry, run selected routing strategies against that immutable candidate set, and return comparison results.

The endpoint must not:

- call `CloudManager`
- make AWS calls
- mutate cloud resources
- mutate `LoadBalancer` state
- append LASE allocation events
- alter existing allocation endpoint behavior
- change CLI behavior

## Recommended Class Shape

Keep the implementation narrow and separate from allocation:

- Add `api.RoutingController` with `@RequestMapping("/api/routing")`.
- Add `api.RoutingComparisonService` to map API DTOs to core `ServerStateVector` objects and call `RoutingComparisonEngine`.
- Add request and response DTO records under `api`.
- Update OAuth2 authorization in `api.config.ApiSecurityConfiguration`.
- Do not change `AllocatorController` unless the project prefers one controller for all `/api` routes.
- Do not change `AllocatorService` unless a shared helper is genuinely needed.

This keeps allocation behavior stable and makes the new product surface easy to test.

## Request Model Proposal

```json
{
  "strategies": ["TAIL_LATENCY_POWER_OF_TWO"],
  "servers": [
    {
      "serverId": "server-a",
      "healthy": true,
      "inFlightRequestCount": 4,
      "configuredCapacity": 100.0,
      "estimatedConcurrencyLimit": 40.0,
      "averageLatencyMillis": 18.4,
      "p95LatencyMillis": 42.0,
      "p99LatencyMillis": 80.0,
      "recentErrorRate": 0.01,
      "queueDepth": 2,
      "networkAwareness": {
        "timeoutRate": 0.0,
        "retryRate": 0.0,
        "connectionFailureRate": 0.0,
        "latencyJitterMillis": 4.0,
        "recentErrorBurst": false,
        "requestTimeoutCount": 0,
        "sampleSize": 120
      }
    }
  ]
}
```

Recommended DTOs:

- `RoutingComparisonRequest`
  - `List<String> strategies`
  - `List<RoutingServerStateInput> servers`
- `RoutingServerStateInput`
  - `String serverId`
  - `Boolean healthy`
  - `Integer inFlightRequestCount`
  - `Double configuredCapacity`
  - `Double estimatedConcurrencyLimit`
  - `Double averageLatencyMillis`
  - `Double p95LatencyMillis`
  - `Double p99LatencyMillis`
  - `Double recentErrorRate`
  - `Integer queueDepth`
  - `NetworkAwarenessInput networkAwareness`
- `NetworkAwarenessInput`
  - `Double timeoutRate`
  - `Double retryRate`
  - `Double connectionFailureRate`
  - `Double latencyJitterMillis`
  - `Boolean recentErrorBurst`
  - `Integer requestTimeoutCount`
  - `Integer sampleSize`

Use boxed types for request DTO fields so missing values can be validated intentionally instead of silently defaulting to Java primitive values.

## Response Model Proposal

```json
{
  "requestedStrategies": ["TAIL_LATENCY_POWER_OF_TWO"],
  "candidateCount": 2,
  "timestamp": "2026-05-03T00:00:00Z",
  "results": [
    {
      "strategyId": "TAIL_LATENCY_POWER_OF_TWO",
      "status": "SUCCESS",
      "chosenServerId": "server-b",
      "reason": "Selected lower tail-latency candidate",
      "candidateServersConsidered": ["server-a", "server-b"],
      "scores": {
        "server-a": 0.82,
        "server-b": 0.31
      }
    }
  ]
}
```

Recommended response DTOs:

- `RoutingComparisonResponse`
  - `List<String> requestedStrategies`
  - `int candidateCount`
  - `Instant timestamp`
  - `List<RoutingComparisonResultResponse> results`
- `RoutingComparisonResultResponse`
  - `String strategyId`
  - `String status`
  - `String chosenServerId`
  - `String reason`
  - `List<String> candidateServersConsidered`
  - `Map<String, Double> scores`

Do not expose Java `Optional` directly in JSON. Flatten the core model into explicit nullable response fields.

## DTO to Core Mapping

`RoutingComparisonService` should map each `RoutingServerStateInput` into `ServerStateVector`.

Mapping rules:

- `serverId` maps directly to `ServerStateVector.serverId`.
- `healthy` maps directly and must be explicitly provided.
- `inFlightRequestCount` maps directly and must be non-negative.
- `configuredCapacity` maps to `OptionalDouble.empty()` when omitted.
- `estimatedConcurrencyLimit` maps to `OptionalDouble.empty()` when omitted.
- `queueDepth` maps to `OptionalInt.empty()` when omitted.
- Latency fields map directly and must be finite and non-negative.
- `recentErrorRate` maps directly and must be between `0.0` and `1.0`.
- Missing `networkAwareness` maps to `NetworkAwarenessSignal.neutral(serverId, requestTimestamp)`.
- Present `networkAwareness` maps to `NetworkAwarenessSignal` and uses the same validation ranges as the core record.

The service should use one request timestamp for the whole comparison, so all generated neutral network signals and the report timestamp are consistent.

## Supported Strategy IDs

First release support:

- `TAIL_LATENCY_POWER_OF_TWO`

Strategy parsing should use `RoutingStrategyId` and `RoutingStrategyRegistry`.

Unknown strategy IDs should return `400` with a structured API error. The API should reject unknown IDs before calling the core engine because unknown text cannot be represented as a `RoutingStrategyId`.

If `strategies` is missing or empty, default to the registry's deterministic registered IDs. Today that means `TAIL_LATENCY_POWER_OF_TWO`.

## Validation Rules

Recommended request validation:

- `servers` is required and must contain at least one candidate.
- `serverId` is required, trimmed, non-blank, and unique within the request.
- `healthy` is required.
- `inFlightRequestCount` is required and must be `>= 0`.
- `averageLatencyMillis`, `p95LatencyMillis`, and `p99LatencyMillis` are required, finite, and `>= 0`.
- Prefer rejecting latency ordering that is clearly inconsistent: `averageLatencyMillis <= p95LatencyMillis <= p99LatencyMillis`.
- `recentErrorRate` is required and must be `0.0` through `1.0`.
- `configuredCapacity`, if present, must be finite and `>= 0`.
- `estimatedConcurrencyLimit`, if present, must be finite and `> 0`.
- `queueDepth`, if present, must be `>= 0`.
- `networkAwareness` rates, if present, must be `0.0` through `1.0`.
- `networkAwareness.latencyJitterMillis`, if present, must be finite and `>= 0`.
- `networkAwareness.requestTimeoutCount`, if present, must be `>= 0`.
- `networkAwareness.sampleSize`, if present, must be `>= 0`.
- `strategies`, if present, must contain non-blank, unique, supported strategy IDs.

Keep validation errors aligned with the existing structured `ApiErrorResponse` behavior.

## Error Behavior

Expected error behavior:

- Malformed JSON returns structured `400`.
- Bean validation failures return structured `400` with `validation_failed`.
- Unknown or duplicate strategy IDs return structured `400`.
- Duplicate server IDs return structured `400`.
- Unsupported media type returns structured `415`.
- Wrong HTTP method returns structured `405`.
- Oversized request bodies remain protected by `RequestSizeLimitFilter`.

All-unhealthy candidates should not be an API error. The comparison should return a result for the requested strategy with the core strategy's safe no-decision status/reason.

Strategy failure isolation should remain a core responsibility. If one strategy throws in a future multi-strategy release, the response should contain a failed result for that strategy and continue returning results for the others.

## Auth and Security Behavior

Local/demo profile:

- The endpoint should work like other local demo APIs.
- No API key should be required in local profile.

Prod/cloud-sandbox API-key mode:

- `ProdApiKeyFilter` already protects every `POST`, `PUT`, or `PATCH` request under `/api/**`.
- `POST /api/routing/compare` should therefore be protected automatically by the existing API-key filter.
- Add tests to confirm missing, wrong, and correct API key behavior for the new endpoint.

OAuth2 mode:

- Add an explicit matcher before the fallback `/api/**` matcher:
  - `POST /api/routing/**` should require the allocation/operator role.
- This keeps recommendation access aligned with allocation authority and prevents any authenticated viewer token from using the endpoint.
- CORS preflight should continue to be allowed by the existing `OPTIONS /api/**` permit rule.

## Tests Required

Add focused API tests without weakening existing allocation tests:

- Valid local request returns one result for `TAIL_LATENCY_POWER_OF_TWO`.
- Missing `strategies` defaults to `TAIL_LATENCY_POWER_OF_TWO`.
- Result includes `strategyId`, `status`, `reason`, and either a chosen server or safe no-decision output.
- Deterministic response ordering is preserved.
- Empty `servers` returns structured `400`.
- Duplicate server IDs return structured `400`.
- Unknown strategy ID returns structured `400`.
- Invalid metric ranges return structured `400`.
- All-unhealthy candidates return a safe comparison result, not a controller crash.
- Unsupported media type returns structured `415`.
- Wrong method returns structured `405`.
- Prod API-key mode rejects missing or wrong keys and accepts the configured key.
- Cloud-sandbox API-key mode remains fail-closed if the API key is missing.
- OAuth2 mode rejects unauthenticated requests, rejects observer/viewer-only tokens, and accepts the operator/allocation role.
- Existing `POST /api/allocate/**` tests remain unchanged and passing.

Core tests should remain in place:

- `RoutingComparisonEngineTest`
- `TailLatencyPowerOfTwoStrategy` behavior tests
- Registry tests

## Risks

- The endpoint could accidentally become a second allocation path if it reuses `LoadBalancer` or `AllocatorService` too broadly.
- Returning core records directly could expose `Optional` shapes or internal explanations that are awkward to evolve.
- Duplicate server IDs can make score maps ambiguous, so reject duplicates at the API boundary.
- Power-of-two selection has randomized sampling; controller tests should use deterministic fixtures or an injected deterministic strategy/engine.
- OAuth2 fallback `/api/**` currently means authenticated users could access the route unless an explicit operator-role matcher is added.
- Adding broad DTO/resource filtering or version plumbing is out of scope for this slice.

## What Not To Do

- Do not call `CloudManager`.
- Do not touch AWS mutation logic.
- Do not change `LoadBalancer` default allocation behavior.
- Do not alter existing allocation endpoint request/response contracts.
- Do not expose this through CLI yet.
- Do not add multiple new algorithms in this slice.
- Do not tag v1.2.0 until the API slice is implemented, verified locally, pushed, and passed GitHub Actions/Trivy.

## Recommended Implementation Slice

Implement only `POST /api/routing/compare` as a read-only comparison endpoint:

1. Add API request/response DTO records.
2. Add `RoutingComparisonService`.
3. Add `RoutingController`.
4. Add OAuth2 route authorization for `POST /api/routing/**` requiring the allocation/operator role.
5. Add local controller/service tests.
6. Add prod API-key, cloud-sandbox API-key, and OAuth2 authorization tests.
7. Run full Maven verification.
8. Run package and Docker smoke verification.
9. Push the implementation branch for GitHub Actions/Trivy review.
10. Only after CI passes, merge to `loadbalancerpro-clean`, verify again, push, and then decide whether v1.2.0 is ready to tag.

This gives v1.2.0 a real user-visible product capability while keeping it recommendation-only, read-only, and isolated from allocation/cloud mutation behavior.
