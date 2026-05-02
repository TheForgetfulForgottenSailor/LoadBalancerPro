# LoadBalancerPro Residual Risk Register

Date: 2026-05-01  
Branch: `codex/residual-risk-register`  
Verification command: `mvn -q test`

This register tracks known residual risks for LoadBalancerPro as an enterprise-demo SRE/control-plane lab. It is intentionally explicit so the project does not overclaim production readiness, formal verification, or complete security.

Severity and likelihood are qualitative project-review values, not formal GRC scoring. Owners identify the party most responsible for the next risk decision:

- App: application code or tests.
- Deployment: infrastructure, IAM, network, runtime, or secrets management.
- Future Work: planned hardening outside the current implementation.
- Operator: human configuration, release, or validation practice.

## How to Use This Register

- Use the risk IDs in reviews, release notes, issues, and future hardening events.
- Treat `Mitigated` as "current controls reduce the risk," not "the risk is gone."
- Treat `Accepted` as an explicit lab/deployment responsibility, not a production waiver.
- Treat `Needs Future Hardening` as a good candidate for a focused branch, test, or deployment guide.
- Update this file whenever code, tests, profiles, evidence docs, or deployment assumptions change.

Reference evidence:

- `evidence/THREAT_MODEL.md`
- `evidence/SAFETY_INVARIANTS.md`
- `evidence/SECURITY_POSTURE.md`
- `evidence/TEST_EVIDENCE.md`
- `evidence/HARDENING_AUDIT_001.md`

## Register Summary

| Risk ID | Risk name | Severity | Likelihood | Owner | Status |
| --- | --- | --- | --- | --- | --- |
| RR-001 | Primitive DTO numeric defaults | Medium | Medium | Future Work | Needs Future Hardening |
| RR-002 | Real AWS validation outside default CI | High | Medium | Operator | Accepted |
| RR-003 | Pattern-based redaction limits | Medium | Medium | App | Mitigated |
| RR-004 | Heuristic OTLP private endpoint validation | High | Medium | Deployment | Mitigated |
| RR-005 | Production infrastructure controls outside app | High | Medium | Deployment | Accepted |
| RR-006 | Local/default convenience exposure | High | Low | Operator | Accepted |
| RR-007 | Cloud-sandbox fixed prefix assumption | High | Medium | Future Work | Needs Future Hardening |
| RR-008 | External OAuth issuer/JWK trust and availability | High | Medium | Deployment | Accepted |
| RR-009 | Operator misconfiguration | High | Medium | Operator | Accepted |
| RR-010 | Dependency/supply-chain drift | High | Medium | App | Needs Future Hardening |
| RR-011 | Framework-generated error surface review | Medium | Medium | App | Needs Future Hardening |
| RR-012 | Lack of live chaos/SLO validation | Medium | Medium | Future Work | Accepted |
| RR-013 | No formal verification/model checking | Medium | Low | Future Work | Accepted |
| RR-014 | API keys as compatibility auth mode | Medium | Medium | Future Work | Accepted |
| RR-015 | Evidence staleness | Medium | Medium | Operator | Needs Future Hardening |

## Risk Details

### RR-001: Primitive DTO Numeric Defaults

- Description: Some primitive numeric DTO fields can default to `0.0` when omitted from JSON input.
- Severity: Medium.
- Likelihood: Medium.
- Current mitigation: The behavior is documented as residual risk instead of claimed fixed; malformed, negative, non-finite, and dangerous numeric inputs have focused coverage in core paths.
- Evidence/tests: `evidence/TEST_EVIDENCE.md` documents primitive DTO numeric omission as residual. `evidence/SAFETY_INVARIANTS.md` lists nullable validated DTO fields as a future formalization opportunity.
- Owner: Future Work.
- Status: Needs Future Hardening.
- Recommended next action: In a compatibility-aware branch, convert ambiguous primitive fields to nullable validated fields where API behavior should reject missing numeric input.

### RR-002: Real AWS Validation Outside Default CI

