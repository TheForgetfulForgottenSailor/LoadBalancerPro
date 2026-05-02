# LoadBalancerPro Resilience Scorecard

Date: 2026-05-01  
Branch: `codex/resilience-score`  
Verification command: `mvn -q test`

## Disclaimer

This is an internal evidence-backed engineering scorecard for LoadBalancerPro as an enterprise-demo SRE/control-plane lab. It is not a certification, not a guarantee of production security, and not formal verification.

Scores are based on the current tests, evidence documents, and known residual risks. They should change when implementation, tests, deployment assumptions, or residual risks change.

Reference evidence:

- `evidence/THREAT_MODEL.md`
- `evidence/SAFETY_INVARIANTS.md`
- `evidence/SECURITY_POSTURE.md`
- `evidence/RESIDUAL_RISKS.md`
- `evidence/TEST_EVIDENCE.md`
- `evidence/HARDENING_AUDIT_001.md`
- `evidence/SUPPLY_CHAIN_EVIDENCE.md`

## Scoring Scale

| Score | Meaning |
| --- | --- |
| 0-20 | Weak / mostly unproven |
| 21-40 | Partial coverage |
| 41-60 | Moderate coverage |
| 61-80 | Strong lab coverage with meaningful residual risks |
| 81-90 | Very strong lab coverage with focused residual risks |
| 91-100 | Reserved for independently audited, formally verified, or production-validated evidence |

The project should not score itself above 90 without independent audit, formal verification, or production-validated operating evidence.

## Summary

Scores are intentionally conservative and use a simple unweighted average.

| Category | Score |
| --- | --- |
| Auth/RBAC fail-closed behavior | 84 |
| Telemetry guardrails | 84 |
| Cloud mutation safety | 74 |
| Replay/evaluation isolation | 82 |
| LASE shadow safety | 83 |
| Input/API hardening | 74 |
| Profile isolation | 80 |
| Documentation truthfulness | 86 |
| Residual risk discipline | 86 |
| Supply-chain/dependency posture | 58 |

Overall score: **79/100**.

Calculation: `(84 + 84 + 74 + 82 + 83 + 74 + 80 + 86 + 86 + 58) / 10 = 79.1`, rounded to 79.

This score means the project has strong lab coverage with meaningful residual risks. It does not imply production readiness.

## Category Scores

### Auth/RBAC Fail-Closed Behavior

Score: 84/100.

Why this score: Prod/cloud-sandbox API-key behavior, OAuth2 fail-closed startup, RBAC route checks, Swagger/OpenAPI gating in OAuth2 mode, health access, CORS preflight behavior, and auth-before-size behavior are covered by focused tests and evidence. OAuth2 role mapping handles common JWT claim shapes.

Evidence references:

- `evidence/SECURITY_POSTURE.md`
- `evidence/TEST_EVIDENCE.md`
- `evidence/HARDENING_AUDIT_001.md`
- `evidence/SAFETY_INVARIANTS.md`

Residual risks preventing a higher score: API keys remain a compatibility mode rather than ideal long-term enterprise auth. OAuth issuer/JWK trust, key rotation, token lifecycle, and upstream identity governance remain deployment responsibilities. Local/default remains intentionally convenient.

Suggested next hardening action: Prefer OAuth2 for enterprise demos and add deployment guidance for issuer operations, API-key compatibility limits, and secret rotation.

### Telemetry Guardrails

Score: 84/100.

Why this score: OTLP is disabled by default, prod/cloud-sandbox disable OTLP and Prometheus exposure by default, OTLP requires explicit opt-in, endpoint guardrails reject unsafe endpoint shapes, and startup summaries are sanitized.

Evidence references:

- `evidence/SECURITY_POSTURE.md`
- `evidence/TEST_EVIDENCE.md`
- `evidence/HARDENING_AUDIT_001.md`
- `evidence/SAFETY_INVARIANTS.md`

Residual risks preventing a higher score: OTLP private endpoint validation is heuristic. Collector trust, TLS, IAM, firewalling, egress controls, retention policy, and collector credentials are deployment responsibilities.

