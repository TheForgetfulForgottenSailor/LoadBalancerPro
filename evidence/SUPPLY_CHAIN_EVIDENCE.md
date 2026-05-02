# LoadBalancerPro Supply Chain Evidence

Date: 2026-05-01  
Branch: `codex/supply-chain-evidence`  
Verification command: `mvn -q test`

## Purpose and Scope

This document records the current supply-chain and dependency evidence posture for LoadBalancerPro as an enterprise-demo SRE/control-plane lab.

It is documentation evidence only. It does not change production code, build behavior, CI behavior, dependencies, Maven plugins, container build behavior, or release gates.

This document is not a certification, not an independent audit, and not a guarantee that dependencies or build inputs are vulnerability-free. It is a factual baseline for future SBOM, dependency-audit, and release-evidence work.

Related evidence:

- `evidence/RESILIENCE_SCORE.md`
- `evidence/RESIDUAL_RISKS.md`
- `evidence/SECURITY_POSTURE.md`
- `evidence/TEST_EVIDENCE.md`
- `evidence/THREAT_MODEL.md`

## Current Dependency Posture

LoadBalancerPro is a Java 17 Maven project with Spring Boot, AWS SDK v2, Spring Security/OAuth2, Micrometer telemetry libraries, JavaFX GUI support, JSON parsing utilities, caching, Reactor, and test dependencies.

The project currently uses Maven dependency management for the largest framework families and pins build plugins directly in `pom.xml`. CI runs dependency resolution, tests, packaging, smoke checks, Docker image build/runtime checks, Trivy image scanning, and GitHub dependency review for pull requests.

This posture provides useful regression and review evidence, but it is not yet a full supply-chain evidence program because no SBOM, dependency-audit report, dependency update cadence, or accepted dependency-risk workflow is committed.

## BOM-Managed Dependencies

The following dependency families are centrally managed in `pom.xml`:

- Spring Boot dependency BOM: `${spring-boot.version}`.
- AWS SDK v2 BOM: `${aws-sdk-v2.version}`.

Spring Boot BOM-managed areas include Spring Web, Actuator, Security, OAuth2 Resource Server, Validation, Micrometer versions provided through the Boot dependency ecosystem, Reactor, test dependencies, and Log4j versions where Boot management applies.

AWS SDK v2 BOM-managed modules currently include:

- `software.amazon.awssdk:autoscaling`
- `software.amazon.awssdk:cloudwatch`
- `software.amazon.awssdk:ec2`

## Direct Dependency Families

Current direct dependency families include:

- Spring Boot API/runtime: web, actuator, security, OAuth2 resource server, validation.
- API documentation: SpringDoc OpenAPI UI.
- Telemetry: Micrometer Prometheus registry and OTLP registry.
- GUI: JavaFX controls.
- Logging: Log4j API and Log4j Core.
- JSON parsing/utilities: `org.json` and Gson, alongside Jackson from Spring Boot Web.
- Caching/concurrency utilities: Caffeine and Reactor Core.
- Cloud integration boundary: AWS SDK v2 Auto Scaling, CloudWatch, and EC2 clients.
- Test support: Spring Boot test starter and Spring Security test.

Some dependencies use explicit versions outside the Spring Boot or AWS BOMs, including SpringDoc, JavaFX, `org.json`, Gson, and Caffeine. This is not automatically unsafe, but it means those versions need explicit review during dependency updates.

## Pinned Maven Plugins

The following Maven build plugins are pinned in `pom.xml`:

- `maven-compiler-plugin` `3.11.0`
- `maven-surefire-plugin` `3.1.2`
- `maven-jar-plugin` `3.3.0`
- `spring-boot-maven-plugin` `${spring-boot.version}`

Pinned plugins reduce implicit build drift, but they do not replace vulnerability review, plugin update review, or build provenance evidence.

## Existing CI Supply-Chain Checks

Current GitHub Actions behavior includes:

