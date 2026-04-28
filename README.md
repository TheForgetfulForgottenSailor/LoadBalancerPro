# LoadBalancerPro

LoadBalancerPro is a Java load balancing project with:

- Core allocation strategies and server health models
- A command-line interface
- A Spring Boot REST API for calculation-only allocation requests
- Tests for allocator safety, server behavior, monitoring, and API responses

The API and CLI are safe by default: allocation endpoints and demo commands do not call `CloudManager`, do not call AWS, and do not perform automatic scaling.

## Requirements

- Java 17+
- Maven

## Build And Test

```bash
mvn -q clean test
```

## CLI

Run the interactive CLI:

```bash
mvn -q exec:java "-Dexec.mainClass=cli.LoadBalancerCLI"
```

Run the allocator safety demo:

```bash
mvn -q exec:java "-Dexec.mainClass=cli.LoadBalancerCLI" "-Dexec.args=--allocator-demo"
```

The demo prints capacity-aware allocation results, unallocated load, and a calculation-only scaling recommendation.

## REST API

Run the Spring Boot API:

```bash
mvn -q exec:java "-Dexec.mainClass=api.LoadBalancerApiApplication"
```

Endpoints:

```text
GET  /api/health
POST /api/allocate/capacity-aware
POST /api/allocate/predictive
```

Enterprise endpoints:

```text
GET /swagger-ui.html
GET /actuator/health
GET /actuator/health/readiness
GET /actuator/info
GET /actuator/metrics
```

## CORS and Security

The API includes a safe default browser configuration for local development and demos. CORS is enabled for `/api/**` from:

```text
http://localhost:3000
http://localhost:8080
```

Allowed methods are `GET`, `POST`, and `OPTIONS`. Allowed request headers are `Content-Type` and `Authorization`, with credentials disabled.

Responses include lightweight security headers without adding an authentication framework:

```text
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Cache-Control: no-store
```

The API validates allocation requests before they reach the allocator. Invalid request bodies return HTTP 400 with a structured response:

```json
{
  "error": "validation_failed",
  "message": "Request validation failed",
  "details": [
    "servers: servers must contain at least one server"
  ]
}
```

Example request:

```bash
curl -X POST http://localhost:8080/api/allocate/capacity-aware \
  -H "Content-Type: application/json" \
  -d '{
    "requestedLoad": 75.0,
    "servers": [
      {
        "id": "api-1",
        "cpuUsage": 90.0,
        "memoryUsage": 90.0,
        "diskUsage": 90.0,
        "capacity": 100.0,
        "weight": 1.0,
        "healthy": true
      },
      {
        "id": "worker-1",
        "cpuUsage": 80.0,
        "memoryUsage": 80.0,
        "diskUsage": 80.0,
        "capacity": 100.0,
        "weight": 1.0,
        "healthy": true
      }
    ]
  }'
```

Example response:

```json
{
  "allocations": {
    "api-1": 10.0,
    "worker-1": 20.0
  },
  "unallocatedLoad": 45.0,
  "recommendedAdditionalServers": 1,
  "scalingSimulation": {
    "recommendedAdditionalServers": 1,
    "reason": "Unallocated load exceeds available capacity; simulated scale-up recommended.",
    "simulatedOnly": true
  }
}
```

## Safety Notes

- The REST API creates request-scoped `LoadBalancer` instances.
- Allocation APIs are calculation-only.
- Scaling recommendations are simulation-only and never call `CloudManager` or AWS.
- Cloud operations remain behind existing `CloudManager` safety defaults.
- No API endpoint performs live AWS mutation or automatic scaling.
