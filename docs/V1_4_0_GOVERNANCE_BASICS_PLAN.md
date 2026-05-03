# LoadBalancerPro v1.4.0 Governance Basics Plan

Date: 2026-05-03

## A. Current Release State

- `v1.3.1` is shipped and pushed to `origin/loadbalancerpro-clean`.
- Remote release branch hash: `b87ecbae08ecde49973a3a5265e19926a60a7f12`.
- Remote `v1.3.1` annotated tag object: `4ee5973f0c4fb9495b43f5c1ee194a153632b30c`.
- Remote `v1.3.1` tag target: `b87ecbae08ecde49973a3a5265e19926a60a7f12`.
- `loadbalancerpro-clean` is the release branch.
- Public `main` remains untouched.
- Existing release tags, including `v1.3.0` and `v1.3.1`, must remain immutable.
- `v1.3.0` added `WEIGHTED_LEAST_LOAD` and optional routing-only weight support.
- `v1.3.1` aligned active Maven, API, CLI, telemetry, tests, and README JAR examples to `1.3.1`.

## B. Why Governance Basics Are Next

LoadBalancerPro now has real release discipline, versioned tags, a documented routing comparison API, and two real routing strategies. It is credible as an enterprise-demo repository, but enterprise reviewers also expect basic repository governance before trusting reuse, contribution, or vulnerability disclosure.

The current evidence set already documents security posture, threat model, supply-chain baseline, residual risks, and enterprise-readiness gaps. The missing governance files are the public entry points that tell reviewers:

- what rights they have to reuse the code,
- how to report vulnerabilities,
- who owns critical review paths,
- how to contribute without weakening safety boundaries, and
- how dependency update notifications will be managed.

Adding these basics is a high-signal, low-risk hardening slice because it does not require production code, routing behavior, CloudManager/AWS logic, Maven dependency, or release tag changes.

## C. Files To Add

### 1. `LICENSE`

Purpose: make repository reuse rights explicit.

Recommended choice: MIT License for portfolio/open-source demo simplicity. MIT is short, widely recognized, easy for reviewers to understand, and appropriate if the owner wants permissive reuse with minimal friction.

Alternative choices:

- Apache-2.0 if the owner wants a more explicit patent grant and more formal terms.
- Proprietary/no-license if the owner does not want public reuse rights.

Important note: no license means other people do not clearly have reuse, modification, or redistribution rights even if the repository is public.

Recommended contents:

- Standard MIT License text.
- Copyright holder should use the repository owner's preferred legal name or handle.
- Year should be `2026` unless the owner wants a range.

### 2. `SECURITY.md`

Purpose: define supported versions and a safe vulnerability disclosure path.

Recommended contents:

- Supported versions:
  - Current supported release line: `v1.3.x`.
  - Older tags remain historical release references unless explicitly supported later.
- Vulnerability reporting process:
  - Ask reporters to use a private channel such as GitHub Security Advisories if enabled, or a project-owner email/contact placeholder.
  - If no private channel is configured yet, mark the contact as `TODO: owner-provided private security contact`.
- What not to include in reports:
  - real AWS credentials,
  - API keys,
  - OAuth tokens,
  - private account IDs,
  - customer data,
  - sensitive logs,
  - live exploit traffic against infrastructure the reporter does not own.
- Expected response language:
  - acknowledge that reports will be reviewed as project capacity allows,
  - avoid promising a fixed SLA unless the owner explicitly wants one,
  - state that accepted findings may be fixed in a future patch release.
- Security scope:
  - Spring Boot API behavior,
  - routing comparison API safety boundaries,
  - Docker packaging and runtime hardening,
  - CloudManager/AWS guardrails,
  - dependency vulnerabilities,
  - documentation that could cause unsafe operation.
- Out of scope:
  - social engineering,
  - physical attacks,
  - denial-of-service against public infrastructure,
  - attacks against live AWS resources not owned by the repo owner,
  - vulnerability claims that require unsafe real-world exploitation.

The policy should be careful not to claim enterprise-production readiness. It should align with `evidence/SECURITY_POSTURE.md`, `evidence/THREAT_MODEL.md`, and `evidence/RESIDUAL_RISKS.md`.

### 3. `.github/CODEOWNERS`

Purpose: make review ownership visible and auditable.

Recommended location: `.github/CODEOWNERS`, because the repository already has a `.github/` directory for workflows.

Recommended owner:

- Use `@richmond423` if the repository owner confirms that handle should own reviews.
- Otherwise use a clear placeholder such as `@OWNER_HANDLE`.
- Do not invent a private organization or team.

Recommended ownership paths:

```text
* @OWNER_HANDLE

/src/main/java/core/CloudManager.java @OWNER_HANDLE
/src/main/java/cloud/ @OWNER_HANDLE
/src/main/java/api/ @OWNER_HANDLE
/src/main/java/core/RoutingStrategy*.java @OWNER_HANDLE
/src/main/java/core/*Routing*.java @OWNER_HANDLE
/src/main/java/core/*Strategy.java @OWNER_HANDLE
/Dockerfile @OWNER_HANDLE
/.github/workflows/ @OWNER_HANDLE
/pom.xml @OWNER_HANDLE
/evidence/ @OWNER_HANDLE
/docs/ @OWNER_HANDLE
```

The implementation should adjust paths after confirming which AWS-related paths exist. The current repository has `src/main/java/core/CloudManager.java` and a `.github/workflows/ci.yml`; `src/main/java/cloud/` should be included only if present or if the project expects that path soon.

### 4. `CONTRIBUTING.md`

Purpose: tell contributors how to work without breaking release discipline or safety boundaries.

