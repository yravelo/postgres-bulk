# Release readiness for 0.1.0

## Verdict

The final project identity is approved and the private GitHub repository and remote CI are active,
but `0.1.0` is **not ready for public publication**. The Central account/namespace, publishing
credentials, signing material and usable protected environment secrets remain external
prerequisites. No tag, release candidate workflow, Central upload or publication was executed.

## Final identity

| Item | Value | Status |
| --- | --- | --- |
| Project | `postgres-bulk` | PASS |
| GitHub owner | `yravelo` | PASS — approved identity |
| Repository | `https://github.com/yravelo/postgres-bulk` | PASS — created and confirmed PRIVATE |
| Git remote | `git@github.com:yravelo/postgres-bulk.git` | PASS — `main` tracks `origin/main` |
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

The repository exists at the URL anticipated by the POM and its HTTPS/SSH SCM metadata. Name,
description, Apache-2.0 license, project URL, SCM and developer identity `yravelo` are present. No
email is published. GitHub Issues is enabled.

The repository is a **private development repository**. That decision is distinct from
publishing binary artifacts to Maven Central and does not promise a future public source repository.

## Maven Central status

Target: Maven Central Publisher Portal using Sonatype's official plugin with
`autoPublish=false`.

Central account: **UNKNOWN — PENDING USER ACTION**. Namespace `io.github.yravelo`: **UNKNOWN**.
Neither can be queried safely without an authenticated Portal session. The owner must sign in with
GitHub identity `yravelo`, confirm the automatically provisioned namespace or add/verify exactly
`io.github.yravelo`, and then generate a Portal user token. Matching the GitHub username alone is
not recorded as proof of verification.

- [Central publication requirements](https://central.sonatype.org/publish/requirements/)
- [Central namespace registration](https://central.sonatype.org/register/namespace/)
- [Official Central Maven plugin](https://central.sonatype.org/publish/publish-portal-maven/)

## Security, signing and GitHub environment

| Control | Status |
| --- | --- |
| GitHub Private Vulnerability Reporting | DEFERRED (non-blocking) — API returned 404 for this private repository; unchanged |
| OpenPGP strategy | PASS — required by Central and isolated in `central-publish` |
| Protected OpenPGP key | EXTERNAL PREREQUISITE — real key was not generated |
| GitHub branch protection/rules | DEFERRED (non-blocking) — unavailable for this private repository on the current plan |
| GitHub environment `maven-central` | PASS — created empty; ID `20189458466` |
| Environment protection | EXTERNAL PREREQUISITE — no rules available/configured for the private repository on the current entitlement |
| `CENTRAL_USERNAME` / `CENTRAL_PASSWORD` | MISSING |
| `GPG_PRIVATE_KEY` / `GPG_PASSPHRASE` | MISSING |
| Tag `v0.1.0` | NOT EXECUTED — creation/push not authorized |
| Remote Build and Compatibility workflows | PASS |
| Benchmarks and Release candidate workflows | NOT EXECUTED |
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

## Phase 16C remote validation

Remote validation is **PASS** for implementation SHA
`7b7c0f6394c8220f1149ef2fb21c718e535522bb`. Build run `32264391877` succeeded on the Java 17,
Spring Boot 3.5.16 and PostgreSQL 15.18 baseline. Compatibility run `32264393355` succeeded in all
10 jobs: Java 21/25, Spring Boot 3.5.0, PostgreSQL 16.14/17.10, the newest supported boundary with
PostgreSQL 18.4, Hibernate 6.6.15/6.6.55 and pgJDBC 42.7.5/42.7.13.

The first remote run exposed two runner-specific gaps without reducing coverage: Java 17 strict
Javadocs required record-constructor parameter tags, and the Ubuntu runner required explicit
installation of `ripgrep` for the documentation/release audit scripts. Both were corrected and
revalidated. Build, Compatibility, Benchmarks and Release candidate are visible and active;
Benchmarks and Release candidate remain manual and were not executed.

## Phase 16D Central and signing preparation

The current Central Publisher Portal flow is documented and audited: sign in using GitHub, confirm
the namespace, generate a Portal user token, sign every deployed POM/JAR with OpenPGP, run Maven
`deploy` to upload a bundle, wait for Portal validation and publish manually. The project uses
`org.sonatype.central:central-publishing-maven-plugin:0.11.0`; `autoPublish=false` deliberately
keeps upload and publication separate. Plugin `validate` completed without credentials or upload.

The release workflow keeps candidate validation secret-free and gives only `central-upload` access
to the `maven-central` environment. Its Actions are pinned to commits, checkout credentials are not
persisted and simultaneous Central uploads are serialized. `setup-java` imports the armored key
from the runner temp directory, removes that file after import and cleans the imported key after
the job. No private key is uploaded as an artifact.

The empty `maven-central` environment exists, but the current private-repository entitlement does
not provide usable environment secrets or protection rules. All four required secret names are
missing at both environment and repository level. The environment must not be treated as a
security boundary until the account supports those controls or an explicitly approved alternative
is adopted.

The repeated local `0.1.0` dry-run is **PASS**: 25 primary artifacts, zero SNAPSHOT dependencies,
no benchmark/example artifacts and an isolated external consumer PASS. No real GPG key, Central
bundle upload, tag or publication was produced.

## Remaining activation sequence

1. Sign in to Central using GitHub `yravelo`; report account status and whether
   `io.github.yravelo` is shown as verified.
2. Generate a Portal user token and a real passphrase-protected OpenPGP key outside the repository;
   distribute only its public key through a Central-supported keyserver.
3. Obtain GitHub environment-secret support for this private repository or explicitly approve a
   different secrets strategy; then configure the four names without exposing values.
4. Resolve the private vulnerability channel independently; it is non-blocking for Phase 16D.
5. Recheck a clean remote candidate, then create and push `v0.1.0` only with authorization.
6. Run the remote candidate workflow; authorize Central upload and Portal publication separately.

No external action above is authorized by this document.
