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
isolation boundary. Release credentials belong exclusively to the `central-upload` job, and no
candidate, build, test, cache or artifact may contain them.

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

## Release candidate dry-run

The default build uses `0.1.0-SNAPSHOT` and never needs signing material. The `release` profile
uses the same tracked POMs with an explicit CI-friendly version:

```bash
./scripts/release-dry-run.sh 0.1.0
./scripts/audit-production-licenses.sh 0.1.0
./scripts/compare-release-builds.sh 0.1.0
```

Run these commands from the repository root with Docker available. They write only under
`target/`; no remote publication occurs. The `central-publish` profile is reserved for the manual,
protected release workflow after all external prerequisites in
[release readiness](docs/releases/release-readiness.md) are resolved.

## Central and OpenPGP activation (owner only)

Central uses the Publisher Portal, not the legacy OSSRH workflow. Sign in at
`https://central.sonatype.com` using GitHub identity `yravelo`. The owner confirmed on 2026-08-19
that `io.github.yravelo` is `VERIFIED`. Reconfirm that state before activation, then generate a
named, expiring user token at
`https://central.sonatype.com/usertoken`. Its generated username/password pair maps to
`CENTRAL_USERNAME` and `CENTRAL_PASSWORD`; neither value belongs in this repository or chat.

Central requires an OpenPGP signature for every deployed POM and JAR. Generate the real key only
on a trusted owner-controlled machine, choose a deliberately public UID and strong passphrase,
record the full fingerprint and keep the revocation certificate and private-key backup offline:

```bash
gpg --full-generate-key
gpg --list-secret-keys --keyid-format long
gpg --armor --export <FINGERPRINT> > public-key.asc
gpg --armor --export-secret-keys <FINGERPRINT> > private-key.asc
gpg --keyserver keyserver.ubuntu.com --send-keys <FINGERPRINT>
gpg --keyserver keyserver.ubuntu.com --recv-keys <FINGERPRINT>
```

Verify the imported fingerprint matches before continuing. `keyserver.ubuntu.com`,
`keys.openpgp.org` and `pgp.mit.edu` are currently supported by Central; only the public key is
sent to them. Keep `private-key.asc` and its passphrase separate and remove the export from normal
storage after GitHub configuration according to the owner's secure deletion policy.

Phase 16E explicitly selects Actions Repository Secrets for the current private, single-maintainer
repository. The existing `maven-central` environment is not used by the workflow because it has no
effective secrets or protection rules under the current plan. `gh secret set` without `--body`
reads interactively; the armored key can be read from a protected file without placing its content
in shell history:

```bash
gh secret set CENTRAL_USERNAME --repo yravelo/postgres-bulk
gh secret set CENTRAL_PASSWORD --repo yravelo/postgres-bulk
gh secret set GPG_PRIVATE_KEY --repo yravelo/postgres-bulk < /secure/path/private-key.asc
gh secret set GPG_PASSPHRASE --repo yravelo/postgres-bulk
gh secret list --repo yravelo/postgres-bulk --app actions
```

The list command verifies names only; never request or display values. Before dispatch, review the
workflow on `main`, resolve the full 40-character candidate SHA, and ensure it belongs to
`origin/main`. Candidate-only confirmation is `candidate <version>`; upload confirmation is
`publish <version>`. Upload additionally requires `v<version>` to point exactly to that SHA. Never
enable shell tracing or print the environment in the upload job. Creating the tag, executing the
workflow, uploading and publishing in Central are separate authorized actions. Reevaluate this
storage decision before adding write collaborators, making the repository public or changing the
GitHub plan.
