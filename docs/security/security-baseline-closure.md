# Security baseline technical closure

**SEC8 verdict:** `DONE` on 2026-08-25 after local adversarial closure and final remote
Build, Compatibility and Security validation. The Security & Supply Chain technical roadmap is
`COMPLETE`. EP-01 subsequently closed on 2026-08-25 with an owner-authorized, externally tested
private reporting channel, so SEC6 and the full SEC0–SEC8 roadmap are now `DONE` without reopening
the SEC8 technical audit.

The repository remains private. This closure does not authorize REL0, REL1, a tag, a Release
workflow run, OpenPGP use with the real private key, Central upload or publication.

## Final control inventory

| Control | Threat | Implementation | Positive evidence | Negative evidence | CI placement | Release relevance | Limitation |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Secret scanning | committed credential/history leakage | `check-secrets.sh`, Gitleaks 8.30.1 with verified binary | current and full-history scans | safe synthetic GitHub-token-shaped input is detected; corrupt download fails | Build fast; Security/release history | mandatory before candidate/REL1 | pattern detection cannot recognize every secret |
| Workflow policy | Action, trigger, permission, input or runner bypass | deterministic YAML auditor and SHA allow-list | all five real workflows pass | mutable/unlisted Action, write permission, secret, unexpected trigger/workflow and unsafe checkout fail | Build, Security, release candidate | prevents hidden publication/trust bypass | approved Action SHA still depends on upstream review |
| Runner trust | untrusted PR on Docker-capable persistent host | hosted-only jobs and zero canonical runner registrations | fork, Dependabot, external, owner PR and main select hosted | any self-hosted selector, actor gate or secret reference fails | Build and Compatibility | runner never signs or publishes | GitHub-hosted execution depends on the Actions platform |
| Dependency SCA | vulnerable, unscanned or confused component | resolved Maven inventory, OSV 2.5.1 and accepted-risk registry | real 138/138 scan, zero BLOCK | untriaged, expired, stale, wrong-scope, incomplete and malformed result fail | Build, Security, candidate | fresh result required | advisory data and reachability judgment can lag |
| Tool/module drift | new POM/tool/version escaping coverage | continuous and baseline machine policies | 14 POMs, reactor and tool versions reconcile | new POM/module and version drift fail | fast/full/release | inventory must be complete | policy still requires maintainer review |
| Java SAST | injection and bytecode security defects | SpotBugs 4.10.4 + FindSecBugs 1.14.0 | seven reports, zero untriaged findings | new finding, analyzer/missing class, expired/stale exclusion fail | Build/Security/candidate | no untriaged productive finding | framework dataflow needs six exact exclusions |
| SQL construction | identifier/value injection | structured `TableName`, identifier quoting, COPY encoding | PostgreSQL and quoting tests | adversarial identifiers/NUL/quotes and exact SAST sinks | reactor | product integrity | caller authorizes the physical target |
| SBOM/license | omitted, contaminated or non-compliant release graph | CycloneDX 1.6, Maven reconciliation and exact license policy | nine module SBOMs plus aggregate, zero unknown/BLOCK | missing/test/SNAPSHOT/path/wrong identity/broken edge/unclassified module/license failures | Build/Security/candidate | binds consumer-reachable graph | license gate is engineering review, not legal advice |
| Artifact reproducibility | source/binary mismatch | two clean release builds and semantic SBOM compare | byte-identical primary artifacts and semantic SBOM equality | inventory drift and unexpected artifacts fail | candidate/local release preparation | mandatory evidence | build environment remains part of trust boundary |
| Signing/inventory | tamper, wrong signer or leaked non-product artifact | source-bound manifest, SHA-256 and detached OpenPGP verifier | ephemeral valid signed fixture and historical real candidate evidence | missing/wrong/tampered signature, content/checksum, version/tag/commit and leakage fail | candidate fixtures; owner workstation for real key | mandatory before authorized upload | real private key remains external/offline-controlled |
| Continuous operation | stale successful result or unhealthy runner | fast/full/release orchestration and weekly Security workflow | local full and manual Security PASS | scanner checksum, keyserver required-mode, policy expiry and runner failures stay non-PASS | weekly/manual Security | freshness required | a health PASS does not prove absence of compromise |
| Release boundary | accidental tag/upload/publication or missing reporting channel | candidate-only workflow and separate technical/REL1 preflights | technical and REL1 preflights PASS after verified channel closure | dirty/wrong HEAD or reporting-state regression fail | candidate and local activation review | fail-closed activation boundary | sole owner remains authorization authority |

## SEC0 threat reconciliation and coverage matrix

The machine-readable source is
`config/security/security-baseline-policy.json`. Every SEC0 threat has preventive, detective and
response coverage; none is left unclassified.