- Description: The default Maven suite uses mocked AWS clients and does not prove behavior against a live AWS account.
- Severity: High.
- Likelihood: Medium.
- Current mitigation: Cloud mutation paths are dry-run/no-op by default, guarded by explicit live configuration, mocked tests, placeholder credential rejection, account/region/ownership checks, and sandbox-oriented resource prefixing.
- Evidence/tests: `evidence/HARDENING_AUDIT_001.md` records mocked AWS boundary checks and notes live AWS validation is outside default CI. `evidence/TEST_EVIDENCE.md` records CloudManager guardrail tests.
- Owner: Operator.
- Status: Accepted.
- Recommended next action: Run live validation only in disposable sandbox infrastructure with explicitly documented IAM boundaries, test resources, and teardown steps.

### RR-003: Pattern-Based Redaction Limits

- Description: LASE and telemetry redaction is pattern-based, not full data-loss-prevention. It cannot guarantee removal of every possible secret shape.
- Severity: Medium.
- Likelihood: Medium.
- Current mitigation: Sensitive-looking API-key/token/bearer-shaped values are redacted in covered LASE failure paths, telemetry summaries avoid full secret-bearing URLs, and docs warn against committing or logging credentials.
- Evidence/tests: `evidence/TEST_EVIDENCE.md` records LASE redaction and telemetry summary sanitization tests. `evidence/HARDENING_AUDIT_001.md` records shared LASE redaction hardening.
- Owner: App.
- Status: Mitigated.
- Recommended next action: Keep adding redaction regression cases when new logs, diagnostics, telemetry fields, or evidence generators are introduced.

### RR-004: Heuristic OTLP Private Endpoint Validation

- Description: OTLP endpoint validation detects obvious private/local endpoints but does not prove collector trust, TLS, IAM, network reachability, or egress enforcement.
- Severity: High.
- Likelihood: Medium.
- Current mitigation: OTLP export is disabled by default, requires explicit opt-in, rejects blank/malformed/credential/query/fragment endpoints, and blocks public endpoints by default unless explicitly overridden.
- Evidence/tests: `evidence/TEST_EVIDENCE.md` records OTLP endpoint guardrail tests. `evidence/SAFETY_INVARIANTS.md` documents OTLP validation as heuristic.
- Owner: Deployment.
- Status: Mitigated.
- Recommended next action: Enforce collector access through private networking, IAM or equivalent identity, firewall/egress policy, TLS policy, and deployment secret management.

### RR-005: Production Infrastructure Controls Outside App

- Description: Production TLS, IAM, firewalling, external rate limiting, secret rotation, deployment identity, log retention, and collector access controls are outside the application.
- Severity: High.
- Likelihood: Medium.
- Current mitigation: Evidence docs clearly describe prod/cloud-sandbox profiles as hardened lab baselines, not complete production deployments.
- Evidence/tests: `evidence/SECURITY_POSTURE.md`, `evidence/THREAT_MODEL.md`, and `evidence/SAFETY_INVARIANTS.md` all identify these controls as deployment responsibilities.
- Owner: Deployment.
- Status: Accepted.
- Recommended next action: Add a deployment hardening guide or checklist before any release that is meant to be operated beyond local/portfolio evaluation.

### RR-006: Local/Default Convenience Exposure

- Description: Local/default mode is intentionally convenient and does not require the same auth/telemetry/cloud posture as prod or cloud-sandbox.
- Severity: High.
- Likelihood: Low.
- Current mitigation: Profile-specific tests and documentation distinguish local/default behavior from prod/cloud-sandbox behavior.
- Evidence/tests: `evidence/SECURITY_POSTURE.md` documents local/default convenience. `evidence/SAFETY_INVARIANTS.md` states local/demo convenience must not silently change hardened profile posture.
- Owner: Operator.
- Status: Accepted.
- Recommended next action: Add release/deployment checklist items that verify active profile, auth mode, actuator exposure, telemetry export, and cloud dry-run/live configuration before exposing the app.

### RR-007: Cloud-Sandbox Fixed Prefix Assumption

