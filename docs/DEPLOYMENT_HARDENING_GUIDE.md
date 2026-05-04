# LoadBalancerPro Deployment Hardening Guide

Date: 2026-05-04

## Purpose And Scope

LoadBalancerPro is an enterprise-demo and lab system. This guide documents safer deployment patterns for reviewers and operators who want to run the application in a production-like environment.

This guide is not production certification. It does not prove that a deployment is secure, compliant, highly available, or ready for unmanaged production traffic.

Deployment operators remain responsible for TLS, IAM, network policy, secrets, monitoring, incident response, logging, retention, backup, rollback, and all infrastructure-specific controls.

## Deployment Posture Summary

The local/default profile is for development, CI smoke tests, and demos. It intentionally favors convenient local behavior such as localhost browser access, public health checks, local Actuator visibility, and no live AWS mutation by default.

The `prod` profile is production-like, but it is not complete production readiness. It narrows Actuator exposure by default, uses explicit CORS configuration, keeps live AWS disabled, and protects allocation/routing mutation-style routes when a hardened auth mode is configured.

The `cloud-sandbox` profile is controlled validation only. It is dry-run by default, constrained around sandbox expectations, and intended for mocked or disposable cloud-sandbox validation.

Live AWS is disabled by default. Do not enable live mutation without a separate sandbox-specific plan, disposable resources, reviewed IAM boundaries, and explicit operator approval.

Allocation and routing APIs are calculation and recommendation boundaries unless future work explicitly connects them to an execution path. The routing comparison API is recommendation-only and does not call AWS. `CloudManager` is the AWS mutation boundary.

## Network And Edge Guidance

Run the application behind a trusted reverse proxy, ingress, API gateway, or managed load balancer when exposing it beyond local development.

Recommended edge posture:

- Terminate TLS at the trusted HTTPS edge.
- Apply HSTS at the trusted HTTPS edge where appropriate.
- Bind the app to a private interface, container network, or service network whenever possible.
- Expose only the trusted edge publicly.
- Configure forwarded headers only when the deployment owns the proxy trust boundary.
- Add external rate limiting and request filtering at the proxy, ingress, or gateway layer.
- Keep management and telemetry endpoints off the public internet.

Do not rely on the Spring Boot app alone to provide a full network security boundary. Firewall rules, ingress policy, service mesh policy, cloud security groups, private routing, and proxy configuration remain deployment responsibilities.

## Auth Guidance

Prefer OAuth2 mode for stronger enterprise demos. OAuth2 mode provides app-native JWT validation and role-based access checks for observer/operator routes when issuer or JWK configuration is supplied.

API-key mode is compatibility and demo friendly. It is not full enterprise identity, user lifecycle management, fine-grained authorization, or secret rotation. If API-key mode is used, treat the key as a deployment secret and rotate it outside the application.

Recommended auth posture:

- Keep `/api/health` public only where intentionally allowed.
- Protect POST, PUT, and PATCH routes with deliberate auth configuration.
- Prefer OAuth2 for shared enterprise demos or multi-user review.
- Keep Swagger/OpenAPI public only for local demos or intentionally private review environments.
- Gate or disable Swagger/OpenAPI outside local/demo use when route and schema exposure matters.
- Do not trust identity headers from public clients unless a trusted reverse proxy strips and injects them.

CORS does not authenticate users. It only controls browser cross-origin behavior. Authorization still belongs in API-key mode, OAuth2 mode, or a trusted upstream identity layer.

## Actuator And Telemetry Exposure

Actuator and telemetry endpoints can reveal service health, runtime shape, route names, metric labels, error rates, latency, JVM characteristics, and operational patterns.

Recommended posture outside local demos:

- Keep `/actuator/health` and `/actuator/info` private or deployment-authenticated.
- Do not expose `/actuator/metrics` publicly.
- Do not expose `/actuator/prometheus` publicly.
- Keep Prometheus scraping on a private network or trusted monitoring plane.
- Keep OTLP collectors private and trusted.
- Do not include telemetry credentials, bearer tokens, query strings, or auth headers in source-controlled configuration.

OTLP export is disabled by default. If enabled, point it only at a trusted private collector and use deployment secret management for any collector authentication.

## CORS Guidance

Configure explicit production origins. Avoid wildcard origins for protected, credentialed, or browser-facing deployments.

Recommended posture:

- Set production CORS origins through deployment configuration.
- Keep allowed origins narrow and reviewed.
- Do not use CORS as authentication.
- Avoid documenting real customer domains in source-controlled examples unless intentionally public.
- Review CORS whenever auth mode, frontend hosting, proxy routing, or deployment domains change.

Browser access should be intentional. Localhost defaults are suitable for local demos, not public production exposure.

## Cloud/AWS Safety Guidance

Live AWS mutation remains off by default. Do not enable live mutation during demos unless the run has a dedicated sandbox plan.

Before any live AWS validation:

- Use disposable sandbox resources only.
- Do not use production AWS accounts.
- Confirm operator intent.
- Confirm account allow-lists.
- Confirm region allow-lists.
- Confirm capacity caps.
- Confirm sandbox resource prefix expectations.
- Confirm ownership checks before deletion.
- Confirm `cloud.liveMode=true` is intentional.
- Confirm `cloud.allowLiveMutation=true` is intentional.
- Keep deletion disabled unless the test specifically requires deletion and ownership controls have been reviewed.

The default Maven suite uses mocked AWS clients. It does not prove IAM least privilege, live account behavior, network boundary safety, or sandbox cleanup correctness.

## Runtime Hardening Checklist

Use this checklist before exposing a production-like deployment:

- Non-root container posture is preserved.
- The app binds to a private interface or service network when possible.
- Public traffic reaches only the trusted proxy, ingress, gateway, or managed load balancer.
- Environment variables are least-privilege and deployment-specific.
- No real secrets are baked into Docker images.
- No real secrets are committed to Git.
- No real secrets are printed in startup scripts or shell history.
- Read-only filesystem mode is considered where feasible.
- Memory and CPU limits are configured in the orchestrator.
- Logs and metrics have access controls and retention policy.
- Secret rotation is handled outside the app.
- OTLP and Prometheus endpoints are reachable only by trusted collectors.
- Cloud live-mode flags remain off unless a reviewed sandbox run is in progress.

## Pre-Exposure Checklist

Before exposing LoadBalancerPro beyond a local demo:

- [ ] Correct Spring profile is selected.
- [ ] Auth mode is confirmed.
- [ ] API key or OAuth2 settings are present where required.
- [ ] API key or OAuth2 secrets are stored outside Git.
- [ ] CORS origins are reviewed.
- [ ] Swagger/OpenAPI exposure is reviewed.
- [ ] Actuator exposure is reviewed.
- [ ] OTLP and Prometheus exposure is reviewed.
- [ ] Cloud live flags are reviewed.
- [ ] AWS credentials are absent unless a reviewed sandbox run requires them.
- [ ] Release artifact bundle, checksum file, and attestation evidence are reviewed.
- [ ] Rollback plan is identified.
- [ ] Logs and metrics retention expectations are known.
- [ ] Incident response owner is identified.

## What This Guide Does Not Provide

This guide does not provide:

- TLS certificates.
- A full IAM model.
- A production SLO.
- WAF configuration.
- Kubernetes manifests.
- Helm charts.
- Terraform.
- Docker Compose.
- Live AWS validation.
- GitHub Release asset publishing.
- Container signing.
- Production certification.

Treat this guide as deployment hardening guidance for an enterprise-demo repository. A real production deployment needs infrastructure-specific design, review, testing, monitoring, and incident response.