| ID | Threat | Status | Preventive | Detective | Response |
| --- | --- | --- | --- | --- | --- |
| T-01 | account/source compromise | `ACCEPTED` | private owner model, least privilege | workflow/Gitleaks/CI evidence | account/repository compromise runbook |
| T-02 | vulnerable dependency | `MITIGATED` | pins and bounded updates | OSV/Maven/Compatibility | dependency response |
| T-03 | malicious update | `MITIGATED` | no auto-merge, review | OSV/SBOM/Compatibility | build-chain response |
| T-04 | compromised Maven plugin | `MITIGATED` | Central-only exact tools | build inventory/OSV/version drift | build-chain response |
| T-05 | compromised Action | `MITIGATED` | full-SHA allow-list | workflow auditor | build-chain response |
| T-06 | secret exfiltration | `MITIGATED` | zero CI secrets/local signing | secret references/runner boundary | secret exposure matrix |
| T-07 | accidental committed secret | `MITIGATED` | ignore/guidance | Gitleaks current/history | revoke, preserve, then clean |
| T-08 | artifact tampering | `MITIGATED` | synchronized source/reproducibility/signing | hashes/signatures/comparison | artifact response |
| T-09 | dependency confusion | `MITIGATED` | final namespace/Central-only | POM/SBOM/isolated consumers | build-chain/impersonation response |
| T-10 | untrusted PR on runner | `MITIGATED` | hosted-only jobs/no canonical registration | workflow fixtures/remote audit | runner compromise response |
| T-11 | accidental release | `MITIGATED` | manual candidate/separate authorizations | workflow/preflights | invalidate and stop candidate |
| T-12 | license incompatibility | `MITIGATED` | exact policy/no shading | SBOM/license audit | replace or reviewed exception |
| T-13 | mutable container image | `ACCEPTED` | exact patch tags/trusted host | Docker/Compatibility smoke | build-chain response |
| T-14 | SQL injection | `MITIGATED` | structured metadata/quoting/COPY | tests/FindSecBugs | source fix and exclusion removal |
| T-15 | volume/callback DoS | `ACCEPTED` | bounded batching/streaming/guidance | tests/metrics | caller validation/timeouts/pool controls |

## Adversarial closure evidence

- Gitleaks detects a deliberately invalid synthetic fixture and passes the real current tree and
  full history. There is no broad `.gitleaks.toml` allow-list.
- Workflow fixtures reject `pull_request_target`, `workflow_run`, release push, write permission,
  secret reference, mutable/unclassified Action, unclassified workflow, any self-hosted selector,
  actor-dependent PR guards and persistent checkout/settings credentials.
- The runner model proves fork, Dependabot, external-actor and owner PRs plus trusted `main` pushes
  all select hosted infrastructure without secrets.
- OSV triage rejects production findings without review, expired/stale/wrong-scope acceptances,
  incomplete coverage and malformed scanner output.
- SAST rejects any nonzero finding, disabled FindSecBugs, analyzer error, missing class/report and
  expired/stale/widened exclusion.
- License/SBOM fixtures reject unknown/blocked/unreviewed license, stale exception, missing/test
  component, wrong version/group, SNAPSHOT, local path, broken dependency edge and unclassified
  internal module.
- Signing fixtures use only ephemeral keys and reject missing signature, wrong signer, tampered
  content, tampered signature, wrong checksum, unexpected artifact/evidence, SNAPSHOT, benchmark,
  wrong release version, planned tag and source commit.
- Technical-preflight fixtures reject dirty and local-only Git state. Reporting-policy fixtures
  prove that `PENDING` fails closed and `CONFIGURED` clears only the REL1 entry blocker; the real
  configured evidence is separately checked for provider, address, date, control, MFA, recovery,
  delivery and reply-round-trip integrity.
- Operational fixtures corrupt a scanner download and force a required keyserver connection
  failure; neither can become PASS.

## Integrated inventory and identity

Maven remains authoritative. The 13-project reactor and 14 tracked POMs feed OSV; the nine
publishable modules feed SBOM, license, release and signing inventories. The supporting parent POM
is deployed metadata. Benchmarks, both examples, tests, temporary repositories, scanner output and
every `GNUPGHOME` are forbidden from release staging.

The Maven namespace is `io.github.yravelo`; Java source is `io.ybr.postgresbulk`. Active POMs
contain no provisional coordinate. The public inventory contains exactly:

```text
postgres-bulk-core
postgres-bulk-pgjdbc
postgres-bulk-hibernate
postgres-bulk-spring-data
postgres-bulk-spring-data-jdbc
postgres-bulk-spring-boot-autoconfigure
postgres-bulk-spring-boot-starter
postgres-bulk-spring-boot-autoconfigure-jdbc
postgres-bulk-spring-boot-starter-data-jdbc
```

## Release and signing closure

Two clean unsigned `0.1.0` builds must compare byte-for-byte for the parent plus all primary module
artifacts. Two SBOM generations compare semantic identities, purls, versions, licenses, hashes and
edges. The signed verifier now additionally binds the manifest group, reviewed version, planned
tag and explicit expected source commit before trusting signatures.

The real public-key preflight requires full fingerprint
`11545CD242C9575DF408AC08F83D364143C798A3`, tracked expiry `2028-08-23` and the configured minimum
validity. Remote retrieval is availability evidence; the tracked complete export is the expiry
authority. No real private-key operation is performed by SEC8. Historical SEC5 signed-candidate
evidence remains valid; SEC8 revalidates its verifier adversarially with ephemeral keys.

