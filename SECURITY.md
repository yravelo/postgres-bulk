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

## CI trust boundary

Build and Compatibility run on a persistent, repository-scoped self-hosted runner with Docker
access. That capability is trusted infrastructure and is not available to arbitrary PR code:
self-hosted jobs accept pushes and only owner-authored PRs whose head repository is this repository.
Fork or other untrusted PR jobs are skipped. No publishing secret is present on the runner, checkout
credentials are not persisted, and Release remains separate on GitHub-hosted infrastructure. See
the [self-hosted runner security model](docs/security/self-hosted-runner.md).

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

## Static analysis

Build and every future release candidate run SpotBugs with FindSecBugs on production bytecode.
Analyzer failure, missing classes, inactive security detectors or a new medium/high finding blocks
the build. Findings are reviewed by rule, source/sink, reachability and existing contracts; a real
defect is fixed and tested before considering an exact, expiring exclusion. The
[Java static-analysis policy](docs/security/java-static-analysis.md) records the current triage and
local reproduction command. CodeQL, Semgrep and Sonar are not enabled baselines.

## SBOM and production licenses

Build and every future release candidate generate CycloneDX 1.6 JSON for all nine publishable
artifacts plus an aggregate. The fail-closed gate reconciles Maven and OSV production inventories,
rejects test/build/non-publishable components, SNAPSHOTs and invalid identity or private metadata,
and requires every compile/runtime dependency to have classified license metadata. Unknown or
incompatible strong-copyleft licenses block; exact weak/multiple-license reviews have owners and
expiry. The current baseline has 55 external production components, zero unknown licenses and zero
unresolved blocks. See the
[SBOM and dependency/license integrity policy](docs/security/sbom-and-license-integrity.md).
