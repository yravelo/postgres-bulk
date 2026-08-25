# Release readiness for 0.1.0

## Verdict

The final project identity, private GitHub repository, Central namespace and local OpenPGP release
identity are approved. SEC5 signed dry-run and verification are PASS, but `0.1.0` is **not ready for
public publication**: offline key-backup verification, the Portal token, tag, upload and Portal
publication remain intentionally withheld, and SEC6 has no approved/tested external private
reporting channel. No Release or benchmark workflow ran.

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
| `postgres-bulk-spring-data-jdbc` | JAR | Spring Data JDBC repository adapter | binary, sources, Javadocs, POM | yes |
| `postgres-bulk-spring-boot-autoconfigure` | JAR | Boot composition and observability | binary, sources, Javadocs, POM | yes |
| `postgres-bulk-spring-boot-starter` | JAR | Dependency-only adoption entry point | binary, sources, Javadocs, POM | yes |
| `postgres-bulk-spring-boot-autoconfigure-jdbc` | JAR | JDBC-only Boot composition | binary, sources, Javadocs, POM | yes |
| `postgres-bulk-spring-boot-starter-data-jdbc` | JAR | JDBC-only dependency entry point | binary, sources, Javadocs, POM | yes |

The `postgres-bulk-parent` POM is supporting publication metadata. Benchmarks and both standalone
examples are non-published consumers and must not appear in staging.

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
| Current private vulnerability channel | PENDING — non-blocking for SEC7/SEC8/REL0; blocks REL1 and a supported release |
| GitHub Private Vulnerability Reporting | DEFERRED (non-blocking as a feature) — public-repository feature; current private API returns 404 |
| Vulnerability/incident governance | PASS — supported versions, triage, severity, GHSA/CVE/disclosure, compromise runbook and templates implemented |
| OSV dependency gate | PASS — 2.5.1 checksum-pinned; 138/138 exact package versions, zero BLOCK |
| Java SAST gate | PASS — SpotBugs 4.10.4 + FindSecBugs 1.14.0; 7 modules, 6/6 initial findings triaged, 0 untriaged |
| CycloneDX/license gate | PASS — 2.9.3, spec 1.6 JSON; 9 per-artifact + aggregate, 55 external production components, 0 unknown/0 BLOCK |
| Accepted dependency risks | PASS — five exact WARN records expire 2026-10-24 |
| Dependabot update policy | PASS — five weekly Maven/Actions lanes, majors manual, no auto-merge; Compose manual |
| Dependabot alerts/security updates | ENABLED — private visibility unchanged; security updates enabled and unpaused |
| GitHub dependency graph | ENABLED — bodyless endpoint check passed; no SBOM downloaded or committed |
| OpenPGP strategy | PASS — local `local-signing` profile, gpg-agent/pinentry, SHA-512 and exact fingerprint |
| Protected OpenPGP key | PASS — RSA-3072 release identity; expires 2028-08-23; private material outside repo/runner |
| GitHub branch protection/rules | DEFERRED (non-blocking) — unavailable for this private repository on the current plan |
| Repository Secrets model | SUPERSEDED — signing is local; Release references zero repository secrets |
| Trusted self-hosted Build/Compatibility/Security runner | PASS — repository-scoped, non-root, dedicated labels, owner+same-repo PR guard for PR workflows; 11/11 compatibility lanes |
| Continuous Security workflow | IMPLEMENTED — weekly UTC plus manual dispatch, full-history/fresh gates, read-only and zero secrets; remote closure pending |
| Expiry/drift and runner-health gates | PASS locally — policy fixtures, module/POM/workflow/tool drift, Docker/PostgreSQL smoke and Testcontainers residue boundary |
| GitHub environment `maven-central` | DEFERRED (non-blocking) — retained as an inert marker; not referenced by release workflow |
| Environment protection | DEFERRED (non-blocking) — unavailable for this private repository on the current entitlement |
| `CENTRAL_USERNAME` / `CENTRAL_PASSWORD` | MISSING |
| `GPG_PRIVATE_KEY` / `GPG_PASSPHRASE` | NOT USED — must not be created for the local strategy |
| Tag `v0.1.0` | NOT EXECUTED — creation/push not authorized |
| Remote Build and Compatibility workflows | PASS |
| Benchmarks and Release candidate workflows | NOT EXECUTED |
| Central upload/publication | NOT EXECUTED |

The Release workflow is candidate-only, owner-dispatched and secret-free. The private signing key
and passphrase must never become Actions secrets or runner state. Central credentials remain absent;
their eventual local handling and any upload require a separately reviewed activation.

## License, supply chain and reproducibility

The root `LICENSE`, inherited POM metadata and documentation use Apache-2.0. The canonical
CycloneDX/license audit covers 55 external production components, eight represented SPDX license
IDs, six exact multiple-license reviews, two exact weak-copyleft exceptions and zero unknown or
blocked findings. Each Java module attaches binary, sources and strict Javadocs; code-free starters
use explanatory archives. Staging emits separate SHA-256 evidence for the parent POM plus four
files for each of the nine modules (37 primary artifacts) and nine attached SBOM JSON files. The
aggregate SBOM remains separate signed release security evidence. SEC5 binds all files, checksums,
the aggregate and the exact source commit in `release-inventory.json`.

SEC4 pins CycloneDX Maven plugin 2.9.3 and emits spec 1.6 JSON. Per-artifact identities, purls,
versions, hashes, licenses and dependency graphs are checked against Maven and the
consumer-reachable SEC2 OSV inventory; test, benchmark, example and build-tool contamination fails.
Two clean generations compare semantically. Generated evidence remains under `target/` and is not
committed. See [SBOM and dependency/license integrity](../security/sbom-and-license-integrity.md).

