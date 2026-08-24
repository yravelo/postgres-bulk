# SBOM and dependency/license integrity

SEC4 makes the dependency and license inventory reproducible evidence for the `0.1.0` release
candidate. Maven remains the source of truth. Generated evidence is ephemeral under `target/`, is
not committed and does not authorize signing, upload or publication.

## Selected tooling and format

The release profile pins `org.cyclonedx:cyclonedx-maven-plugin:2.9.3`. The plugin supports Java 17,
Maven 3.9.16 and CycloneDX 1.6 in JSON or XML; this project deliberately emits only CycloneDX 1.6
JSON. JSON is the canonical machine-readable format and uses the recognized `*.cdx.json` suffix.
Document serial numbers are disabled and Maven's fixed `project.build.outputTimestamp` controls the
remaining generated timestamp. Semantic comparison ignores only document-level variable metadata;
it compares component identities, purls, versions, types, licenses, hashes and dependency edges.

- [CycloneDX Maven plugin schema support](https://cyclonedx.github.io/cyclonedx-maven-plugin/)
- [CycloneDX specification overview](https://cyclonedx.org/specification/overview/)
- [CycloneDX 1.6 JSON schema](https://cyclonedx.org/schema/bom-1.6.schema.json)

## Exact publishable inventory

The supporting `postgres-bulk-parent` POM is deployed, but the nine public library artifacts are:

1. `postgres-bulk-core`
2. `postgres-bulk-pgjdbc`
3. `postgres-bulk-hibernate`
4. `postgres-bulk-spring-data`
5. `postgres-bulk-spring-data-jdbc`
6. `postgres-bulk-spring-boot-autoconfigure`
7. `postgres-bulk-spring-boot-starter`
8. `postgres-bulk-spring-boot-autoconfigure-jdbc`
9. `postgres-bulk-spring-boot-starter-data-jdbc`

`postgres-bulk-benchmarks`, `examples/spring-boot-basic` and
`examples/spring-boot-data-jdbc` are non-publishable. The machine-readable inventory lives once in
`config/security/sbom-policy.json`; Maven, generation, staging and reproducibility checks consume
or enforce the same list.

## Generation, scope and identity

Each public JAR produces `<artifactId>-<version>.cdx.json`. The public reactor additionally produces
`postgres-bulk-<version>-aggregate.cdx.json`. Release generation requires a stable SemVer and
therefore uses `io.github.yravelo:*:0.1.0`, never `0.1.0-SNAPSHOT`. Internal components use Maven
purls such as `pkg:maven/io.github.yravelo/postgres-bulk-core@0.1.0?type=jar`; `bom-ref` must equal
the purl and edges must reference existing nodes. The aggregate root is the parent POM with
`?type=pom`. The Java namespace `io.ybr.postgresbulk` is valid source identity but is rejected as a
Maven group.

Compile and runtime dependencies are included. Test, provided and system scopes are excluded.
Benchmark and example modules are excluded from both per-artifact and aggregate output. CycloneDX
retains a conservative resolution DAG, including ten version-pinned components that Maven's
mediated consumer tree omits: Spring JDBC in the JPA adapter and nine standard Boot logging/base
components in the JDBC starter. Those exact differences are reviewed in `sbom-policy.json`; any
addition, removal or version drift fails closed. Maven nodes must otherwise match exactly and every
Maven dependency edge must exist in the SBOM.

The JDBC-only starter has a stricter negative gate: Hibernate, JPA, the JPA adapters, Actuator and
Testcontainers must not appear. The JPA and JDBC paths are also resolved as isolated staged
consumers during the release dry-run.

## Canonical integrity auditor

`scripts/generate-sbom.sh 0.1.0` performs a clean release-profile install, resolves each public
artifact independently, generates the aggregate and invokes `scripts/check-sbom.py`. The auditor
fails closed on invalid JSON or policy, missing/unexpected files or components, broken edges,
version/coordinate/purl drift, SNAPSHOTs, test/build/non-publishable components, JPA leakage into
the JDBC starter, unknown or blocked licenses, stale reviews, local paths, private workstation
metadata and secret-like content. The CycloneDX plugin validates each document against its official
schema while generating it.

The auditor reconciles all consumer-reachable external name/version pairs with the existing SEC2
OSV inventory. OSV remains broader overall because it separately inventories tests, examples,
benchmarks and build tools; only its `consumer_reachable` production subset must equal the aggregate
SBOM. Build plugins are loaded from `config/security/build-tools.json` and are forbidden as runtime
components. Dependabot manages updates, OSV is the vulnerability gate, CycloneDX is inventory
evidence, and SpotBugs/FindSecBugs remains a distinct source/bytecode gate.

Seven focused fixtures cover a valid document and failures for a missing component, test
dependency, SNAPSHOT, unknown production license, wrong internal group and absolute path:

```bash
python3 scripts/test-sbom-auditor.py
```

## License policy and current baseline

`config/security/license-policy.json` is the canonical production license policy. Permissive
SPDX identifiers pass. Strong copyleft identifiers block. An unknown compile/runtime license
blocks. Weak-copyleft or ambiguous metadata requires an exact coordinate, full license set, scope,
rationale, owner, review date and expiry. The current review expires on 2027-02-24 and covers six
multiple-license components plus exact single-license exceptions for Hibernate ORM and AspectJ
Weaver. This is an engineering compliance gate and not an absolute legal opinion.

The real `0.1.0` baseline contains 55 external production components and these eight license IDs:
`Apache-2.0`, `BSD-2-Clause`, `BSD-3-Clause`, `CC0-1.0`, `EPL-2.0`,
`GPL-2.0-with-classpath-exception`, `LGPL-2.1-only` and `MIT`. Results: zero unknown, eight
review-required records and zero blocked/unresolved findings. No third-party code is shaded or
bundled into the thin library JARs and no mandatory third-party attribution text was found for the
project distribution, so an empty `NOTICE` is not created. Reassess this whenever bundling or the
license inventory changes. Apache-2.0 section 4(d) only carries forward applicable NOTICE content;
it does not justify a ritual empty file.

- [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [Apache guidance for LICENSE and NOTICE contents](https://infra.apache.org/licensing-howto.html)

## Baseline evidence

The local `0.1.0` baseline is:

| Artifact | Components | Dependency edges | Approximate JSON size |
| --- | ---: | ---: | ---: |
| `postgres-bulk-core` | 0 | 0 | 3 KiB |
| `postgres-bulk-pgjdbc` | 3 | 3 | 10 KiB |
| `postgres-bulk-hibernate` | 18 | 21 | 49 KiB |
| `postgres-bulk-spring-data` | 39 | 65 | 100 KiB |
| `postgres-bulk-spring-data-jdbc` | 18 | 40 | 46 KiB |
| `postgres-bulk-spring-boot-autoconfigure` | 43 | 75 | 110 KiB |
| `postgres-bulk-spring-boot-starter` | 57 | 101 | 146 KiB |
| `postgres-bulk-spring-boot-autoconfigure-jdbc` | 21 | 45 | 54 KiB |
| `postgres-bulk-spring-boot-starter-data-jdbc` | 34 | 67 | 88 KiB |
| aggregate | 64 | 134 | 164 KiB |

The ten SBOM documents total approximately 770 KiB. The aggregate contains nine internal release
components and 55 external components. Generated `audit-summary.json`, dependency trees and
`production-licenses.txt` remain under `target/`.

Dependency components carry the hashes supplied by the plugin, including SHA-256; semantic repeat
comparison includes them. Hashes inside an SBOM are not a substitute for artifact evidence. The
staging audit emits separate SHA-256 reports for 37 primary Maven artifacts and nine attached SBOM
files. The parent POM plus each public module's POM/JAR/sources/Javadocs remain the primary count;
SBOM JSON is security evidence.

## Build, release and retention policy

Build generates and audits the baseline once after OSV and static analysis. Compatibility does not
repeat it. The release candidate's secret-free dry-run stages the nine per-artifact SBOMs, creates
the separate aggregate, runs the same license/integrity gate and validates isolated consumers
before any future upload. `scripts/compare-release-builds.sh` performs two clean primary builds and
two clean SBOM generations; primary artifacts compare byte-for-byte and SBOMs compare semantically.
Warm local SEC4 runs measured approximately 45–57 seconds for one complete generation and audit
(release-profile install, nine independent dependency trees, aggregate and policy checks). This is
why Build pays the roughly one-minute cost once and Compatibility does not duplicate it.

Remote closure is PASS. On 2026-08-24, repository-scoped self-hosted Build `32774191694` generated
and audited the canonical nine per-artifact SBOMs plus aggregate as part of its complete successful
run for `fbb1105c83c3a75312604ae6c9bb5f14b74a782c`. Compatibility `32774191674` passed 11/11 on the
same SHA. Dedicated runner labels and the owner+same-repository PR guard remove automatic execution
of untrusted fork code; no repository secret, signing key, provenance, upload or publication was
introduced. SEC4 is therefore `DONE`; SEC5 remains `NOT STARTED`.

Future policy is to deploy each per-artifact SBOM with Maven classifier `cyclonedx` and attach the
aggregate plus audit summary/checksums as GitHub Release evidence. That attachment decision is
documented but no GitHub release upload is activated in SEC4. OpenPGP `.asc` signatures, provenance
and attestations belong to SEC5. Maven Central's current primary publication requirements still
include the POM, binary, sources, Javadocs and signatures; SBOM evidence remains separately counted.

- [Maven Central publication requirements](https://central.sonatype.org/publish/requirements/)

Reproduce locally from the repository root:

```bash
./scripts/check-vulnerabilities.sh
./scripts/generate-sbom.sh 0.1.0
./scripts/audit-production-licenses.sh 0.1.0
./scripts/compare-release-builds.sh 0.1.0
```

Each output directory is single-use. Move or remove prior generated `target/` evidence explicitly
before repeating a run. No command above creates a tag, signs, uploads or publishes anything.
