# Contributing

PostgreSQL Bulk currently targets Java 17 bytecode and supports development on Java 17 or 21. Use
the checked-in Maven Wrapper; Docker is required for integration and example tests.

## Build

```bash
cd code/postgres-bulk-parent
./mvnw clean verify
```

The build runs unit tests, PostgreSQL Testcontainers integration tests, the standalone example
consumer, Spotless, SpotBugs with FindSecBugs and warning-free public Javadocs. Apply Java
formatting with:

```bash
./mvnw spotless:apply
```

Before submitting a change, also run from the repository root:

```bash
./scripts/check-workflow-security.py
./scripts/test-workflow-security.py
./scripts/check-secrets.sh current
./scripts/check-vulnerabilities.sh
./scripts/check-static-analysis.py
./scripts/check-documentation.sh
git diff --check
```

Run the full Git history scan before a release candidate or after changing secret-handling policy:

```bash
./scripts/check-secrets.sh history
```

The Gitleaks wrapper downloads version 8.30.1 from the official release, verifies the platform
archive and extracted binary by SHA-256, fully redacts detected values and writes no report. Linux
x86-64 and arm64 are supported. A finding must be treated as potentially compromised: stop the
change, revoke or rotate real credentials first, then investigate cleanup. Do not rewrite history,
add a broad allow-list or paste the value into an issue, log or chat. If an exception is genuinely
required, it must be rule/path-specific and document evidence, owner and expiry.

Use the SEC7 entrypoint for composed maintainer validation:

```bash
./scripts/check-security.sh fast
./scripts/check-security.sh full
./scripts/check-security.sh release
```

`fast` is the normal change preflight, `full` adds fresh network/history, reactor, SBOM and runner
health checks, and `release` additionally runs only ephemeral signing fixtures plus the technical
release preflight. The REL1 preflight remains a separate fail-closed command and validates the
configured private reporting channel. See
[continuous security validation](docs/security/continuous-security-validation.md).
The integrated SEC8 threat, residual-risk and publication-boundary audit is
`./scripts/check-security-baseline.py`; its source of truth and handoff are documented in
[security baseline closure](docs/security/security-baseline-closure.md).

The dependency wrapper builds JSON Maven trees for the full reactor, validates the explicit build
tool inventory and scans every unique external name/version with checksum-pinned OSV-Scanner
2.5.1. It requires network access and fails closed on a tool error, incomplete package set,
untriaged production finding or expired accepted risk. Generated evidence stays under
`target/security/`; reviewable exceptions belong only in
`config/security/accepted-dependency-risks.json` with exact advisory/dependency/version, owner and
expiry. Do not add broad ignores or override one member of the Boot BOM only to silence a finding.

CycloneDX 1.6 JSON is generated for each of the nine publishable JARs plus the public aggregate.
The canonical auditor reconciles it with independently resolved Maven trees, the SEC2
consumer-reachable OSV inventory and the exact production license policy:

```bash
./scripts/generate-sbom.sh 0.1.0
./scripts/audit-production-licenses.sh 0.1.0
python3 scripts/test-sbom-auditor.py
```

Outputs are single-use evidence under `target/` and must not be committed. An unknown production
license, expired or drifting review, SNAPSHOT, broken purl/edge, build/test/non-publishable
component, private path or JDBC-to-JPA leak fails closed. See the
[SBOM and license integrity policy](docs/security/sbom-and-license-integrity.md).

SpotBugs 4.10.4 with FindSecBugs 1.14.0 runs automatically during `verify` on the seven productive
code modules. After a clean reactor build, `check-static-analysis.py` confirms detector activation,
zero analyzer errors and zero untriaged findings. Do not bypass it in Build or Release. Fix a real
defect with a regression test; a proven false positive or non-applicable result needs an exact
Bug/Class/Method exclusion with rationale, owner and future review date. Package/category-wide or
mass medium suppressions are forbidden. See the
[Java static-analysis policy](docs/security/java-static-analysis.md).

All GitHub Actions must use an approved full commit SHA with a human-readable version comment.
Keep workflow permissions read-only, checkout credentials non-persistent and event/input expressions
outside shell blocks. Secret masking is only defense-in-depth; it is not an authorization or
isolation boundary. Release, signing and Central credentials are intentionally absent from GitHub
Actions and the self-hosted runner; local signing/upload requires separate owner authorization.
No candidate, build, test, cache or artifact may contain secret material.

Dependabot opens bounded weekly Maven and Actions update PRs. It never auto-merges. Review
each PR, keep majors and Boot generation changes manual, preserve full Action SHAs plus version
comments and run Build plus relevant Compatibility lanes for dependency changes. Docker references
must remain explicit non-`latest` tags and are reviewed manually because GitHub's Docker updater did
not recognize this repository's Compose-only manifests.

## Compatibility

The baseline uses PostgreSQL 15.18. Supported server, Boot, Hibernate and pgJDBC boundaries are
documented in [compatibility](docs/architecture/compatibility.md). Representative overrides are:

```bash
./mvnw clean verify -Dpostgres.version=18.4-alpine
./mvnw clean verify -Dspring-boot.version=3.5.0
./mvnw clean verify -pl postgres-bulk-hibernate -am -Dhibernate.version=6.6.55.Final
./mvnw clean verify -pl postgres-bulk-pgjdbc -am -Dpostgresql.version=42.7.13
```

Do not claim a new supported version without a green job and updated evidence.

