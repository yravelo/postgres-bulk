# REL0 final release-readiness audit

Audit date: 2026-08-25  
Audited product source: `4e4ec8dde98331d29cc663c0527f193f923f25cf`
Candidate version: `0.1.0`  
Decision: **READY FOR REL1** after EP-01 closure on 2026-08-25

## Scope and boundary

REL0 is a private, read-only release-readiness decision. It reconciles product behavior, public
API, documentation, supply-chain controls, release-candidate structure and the external activation
prerequisites. It does not authorize or start REL1, change repository visibility, create a tag,
execute Benchmarks or Release, upload to Central, or publish anything.

The audited product source is the synchronized `main` commit above. REL0 documentation-only
closure commits do not change runtime code or release coordinates; the final repository commit is
validated again by the normal remote workflows.

## Readiness decision

| Boundary | Result | Evidence / consequence |
| --- | --- | --- |
| Product and public API | PASS | Nine publishable modules, JPA and Spring Data JDBC integrations, multi-schema operations, transaction boundaries and observability are implemented and covered. |
| Local build | PASS | `spotless:check`, `test`, `verify`, `clean verify` and `install` pass through the Maven wrapper. |
| Security technical baseline | COMPLETE | Full policy, secret-history, OSV, SAST, SBOM/license, runner, documentation and signing-identity checks pass. |
| Candidate structure | PASS | Local `0.1.0` staging contains 37 primary files and nine attached SBOMs; isolated JPA/JDBC consumers pass. |
| Reproducibility | PASS | Primary release artifacts are byte-identical across two clean builds; SBOMs compare semantically. |
| Central structural readiness | PASS | POM metadata, sources, Javadocs, checksums, SBOMs and manual-publication configuration are present. |
| REL1 operational entry | READY | EP-01 private vulnerability reporting is `PASS`; the real REL1 preflight passes. REL1 remains not started. |
| REL2 operational entry | NOT EVALUATED | EP-02 offline OpenPGP recovery is `PENDING` and EP-03 Central token is `MISSING`; neither is a REL1 blocker. |

REL0 closed technically while EP-01 was pending. The owner later supplied complete channel
evidence, so the prerequisite update changes the entry verdict to **READY FOR REL1** without
reopening REL0 or starting REL1.

## Repository and identity

| Item | Audited value | State |
| --- | --- | --- |
| Repository | `yravelo/postgres-bulk` | PRIVATE; default branch `main`; Issues enabled |
| Remote | `git@github.com:yravelo/postgres-bulk.git` | PASS |
| License | Apache-2.0 | PASS |
| GitHub description | empty | REL1 activation metadata item; not a technical artifact blocker |
| Maven group | `io.github.yravelo` | PASS |
| Java namespace | `io.ybr.postgresbulk` | PASS; intentionally distinct from Maven group |
| Development version | `0.1.0-SNAPSHOT` | PASS |
| Candidate override | `-Drevision=0.1.0` | PASS |
| Planned tag | `v0.1.0` | NOT CREATED |

The parent POM contains project URL, SCM, license, description and developer identity. No personal
email is published. GitHub metadata activation remains part of REL1 and must not be represented as
already complete.

## Reactor and publication inventory

The parent reactor contains 13 projects: the parent, nine publishable library/starter modules, the
benchmark module and two executable examples. The standalone verification JDBC consumer is outside
the reactor. Only the parent POM and the following nine artifacts belong in a Central deployment:

| Artifact | Public boundary |
| --- | --- |
| `postgres-bulk-core` | API, SPI and neutral metadata |
| `postgres-bulk-pgjdbc` | PostgreSQL COPY execution and temporary-table lookup |
| `postgres-bulk-hibernate` | Hibernate/JPA metadata adapter |
| `postgres-bulk-spring-data` | Spring Data JPA repository fragment and transactions |
| `postgres-bulk-spring-data-jdbc` | Spring Data JDBC metadata and repository adapter |
| `postgres-bulk-spring-boot-autoconfigure` | JPA-oriented Boot composition and observability |
| `postgres-bulk-spring-boot-starter` | JPA-oriented dependency entry point |
| `postgres-bulk-spring-boot-autoconfigure-jdbc` | JDBC-only Boot composition |
| `postgres-bulk-spring-boot-starter-data-jdbc` | JDBC-only dependency entry point |

