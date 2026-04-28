# LoadBalancerPro

## 🚀 What This Does

LoadBalancerPro is a backend service that:

- Distributes load across servers using multiple strategies
- Enforces strict capacity limits (no over-allocation)
- Detects overload conditions in real time
- Reports unallocated load when demand exceeds capacity
- Recommends how many additional servers are needed

It includes:

- CLI demo for quick simulation
- Spring Boot REST API for integration
- Fully tested allocation engine (100+ tests)

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

## Example Output

Request:

```text
Total Load: 120
Servers: 2 x capacity 50
```

Result:

```text
Allocation:
S1: 50
S2: 50

Unallocated Load: 20
Recommended Additional Servers: 1
```

## 💰 Why This Matters

Many load balancers can spread traffic, but unsafe allocation can hide overload by assigning more work than servers can actually handle. LoadBalancerPro keeps the math honest: it caps allocation at capacity, exposes unmet demand, and turns overload into an actionable scaling recommendation without mutating cloud infrastructure.

## Safety Notes

- The REST API creates request-scoped `LoadBalancer` instances.
- Allocation APIs are calculation-only.
- Cloud operations remain behind existing `CloudManager` safety defaults.
- No API endpoint performs live AWS mutation or automatic scaling.