## Module boundaries

- core remains Java SE and framework-independent;
- pgJDBC and Hibernate are sibling adapters and must not depend on each other;
- Spring Data composes core/pgJDBC through the metadata port;
- Boot auto-configuration is composition only;
- the starter contains no production Java;
- examples and benchmarks are non-published consumers.

Read [module boundaries](docs/architecture/module-boundaries.md) before moving dependencies or
types. Record a meaningful architectural change in an ADR.

## Benchmarks

Benchmarks are explicit and never a normal-build performance gate:

```bash
JAVA_HOME=/path/to/jdk-21 ./scripts/run-benchmarks.sh smoke smoke-local
```

Changes based on performance must include methodology, raw evidence and uncertainty. Do not infer a
universal threshold from one host.

## Pull requests

Keep changes within one phase/problem, add tests for failure paths, preserve root causes and avoid
sensitive values in errors or metrics. Update user documentation when observable behavior changes.
Do not add generated IDs, retries, adaptive lookup or new mapping support merely to simplify an
example; document friction and propose the behavior separately.

Build and Compatibility currently run on trusted self-hosted infrastructure. Only owner branches
from this repository may execute there; external or otherwise untrusted PR code must not run on the
self-hosted runner. Maintainers must preserve the exact PR guard and dedicated labels documented in
the [self-hosted runner security model](docs/security/self-hosted-runner.md).

## Responsible security reporting

Do not report a suspected vulnerability in an Issue, Discussion, pull request, commit message or
other public channel. Follow [`SECURITY.md`](SECURITY.md) and use the verified project channel
`postgresbulk-security@proton.me`; do not infer that the Git commit email is an approved security
contact. Detailed delivery, reply, account-control, MFA and recovery evidence remains private.

Use sanitized fixtures and descriptions. Never put a live credential, private key, personal data,
production database content or weaponized exploit in Git, CI artifacts or chat. Incident records
and undisclosed triage belong in restricted owner-controlled storage, using the versioned templates
only as empty forms.

## Security-sensitive changes

Changes to workflows, Dependabot, `config/security`, security/release scripts, parent release POM
configuration, `SECURITY.md`, security documentation or release readiness require explicit owner
review. `.github/CODEOWNERS` documents this responsibility but does not currently enforce an
independent approval on the private single-maintainer plan.

Before merging such a change:

- identify the threat, trust boundary, owner, rollback and exit criteria;
- preserve immutable Action pins, least privilege, safe shell inputs and the self-hosted PR guard;
- verify scanner provenance/checksums and retain fail-closed behavior;
- keep accepted risks, SAST exclusions and license exceptions exact, evidenced and expiring;
- preserve source-bound inventory, reproducibility, approved fingerprint and signing isolation;
- run the applicable security gates and record Build/Compatibility evidence;
- do not mix tag, upload, publication, visibility or plan changes into the implementation.

The complete checklist is in
[vulnerability response and repository governance](docs/security/vulnerability-response-and-governance.md#security-sensitive-change-checklist).

## Release candidate dry-run

The default build uses `0.1.0-SNAPSHOT` and never needs signing material. The `release` profile
uses the same tracked POMs with an explicit CI-friendly version:

```bash
./scripts/release-dry-run.sh 0.1.0
./scripts/audit-production-licenses.sh 0.1.0
./scripts/compare-release-builds.sh 0.1.0
./scripts/test-release-signatures.py
```

Run these commands from the repository root with Docker available. They write only under
`target/`; no remote publication occurs. The GitHub Release workflow is deliberately candidate-only
and contains no signing or Central credentials.

## Local OpenPGP release ceremony (owner only)

Central uses the Publisher Portal, not the legacy OSSRH workflow. Sign in at
`https://central.sonatype.com` using GitHub identity `yravelo`. The owner confirmed on 2026-08-19
that `io.github.yravelo` is `VERIFIED`. Reconfirm that state before activation, then generate a
named, expiring user token at
`https://central.sonatype.com/usertoken`. Its generated username/password pair maps to
`CENTRAL_USERNAME` and `CENTRAL_PASSWORD`; neither value belongs in this repository or chat.

Central requires an OpenPGP signature for every deployed file, including attached CycloneDX SBOMs.
The approved release key and complete ceremony are documented in
[release signing and provenance](docs/security/release-signing-and-provenance.md). Verify the public
fingerprint before use:

```bash
gpg --show-keys --with-fingerprint \
  docs/security/keys/postgres-bulk-release-11545CD242C9575DF408AC08F83D364143C798A3.asc
./scripts/signed-release-dry-run.sh 0.1.0 \
  /path/to/dedicated/release-gnupg
```

Verify the imported fingerprint matches before continuing. `keyserver.ubuntu.com`,
`keys.openpgp.org` and `pgp.mit.edu` are currently supported by Central; only the public key is
sent to them. Keep `private-key.asc` and its passphrase separate and remove the export from normal
storage after GitHub configuration according to the owner's secure deletion policy.

The passphrase is entered only through pinentry/gpg-agent. Never export the secret key into this
repository, a GitHub secret, runner filesystem, argument, environment variable, Maven settings or
log. The real script requires a clean synchronized `main`, produces 46 Central-bound signatures
plus three evidence signatures, verifies the approved full fingerprint, and proves payload hashes
are unchanged. `local-signing` and `central-publish` remain separate Maven profiles; enabling the
latter, creating a tag, uploading or publishing each require separate future authorization.