- Maven dependency resolution through `mvn -B -DskipTests dependency:tree`.
- Full Maven tests through `mvn -B test`.
- Packaging through `mvn -B package`.
- Packaged JAR smoke checks.
- Docker image build and runtime health smoke checks.
- Trivy image scan for fixed high/critical OS and library vulnerabilities.
- GitHub dependency review on pull requests, failing high-severity dependency findings.

The repository also includes `.trivyignore`, documented as empty-by-default. Allowlist use is expected to be temporary and reviewed, with vulnerability ID, affected package or layer, owner, reason, and expiry or follow-up.

## Known Gaps

Known gaps as of this evidence pass:

- No committed SBOM or CycloneDX evidence exists yet.
- No OWASP dependency-check report exists yet.
- CI dependency tree output is generated but not captured as durable evidence.
- No Dependabot or Renovate configuration was found.
- Docker base images are tag-pinned, not digest-pinned.
- GitHub Actions are tag-pinned, not commit-SHA pinned.
- Some direct dependency versions are explicit and outside the Spring Boot or AWS BOMs.
- `log4j-core` is a direct dependency and should remain review-sensitive.
- There is no formal dependency update cadence or accepted dependency-risk register beyond the residual risk entry in `evidence/RESIDUAL_RISKS.md`.

## Higher-Sensitivity Dependency Areas

These areas deserve extra attention during dependency review:

- AWS SDK v2: cloud clients sit near guarded cloud mutation boundaries, even though default tests use mocked cloud clients and dry-run safety is documented separately.
- Spring Boot and Spring Security/OAuth2: route protection, filter behavior, validation, error handling, and OAuth2 resource-server behavior depend on this stack.
- Micrometer and OTLP/Prometheus telemetry libraries: telemetry configuration can expose operational metadata if deployment guardrails are misconfigured.
- JavaFX: GUI workflows pass operator configuration into cloud-adjacent paths and need compatibility review during upgrades.
- JSON and CSV parsing libraries/utilities: malformed input handling, safe failure, and import/export behavior depend on parser behavior and project validation code.
- Log4j Core: logging dependencies should stay under explicit review because logging surfaces can process untrusted or sensitive operational text.
- Test libraries and build plugins: test and build tool drift can weaken evidence quality even when production code is unchanged.

## Review Cadence

Review this evidence and the dependency posture:

- Before release tags.
- After dependency or Maven plugin updates.
- After Spring Boot, Spring Security, OAuth2, AWS SDK, Micrometer, JavaFX, Log4j, JSON parser, or test-library changes.
- After Docker base image updates.
- After GitHub Actions workflow or action-version changes.
- After adding SBOM, dependency-check, Dependabot, Renovate, digest pinning, or action SHA pinning.

## Future Hardening Options

Practical next steps, in conservative order:

- Add manually invoked CycloneDX SBOM generation first, before making SBOM generation a release gate.
- Define a dependency update cadence and dependency-risk triage process.
- Capture dependency tree output or SBOM artifacts as durable release evidence.
- Document accepted dependency risks with owner, severity, expiry, and follow-up action.
- Consider digest pinning for Docker base images.
- Consider GitHub Actions commit-SHA pinning where maintainability tradeoffs are acceptable.
- Consider Dependabot or Renovate after defining review ownership and cadence.
- Consider OWASP dependency-check after a triage and false-positive handling process exists.

## What This Evidence Does Not Prove

This document does not prove:

- Dependencies are free of vulnerabilities.
- Transitive dependencies are fully reviewed.
- Container base images are immutable or vulnerability-free.
- GitHub Actions are immune to tag movement or action supply-chain compromise.
- The Maven repository, CI runner, Docker registry, or developer workstation is trusted.
- Runtime deployment uses secure TLS, IAM, firewalling, egress policy, secret rotation, or artifact provenance.
- Live AWS behavior has been validated by this documentation pass.

Supply-chain security remains a combination of project controls, CI controls, dependency review, artifact provenance, runtime deployment controls, and operator discipline.