Suggested next hardening action: Add a deployment guide for private collector topology, authentication, TLS policy, egress controls, and telemetry retention.

### Cloud Mutation Safety

Score: 74/100.

Why this score: Cloud mutation defaults to dry-run/no-op behavior, placeholder credentials fail closed, live paths are guarded, and CloudManager boundaries are covered with mocked AWS tests. The evidence is strong for lab safety but intentionally avoids real AWS mutation in default CI.

Evidence references:

- `evidence/SECURITY_POSTURE.md`
- `evidence/TEST_EVIDENCE.md`
- `evidence/HARDENING_AUDIT_001.md`
- `evidence/RESIDUAL_RISKS.md`
- `evidence/SAFETY_INVARIANTS.md`

Residual risks preventing a higher score: Real AWS validation is outside default CI. IAM boundaries, account policy, network policy, sandbox teardown, and live cloud behavior are not proven by the Maven suite. Cloud-sandbox fixed `lbp-sandbox-` prefix enforcement remains a future hardening item if operators can override prefixes.

Suggested next hardening action: Run disposable live AWS sandbox validation outside default CI, and strengthen fixed-prefix enforcement if sandbox operators are not fully trusted.

### Replay/Evaluation Isolation

Score: 82/100.

Why this score: Replay/evaluation paths are documented and tested as offline/read-only, do not require AWS credentials, handle malformed input safely, and are positioned as analysis paths rather than execution paths.

Evidence references:

- `evidence/TEST_EVIDENCE.md`
- `evidence/HARDENING_AUDIT_001.md`
- `evidence/THREAT_MODEL.md`
- `evidence/SAFETY_INVARIANTS.md`

Residual risks preventing a higher score: Default evidence does not include live cloud validation. If replay internals change, the no-CloudManager/no-network boundary should stay under explicit regression coverage.

Suggested next hardening action: Add architecture or dependency tests that prevent replay/evaluation packages from depending on CloudManager or AWS SDK clients.

### LASE Shadow Safety

Score: 83/100.

Why this score: LASE shadow mode is documented and tested as advisory, preserves allocation behavior, does not execute cloud mutation, and redacts sensitive-looking failure text in stored observability output.

Evidence references:

- `evidence/SECURITY_POSTURE.md`
- `evidence/TEST_EVIDENCE.md`
- `evidence/HARDENING_AUDIT_001.md`
- `evidence/SAFETY_INVARIANTS.md`

Residual risks preventing a higher score: Redaction is pattern-based rather than full DLP. Future LASE execution or promotion behavior would require a separate guarded design, auth policy, rollback model, and tests.

Suggested next hardening action: Keep LASE execution out of scope unless a future branch adds explicit execution gates, audit logging, rollback behavior, and route authorization.

### Input/API Hardening

Score: 74/100.

Why this score: Core API validation, malformed JSON handling, oversized request behavior, safe error envelopes, CSV/JSON import validation, trailing JSON rejection, non-finite numeric rejection, and CSV formula-injection handling are covered in evidence.

Evidence references:

- `evidence/TEST_EVIDENCE.md`
- `evidence/HARDENING_AUDIT_001.md`
- `evidence/THREAT_MODEL.md`
- `evidence/SAFETY_INVARIANTS.md`
- `evidence/RESIDUAL_RISKS.md`

Residual risks preventing a higher score: Primitive numeric DTO fields can default to `0.0` when omitted. Not every possible framework-generated 404/405/415/proxy-level error surface is claimed to have identical safe-envelope behavior. Very large hostile files and future schema changes remain review areas.

Suggested next hardening action: Add strict required-field DTO validation and broaden contract tests for less-common framework-generated error surfaces.

### Profile Isolation

Score: 80/100.

Why this score: Local/default, prod, and cloud-sandbox behavior are documented and tested separately for auth posture, telemetry defaults, actuator exposure, and cloud-safety defaults.

Evidence references:

- `evidence/SECURITY_POSTURE.md`
- `evidence/TEST_EVIDENCE.md`
- `evidence/SAFETY_INVARIANTS.md`
- `evidence/HARDENING_AUDIT_001.md`

