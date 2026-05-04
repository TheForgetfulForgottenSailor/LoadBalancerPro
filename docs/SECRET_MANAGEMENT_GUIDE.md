# LoadBalancerPro Secret Management Guide

Date: 2026-05-04

## Purpose And Scope

This guide documents secret handling guidance for LoadBalancerPro demos and production-like deployments.

It is not a complete enterprise secret-management program. It does not replace organization policy, cloud IAM review, secret rotation systems, audit logging, endpoint protection, or incident response.

The goal is to keep real secrets out of source control, examples, screenshots, logs, Docker images, and evidence files while preserving useful deployment guidance.

## Secret Categories

Common LoadBalancerPro-related secret or sensitive configuration categories include:

- `LOADBALANCERPRO_API_KEY`
- OAuth2 issuer settings when environment-specific or private
- OAuth2 JWK settings when environment-specific or private
- OAuth client secrets or token material if used by a surrounding deployment
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- AWS session tokens
- AWS region and account IDs when they are private or environment-sensitive
- OTLP collector endpoints when private
- OTLP auth headers or collector credentials
- CORS origins when they reveal private environments
- Local config files
- CLI config files
- Proxy, ingress, or gateway credentials

Not every value in this list is always secret. Treat values as sensitive when they identify private infrastructure, grant access, help target an environment, or would be inappropriate in a public issue or README example.

## Storage Guidance

Use environment variables only for local demos and short-lived validation.

For serious deployments, prefer:

- orchestrator-managed secrets,
- cloud secret managers,
- CI/CD secret stores,
- workload identity where available,
- short-lived credentials over long-lived static keys.

Do not:

- commit `.env` files,
- commit real API keys,
- commit AWS credentials,
- commit OAuth tokens,
- commit private account IDs unless intentionally public,
- bake secrets into Docker images,
- put real secrets in README examples,
- put real secrets in evidence files,
- put real secrets in screenshots,
- paste real secrets into issues, pull requests, or chat transcripts.

Use placeholders such as `replace-with-random-deployment-secret`, `<sandbox-account-id>`, and `<trusted-private-collector>` in source-controlled documentation.

## Leakage Paths

Secrets can leak outside source files. Review these paths during deployment and incident response:

- shell history,
- terminal screenshots,
- CI logs,
- proxy logs,
- application logs,
- startup scripts,
- crash dumps,
- JVM diagnostic files,
- support tickets,
- pasted issue reports,
- copied curl commands,
- local config backups,
- Docker build logs,
- container environment inspection,
- monitoring labels and annotations.

Avoid logging request headers that may contain `Authorization`, `X-API-Key`, bearer tokens, OTLP credentials, or cloud provider credentials.

## Rotation And Incident Response

Rotate `LOADBALANCERPRO_API_KEY` after exposure or suspected exposure.

Rotate AWS credentials immediately after suspected leak. Prefer disabling or deleting exposed access keys, reviewing recent CloudTrail or equivalent audit events, and replacing credentials through the normal secret-management path.

Invalidate OAuth client secrets, refresh tokens, or bearer tokens if exposed. Review issuer logs and token lifetime settings according to the identity provider's process.

After accidental disclosure:

1. Stop using the exposed secret.
2. Rotate or revoke it.
3. Review logs and Git history to understand exposure.
4. Review CI logs, screenshots, issues, pull requests, and release artifacts for copies.
5. Document the incident in the appropriate private channel.
6. Avoid rewriting public Git history without a separate plan.

If a secret was committed, rotating the secret is more important than trying to hide it. Public history may already be copied.

## Sanitized Examples

Use placeholders only:

```text
LOADBALANCERPRO_API_KEY=replace-with-random-deployment-secret
AWS_ACCESS_KEY_ID=<sandbox-access-key-id>
AWS_SECRET_ACCESS_KEY=<sandbox-secret-access-key>
OTEL_EXPORTER_OTLP_METRICS_ENDPOINT=https://collector.internal.example/v1/metrics
LOADBALANCERPRO_CORS_ALLOWED_ORIGINS=https://app.example.com
```

Avoid real account IDs unless they are intentionally public. Redact tokens and bearer values before adding logs to issues, evidence, or review notes.

Recommended redaction style:

```text
Authorization: Bearer <redacted>
X-API-Key: <redacted>
AWS_SECRET_ACCESS_KEY=<redacted>
```

## Review Checklist

Before committing docs, examples, scripts, or evidence:

- [ ] No real API keys.
- [ ] No AWS credentials.
- [ ] No OAuth tokens.
- [ ] No private account IDs unless intentionally public.
- [ ] No private OTLP credentials or auth headers.
- [ ] No secret-bearing `.env` or local config files.
- [ ] No sensitive logs.
- [ ] No screenshots containing terminal secrets.
- [ ] Placeholders are clearly fake.
- [ ] Secret rotation expectations are documented for any new deployment path.
