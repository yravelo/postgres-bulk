# REL1-MIG5 old private archive decommission readiness

Date: 2026-08-26  
Mode: `DECOMMISSION / READINESS`  
Canonical repository: `https://github.com/yravelo/postgres-bulk`

## 1. MIG5 result

`DONE` in readiness-only mode. Every source, documentation, CI/security, audit, release-readiness,
runner and metadata-loss gate passed. Verdict: **READY FOR OLD PRIVATE ARCHIVE DELETION**. The
archive was not deleted or otherwise mutated.

## 2. Mode

```text
DECOMMISSION / READINESS
```

This phase only audits and prepares the future transaction. It does not consume deletion authority.

## 3. Public canonical repo state

`yravelo/postgres-bulk` is `PUBLIC`, defaults to protected `main`, has no tag or Release, and
contains the synchronized clean history. At the start of MIG5, HEAD was
`957782de342f9e53a429f3241c3daed11aea9b42`, 122 commits. Canonical repository runners, Actions
secrets and variables are all zero.

## 4. Old archive state

`yravelo/postgres-bulk-private-archive` is still `PRIVATE`, defaults to `main` at
`7c66bc82c7ab32c4e0320eb6839a25c15fd2f8f3`, has 111 commits, 6 closed pull requests, 196 workflow
runs, 0 issues, 0 tags, 0 Releases, 0 artifacts, 0 forks and reported repository size 1362 KiB.
It also has 44 disposable Actions caches and one inert `maven-central` environment with no secret,
variable, deployment or protection rule.

## 5. Public/archive repository IDs

| Repository | Database ID | Node ID | Visibility |
| --- | ---: | --- | --- |
| `yravelo/postgres-bulk` | `1346700826` | `R_kgDOUEUGGg` | `PUBLIC` |
| `yravelo/postgres-bulk-private-archive` | `1339652660` | `R_kgDOT9l6NA` | `PRIVATE` |

The distinct identities prove that archive deletion cannot delete the canonical repository.

## 6. Source independence result

`PASS`. Archive `main` is the exact merge-base and ancestor of public `main`. The comparison is
11 public-only commits versus 0 archive-only commits. All product source, tests, examples,
benchmarks, verification consumers, documentation, ADRs, security policy and release plans are in
the public tree.

## 7. Archive-only files/commits needed

`0`. The tree comparison found no file present only in archive `main` and no archive-only commit.
Differences are public additions or newer public versions, classified `SUPERSEDED` in the archive
and `REQUIRED_BY_PUBLIC_PROJECT` in public `main`. Private GitHub PR/Actions objects are
`HISTORICAL_ONLY` and `MIGRATION_ONLY`, not source inputs.

## 8. Branch/tag dependency result

`PASS`. The archive exposes only default branch `main` and zero tag. Public `main` includes that
branch's complete intended clean lineage plus the migration/public-activation commits. No useful
branch or tag exists exclusively in the archive.

## 9. Documentation archive-link inventory

Before this report, 20 plain-text occurrences of the archive name existed across 8 Markdown files.
They are historical topology/containment identifiers and are classified `KEEP_AS_PLAIN_HISTORICAL_ID`
or `SAFE`. Actual clickable HTTP/Markdown links to the private archive: `0`. Public run links point
only to the canonical repository.

## 10. Documentation independence result

`PASS`. No public page requires an archive URL, private run payload or archive-only object to be
understood. README, SECURITY, CONTRIBUTING, release notes/readiness, benchmark reports and security
closure are self-contained. MIG5 updated stale current-state language while retaining clearly
historical identifiers needed to explain the migration.

## 11. CI independence result

`PASS`. Every canonical workflow selects `ubuntu-latest`, has read-only default permissions, uses
no archive URL and can run with zero canonical self-hosted runner registration and zero repository
secret. The public workflows and their policy/fixtures are newer than the archive versions.

## 12. Latest public Build evidence

