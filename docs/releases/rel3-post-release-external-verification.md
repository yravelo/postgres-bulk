# REL3 — Post-release external consumer verification

Date: 2026-08-26. This is the sanitized operational record for external verification of the
already published `postgres-bulk 0.1.0`. It contains no credential, token, private path or signing
secret.

## 1. REL3 result

**COMPLETE.** Release `0.1.0` is externally resolvable, its published bytes and signatures are
verified, both supported persistence integrations work as clean consumers, and the authorized
GitHub Release completes the public release surface.

## 2. Initial repository state

The canonical `yravelo/postgres-bulk` repository was public on `main`, clean and synchronized at
`729fbe23e34c40cc6b32e6622f95760d81870140`. Issues were enabled, Discussions disabled, the license
was Apache-2.0, repository secrets were zero, and no GitHub Release existed.

## 3. Current HEAD

REL3 began at `729fbe23e34c40cc6b32e6622f95760d81870140`. The REL3 closure changes only public adoption and
operational documentation on `main`; it does not change the release source or published payload.

## 4. `v0.1.0` tag state

Exactly one `v0.1.0` tag exists. Annotated tag object
`b77fb1bda8de5ecd73da51f0ef7b7b05ff6d86a8` remained unchanged before and after GitHub Release
creation.

## 5. Tag signature verification

Independent verification in an ephemeral OpenPGP keyring returned a valid RSA/SHA-512 signature
from full fingerprint `11545CD242C9575DF408AC08F83D364143C798A3`.

## 6. Tag source SHA

`v0.1.0` resolves exactly to `9d05829ae66e54be82b33728bd6f56f8318f4b7a` locally, remotely and
from an anonymous clone.

## 7. Maven Central deployment

The immutable Central deployment is `04f5f426-9074-4077-87b2-ff838b57638a`, previously published
from the authorized bundle with SHA-256
`8da79fe7ed9eeec0728fc25cd90585dbae03a6f88b7cd0af804ea54ab58ceb30`.

## 8. Central propagation status

**PASS.** Direct anonymous retrieval from `repo1.maven.org` and isolated Maven resolution work.
The separate `search.maven.org` Solr catalogue had not indexed the group at verification time;
this is search-index lag, not an artifact-resolution failure.

## 9. Coordinates verified (expected 10/10)

All ten `io.github.yravelo:*:0.1.0` coordinates resolve: parent, core, pgJDBC, Hibernate, Spring Data
JPA, Spring Data JDBC, both auto-configuration modules and both starters. Result: **10/10 PASS**.

## 10. Public file integrity

All 184 public files were downloaded from Central and compared with the exact REL2 bundle:
**184/184 byte-identical**. All 46 MD5 and all 46 SHA-1 sidecar checksums match their payloads.

## 11. OpenPGP artifact signature result

All 46 detached Central payload signatures verify against fingerprint
`11545CD242C9575DF408AC08F83D364143C798A3` using an isolated public-key-only keyring. Result:
**46/46 PASS**.

## 12. Sources availability

Nine `-sources.jar` files are present and readable. The seven code-bearing modules contain their
Java sources; the two dependency-only starters have intentionally empty source archives.

## 13. Javadocs availability

Nine `-javadoc.jar` files are present and readable. The seven code-bearing modules contain a
usable Javadoc index; the two dependency-only starters intentionally have no API index.

## 14. Parent POM availability

`io.github.yravelo:postgres-bulk-parent:0.1.0:pom` resolves directly from Central and is the parent
for all nine published JAR modules.

## 15. Clean-room Maven repository strategy

REL3 used fresh Maven local repositories and a settings mirror whose only remote was Maven Central.
No checkout-local artifact, normal user cache, staging repository or `mavenLocal` source could
satisfy resolution.

## 16. External core/pgjdbc consumer

Direct dependency resolution for `postgres-bulk-core` and `postgres-bulk-pgjdbc` succeeded from
Central in the isolated repository. Their transitive graphs contain the expected common core and
pgJDBC runtime without either Spring persistence stack.

## 17. External JPA consumer

A fresh copy of the standalone JPA example consumed stable `0.1.0`, started Spring Boot against a
Testcontainers PostgreSQL 15 instance and passed both tests. It exercised insert, lookup, rollback,
read-only rejection, multi-schema targeting and observability.

