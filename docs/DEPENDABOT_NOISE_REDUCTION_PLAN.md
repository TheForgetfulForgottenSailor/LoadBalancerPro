# Dependabot Noise Reduction Plan

## Purpose

Reduce risky Dependabot noise while preserving security updates and low-risk maintenance updates.

## Current Problem

The current Dependabot configuration checks Maven, GitHub Actions, and Docker weekly with an open pull request limit of five for each ecosystem. It does not group or defer update classes, so major Java, Docker/base image, Spring, and JavaFX PRs can appear alongside low-risk patch or minor updates.

## Lessons From v2.3.3-v2.3.5

- One PR at a time worked well.
- Release evidence and post-release artifact verification worked well.
- GitHub Actions updates should be handled separately from application dependencies.
- `actions/upload-artifact` changes require tag-triggered Release Artifacts verification because they affect the release upload path.

## Proposed Grouping Strategy

- Maven low-risk patch/minor runtime dependencies.
- Maven framework major upgrades, such as Spring Boot, separately.
- JavaFX separately.
- Docker/base images separately.
- GitHub Actions separately.
- Security-only urgent updates separately.

## Proposed Scheduling

- Routine patch/minor updates: weekly or monthly.
- Major upgrades: manual review before implementation.
- Java, Spring, and JavaFX major upgrades: dedicated compatibility projects with explicit verification plans.

## Proposed Ignore/Defer Policy

- Defer Java runtime/base image major jumps until an explicit Java baseline migration is approved.
- Defer Spring Boot 4 until a dedicated Spring 4 compatibility branch exists.
- Defer JavaFX 26 until a GUI/runtime compatibility plan exists.

## Required Verification By Category

- Maven runtime dependency: dependency diff, tests, package, CLI/API smoke checks, SBOM generation, `git diff --check`.
- Spring/framework dependency: migration notes, dependency tree review, API tests, OpenAPI checks, smoke tests, SBOM generation.
- JavaFX: compile/test verification, GUI runtime compatibility review, Java runtime compatibility review.
- Docker/base image: image build, container startup, healthcheck, Trivy scan, Java/runtime compatibility review.
- GitHub Actions: workflow syntax review, check status, Dependency Review, CodeQL, no behavior drift.
- Release-artifact-affecting action: all GitHub Actions checks plus tag-triggered Release Artifacts verification and artifact bundle inspection.

## Recommendation

Do not edit `.github/dependabot.yml` until this plan is reviewed and approved.

## Safety Notes

- This is a planning document only and makes no behavior changes.
- Namespace migration is not started.
- `public/main` remains untouched.