- Description: Cloud-sandbox safety assumes the documented `lbp-sandbox-` resource namespace, but future or custom operator configuration may weaken that assumption if fixed-prefix enforcement is not strengthened.
- Severity: High.
- Likelihood: Medium.
- Current mitigation: Cloud-sandbox defaults use `lbp-sandbox-`, docs call out the prefix, and GUI/CLI configuration can pass the sandbox prefix into `CloudConfig`.
- Evidence/tests: `evidence/SECURITY_POSTURE.md` records documented sandbox prefix posture. `evidence/SAFETY_INVARIANTS.md` identifies fixed-prefix enforcement as a future hardening opportunity.
- Owner: Future Work.
- Status: Needs Future Hardening.
- Recommended next action: If sandbox operators are not fully trusted, add fixed `lbp-sandbox-` enforcement and tests for incorrect custom prefixes before live sandbox use.

### RR-008: External OAuth Issuer/JWK Trust and Availability

- Description: OAuth2 mode depends on the configured issuer/JWK provider for token validity, key rotation, availability, and correct identity lifecycle.
- Severity: High.
- Likelihood: Medium.
- Current mitigation: OAuth2 mode fails startup without issuer or JWK configuration, validates JWTs through Spring Security resource-server support, and tests common claim mapping and route authorization.
- Evidence/tests: `evidence/TEST_EVIDENCE.md` records OAuth 401/403 and role-shape tests. `evidence/THREAT_MODEL.md` identifies app-to-OAuth issuer/JWK as a trust boundary.
- Owner: Deployment.
- Status: Accepted.
- Recommended next action: Document issuer operational requirements, key-rotation expectations, outage behavior, token lifetime policy, and user/role lifecycle controls.

### RR-009: Operator Misconfiguration

- Description: Incorrect profiles, auth settings, cloud live flags, credentials, resource prefixes, OTLP endpoints, or actuator exposure can undermine the intended safety posture.
- Severity: High.
- Likelihood: Medium.
- Current mitigation: Defaults are conservative for prod/cloud-sandbox, startup guardrails fail closed for key auth/telemetry cases, and docs call out deployment responsibilities.
- Evidence/tests: `evidence/THREAT_MODEL.md` lists misconfigured deployment/operator scenarios. `evidence/SAFETY_INVARIANTS.md` documents profile and deployment responsibility boundaries.
- Owner: Operator.
- Status: Accepted.
- Recommended next action: Add an operator preflight checklist and consider a read-only startup posture summary that can be captured in release evidence.

### RR-010: Dependency/Supply-Chain Drift

- Description: Maven dependencies, transitive dependencies, build plugins, and GitHub Actions can drift or introduce vulnerabilities over time.
- Severity: High.
- Likelihood: Medium.
- Current mitigation: CI release gates and dependency review are documented, and the test suite exercises core behavior after changes.
- Evidence/tests: README documents CI dependency review and release gates. `evidence/THREAT_MODEL.md` lists dependency/SBOM scanning evidence as future hardening.
- Owner: App.
- Status: Needs Future Hardening.
- Recommended next action: Add SBOM/dependency scanning evidence, document accepted dependency risks, and review dependencies before release tags.

### RR-011: Framework-Generated Error Surface Review

- Description: Core error paths have safe-envelope coverage, but every possible framework-generated 404/405/415/proxy-level error variant is not claimed to have identical project JSON-envelope behavior.
- Severity: Medium.
- Likelihood: Medium.
- Current mitigation: Core validation, auth, request-size, malformed JSON, and LASE failure paths use safe envelopes or redaction where covered.
- Evidence/tests: `evidence/TEST_EVIDENCE.md` records safe-envelope coverage for core API paths. `evidence/THREAT_MODEL.md` and `evidence/SAFETY_INVARIANTS.md` both identify broader framework-generated errors as residual review scope.
- Owner: App.
- Status: Needs Future Hardening.
- Recommended next action: Add focused contract tests for less-common 404/405/415 and unsupported-media/method paths before hostile exposure.

### RR-012: Lack of Live Chaos/SLO Validation