Residual risks preventing a higher score: Local/default remains intentionally convenient and must not be exposed as production. Deployment automation must choose and preserve the correct profile and environment properties.

Suggested next hardening action: Add an operator preflight checklist that captures active profile, auth mode, telemetry export state, actuator exposure, and cloud dry-run/live state before release.

### Documentation Truthfulness

Score: 86/100.

Why this score: The evidence set explicitly separates tested behavior, residual risks, deployment responsibilities, and future hardening. README links to evidence rather than relying on brittle exact test-count claims.

Evidence references:

- `evidence/THREAT_MODEL.md`
- `evidence/SAFETY_INVARIANTS.md`
- `evidence/SECURITY_POSTURE.md`
- `evidence/RESIDUAL_RISKS.md`
- `evidence/HARDENING_AUDIT_001.md`

Residual risks preventing a higher score: Evidence can become stale if future branches change code, tests, profiles, dependencies, or deployment assumptions without updating the evidence docs.

Suggested next hardening action: Add evidence-review checklist items to release and branch-review workflows.

### Residual Risk Discipline

Score: 86/100.

Why this score: Residual risks are tracked in a structured register with severity, likelihood, mitigation, evidence, owner, status, and next action. The register distinguishes accepted risks from mitigated risks and future hardening needs.

Evidence references:

- `evidence/RESIDUAL_RISKS.md`
- `evidence/SAFETY_INVARIANTS.md`
- `evidence/THREAT_MODEL.md`

Residual risks preventing a higher score: The register is maintained manually. It is not tied to an automated risk workflow, release gate, or external audit process.

Suggested next hardening action: Add a release checklist that requires confirming or updating the residual-risk register before release tags.

### Supply-Chain/Dependency Posture

Score: 58/100.

Why this score: CI release gates, dependency review, and current dependency/build posture are documented, and the Maven test suite provides broad behavioral regression coverage after dependency changes. Phase 6A added `evidence/SUPPLY_CHAIN_EVIDENCE.md` as a factual baseline. The score remains conservative because no SBOM/CycloneDX tooling, OWASP dependency-check report, digest pinning, GitHub Actions SHA pinning, automated dependency management, or dependency triage workflow has been added yet.

Evidence references:

- `README.md`
- `evidence/THREAT_MODEL.md`
- `evidence/RESIDUAL_RISKS.md`
- `evidence/SUPPLY_CHAIN_EVIDENCE.md`

Residual risks preventing a higher score: Dependency/supply-chain drift is explicitly tracked as `RR-010`. The supply-chain evidence page is documentation, not a generated SBOM or vulnerability scan. No SBOM/CycloneDX evidence, OWASP dependency-check report, dependency audit cadence, automated dependency update workflow, or accepted dependency-risk process is currently in place.

Suggested next hardening action: Add SBOM/CycloneDX evidence, define dependency audit cadence, document accepted dependency risks, and consider digest/SHA pinning after the triage process exists.

## How to Raise the Score

Practical next actions:

- Add strict API required-field DTO hardening for ambiguous primitive numeric fields.
- Run live AWS sandbox validation outside default CI using disposable resources and documented guardrails.
- Add SBOM/CycloneDX evidence to the evidence set.
- Define dependency audit cadence and accepted dependency-risk documentation.
- Add optional PIT mutation testing for high-value safety and routing logic.
- Add optional property-based tests for routing algorithms and allocation invariants.
- Add optional chaos/SLO validation for replay/lab scenarios before making operational reliability claims.
- Write a stronger deployment guide for TLS, IAM, firewalling, rate limiting, secret rotation, OAuth issuer operations, and telemetry collector security.
- Add operator preflight checks for active profile, auth mode, cloud dry-run/live state, telemetry export, and actuator exposure.

## Score Review Cadence

Review this scorecard:

- After auth, telemetry, cloud mutation, replay/evaluation, LASE, or input/API hardening changes.
- After dependency upgrades or build/release workflow changes.
- After live sandbox validation or any deployment evidence is added.
- Before release tags or portfolio release checkpoints.
