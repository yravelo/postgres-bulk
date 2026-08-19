# Release readiness for 0.1.0

## Verdict

The final project identity is approved and applied locally, but `0.1.0` is **not ready for public
publication**. The private GitHub repository does not exist yet, no remote is configured, and the
Central namespace, security channel, release environment, signing material and tag remain external
prerequisites. No repository, tag, upload or publication is created by this assessment.

## Final identity

| Item | Value | Status |
| --- | --- | --- |
| Project | `postgres-bulk` | PASS |
| GitHub owner | `yravelo` | PASS — approved identity |
| Planned repository | `https://github.com/yravelo/postgres-bulk` | EXTERNAL PREREQUISITE — private repository does not exist |
| Git remote | none | EXTERNAL PREREQUISITE |
| Maven groupId | `io.github.yravelo` | PASS — final coordinate |
| Java package root | `io.ybr.postgresbulk` | PASS — final binary namespace |
| Release candidate | `0.1.0` | PASS |

The Maven groupId and Java package root intentionally differ. The groupId aligns with the approved
GitHub owner for Central verification; Java packages remain a separately chosen API namespace.

## Artifact inventory

| Artifact | Packaging | Purpose / boundary | Release files | Publish |
| --- | --- | --- | --- | --- |
| `postgres-bulk-core` | JAR | Public API/SPI and neutral metadata | binary, sources, Javadocs, POM | yes |
| `postgres-bulk-pgjdbc` | JAR | pgJDBC COPY infrastructure | binary, sources, Javadocs, POM | yes |
| `postgres-bulk-hibernate` | JAR | Hibernate metadata adapter | binary, sources, Javadocs, POM | yes |
| `postgres-bulk-spring-data` | JAR | Repository fragment and transaction adapter | binary, sources, Javadocs, POM | yes |
| `postgres-bulk-spring-boot-autoconfigure` | JAR | Boot composition and observability | binary, sources, Javadocs, POM | yes |
| `postgres-bulk-spring-boot-starter` | JAR | Dependency-only adoption entry point | binary, sources, Javadocs, POM | yes |

The `postgres-bulk-parent` POM is supporting publication metadata. Benchmarks and the standalone
example are non-published consumers and must not appear in staging.

## Coordinates and versioning

The final release coordinates are `io.github.yravelo:postgres-bulk-*:0.1.0`. `${revision}` remains
the single version source: development defaults to `0.1.0-SNAPSHOT`, while release validation uses
`-Drevision=0.1.0`. Flattened consumer POMs must contain resolved release dependencies only.

## Repository and public metadata

The POM anticipates the approved repository URL and SCM location. This is preparation, not proof
that the repository exists. Name, description, Apache-2.0 license, project URL, SCM and developer
identity `yravelo` are present. No email is published. GitHub Issues metadata remains absent until
the repository exists and Issues is confirmed.

The repository is planned as a **private development repository**. That decision is distinct from
publishing binary artifacts to Maven Central and does not promise a future public source repository.

## Maven Central status

Target: Maven Central Publisher Portal using Sonatype's official plugin with
`autoPublish=false`.

Namespace `io.github.yravelo`: **PENDING EXTERNAL ACTION**. The owner must sign in to Central,
confirm or request that namespace, complete any requested verification, and create a user token.

- [Central publication requirements](https://central.sonatype.org/publish/requirements/)
- [Central namespace registration](https://central.sonatype.org/register/namespace/)
- [Official Central Maven plugin](https://central.sonatype.org/publish/publish-portal-maven/)

## Security, signing and GitHub environment

| Control | Status |
| --- | --- |
| GitHub Private Vulnerability Reporting | EXTERNAL PREREQUISITE — enable after repository creation |
| OpenPGP strategy | PASS — isolated in `central-publish` |
| Protected OpenPGP key | EXTERNAL PREREQUISITE |
| GitHub environment `maven-central` | EXTERNAL PREREQUISITE — repository does not exist |
| Central username/password secrets | EXTERNAL PREREQUISITE |
| GPG private key/passphrase secrets | EXTERNAL PREREQUISITE |
| Tag `v0.1.0` | NOT EXECUTED — creation/push not authorized |
| Remote workflow | NOT EXECUTED |
| Central upload/publication | NOT EXECUTED |

Secrets belong in the protected GitHub environment, never in Git, documentation, chat or logs.

## License, supply chain and reproducibility

The root `LICENSE`, inherited POM metadata and documentation use Apache-2.0. The production license
audit must remain free of unknown metadata. Each Java module attaches binary, sources and strict
Javadocs; the code-free starter uses explanatory archives. Staging emits SHA-256, and two clean
release builds compare the parent POM plus four files for each of the six modules: 25 primary
artifacts total. SBOM and provenance remain deferred non-blocking decisions.

## Phase 16B local validation

Local revalidation is **PASS**. The final-identity candidate passed `spotless:check`, `test`,
`verify`, `clean verify` and `install`; 217 tests passed with zero failures, errors or skips (the
216-test library baseline plus the standalone example smoke test). Strict Javadocs completed with
zero warnings and errors. Documentation and public API checks passed.

The `0.1.0` release dry-run passed with 25 primary staged artifacts, an isolated external consumer
and a clean dependency tree. The production license audit passed. Two clean release builds produced
identical SHA-256 values for all 25 primary artifacts.

All PostgreSQL Bulk artifacts in the isolated consumer resolved from file staging under
`io.github.yravelo`; third-party dependencies resolved from Maven Central. JAR bytecode and Spring
metadata reference `io.ybr.postgresbulk`, with no active use of the former namespace.

## Remaining activation sequence

1. Create private repository `yravelo/postgres-bulk`.
2. Configure and verify the Git remote.
3. Confirm GitHub Issues and enable Private Vulnerability Reporting.
4. Verify `io.github.yravelo` in Central and create a user token.
5. Create/protect an OpenPGP key and configure the four environment secrets.
6. Create the protected `maven-central` environment and required review policy.
7. Recheck a clean remote candidate, then create and push `v0.1.0` only with authorization.
8. Run the remote candidate workflow; authorize Central upload and Portal publication separately.

No external action above is authorized by this document.