## 18. External JDBC consumer

A fresh copy of the standalone Spring Data JDBC example consumed stable `0.1.0`, started Spring
Boot against PostgreSQL and passed both tests. It exercised insert, lookup, rollback, read-only
rejection and multi-schema targeting.

## 19. Starter dependency isolation

The JPA starter graph contains Spring Data JPA/Hibernate and no Spring Data JDBC module. The JDBC
starter graph contains Spring Data JDBC and no JPA/Hibernate module. Result: **PASS**.

## 20. README Maven coordinates audit

README uses `mavenCentral()` and stable `0.1.0` snippets for both supported starters. It links the
release identity to the public GitHub Release.

## 21. Public documentation audit

Getting started, Spring Data JDBC, auto-configuration, transactions, generated IDs, multi-schema,
observability, compatibility and limitations were audited. Stale pre-publication adoption text was
updated and the documentation checker now fails closed if it returns.

## 22. Remaining inappropriate SNAPSHOT references

**Zero.** Remaining `0.1.0-SNAPSHOT` references describe the intentional `main` development default
or executable example POM defaults and are not external installation instructions.

## 23. GitHub Release initial state

No GitHub Release existed for any tag when REL3 began or immediately before the authorization gate.

## 24. GitHub Release authorization required

Repository governance required the exact owner authorization
`AUTHORIZE_GITHUB_RELEASE_V0_1_0_CREATION` before any remote Release mutation.

## 25. GitHub Release authorization consumed

The owner supplied the exact authorization after receiving the tag, title, notes summary, asset
policy and verification status. It was consumed once for `v0.1.0` only.

## 26. GitHub Release creation result

**CREATED.** The Release reuses the existing verified tag. No tag creation, movement or
replacement occurred.

## 27. GitHub Release URL

<https://github.com/yravelo/postgres-bulk/releases/tag/v0.1.0>

## 28. Release title

`postgres-bulk 0.1.0`

## 29. Release prerelease/draft state

`draft=false`, `prerelease=false`.

## 30. Release assets

None. Maven binaries were not duplicated and no evidence was regenerated after publication.

## 31. Release/tag/SHA/Central consistency

GitHub Release `v0.1.0` points to signed tag `v0.1.0`, which resolves to source
`9d05829ae66e54be82b33728bd6f56f8318f4b7a`; the Central `0.1.0` files match the authorized bundle
from that source. Result: **PASS**.

## 32. Anonymous tag clone

An anonymous HTTPS clone checked out detached tag `v0.1.0` at the exact source SHA. Tag signature
verification passed using only the tracked public key.

## 33. Source build at tag

The anonymous clone ran the full 13-module `clean verify` reactor with an isolated Central-only
Maven repository and Docker. Unit, integration, Testcontainers, formatting, SAST and documentation
checks passed in all modules.

## 34. POM metadata verification

All ten POMs contain a name, description, project URL, Apache-2.0 license, developer identity and
SCM tag `v0.1.0`. The published POMs do not declare `issueManagement`; GitHub Issues remains the
documented operational channel. This is a non-blocking future-release metadata improvement.

## 35. SCM/public links verification

The published metadata contains the current public repository base and no private archive link.
Nine child POMs inherit Maven-appended module suffixes in URL/SCM fields that are not directly
navigable; immutable `0.1.0` is not modified and the parent POM has the canonical links.

## 36. Security post-release state

The full continuous-security gate passes: policy drift/fixtures, hosted workflow hardening,
Gitleaks, OSV, full reactor/Testcontainers, FindSecBugs/SpotBugs, SBOM/license, documentation/API,
public-key preflight and runner-boundary checks. CodeQL has zero open alerts and there are no new
critical blockers.

## 37. Dependabot state

There are zero open Dependabot alerts and zero open Dependabot pull requests. Five weekly,
bounded lanes remain configured with majors manual and no auto-merge.

## 38. PVR/private reporting state

GitHub Private Vulnerability Reporting is enabled. `SECURITY.md` directs private reports to PVR and
retains `postgresbulk-security@proton.me` as the fallback; public Issues are not the vulnerability
channel.