Public hosted Build
[`32925430081`](https://github.com/yravelo/postgres-bulk/actions/runs/32925430081) is `PASS` at the
latest product/CI commit `dd63b0d2a0cdbfe033b94afc07ef1a9d2c648752`. Later MIG4/MIG5 changes are
documentation-only and were covered by the local documentation/security gates.

## 13. Latest Compatibility evidence

Public hosted Compatibility
[`32925429985`](https://github.com/yravelo/postgres-bulk/actions/runs/32925429985) is `11/11 PASS` at
the same latest product/CI commit. No archive runner or archive configuration participated.

## 14. Latest Security evidence

Manually dispatched public hosted Security
[`32925758807`](https://github.com/yravelo/postgres-bulk/actions/runs/32925758807) is `PASS` at the
same product/CI commit. Full local history/current secret validation and final preflights were
rerun during MIG5.

## 15. CodeQL state

CodeQL default setup is configured for Java/Kotlin on standard hosted infrastructure, schedule
weekly and threat model remote. The closure-head analysis `32926180166` passed and the authenticated
open-alert count is `0`.

## 16. Public self-hosted runner count

`0`. The canonical repository runner API returns an empty inventory. The other four accessible
owner repositories also return zero repository runners.

## 17. Old archive runner state

Runner `postgres-bulk-ci-01`, ID `22`, remains registered only to the archive, `online`, `idle`,
Linux x64, with its dedicated label. Host inspection found its single GitHub Actions service active
and enabled and one listener process. MIG5 did not stop, disable, deregister or delete it.

## 18. Runner still required by canonical repo

```text
no
```

Build, Compatibility, Security, CodeQL, public PRs and technical/REL1 preflights all operate without
it. It is classified `DECOMMISSIONABLE`.

## 19. Runner decommission plan

In the future authorized deletion transaction: first confirm the runner is idle; stop its service;
use GitHub's supported repository-runner removal token/configuration flow; verify repository
registration, listener process and service are absent; and remove only the dedicated runner
installation if separately appropriate. Preserve the host: repository registration checks found no
other GitHub runner, but unrelated non-runner host use has not been ruled out. GitHub documents the
[supported removal procedure](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/remove-runners).

## 20. REL2 archive dependency

```text
none
```

The public tree contains release inventory generation, reproducibility checks, nine-artifact SBOM
and license policy, signing/public-key documentation, verified Central namespace evidence,
candidate-only release workflow and exact source-SHA binding. EP-02 OpenPGP recovery media and
EP-03 Portal token are external boundaries, not archive assets.

## 21. Security evidence archive dependency

`none`. Public history retains SEC0–SEC8 roadmaps/closures and reproducible Gitleaks, OSV, SAST,
SBOM/license, workflow-security, PVR, runner/privacy remediation and MIG4 activation evidence. Old
Actions results are superseded by public hosted PASS evidence.

## 22. Clean history archive dependency

`none`. Canonical `main` independently contains the intended rewritten history. Its 122-commit
pre-report history has only the owner noreply and Dependabot noreply identities, with zero personal
email and zero personal owner path. The archive contains pre-rewrite/private GitHub history and is
not needed to reconstruct or validate the clean lineage.

## 23. Old GitHub metadata loss inventory

Deletion will make the archive repository container and its repository-scoped resources
unavailable: 6 closed PRs and synthetic PR refs; 196 workflow runs and their job/log metadata;
repository settings; 44 caches; one inert environment; and one runner registration unless removed
first. Counts already zero are issues, tags, Releases, Actions artifacts, forks, webhooks,
deployments, deploy keys, repository/environment secrets and variables.

## 24. Metadata loss verdict

```text
SAFE TO LOSE
```

The metadata is superseded, private/internal, non-canonical or replaced by public evidence. Some
historical PR/job records contain the privacy metadata that motivated clean-container migration;
retaining them has negative privacy value and no current operational, legal or release need.

## 25. Public links to archive remaining

```text
0
```

Plain historical IDs are not hyperlinks or runtime dependencies and will not break after deletion.

## 26. Final URL continuity

The canonical URL remains `https://github.com/yravelo/postgres-bulk`, repository ID `1346700826`.
The archive has a different name and ID. Deleting that distinct container does not rename or delete
the canonical repository; D6 nevertheless verifies public HTTP, API and anonymous Git access.

## 27. Backup recommendation

```text
none
```

No bare clone, bundle or metadata export is recommended by default. Any future exception would
need a separate explicit purpose and authorization.

## 28. Backup rationale

Public `main` already preserves every needed source and evidence object. A private backup would
recreate retention of the pre-rewrite/privacy-sensitive history that deletion is intended to
abandon, extend access-control and destruction obligations, and provide no REL2 capability. The
loss of old GitHub metadata is intentional closure, not an unmitigated backup gap.

## 29. Deletion/recovery semantics

GitHub warns that repository deletion permanently removes team permissions and that deleting a
private repository deletes its forks; this archive has no fork or collaborator other than its
owner. GitHub says some deleted repositories can be restored within 90 days, may take up to one
hour to appear for restoration, and restored repositories do not recover team permissions. See
[Deleting a repository](https://docs.github.com/en/repositories/creating-and-managing-repositories/deleting-a-repository)
and [Restoring a deleted repository](https://docs.github.com/en/repositories/creating-and-managing-repositories/restoring-a-deleted-repository).
Operationally, deletion is treated as irreversible: eligibility or GitHub internal retention is
not a project rollback guarantee.

## 30. Future archive deletion transaction

```text
D0 final readiness PASS
D1 verify public repository health and exact IDs
D2 confirm archive runner idle; remove registration/service through the supported flow
D3 create no backup unless a new explicit authorization overrides the current none decision
D4 delete only yravelo/postgres-bulk-private-archive (ID 1339652660)
D5 verify authenticated archive API/Git access is unavailable
D6 verify canonical public HTTP/API/anonymous Git and ID 1346700826 are unchanged
D7 verify local archive remote fails against the deleted target
D8 remove obsolete local archive remote and remote-tracking ref
D9 rerun public technical/REL1, privacy and anonymous preflights
D10 commit closure evidence and close clean migration
```

This plan is not executed by MIG5 readiness.

## 31. Public health revalidation

`PASS`: identity/visibility/default branch are correct; Build is PASS; Compatibility is 11/11;
Security is PASS; CodeQL is PASS with zero alerts; `main` protection has 13 strict required checks,
PR/conversation requirements and no force-push/deletion; PVR, secret scanning/push protection and
Dependabot security updates are enabled; canonical runners/secrets/variables are zero.

## 32. Anonymous clone state

`PASS`. A credential-free clone of the canonical HTTPS URL resolved synchronized public `main`,
only `HEAD`/`refs/heads/main`, zero tag, clean worktree, canonical author identities and clean
full-history/current-tree Gitleaks results. The final documentation closure is rechecked from a
fresh anonymous clone before handoff.

## 33. Technical release preflight

`PASS`. The public tree supplies all technical, OpenPGP-public-key and source-synchronization
requirements. No private key, passphrase, Portal token or archive object was consumed.

## 34. REL1 preflight

`PASS`. Reporting is configured and the canonical source/privacy/trust boundary remains green.

## 35. Archive deletion readiness verdict

```text
READY FOR OLD PRIVATE ARCHIVE DELETION
```

This verdict authorizes nothing by itself.

## 36. Remaining blockers

Deletion execution lacks the exact owner authorization. Within that future transaction, the online
archive runner must be removed before the repository. There is no source, documentation,
CI/security, audit, REL2 or backup blocker.

## 37. Documentation changes

MIG5 adds this report and updates the docs index, release roadmap/readiness, MIG4 handoff, release
notes, vulnerability/dependency governance, SEC8 public-state context and runner decommission plan.
Historical evidence remains labelled and self-contained; no private URL or sensitive value was
added.

## 38. Git commits

The readiness documentation commit is titled
`docs(release): assess archive decommission readiness [skip actions]`; its immutable SHA is the
public `main` tip containing this report.

## 39. Final Git state

Final local `main` is clean and synchronized with `origin/main`; `origin` remains the public
canonical repository. During readiness, `archive` remains fetch-only with push URL `DISABLED`.
No tag exists. The future deletion transaction removes the obsolete local remote only after remote
deletion is verified.

## 40. Remote actions performed

MIG5 performed read-only GitHub/API audits and pushed only its public documentation closure. Exact
boundary:

```text
old archive deleted: no
old archive visibility changed: no
old archive runner removed: no
public repository visibility changed: no
repository secrets created: no
tag created: no
Release executed: no
Central upload: no
Maven publication: no
REL2 started: no
```

## 41. MIG5 Definition of Done assessment

`PASS`. Public/archive identities, canonical independence, docs and links, CI/security/REL2
evidence, clean history, runner plan, metadata loss, backup decision, GitHub deletion semantics,
transaction plan, public health, anonymous access and both preflights satisfy every readiness gate.
The archive remains private and undeleted.

## 42. Exact next authorization

```text
AUTHORIZE_OLD_PRIVATE_REPOSITORY_DELETION
```

Only that exact, separate owner authorization may begin the runner-removal/archive-deletion
transaction. It does not authorize REL2.

## 43. Post-deletion next phase

After deletion is separately executed and verified, the next phase is:

```text
REL2 — Maven Central 0.1.0 Publication
```

It must not start automatically and retains its own external prerequisites and authorizations.

## Boundary statement

```text
REL1-MIG5 status: DONE
public canonical repo healthy: yes
archive dependency: none
archive runner needed: no
public links to archive: 0
metadata safe to lose: yes
archive deletion readiness: READY
old archive private: yes
old archive deleted: no
repository public: yes
tag created: no
Central upload: no
Maven publication: no
REL2 started: no
```