The technical preflight requires a clean tree and `HEAD == origin/main`. The REL1 preflight now
passes with the verified channel and remains fail-closed against a policy regression. Release
remains `workflow_dispatch` candidate-only, secret-free and `autoPublish=false`; no workflow or
audited script creates/pushes a tag, performs Central upload or publishes.

## Runner and operational resilience

Runner validation covers supported toolchain, at least 5 GiB free, Docker, PostgreSQL smoke,
official runner minimum, unexpected Maven/GitHub/Git/OpenPGP credential state and Testcontainers
objects introduced by the run. Cleanup is label-targeted; global Docker prune is forbidden.

A successful health check proves reviewed conditions at one time. It does not establish absence of
host, Docker daemon, cache, runner credential or upstream compromise; the SEC6 incident runbook
remains authoritative.

## External prerequisites

| ID | State | Blocks | Required owner action |
| --- | --- | --- | --- |
| EP-01 private reporting channel | `PASS` | none | Proton Mail channel verified 2026-08-25: owner control, MFA, recovery, inbound and reply round-trip PASS |
| EP-02 offline OpenPGP recovery | `PENDING` | tag, Central upload, publication | verify separate protected backup and revocation recovery |
| EP-03 Central Portal token | `MISSING` | Central upload | create a named token only after separate authorization |

EP-02 and EP-03 remain external REL2 activation steps, not failures of the SEC8 technical baseline.
EP-01 is retained in the inventory as resolved evidence.

## Residual risk and security-debt register

| ID | Classification | Risk | Control / review | Blocks |
| --- | --- | --- | --- | --- |
| RR-01 | `ACCEPTED RISK` | single-maintainer trust concentration | MFA/manual authorization/runbook; 2027-02-24 | none |
| RR-02 | `ACCEPTED RISK` | archive retains persistent Docker-capable runner | private archive isolation; canonical workflows hosted-only; 2027-02-24 | none |
| RR-03 | `DEFERRED ENHANCEMENT` | mutable PostgreSQL patch tags | exact tag/smoke/matrix; 2027-02-24 | none |
| RR-04 | `ACCEPTED RISK` | five OSV WARN findings | exact registry; 2026-10-24 | none while valid |
| RR-05 | `ACCEPTED RISK` | six SAST exclusions | exact source/expiry; 2027-02-24 | none while valid |
| RR-06 | `ACCEPTED RISK` | eight license reviews | exact coordinate/license/expiry; 2027-02-24 | none while valid |
| RR-07 | `FUTURE GENERATION` | PVR/fork approval/rulesets public-only features inactive | MIG4 ordered activation; 2026-10-24 | public visibility until MIG4 step |
| RR-08 | `DEFERRED ENHANCEMENT` | no SLSA/attestation/Sigstore | inventory/hash/OpenPGP; 2027-02-24 | none |
| RR-09 | `ACCEPTED RISK` | caller-controlled resource exhaustion | batching/streaming/guidance; 2027-02-24 | none |

No current `BLOCKER` exists inside the technical baseline. Expired reviews automatically become
blockers rather than silently rolling forward.

## Scanner gap review

No new scanner is justified. CodeQL, Scorecard and native attestations are reconsidered at public
visibility or entitlement change. Semgrep requires a concrete uncovered source rule. Snyk or OWASP
Dependency-Check requires evidence of an OSV/Dependabot/Maven-graph coverage gap. Sonar requires a
distinct maintainability need. Sigstore requires a distribution identity use case not already met
by Central OpenPGP. These are triggers, not planned accumulation.

## Git-history and documentation privacy review

Full-history Gitleaks is the machine gate. The technical review additionally checks tracked binary
archives, private-key markers, token patterns, generated scanner/candidate directories and local
absolute paths. The tracked ASCII-armored file is the approved public key, not private material.
Historical local paths in internal evidence documentation are not release artifacts; REL1 performs
the broader open-source suitability and privacy review before visibility changes.

README, SECURITY, CONTRIBUTING, continuous validation, incident/governance policy, roadmap,
readiness and acceptance criteria retain the same boundary: no supported public release and no
configured private channel. No ADR changes are needed because SEC8 validates existing decisions
rather than changing architecture or publication identity.

## REL0 private handoff

REL0 completed final private release readiness before EP-01 closed. The later prerequisite update
does not reopen REL0 and does not satisfy EP-02/EP-03, create a tag, run Release, upload or publish.

## REL1 open-source handoff

EP-01 is complete and the REL1 entry preflight can pass. REL1 itself must complete a full-history
open-source privacy/license review, move public/fork PR CI to unprivileged infrastructure,
enable/test PVR when available, reevaluate CodeQL, Dependency Review, Scorecard, rulesets and
attestations, and verify anonymous clone/docs/links before visibility changes. The persistent
personal runner must not execute external PR code.

## Completion semantics

```text
Security technical baseline: COMPLETE
SEC0-SEC8: DONE
EP-01 private reporting: PASS
REL1 entry gate: READY
```

The next recommended phase is `REL1 — Open Source Repository Activation`, but this document does
not initiate it or authorize any visibility or release action.