SEC4 local validation, release staging and reproducibility are PASS. A repository-scoped trusted
self-hosted runner removed the GitHub-hosted billing dependency without changing plan, visibility
or coverage. For `fbb1105c83c3a75312604ae6c9bb5f14b74a782c`, Build `32774191694` passed all security,
reactor, SBOM/license, consumer and documentation steps on the dedicated runner; Compatibility
`32774191674` passed all 11 lanes. The earlier pre-step billing rejections remain historical
evidence, not an open SEC4 blocker. Benchmarks and Release were not executed. SEC4 and SEC5 are
`DONE`; SEC6 policy/governance is implemented but remains `PARTIALLY DONE` until its private
reporting channel is configured and tested. SEC7 continuous validation is implemented; its channel
exception is explicitly non-blocking for technical closure and blocking for REL1.

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

SEC5 superseded the Phase 16D remote-signing design. The Release workflow now performs only
secret-free candidate validation; it has no `central-upload` job, publishing input, GPG import or
Central credentials. Its Actions remain pinned and checkout credentials are not persisted.

The empty `maven-central` environment exists, but the current private-repository entitlement does
not provide usable environment secrets or protection rules. It remains inert and is not a signing
boundary. No signing Repository Secrets exist or should be created under the SEC5 local strategy.

The repeated local `0.1.0` dry-run is **PASS**: 25 primary artifacts, zero SNAPSHOT dependencies,
no benchmark/example artifacts and an isolated external consumer PASS. That historical dry-run was
unsigned; SEC5 adds the separately verified real signed dry-run. No Central bundle upload, tag or
publication was produced.

## Phase 16E verified namespace and secure secrets boundary

The owner-confirmed Central namespace `io.github.yravelo` is `VERIFIED`. Current GitHub
documentation confirms Repository Secrets are available to Actions in private repositories,
repository/environment secrets can be administered by the applicable repository roles, repository
secrets are read when a run is queued, Actions secrets are withheld from normal fork PRs and
Dependabot-triggered workflows, and log redaction is not guaranteed for transformed values.

SEC5 chooses local signing instead of Repository Secrets. A malicious workflow or build plugin
therefore cannot read a key from GitHub, and the persistent self-hosted runner never holds signing
material. The workflow is `workflow_dispatch` only and accepts a stable version, full commit SHA
and literal candidate confirmation. It validates that SHA belongs to `origin/main` and performs no
upload.

No workflow imports an armored key or generates publishing settings. The Central plugin retains
`autoPublish=false`, but SEC5 never activates it; any future local upload and later Portal
publication remain separately authorized actions.

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

## SEC5 signing and provenance closure

The dedicated RSA-3072 release identity has fingerprint
`11545CD242C9575DF408AC08F83D364143C798A3`, expires 2028-08-23 and is protected by a passphrase
entered through pinentry. Its private key and GnuPG-generated revocation certificate remain outside
Git and all runners; the public export is tracked and sent to a Central-supported keyserver.

The local gate builds from clean synchronized `main`, compares unsigned and signed SHA-256 for all
46 Central payloads, verifies 46 detached payload signatures and three detached evidence
signatures, and binds the exact source commit in `release-inventory.json`. Valid, missing,
wrong-signer, tampered, bad-checksum, unexpected, SNAPSHOT and benchmark fixtures are covered.
The aggregate SBOM remains evidence rather than a deployed classifier. No SHA-1 signature passes.

GitHub artifact attestations remain disabled because private repositories require Enterprise Cloud
under the current GitHub terms. No SLSA level is claimed and Sigstore is not enabled. Exact commit,
reproducible payloads, SBOMs, SHA-256 inventory and approved OpenPGP signatures form the documented
minimum provenance baseline. See
[release signing, inventory and provenance](../security/release-signing-and-provenance.md).

## SEC6 response and governance status

Supported versions, pre-release handling, intake states, severity/applicability, GHSA/CVE and
coordinated disclosure policy, patch/emergency release rules, exception governance, sensitive-path
ownership, Dependabot/public-PR boundaries and incident procedures are documented. CODEOWNERS is
preparatory and does not claim enforcement; read-only APIs still return 403 for rulesets/protection.
Issues show explicit guidance not to disclose vulnerabilities publicly.

The repository remains private and GitHub PVR/repository-advisory endpoints return 404. No security
email was inferred from Git identity or invented. Until the owner approves a dedicated channel and
passes benign inbound, reply, access and recovery tests, SEC6 remains `PARTIALLY DONE` and the first
supported release/REL1 is blocked. SEC7, SEC8 and REL0 technical work may proceed. See
[vulnerability response and governance](../security/vulnerability-response-and-governance.md) and
the [incident response runbook](../security/incident-response-runbook.md).

## Remaining activation sequence

1. Copy the protected key backup and revocation material to separate offline media and verify
   recovery; never place them on a runner or in Actions secrets.
2. Generate a Portal user token only when a local upload is separately authorized.
3. Approve a private vulnerability channel, verify inbound delivery/reply/owner access/recovery,
   publish the exact route in `SECURITY.md` and mark SEC6 DONE.
4. Recheck an authorized candidate SHA from `main`, then create and push `v0.1.0` only with
   separate authorization and make it point to that exact SHA.
5. Reproduce and verify the signed candidate locally; authorize Central upload and Portal
   publication separately. The candidate workflow does not upload.

SEC7 adds a separate technical release preflight that can pass before step 3 and a REL1 preflight
that must fail until step 3 is complete. See
[continuous security validation](../security/continuous-security-validation.md).

No external action above is authorized by this document.
