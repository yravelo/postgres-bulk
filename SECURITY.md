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

## Dependency vulnerabilities

Dependency advisories are checked by OSV in Build and again for a release candidate. Dependabot
provides alerts and update pull requests without auto-merge. A report is triaged by production,
test, benchmark, example or build scope; direct/transitive ownership; reachability; required
configuration; exploitation; supported fix and compensating controls—not by CVSS alone.

An untriaged production finding, applicable HIGH/CRITICAL production or build-chain finding,
incomplete scan, scanner failure or expired accepted risk blocks release. Narrow accepted risks are
reviewable in version control at `config/security/accepted-dependency-risks.json` and require an exact
advisory/dependency/version, owner, evidence, review date, expiry and removal condition. The
[dependency vulnerability policy](docs/security/dependency-vulnerability-management.md) documents
the reproducible command, current baseline and remediation rules.