Benchmarks, examples and verification consumers are explicitly non-publishable. The two starter
JARs are intentionally code-free adoption artifacts and contain explanatory metadata.

## Functional and API audit

The generated public-API baseline is current and its check passes. Strict Javadocs pass with
doclint enabled. The reviewed tests and documentation cover:

- binary-safe COPY encoding and execution, nulls, escaping and failure propagation;
- root aggregate bulk insert and temporary-table materialized lookup;
- transaction participation, rollback, connection ownership and cleanup;
- Hibernate/Spring Data JPA and Spring Data JDBC metadata resolution;
- operation-scoped physical schema/table targets without mutable global tenant state;
- Boot auto-configuration, JPA/JDBC store coexistence and opt-in boundaries;
- metrics/observability that avoid entity values and sensitive identifiers;
- Java 17 baseline, Java 21 support, Java 25 experimental coverage, supported Boot, Hibernate,
  pgJDBC and PostgreSQL compatibility lanes.

README, user guides, examples, architecture documents and API snippets consistently distinguish
JPA from Spring Data JDBC, document generated-ID limitations, transaction behavior, lookup
materialization, multi-schema target semantics and performance caveats. Architecture and ADRs do
not require a REL0 design change. Historical benchmark JSON that retains an old benchmark-only
package label is evidence provenance, not active product or release content.

## Security and supply-chain audit

`./scripts/check-security.sh full` passes the integrated baseline:

- all 16 policy gates and their negative fixtures pass;
- all 108 commits are scanned by Gitleaks with no detected secret;
- OSV resolves 138/138 exact production package versions, with zero `BLOCK` and five accepted
  `WARN` records expiring 2026-10-24;
- SpotBugs/FindSecBugs covers seven product modules, with six exact reviewed exclusions and zero
  untriaged findings;
- CycloneDX generates nine per-artifact SBOMs plus the aggregate; the license audit covers 55
  external production components with zero unknown and zero `BLOCK`;
- the repository-scoped runner health and Testcontainers cleanup boundaries pass;
- the public OpenPGP identity matches fingerprint
  `11545CD242C9575DF408AC08F83D364143C798A3` and expires 2028-08-23.

No project secret, token, private signing key, local Maven settings or runner credential is part of
the repository or candidate. Git history is suitable for the technical decision, but a fresh
full-history privacy audit remains a mandatory REL1 activation step immediately before opening the
repository.

## Candidate, checksums and reproducibility

`./scripts/release-dry-run.sh 0.1.0` passes without signing or uploading. Its inventory binds the
candidate to the audited product source and records `tag_created=false`.

| Candidate item | Count |
| --- | ---: |
| Parent and module POMs | 10 |
| Binary JARs | 9 |
| Sources JARs | 9 |
| Javadocs JARs | 9 |
| Primary Central files | 37 |
| Attached CycloneDX JSON SBOMs | 9 |
| Total candidate payload files | 46 |
| Primary SHA-256 inventory lines | 37 |
| SBOM SHA-256 inventory lines | 9 |

The candidate contains no SNAPSHOT dependency, benchmark/example artifact, test class, secret,
local path or private metadata. `./scripts/compare-release-builds.sh 0.1.0` confirms byte-identical
primary payloads and semantic SBOM equivalence. Isolated JPA and JDBC consumers resolve the staged
coordinates and pass.

A previous SEC5 owner-controlled real-key dry-run verified all 46 Central payload signatures and
three evidence signatures with the approved fingerprint. REL0 deliberately does not repeat that
operation: a current real-key run requires owner interaction with the protected offline-capable
identity. A test key is not acceptable evidence. Repeating the signed dry-run is an explicit REL2
owner step after EP-02 passes and before any upload.

## Central requirements audit

The candidate satisfies the repository-controlled structural requirements for Maven Central:
resolved POM metadata, license/SCM/developer information, sources, Javadocs, checksums and an
approved OpenPGP identity. The Central plugin is `0.11.0` with `autoPublish=false`, preserving the
separation between upload and manual publication. Published components are immutable, so candidate
verification must precede activation.

The owner-recorded namespace `io.github.yravelo` is `VERIFIED`. REL0 cannot independently log into
the Portal and does not claim that it did. The real nominal Portal token does not exist; this is
EP-03 and blocks REL2 upload, not REL1 repository activation.

