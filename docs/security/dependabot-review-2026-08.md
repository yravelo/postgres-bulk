# Initial Dependabot PR review — 2026-08

**Status:** PRE-SEC6 maintenance complete. The six initial Dependabot pull requests were reviewed
individually and resolved without auto-merge, publication or activation of SEC6.

## Inventory and decisions

| PR | Ecosystem / scope | Change | Classification | Decision | Resulting commit |
| --- | --- | --- | --- | --- | --- |
| [#1](https://github.com/yravelo/postgres-bulk/pull/1) | Maven, isolated JDBC consumer | Maven Failsafe `3.5.4` → `3.5.6` | patch, test tooling | squash-merged after full local and remote validation | `dd440a22324cc41242e26e92021aa4a0b5806cb5` |
| [#2](https://github.com/yravelo/postgres-bulk/pull/2) | Maven, parent reactor | JUnit BOM `5.12.2` → `5.14.4` | minor, test tooling | squash-merged after full local and remote validation | `fa0a19f4c6ef8fe80cf1b1ed1f67b478ad482c22` |
| [#3](https://github.com/yravelo/postgres-bulk/pull/3) | Maven, JDBC example | Spotless `3.9.0` → `3.10.0` | minor, build tooling | closed as superseded by the repository-wide #5 update | — |
| [#4](https://github.com/yravelo/postgres-bulk/pull/4) | Maven, JPA example | Spotless `3.9.0` → `3.10.0` | minor, build tooling | closed as superseded by the repository-wide #5 update | — |
| [#5](https://github.com/yravelo/postgres-bulk/pull/5) | Maven, reactor and examples | six Maven build plugins | patch/minor, build tooling | squash-merged with reviewed formatter compatibility follow-up | `fcecbb85f6e53e25339fd040859cc8ece78a2d85` |
| [#6](https://github.com/yravelo/postgres-bulk/pull/6) | Maven, parent dependency management | Jakarta Activation API `2.1.1` → `2.1.4` | patch, runtime bugfix | squash-merged after resolved-tree and full validation | `d3a9254393ac031c1db96db9352393c2accc22aa` |

All requested versions were inside the project's supported ranges. None of the pull requests was a
GitHub Actions update or fixed one of the five recorded OSV accepted risks. GitHub reported zero
open Dependabot security alerts throughout the review.

## Review method

Each applicable PR was reconstructed on the then-current `main` and inspected before merge. The
local gate covered workflow policy and regression tests, Gitleaks, Dependabot configuration,
Spotless, reactor `test` and `verify`, clean `verify`, SpotBugs/FindSecBugs, OSV triage, CycloneDX
SBOM and license auditing, installation, both executable examples, the isolated JDBC consumer,
public API and documentation checks. Network-dependent OSV validation was fail-closed: the only
local DNS failure was rerun with network access and had to pass before #6 could be merged.

Only one PR was merged at a time. A successful Build workflow and all 11 Compatibility jobs on the
trusted `main` push were required before proceeding to the next merge. No failed gate was ignored,
made optional or removed.

The self-hosted runner boundary was preserved. Some PRs had historical checks from before the
trusted-PR guard was hardened, while newer pull-request checks were intentionally skipped by that
guard. During this review, unmerged Dependabot code ran only in the local reconstructed worktree;
self-hosted execution occurred only after merge on the trusted `push` event.

## Pull-request evidence

### PR #1 — Failsafe patch

The isolated consumer still executed its integration-test lifecycle and the update changed only
the Failsafe patch version. Local gates passed. Post-merge remote evidence: Build
[`32789710887`](https://github.com/yravelo/postgres-bulk/actions/runs/32789710887) and Compatibility
[`32789710893`](https://github.com/yravelo/postgres-bulk/actions/runs/32789710893), 11/11 passed.
The reviewed rationale is recorded in the
[PR comment](https://github.com/yravelo/postgres-bulk/pull/1#issuecomment-5402887768).

### PR #5 — grouped Maven build tooling

The group updated Resources `3.4.0` → `3.5.0`, Surefire/Failsafe `3.5.4` → `3.5.6`, JAR
`3.4.2` → `3.5.1`, Spotless `3.9.0` → `3.10.0`, Flatten `1.7.3` → `1.8.0` and Shade
`3.6.1` → `3.6.2`. Validation exposed a real formatter/runtime boundary: google-java-format
`1.30.0` supports Java 25, whereas the Java 17/21 build lanes require `1.28.0`. The final design
keeps `1.28.0` as the baseline and activates `1.30.0` only in the JDK `[25,26)` Maven profile.
Dependabot now lets the parent group own Spotless and ignores the duplicate example update lanes.

The companion commits are `ac858a50781e63d6c56571705420428f0f7e2f79` and
`a418b466185fcc716171a3111603fedd57392ca6`. After the compatibility fix, local gates passed and
remote Build [`32807512454`](https://github.com/yravelo/postgres-bulk/actions/runs/32807512454)
and Compatibility
[`32807512546`](https://github.com/yravelo/postgres-bulk/actions/runs/32807512546) passed 11/11.
The intermediate Java 17 failure in run `32807210122` was diagnosed and fixed without weakening
a gate. See the [PR comment](https://github.com/yravelo/postgres-bulk/pull/5#issuecomment-5404840483).

### PRs #3 and #4 — duplicate example updates

Both PRs became unnecessary after #5 updated the same plugin consistently across the reactor and
examples. They were closed, not merged, and no source change was discarded. The rationale is
recorded on [#3](https://github.com/yravelo/postgres-bulk/pull/3#issuecomment-5405211058) and
[#4](https://github.com/yravelo/postgres-bulk/pull/4#issuecomment-5405211357).

### PR #2 — JUnit BOM minor update

The update was confined to test dependency management, introduced no production dependency and
passed the complete local gate. Post-merge remote evidence: Build
[`32809621905`](https://github.com/yravelo/postgres-bulk/actions/runs/32809621905) and Compatibility
[`32809621923`](https://github.com/yravelo/postgres-bulk/actions/runs/32809621923), 11/11 passed.
See the [PR comment](https://github.com/yravelo/postgres-bulk/pull/2#issuecomment-5405289308).

### PR #6 — Jakarta Activation patch

Resolved Maven trees confirmed that this managed coordinate is used at runtime through Hibernate,
JAXB, JAXB Core and Angus in the Hibernate, Spring Data, auto-configuration, starter, benchmark and
basic-example paths. The change updated an existing pin rather than adding a new dependency. The
complete local gate passed with zero SAST findings, zero OSV blockers, five unrelated accepted
risks, and clean SBOM/license checks. Post-merge Build
[`32811776793`](https://github.com/yravelo/postgres-bulk/actions/runs/32811776793) passed; the paired
Compatibility [`32811776782`](https://github.com/yravelo/postgres-bulk/actions/runs/32811776782)
passed 11/11. See the
[PR comment](https://github.com/yravelo/postgres-bulk/pull/6#issuecomment-5405622776).

## Security and release boundary

The resulting dependency-security state remains zero untriaged applicable critical/high findings,
zero Dependabot alerts, zero OSV blockers and five documented, unexpired accepted risks unrelated
to these updates. SAST remains at zero findings and the SBOM/license gate reports zero unknown or
blocked production licenses. Public API and documentation checks remain unchanged and passing.

Dependabot auto-merge was not enabled. No workflow guard was weakened, no supported test was
removed, no tag or GitHub Release was created, no Maven Central upload or other publication was
attempted, and SEC6 was not started.