Recommended contents:

- Branch workflow:
  - create feature, release, or planning branches from `loadbalancerpro-clean`,
  - keep public `main` untouched unless a future policy explicitly changes that,
  - use planning branches for plans before implementation when requested.
- Tests required before PR:
  - `mvn -q test`,
  - `mvn -q -DskipTests package`,
  - focused smoke checks when the packaged JAR or CLI behavior changes.
- Credential rules:
  - never commit AWS credentials, API keys, OAuth tokens, private account IDs, secret-bearing local config, or sensitive logs.
- Scope discipline:
  - do not mix behavior changes with docs-only patches,
  - keep release metadata patches separate from feature work,
  - keep governance/docs/config changes separate from algorithm changes.
- CloudManager/AWS safety boundaries:
  - do not bypass dry-run defaults,
  - do not weaken live-mode guardrails,
  - do not add live AWS mutation without explicit planning, tests, and sandbox evidence,
  - keep allocation and routing recommendation paths separate from cloud mutation paths.
- Version/tag rules:
  - do not move existing tags,
  - do not force-push release history,
  - do not tag until release verification passes,
  - update Maven/API/CLI/telemetry/README version metadata together for patch releases.
- Commit/PR expectations:
  - small focused commits,
  - PR description should include scope, tests run, protected areas checked, and any remaining risks,
  - note when docs/config-only changes intentionally skip code changes.
- Local commands:
  - `mvn -q test`,
  - `mvn -q -DskipTests package`,
  - optional packaged JAR smoke checks for release metadata or CLI/API packaging changes.

### 5. `.github/dependabot.yml`

Purpose: add minimal dependency update visibility without changing dependencies in this slice.

Recommended safe minimal plan:

- Maven ecosystem:
  - package ecosystem: `maven`
  - directory: `/`
  - weekly schedule
  - low open PR limit, such as `5`
- GitHub Actions ecosystem:
  - package ecosystem: `github-actions`
  - directory: `/`
  - weekly schedule
  - low open PR limit
- Docker ecosystem:
  - package ecosystem: `docker`
  - directory: `/`
  - weekly schedule
  - include because the repository has a root `Dockerfile`
- Auto-merge:
  - do not enable auto-merge.
- Grouping:
  - start with no grouping, or one simple group for minor/patch updates only after owner preference is clear.
- Dependency changes:
  - do not change Maven dependencies, Docker base image digests, or GitHub Actions versions during the governance planning or implementation slice.

Example shape for future implementation:

```yaml
version: 2
updates:
  - package-ecosystem: "maven"
    directory: "/"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 5

  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 5

  - package-ecosystem: "docker"
    directory: "/"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 5
```

## D. Exact Implementation Scope

A future v1.4.0 implementation slice should add governance files only:

- `LICENSE`
- `SECURITY.md`
- `CONTRIBUTING.md`
- `.github/CODEOWNERS`
- `.github/dependabot.yml`

No production code changes.
No test changes unless a future docs-linting workflow is explicitly introduced, which is not needed for this slice.
No Maven dependency changes.
No CI workflow behavior changes except adding Dependabot configuration.
No release metadata changes unless a later v1.4.0 release task explicitly includes them.

## E. Risks

- Choosing the wrong license can grant reuse rights the owner does not intend, or omit patent language the owner wants.
- Security policy language can accidentally promise unrealistic response SLAs.
- CODEOWNERS entries with invalid GitHub handles can create noisy review routing or fail to provide useful ownership.
- Dependabot may open noisy PRs, especially for Maven, GitHub Actions, and Docker digest updates.
- Governance docs may accidentally imply enterprise-production readiness instead of enterprise-demo readiness.
- The implementation could accidentally touch public `main`, move release tags, or broaden into CI/security automation beyond the approved slice.

## F. What Not To Change

- Do not move `v1.3.1` or any prior tags.
- Do not move `v1.3.0`.
- Do not touch public `main`.
- Do not change code behavior.
- Do not change `CloudManager` or AWS behavior.
- Do not change routing strategies.
- Do not change allocation endpoints.
- Do not change CLI behavior.
- Do not modify Maven dependencies.
- Do not add SBOM generation or signing yet.
- Do not add CodeQL yet.
- Do not add deployment, Kubernetes, Terraform, or operations docs yet.
- Do not alter `.github/workflows/ci.yml` in this slice.

## G. Recommended First Implementation Slice

1. Add `LICENSE`.
2. Add `SECURITY.md`.
3. Add `CONTRIBUTING.md`.
4. Add `.github/CODEOWNERS`.
5. Add `.github/dependabot.yml`.
6. Run repository sanity checks.
7. Run `mvn -q test` and `mvn -q -DskipTests package` even though the patch is docs/config-only.
8. Commit as one governance-basics commit.

Recommended commit message:

```text
Add governance basics
```

## H. Verification Plan

Before implementation:

```text
git status
git branch --show-current
dir
dir .github
```

After implementation:

```text
mvn -q test
mvn -q -DskipTests package
git diff --check
git diff --name-only
git diff -- src/main/java src/test/java pom.xml
```

Expected:

- No `src/main/java` changes.
- No `src/test/java` changes.
- No `pom.xml` changes.
- Only governance docs/config files added.
- Existing release tags remain unchanged.
- Public `main` remains untouched.
- Remotes remain unchanged.

## I. Recommendation

Proceed with governance basics before the next feature.

Keep v1.4.0 governance basics docs/config-only. The project already has enough feature and release substance for enterprise-demo review; the next useful hardening step is to clarify reuse rights, vulnerability reporting, review ownership, contribution rules, and dependency update visibility without touching runtime behavior.
