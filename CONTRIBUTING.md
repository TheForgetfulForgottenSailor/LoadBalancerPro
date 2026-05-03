# Contributing

Thanks for helping improve LoadBalancerPro. This repository is managed as an enterprise-demo project with explicit safety boundaries, release discipline, and conservative change scope.

## Branch Workflow

- Branch from `loadbalancerpro-clean`.
- Use planning branches for planned work before implementation when a task calls for planning.
- Keep public `main` untouched unless the repository policy changes later.
- Keep feature, release, and docs/config work in focused branches.

## Tests Before PR

Run these before opening a PR:

```text
mvn -q test
mvn -q -DskipTests package
```

Run packaged JAR smoke checks when packaging, CLI behavior, API startup behavior, or release version metadata changes.

## Credentials And Sensitive Data

Never commit:

- AWS credentials,
- API keys,
- OAuth tokens,
- private account IDs,
- secret-bearing local config files,
- sensitive logs,
- customer data.

Use sanitized examples in issues, PRs, documentation, and security reports.

## Scope Discipline

- Do not mix behavior changes with docs-only patches.
- Keep release metadata patches separate from feature work.
- Keep governance, docs, and config changes separate from algorithm changes.
- Keep dependency updates separate from unrelated feature work.

## CloudManager And AWS Safety Boundaries

- Do not bypass dry-run defaults.
- Do not weaken live-mode guardrails.
- Do not add live AWS mutation without explicit planning, tests, and sandbox evidence.
- Keep allocation and routing recommendation paths separate from cloud mutation paths.
- Do not connect recommendation-only routing or allocation outputs directly to live cloud mutation.

## Version And Tag Rules

- Do not move existing tags.
- Do not force-push release history.
- Do not tag until release verification passes.
- Align Maven, API, CLI, telemetry, and README version metadata together for patch releases.
- Preserve historical docs unless a task explicitly asks to update them.

## PR Expectations

PRs should include:

- the intended scope,
- tests run,
- protected areas checked,
- remaining risks or follow-up work,
- a note when docs/config-only changes intentionally skip code changes.

Prefer small focused commits over broad mixed changes.

## Local Commands

```text
mvn -q test
mvn -q -DskipTests package
```
