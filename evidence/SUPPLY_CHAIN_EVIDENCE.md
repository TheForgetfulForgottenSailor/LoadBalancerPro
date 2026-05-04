# LoadBalancerPro Supply Chain Evidence

Date: 2026-05-04
Branch: `feature/v1.9.0-release-attestations`
Verification commands: `mvn -q test`, `mvn -q -DskipTests package`, `mvn -B org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom -DoutputFormat=all -DoutputDirectory=target -DoutputName=bom -Dcyclonedx.skipAttach=true`

## Purpose and Scope

This document records the current supply-chain and dependency evidence posture for LoadBalancerPro as an enterprise-demo SRE/control-plane lab.

It is documentation evidence only. It does not change production code, dependencies, Maven plugins, container build behavior, or release gates.

This document is not a certification, not an independent audit, and not a guarantee that dependencies or build inputs are vulnerability-free. It is a factual baseline for future SBOM, dependency-audit, and release-evidence work.

Related evidence:

- `evidence/RESILIENCE_SCORE.md`
- `evidence/RESIDUAL_RISKS.md`
- `evidence/SECURITY_POSTURE.md`
- `evidence/TEST_EVIDENCE.md`
- `evidence/THREAT_MODEL.md`
- `evidence/SBOM_GUIDE.md`

## Current Dependency Posture

LoadBalancerPro is a Java 17 Maven project with Spring Boot, AWS SDK v2, Spring Security/OAuth2, Micrometer telemetry libraries, JavaFX GUI support, JSON parsing utilities, caching, Reactor, and test dependencies.

The project currently uses Maven dependency management for the largest framework families and pins build plugins directly in `pom.xml`. CI runs dependency resolution, tests, packaging, smoke checks, CycloneDX SBOM generation, Docker image build/runtime checks, Trivy image scanning, and GitHub dependency review for pull requests. A separate CodeQL workflow provides a Java/Kotlin static-analysis baseline with a manual Maven build. A separate tag-triggered release artifact workflow builds the executable JAR, generates CycloneDX SBOM JSON/XML, verifies Git tag and Maven version alignment, generates SHA-256 checksums, creates GitHub artifact attestations for release JAR provenance and the JAR/SBOM JSON relationship, and uploads deterministic GitHub Actions artifacts.

This posture provides useful regression and review evidence, but it is not yet a full supply-chain provenance program because there is no PGP-style release artifact signing, container signing, GitHub Release asset publication policy, or mature accepted dependency-risk workflow. CycloneDX SBOM generation is documented in `evidence/SBOM_GUIDE.md` and runs in CI/release workflows without adding a CycloneDX plugin to `pom.xml`. GitHub artifact attestations provide release provenance evidence, not signing, notarization, vulnerability proof, or production-readiness proof. CodeQL is a SAST baseline, not a complete security review or production-readiness proof.

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
- CycloneDX SBOM generation through direct `org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom` invocation.
- Upload of `target/bom.json` and `target/bom.xml` as a GitHub Actions artifact named `loadbalancerpro-sbom`.
- Packaged JAR smoke checks.
- Docker image build and runtime health smoke checks.
- Trivy image scan for fixed high/critical OS and library vulnerabilities.
- GitHub dependency review on pull requests, failing high-severity dependency findings.
- Separate CodeQL Java/Kotlin static analysis through `.github/workflows/codeql.yml`, using manual build mode with `mvn -B -DskipTests package`.

The repository also includes `.trivyignore`, documented as empty-by-default. Allowlist use is expected to be temporary and reviewed, with vulnerability ID, affected package or layer, owner, reason, and expiry or follow-up.

## Release Artifact Provenance Checks

The tag-triggered Release Artifacts workflow is separate from CI and CodeQL. It runs only on semantic version tags matching `v*.*.*` and does not run on normal branch pushes or pull requests. Semantic release tags matching `v*.*.*` require Maven/app metadata alignment before push because the workflow intentionally fails on tag/Maven version mismatches.

Current release artifact workflow behavior includes:

- Extracting the release version from the Git tag.
- Reading the Maven project version with Maven help tooling.
- Failing before artifact upload if the Git tag version and Maven project version differ.
- Building the executable JAR with `mvn -B -DskipTests package`.
- Running lightweight packaged-JAR smoke checks, including `--version`, `--lase-demo=healthy`, and safe invalid-scenario handling.
- Generating CycloneDX SBOM JSON/XML through direct `org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom` invocation.
- Generating and verifying a SHA-256 checksum file for the release JAR and SBOM files.
- Creating GitHub artifact attestations for release JAR build provenance and for the JAR/SBOM JSON relationship.
- Uploading deterministic GitHub Actions artifacts:
  - `LoadBalancerPro-${version}.jar`
  - `LoadBalancerPro-${version}-bom.json`
  - `LoadBalancerPro-${version}-bom.xml`
  - `LoadBalancerPro-${version}-SHA256SUMS.txt`