Official references:

- [Central publishing requirements](https://central.sonatype.org/publish/requirements/)
- [Publisher Portal Maven plugin](https://central.sonatype.org/publish/publish-portal-maven/)
- [Namespace verification](https://central.sonatype.org/register/namespace/)
- [Component immutability](https://central.sonatype.org/publish/requirements/immutability/)
- [OpenPGP signing](https://central.sonatype.org/publish/requirements/gpg/)

## External prerequisite matrix

| ID | Prerequisite | State | Blocks | REL0 treatment |
| --- | --- | --- | --- | --- |
| EP-01 | Approved and tested private vulnerability reporting channel | `PASS` | none | Proton Mail; owner control/MFA/recovery and external delivery/reply verified 2026-08-25 |
| EP-02 | Offline OpenPGP backup/revocation recovery verification | `PENDING` | signed tag, Central upload and publication | REL2 prerequisite; not a REL1 blocker |
| EP-03 | Nominal Central Publisher Portal token | `MISSING` | Central upload | REL2 prerequisite; not a REL1 blocker |

EP-01 is retained as resolved owner evidence. EP-02 and EP-03 remain owner-operated external
actions; REL0 neither satisfies nor weakens their guards.

## Residual-risk review

All nine registered residual risks remain accurate and non-stale: single-maintainer concentration,
persistent Docker runner exposure, mutable PostgreSQL test tags, five accepted OSV findings, six
reviewed SAST exclusions, exact license reviews, public-repository governance/PR isolation,
absence of SLSA/Sigstore and resource-exhaustion limits. RR-07 reinforces the REL1 entry boundary;
the others remain accepted or deferred under their recorded review dates. No risk entry was
silently deleted or reclassified by REL0.

## Release guards and preflights

The technical release preflight passes only from clean synchronized `main`. The REL1 preflight now
passes because EP-01 is verified and would fail closed if that state regressed. Release automation
remains candidate-only, owner-dispatched, secret-free and unable to publish. `autoPublish=false`
is a final defense, not authorization.

## REL0 definition of done

- [x] Product/API, documentation, compatibility and examples inventoried.
- [x] Full security baseline passes locally.
- [x] Unsigned candidate dry-run and isolated consumers pass.
- [x] Reproducibility and checksum inventories pass.
- [x] Central structure and namespace evidence reconciled without credentials.
- [x] External prerequisites and residual risks remain fail-closed.
- [x] Clean-room Maven-wrapper verification passes.
- [x] Final documentation closure commit has remote Build PASS, Compatibility 11/11 PASS and
  manually dispatched Security PASS.

Closure commit `bc288f27a01aa427bb5fd78f38997698b2c4e6d6` passed Build
[`32850719665`](https://github.com/yravelo/postgres-bulk/actions/runs/32850719665), Compatibility
[`32850719735`](https://github.com/yravelo/postgres-bulk/actions/runs/32850719735) in all 11 lanes,
and manually dispatched Security
[`32850787710`](https://github.com/yravelo/postgres-bulk/actions/runs/32850787710). This completes
REL0 but does not authorize crossing an activation boundary.

## Mandatory boundary state

```text
REL0 status: DONE
technical release readiness: PASS
technical release preflight: PASS
Security technical baseline: COMPLETE
Security & Supply Chain roadmap: COMPLETE
SEC6 status: DONE
EP-01 private reporting: PASS
EP-02 OpenPGP recovery: PENDING
EP-03 Central token: PENDING
REL1 preflight: PASS
REL1 entry gate: READY
Build: PASS
Compatibility: 11/11
Security: PASS
repository public: no
GitHub PVR enabled: no
tag created: no
Central upload: no
publication activated: no
REL1 started: no
```

The PASS workflow values above describe both the audited product source and its documentation-only
REL0 closure. Later documentation-only evidence recording remains subject to the normal remote
Build and Compatibility gates plus a manually dispatched Security run.

## Next phase recommendation

After REL0 and EP-01 closure, the only recommended next phase is **REL1 — Open Source Repository
Activation**. It is not started. A fresh full-history privacy/public-readiness audit and public-PR
CI isolation must succeed inside REL1 before repository visibility can change. EP-02 and EP-03
remain later REL2 prerequisites.