## 39. Contribution/Issues readiness

Issues and `CONTRIBUTING.md` are enabled and usable. The issue-template configuration links security
reports to the private policy, blank Issues remain available, and Discussions remain deliberately
disabled.

## 40. CHANGELOG/release notes consistency

CHANGELOG `0.1.0` and GitHub release notes describe the published modules and evidenced features.
Performance language remains bounded to documented local measurements and makes no universal claim.

## 41. Main next-development version policy

`main` deliberately defaults to `0.1.0-SNAPSHOT` after the stable publication. REL3 does not infer
or set `0.1.1-SNAPSHOT` or `0.2.0-SNAPSHOT`; the next version requires a separate evidence-driven
decision.

## 42. Final Build

**PASS.** Hosted Build run `32955204399` passed on the REL2 closure HEAD. The REL3 working tree also
passes the complete local reactor/security validation; normal hosted CI validates the documentation
closure commit.

## 43. Final Compatibility

**PASS — 11/11.** Hosted Compatibility run `32955204365` passed the supported Java/framework/driver
matrix before REL3, and the anonymous tag build independently reconfirmed the release source.

## 44. Final Security

**PASS.** Hosted Security run `32955246724` passed and REL3 reran the full local gate successfully,
including accepted-risk expiry validation.

## 45. Final CodeQL

**PASS.** Hosted CodeQL run `32955203532` passed with zero open alerts. Documentation-only closure
changes introduce no Java production change.

## 46. Final Gitleaks

**PASS.** Current-tree and all 222 public-history commits were scanned with Gitleaks 8.30.1 and no
leaks were found.

## 47. Public surface audit

Anonymous requests to landing, tag, Actions, Security, license and Release pages returned HTTP 200.
The source contains no clickable private-archive URL, owner-local path or release credential.
Historical migration reports retain plain archive names as sanitized audit evidence.

## 48. Documentation changes

REL3 updates stable installation instructions, current support status, build/security facts,
example usage, active release-readiness/roadmap state and the README Release link. A new adoption
guard rejects stale snapshot/publication instructions.

## 49. Git commits

Release source remains `9d05829`; REL2 closure is `729fbe2` (`docs(release): close Maven Central
0.1.0 publication`). REL3 documentation and this canonical record are committed together as
`docs(release): close post-release verification`.

## 50. Final Git state

After closure, `main` is pushed to `origin/main`, the worktree is clean, and tag `v0.1.0` remains
unchanged at its original tag object and source SHA.

## 51. REL3 Definition of Done assessment

Central propagation, 10/10 coordinates, bytes, checksums, signatures, sources/Javadocs, isolated
JPA/JDBC consumers, dependency isolation, public docs, GitHub Release policy, tag/source/Central
consistency, anonymous clone/build, metadata/security/reporting readiness and closure validation all
pass. No feature work is mixed into REL3.

## 52. Final verdict

**REL3 COMPLETE — 0.1.0 externally verified.**

## 53. Remaining operational risks

- Maven Search catalogue indexing may lag direct Central availability.
- Published child POM module-suffixed links and absent `issueManagement` can only be improved in a
  future version.
- Dependency-only starter source/Javadoc archives contain no API by design.
- Five accepted dependency advisories remain monitored under documented scope/reachability
  decisions and expire for mandatory review on 2026-10-24.

None is a blocker for the immutable `0.1.0` release.

## 54. Recommended post-release mode

`OBSERVE / MAINTAIN`: monitor Issues, PVR, security alerts, dependency updates and adoption. An
urgent patch or security fix uses a separate authorized workflow; `0.2.0` remains evidence-driven.

## 55. Remote actions

```text
GitHub Release created: yes
tag created: no
tag moved: no
Central upload: no
Central publication: no
repository visibility changed: no
repository secrets created: no
new feature development started: no
```

```text
REL3 status: DONE
Maven Central external resolution: PASS
coordinates verified: 10/10
artifact signatures: PASS
external JPA consumer: PASS
external JDBC consumer: PASS
GitHub Release: CREATED
security post-release: PASS
final CI: PASS
repository public: yes
v0.1.0 immutable: yes
new feature work started: no
```