The uploaded artifact bundle is named `loadbalancerpro-release-${version}` and is retained for 90 days. The checksum file helps verify downloaded artifact integrity against the uploaded checksum file. GitHub artifact attestations help consumers verify where and how the release JAR was built and which SBOM JSON file was associated with it. This is GitHub Actions artifact publication plus GitHub artifact attestation. It is not GitHub Release asset publication, PGP signing, notarization, vulnerability scanning, or a production-readiness claim.

## Current Automation And Pinning

- Dependabot is configured for Maven, GitHub Actions, and Docker on a weekly schedule.
- GitHub Actions are pinned to reviewed commit SHAs, with comments preserving upstream action names and version tags.
- Docker build and runtime base images are pinned by digest in `Dockerfile`.
- The CycloneDX Maven plugin is invoked directly with explicit version `2.9.1` instead of being added to `pom.xml`.
- CodeQL Actions are pinned to reviewed commit SHAs, with comments preserving the upstream action names and version tags.
- Release artifact workflow actions are pinned to reviewed commit SHAs, with comments preserving upstream action names and version tags.
- The release artifact attestation action is pinned to a reviewed commit SHA, with a comment preserving the upstream action name and version tag.

## Known Gaps

Known gaps as of this evidence pass:

- CycloneDX is not integrated into `pom.xml`, by design for the current phase.
- No OWASP dependency-check report exists yet.
- CI dependency tree output is generated but not captured as durable evidence.
- Some direct dependency versions are explicit and outside the Spring Boot or AWS BOMs.
- `log4j-core` is a direct dependency and should remain review-sensitive.
- There is no formal dependency update cadence or accepted dependency-risk register beyond the residual risk entry in `evidence/RESIDUAL_RISKS.md`.
- First CodeQL findings still need baseline review and triage before CodeQL is treated as a mature release blocker.
- No formal static-analysis triage register exists yet.
- No release artifact signing exists beyond GitHub artifact attestations.
- No container signing exists yet.
- No GitHub Release asset publication policy exists yet.
- No registry or release image signing policy exists yet.

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
- After changing CodeQL/SAST workflow behavior, artifact attestations, release signing, or container signing.

## Future Hardening Options

Practical next steps, in conservative order:

- Keep CI SBOM artifact generation stable and review generated artifacts during release preparation.
- Review tag-triggered release artifact bundles and checksum files after release tags and keep tag/Maven version alignment mandatory.
- Review the first CodeQL findings and document triage expectations before making SAST a hard release blocker.
- Define a dependency update cadence and dependency-risk triage process.
- Capture dependency tree output as durable release evidence if future release evidence needs it.
- Document accepted dependency risks with owner, severity, expiry, and follow-up action.
- Review GitHub artifact attestation records after semantic release tags and document verification expectations for consumers.
- Consider attesting checksum or SBOM XML artifacts separately if that provides useful audit evidence.
- Add container signing after registry and image naming decisions are made.
- Consider OWASP dependency-check after a triage and false-positive handling process exists.

## What This Evidence Does Not Prove

This document does not prove:

- Dependencies are free of vulnerabilities.
- Transitive dependencies are fully reviewed.
- Container base images are immutable or vulnerability-free.
- GitHub Actions are immune to tag movement or action supply-chain compromise.
- The Maven repository, CI runner, Docker registry, or developer workstation is trusted.
- Uploaded SBOM artifacts prove artifact provenance or runtime integrity.
- Uploaded release artifact bundles or checksum files are PGP-signed, notarized, or published as GitHub Release assets.
- GitHub artifact attestations prove dependency safety, production readiness, or artifact notarization.
- Checksums prove who built the artifacts or that dependencies are vulnerability-free.
- CodeQL results prove the absence of vulnerabilities or replace human review.
- Runtime deployment uses secure TLS, IAM, firewalling, egress policy, secret rotation, or artifact provenance.
- Live AWS behavior has been validated by this documentation pass.

Supply-chain security remains a combination of project controls, CI controls, dependency review, artifact provenance, runtime deployment controls, and operator discipline.