- Description: The lab demonstrates safety and deterministic behavior through tests, but it does not prove live SLO behavior, chaos resilience, or production incident response.
- Severity: Medium.
- Likelihood: Medium.
- Current mitigation: Replay/evaluation, LASE shadow behavior, and mocked cloud tests provide controlled evidence without mutating live infrastructure.
- Evidence/tests: `evidence/TEST_EVIDENCE.md` records replay, LASE, and mocked cloud coverage. `evidence/THREAT_MODEL.md` scopes out production incident-response assurance.
- Owner: Future Work.
- Status: Accepted.
- Recommended next action: Define lab SLOs and add controlled chaos/replay scenarios before making operational reliability claims beyond the current evidence.

### RR-013: No Formal Verification or Model Checking

- Description: Safety invariants are documented and regression-tested, but they are not formally verified with TLA+, model checking, or proof tooling.
- Severity: Medium.
- Likelihood: Low.
- Current mitigation: `evidence/SAFETY_INVARIANTS.md` explicitly avoids formal-verification claims and maps invariants to available evidence.
- Evidence/tests: `evidence/SAFETY_INVARIANTS.md` states the current evidence is documentation and tests, not formal verification.
- Owner: Future Work.
- Status: Accepted.
- Recommended next action: Consider model-checking critical state transitions such as cloud mutation gates if the project evolves beyond portfolio/demo scope.

### RR-014: API Keys as Compatibility Auth Mode

- Description: API-key mode is retained for prod/cloud-sandbox compatibility, but API keys are not ideal long-term enterprise identity or authorization.
- Severity: Medium.
- Likelihood: Medium.
- Current mitigation: API-key mode fails closed when configured keys are missing or wrong, and OAuth2 mode exists as a stronger app-native authorization option with RBAC tests.
- Evidence/tests: `evidence/TEST_EVIDENCE.md` records API-key and OAuth2 coverage. `evidence/SECURITY_POSTURE.md` describes OAuth2 as the stronger app-native posture.
- Owner: Future Work.
- Status: Accepted.
- Recommended next action: Prefer OAuth2 for enterprise demos and consider a future deprecation path or tighter deployment guidance for API-key mode.

### RR-015: Evidence Staleness

- Description: Generated evidence can become stale if future hardening events, features, profile changes, or tests do not update the evidence set.
- Severity: Medium.
- Likelihood: Medium.
- Current mitigation: README links the evidence set, and `evidence/SAFETY_INVARIANTS.md` states documentation must not claim stronger guarantees than tests/evidence support.
- Evidence/tests: `evidence/THREAT_MODEL.md`, `evidence/SAFETY_INVARIANTS.md`, and `evidence/HARDENING_AUDIT_001.md` all call out documentation truthfulness.
- Owner: Operator.
- Status: Needs Future Hardening.
- Recommended next action: Add evidence-review checklist items to release and branch review workflows.

## Risk Review Cadence

Review this register:

- After auth changes, including API-key behavior, OAuth2 configuration, JWT claim mapping, RBAC route policy, Swagger/OpenAPI exposure, CORS, or request-size filter ordering.
- After telemetry changes, including OTLP export, Prometheus/actuator exposure, startup summaries, logging, metrics naming, or collector configuration.
- After cloud mutation changes, including dry-run/live gates, CloudManager behavior, credentials, resource prefixes, sandbox assumptions, AWS clients, or GUI/CLI cloud configuration.
- After dependency upgrades, plugin changes, GitHub Actions changes, Docker image changes, or build/runtime base-image changes.
- Before release tags, portfolio release checkpoints, or any deployment beyond local/demo use.

## Operating Guidance

- Treat prod/cloud-sandbox profiles as hardened lab baselines, not complete production deployments.
- Keep real cloud validation in disposable sandbox accounts with explicit guardrails.
- Keep telemetry collectors private and authenticated by deployment infrastructure.
- Keep secrets out of source control, README examples, command history, logs, and evidence files.
- Revisit primitive DTO numeric fields before exposing the API to untrusted callers.
- Keep this register synchronized with `evidence/THREAT_MODEL.md`, `evidence/SAFETY_INVARIANTS.md`, `evidence/SECURITY_POSTURE.md`, `evidence/TEST_EVIDENCE.md`, and `evidence/HARDENING_AUDIT_001.md`.
