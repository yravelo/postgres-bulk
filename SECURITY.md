# Security policy

## Supported versions

PostgreSQL Bulk has not published a supported release yet. The `0.1.0` candidate is not a supported
public version until the release blockers in
[the release readiness assessment](docs/releases/release-readiness.md) are resolved.

## Reporting a vulnerability

The development repository exists at `https://github.com/yravelo/postgres-bulk` and remains
private. GitHub Private Vulnerability Reporting is not available for the repository under its
current configuration, so there is still no confirmed private vulnerability reporting channel.
Do not disclose a suspected vulnerability in a public issue.

If a credential or private key may have entered the working tree or Git history, assume exposure
until disproved. Do not print, copy into an issue or rely on GitHub log masking. Stop the affected
workflow, revoke or rotate the credential through its owner, preserve non-sensitive evidence and
run the repository Gitleaks current/history scans. Never rewrite shared history automatically:
revocation and incident triage come before repository cleanup.

Resolving and testing an appropriate private reporting channel remains an external action. Once
enabled, replace this paragraph with the repository's real Security Advisories reporting
instructions. No email, response time or remediation SLA is implied by this provisional policy.
