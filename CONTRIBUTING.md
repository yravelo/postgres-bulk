# Contributing

PostgreSQL Bulk currently targets Java 17 bytecode and supports development on Java 17 or 21. Use
the checked-in Maven Wrapper; Docker is required for integration and example tests.

## Build

```bash
cd code/postgres-bulk-parent
./mvnw clean verify
```

The build runs unit tests, PostgreSQL Testcontainers integration tests, the standalone example
consumer, Spotless and warning-free public Javadocs. Apply Java formatting with:

```bash
./mvnw spotless:apply
```

Before submitting a change, also run from the repository root:

```bash
./scripts/check-documentation.sh
git diff --check
```

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
`https://central.sonatype.com` using GitHub identity `yravelo`. In Publishing Settings, confirm
that `io.github.yravelo` is verified; if it is absent, add that exact namespace and follow its
verification flow or contact Central Support. Then generate a named, expiring user token at
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

After the private repository has GitHub environment-secret support, configure the four required
names directly in `maven-central`. `gh secret set` without `--body` reads interactively; the
armored key can be read from a protected file without placing its content in shell history:

```bash
gh secret set CENTRAL_USERNAME --repo yravelo/postgres-bulk --env maven-central
gh secret set CENTRAL_PASSWORD --repo yravelo/postgres-bulk --env maven-central
gh secret set GPG_PRIVATE_KEY --repo yravelo/postgres-bulk --env maven-central < /secure/path/private-key.asc
gh secret set GPG_PASSPHRASE --repo yravelo/postgres-bulk --env maven-central
```

Do not use repository-level secrets as a silent fallback. The current private-repository
entitlement does not make environment secrets/protection available; changing that security model
requires a separate explicit decision. Creating the tag, executing the release workflow, uploading
to Central and publishing are also separate authorized actions.
