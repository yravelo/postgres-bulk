# Release readiness for 0.1.0

## Verdict

The final project identity, private GitHub repository, Central namespace and secure-secrets model
are approved. Phase 16 is technically ready for credential activation, but `0.1.0` is **not ready
for public publication**: the real Portal token, signing material, four secret values, tag, upload
and Portal publication remain intentionally withheld. No release or benchmark workflow ran.

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

Namespace `io.github.yravelo`: **PASS — VERIFIED** in Maven Central Portal, confirmed by owner
`yravelo` on 2026-08-19. This owner-confirmed Portal state is the evidence of record; no screenshot,
session data or token is stored. The real Portal user token has not been generated.

- [Central publication requirements](https://central.sonatype.org/publish/requirements/)
- [Central namespace registration](https://central.sonatype.org/register/namespace/)
- [Official Central Maven plugin](https://central.sonatype.org/publish/publish-portal-maven/)

## Security, signing and GitHub environment

| Control | Status |
| --- | --- |
| GitHub Private Vulnerability Reporting | DEFERRED (non-blocking) — API returned 404 for this private repository; unchanged |
| OSV dependency gate | PASS — 2.5.1 checksum-pinned; 132/132 exact package versions, zero BLOCK |
| Java SAST gate | PASS — SpotBugs 4.10.4 + FindSecBugs 1.14.0; 7 modules, 6/6 initial findings triaged, 0 untriaged |
| Accepted dependency risks | PASS — five exact WARN records expire 2026-10-24 |
| Dependabot update policy | PASS — five weekly Maven/Actions lanes, majors manual, no auto-merge; Compose manual |
| Dependabot alerts/security updates | ENABLED — private visibility unchanged; security updates enabled and unpaused |
| GitHub dependency graph | ENABLED — bodyless endpoint check passed; no SBOM downloaded or committed |
| OpenPGP strategy | PASS — required by Central and isolated in `central-publish` |
| Protected OpenPGP key | EXTERNAL PREREQUISITE — real key was not generated |
| GitHub branch protection/rules | DEFERRED (non-blocking) — unavailable for this private repository on the current plan |
| Repository Secrets model | PASS — explicitly selected for the current private, single-maintainer threat model |
| GitHub environment `maven-central` | DEFERRED (non-blocking) — retained as an inert marker; not referenced by release workflow |
| Environment protection | DEFERRED (non-blocking) — unavailable for this private repository on the current entitlement |
| `CENTRAL_USERNAME` / `CENTRAL_PASSWORD` | MISSING |
| `GPG_PRIVATE_KEY` / `GPG_PASSPHRASE` | MISSING |
| Tag `v0.1.0` | NOT EXECUTED — creation/push not authorized |
| Remote Build and Compatibility workflows | PASS |
| Benchmarks and Release candidate workflows | NOT EXECUTED |
| Central upload/publication | NOT EXECUTED |

Future values belong in Actions Repository Secrets, never in Git, documentation, chat or logs.
Repository Secrets are not an approval boundary: GitHub reads them when a run is queued and any
trusted workflow can reference them. The approved compensating controls are owner-only dispatch
from `main`, strict stable-SemVer/full-SHA/confirmation validation, candidate SHA membership in
`origin/main`, exact tag-to-candidate verification, candidate dependency, `contents: read`, pinned
Actions, upload concurrency and `autoPublish=false`.

## License, supply chain and reproducibility

The root `LICENSE`, inherited POM metadata and documentation use Apache-2.0. The production license
audit must remain free of unknown metadata. Each Java module attaches binary, sources and strict
Javadocs; the code-free starter uses explanatory archives. Staging emits SHA-256, and two clean
release builds compare the parent POM plus four files for each of the six modules: 25 primary
artifacts total. SBOM and provenance remain deferred non-blocking decisions.

SEC2 adds a fail-closed dependency gate to Build and the release candidate before any future
upload. The applicable pgJDBC HIGH finding on 42.7.11 was remediated by selecting supported
42.7.13; the post-fix scan has no BLOCK finding. Five moderate findings are explicitly triaged with
scope, reachability, owner and expiry in the accepted-risk register. Dependency inventory and raw
OSV output remain generated under `target/security/`, not committed artifacts. OWASP
Dependency-Check remains optional because exact OSV coverage is complete; Snyk is not connected.
Implementation commit `46e7c1606a51574b0aeb4f86e37b93550a58604f` passed Build
`32752820439` and all 11 jobs in Compatibility `32752820231`. Benchmarks and Release remained
unexecuted.

SEC3 liga SpotBugs/FindSecBugs a `verify` para los siete módulos con bytecode productivo. El scan
inicial sin filtros encontró tres sinks SQL de FindSecBugs, dos exposiciones de listas y un
constructor de infraestructura; los seis fueron revisados como false positive/no aplicable contra
quoting, inmutabilidad y lifecycle reales. Seis exclusiones Bug/Class/Method con owner y revisión
2027-02-24 dejan cero findings sin triage. Build y Release conservan el gate y validan activación
FindSecBugs/reportes; Compatibility omite scans duplicados. No cambió source ni API productiva.
El commit de baseline `b0313efb557bd26c54a4954c5c398355b1c98b01` pasó Build `32758573085`
y los 11 jobs de Compatibility `32758573080`; Benchmarks y Release no se ejecutaron.

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

## Phase 16E verified namespace and secure secrets boundary

The owner-confirmed Central namespace `io.github.yravelo` is `VERIFIED`. Current GitHub
documentation confirms Repository Secrets are available to Actions in private repositories,
repository/environment secrets can be administered by the applicable repository roles, repository
secrets are read when a run is queued, Actions secrets are withheld from normal fork PRs and
Dependabot-triggered workflows, and log redaction is not guaranteed for transformed values.

The chosen model is Repository Secrets plus code-level controls appropriate to the current single
maintainer. A malicious workflow reaching trusted `main` remains the main residual risk, because a
repository secret can be referenced by other workflows; adding collaborators therefore triggers a
mandatory reevaluation. The workflow is `workflow_dispatch` only and accepts a stable version,
full commit SHA, boolean publish intent and literal confirmation. Candidate validates the SHA is
on `origin/main`; upload depends on candidate and checks `v<version>` points to the identical SHA.
Only `central-upload` contains the four `secrets.*` references.

`actions/setup-java` imports the armored key through temporary runner material and removes the
imported key in its post-step. Maven settings is generated in `RUNNER_TEMP`, used explicitly and
removed even after failure. No upload-job artifacts contain the key, keyring or settings. The
Central plugin retains `autoPublish=false`, so successful upload still requires a separate manual
Portal publication.

- [GitHub secret types](https://docs.github.com/en/code-security/reference/secret-security/secret-types)
- [Using secrets in Actions](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)
- [Secrets reference and redaction limits](https://docs.github.com/en/actions/reference/security/secrets)
- [GitHub Actions secure use](https://docs.github.com/en/actions/reference/security/secure-use)
- [Deployment environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments)
- [setup-java GPG lifecycle](https://github.com/actions/setup-java/blob/main/docs/advanced-usage.md#gpg)

The environment object ID `20189458466` remains remotely present only as an inert marker/deployment
history placeholder. Removing `environment: maven-central` from the job avoids representing it as
a security gate while the plan supplies neither usable environment secrets nor protection rules.

Remote validation for hardening SHA `457681c7be28222fa2cd5b715f613da8523abc5a` is **PASS**: Build
run `32274812469` succeeded and Compatibility run `32274812453` succeeded in all 10 matrix jobs.
The PostgreSQL 16.14 job failed on its first attempt and passed unchanged when only failed jobs were
retried; no workflow/code adjustment or matrix reduction was made. Release candidate and
Benchmarks were not executed.

## Remaining activation sequence

1. Generate a Portal user token and a real passphrase-protected OpenPGP key outside the repository;
   distribute only its public key through a Central-supported keyserver.
2. Configure the four approved Repository Secret names without exposing their values.
3. Resolve the private vulnerability channel independently; it remains non-blocking.
4. Recheck an authorized candidate SHA from `main`, then create and push `v0.1.0` only with
   separate authorization and make it point to that exact SHA.
5. Dispatch from `main` with the full SHA and explicit intent; authorize Central upload and Portal
   publication separately.

No external action above is authorized by this document.
