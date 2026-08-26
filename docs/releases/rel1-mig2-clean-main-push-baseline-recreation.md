# REL1-MIG2 clean main push and baseline recreation

Date: 2026-08-26

Result: **DONE**

MIG2 migrated only canonical clean `main` to the new private GitHub repository, recreated the
approved basic metadata and proved the result from a fresh authenticated clone. It did not migrate
old GitHub resources, register a runner, create credentials, make the repository public or delete
the private archive.

## 1. MIG2 result

`DONE`. The new private `yravelo/postgres-bulk` contains the clean canonical history on `main`, its
default branch is `main`, its refs and identities are clean, and its minimum approved metadata is
present. The old repository remains an intact private rollback archive.

## 2. Initial local Git state

```text
branch: main
working tree: clean
HEAD: 4cd10ed3d318337173f8b40ceae45c313bee7e31
origin/main before separation: 7c66bc82c7ab32c4e0320eb6839a25c15fd2f8f3
ahead/behind before separation: 2/0
initial remote text: git@github.com:yravelo/postgres-bulk.git
```

The initial remote text already identified the new empty repository, while its local tracking ref
still represented the archive-era branch. No fetch or push used that ambiguous state.

## 3. Pending local commits preserved

Both local-only migration commits were present in and preserved by canonical `main`:

```text
9eeae8d docs(release): recommend clean repository migration [skip actions]
4cd10ed docs(release): record repository container migration [skip actions]
```

They were included in the explicit main push; neither was pushed to the archive.

## 4. Old repository identity verification

`PASS` immediately before separation and after migration:

```text
full name: yravelo/postgres-bulk-private-archive
repository ID: 1339652660
visibility: PRIVATE
default branch: main
archived: false
```

## 5. New repository identity verification

`PASS` immediately before the push:

```text
full name: yravelo/postgres-bulk
repository ID: 1346700826
visibility: PRIVATE
branches: 0
workflow runs: 0
```

The ID and empty baseline were re-read directly from GitHub immediately before writing.

## 6. Remote separation strategy

The ambiguous `origin` was renamed to `origin-new`; an explicit `origin-old` was added for read-only
archive access. A prune against the empty new repository removed the stale `origin-new/main`, and a
read-only archive fetch created the correctly named `origin-old/main`. After the first push was
verified, the provisional pair was finalized as canonical `origin` and guarded `archive` so existing
preflight tooling can verify the new canonical repository without weakening its checks.

## 7. Final remote mapping

```text
origin fetch/push -> git@github.com:yravelo/postgres-bulk.git
archive fetch     -> OLD private archive (fetch-only)
archive push      -> DISABLED
```

The provisional `origin-new` was verified as repository ID `1346700826`; provisional `origin-old`
was verified as ID `1339652660` before final naming. Local `main` tracks only `origin/main`.

## 8. Source ref migrated

```text
refs/heads/main
```

No wildcard or implicit branch set was used.

## 9. Source commit count

`113` before the MIG2 documentation commits. The report and remote-finalization commits add two
clean descendants, so the synchronized final count is `115` after closure.

## 10. Source HEAD

The audited source and first-push HEAD was:

```text
4cd10ed3d318337173f8b40ceae45c313bee7e31
```

The final repository HEAD is the MIG2 documentation commit described in section 56 and reported by
the handoff; it preserves this commit as its direct history.

## 11. Source root commit

```text
e2161df6813eb38810e012a5460b986c2d8c4afc
```

The fresh clone reproduced the same root.

## 12. Personal email audit

`PASS`. Author and committer email inventory on canonical `main` contains only:

```text
29708813+yravelo@users.noreply.github.com
49699333+dependabot[bot]@users.noreply.github.com
noreply@github.com
```

Searches for the prior personal email returned zero commit-diff or current-tree matches.

## 13. Personal path audit

`PASS`. Canonical-history pickaxe and current-tree searches found zero occurrence of the prior owner
home path. Private-key markers were also absent. Archive-name occurrences are limited to explicit
migration/security reports and roadmap state; they are not filesystem or credential disclosures.

## 14. Full-history Gitleaks

`PASS`. Before the first push, Gitleaks 8.30.1 scanned the broader 207-commit local ref set with no
leak. A fresh clone of the new repository independently scanned exactly 113 canonical commits and
found no leak. The final report commit is scanned again before closure.

## 15. Tag inventory

```text
local tags before push: 0
new repository tags after push: 0
tags pushed: 0
```

`v0.1.0` does not exist.

## 16. Ref exclusion audit

The explicit refspec excluded:

- local `refs/presec6/pr2` and `refs/presec6/pr6`;
- every `refs/remotes/*` tracking ref;
- the archive's six `refs/pull/*` refs;
- all tags, backup refs, rewrite refs and temporary refs.

The new server advertised only `HEAD` and `refs/heads/main`; `refs/pull/*` remained zero.

## 17. First-push workflow safety assessment

Build and Compatibility have `push`/`pull_request` triggers and still target the dedicated
self-hosted label. Security is schedule/manual and also targets self-hosted; Benchmarks and Release
are manual hosted workflows. The new repository has no registered runner.

The first-push HEAD message contains `[skip actions]`. GitHub explicitly documents this marker as a
skip instruction for `push` and `pull_request` workflows. It does not apply to other event types:
[GitHub — Skipping workflow runs](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/skip-workflow-runs).

## 18. First-push strategy used

Strategy C: use the already versioned `[skip actions]` HEAD marker. Build and Compatibility did not
run, so no self-hosted job queued. GitHub did independently discover `.github/dependabot.yml` and
created five `dynamic` Dependabot update runs; this event is outside the documented push skip scope.

All five jobs used GitHub-hosted `ubuntu-latest`, not the archive runner. Four completed successfully;
one still-active job was cancelled to prevent premature update activity. The other cancellation
requests arrived after their runs had completed. No run was deleted and no PR was created.

## 19. Exact push scope

```text
main only
```

Executed refspec:

```text
refs/heads/main:refs/heads/main
```

## 20. `--mirror` used

```text
no
```

## 21. `--all` used

```text
no
```

## 22. Tags pushed

```text
0
```

## 23. Main push result

`PASS`. Git reported a single new remote branch, `main -> main`, at the audited local HEAD. No other
refspec was present.

## 24. New repo HEAD

After the first push:

```text
4cd10ed3d318337173f8b40ceae45c313bee7e31
```

After documentation closure, HEAD is the remote-finalization commit recorded in section 56 and the
final handoff. Both local and `origin/main` match it.

## 25. New repo commit count

```text
first-push count: 113
final synchronized count: 115
```

## 26. Default branch

```text
main
```

GitHub selected `main` automatically on the first branch push; no separate setting mutation was
needed.

## 27. New repo refs/pull count

```text
0
```

## 28. Unexpected refs

```text
0
```

Expected server refs are only symbolic `HEAD` and `refs/heads/main`. There are no unexpected heads,
tags, PR refs or backup refs.

## 29. Old PRs inherited

```text
0
```

The new repository still has zero PRs after the Dependabot discovery activity.

## 30. Old workflow runs/jobs inherited

```text
0 / 0
```

Every new run/job has the new repository's current head SHA and `dynamic` Dependabot identity. None
of the archive's 196 runs or historical jobs appears in the new repository.

## 31. New workflow runs created by MIG2 push

```text
new runs: 5
new jobs: 5
event: dynamic Dependabot updates
conclusions: 4 success, 1 cancelled
new PRs created: 0
Build/Compatibility push runs: 0
```

These records are retained as truthful new-repository baseline evidence; none was deleted.

## 32. New Actions artifacts

```text
0
```

No release or workflow artifact was produced.

## 33. New runner registrations

```text
0
```

The five Dependabot jobs used ephemeral GitHub-hosted runners. No repository-level runner was added.

## 34. New secrets/variables

```text
Actions secrets: 0
Actions variables: 0
```

Environments, deploy keys and webhooks also remain zero.

## 35. Description

```text
High-throughput PostgreSQL bulk insert and lookup for Java, Spring Data JPA and Spring Data JDBC.
```

This is the exact MIG0-approved description.

## 36. Topics

```text
bulk-insert
copy
java
jdbc
jpa
postgresql
spring-boot
spring-data
```

## 37. Homepage

Empty. No website was invented.

## 38. Issues/Discussions/Wiki settings

```text
Issues: enabled
Projects: disabled
Discussions: disabled
Wiki: disabled
```

These are the MIG0-approved initial surfaces. No issue or template was created through GitHub.

## 39. Actions baseline settings

Observed and deliberately unchanged in MIG2:

```text
allowed actions: all
SHA pinning setting: false
default GITHUB_TOKEN permission: read
workflow PR approval: false
repository runner registrations: 0
```

The read-only token baseline already met the minimum MIG2 requirement. Allowed-actions hardening,
workflow runner migration and public/fork policy belong to MIG3.

## 40. Security/dependency baseline settings

Dependency/vulnerability alerts, automated security fixes and `security_and_analysis` remain at the
new private repository defaults (disabled/not configured). MIG2 did not enable them because the
tracked Dependabot version-update configuration already generated discovery activity. MIG3 must
enable and validate the planned controls deliberately after hosted-safe workflows are ready.

PVR, CodeQL, Dependency Review, release attestations and credentials remain unconfigured.

## 41. Rulesets/branch-protection status

No ruleset or branch protection was added. The current private plan reports rulesets unavailable,
and adding required checks before CI recreation could lock the migration. MIG3 must establish the
public-trust rules only after the new hosted checks exist.

Default merge settings remain unchanged and inventoried: merge commits, squash and rebase enabled;
auto-merge and automatic head-branch deletion disabled.

## 42. Old Actions links inventory

There are 27 live old-run links across seven files:

| File | Count |
| --- | ---: |
| `docs/architecture/compatibility-evidence.md` | 4 |
| `docs/security/continuous-security-validation.md` | 3 |
| `docs/security/dependabot-review-2026-08.md` | 8 |
| `docs/releases/rel0-final-release-readiness.md` | 3 |
| `docs/releases/rel1a-open-source-exposure-audit.md` | 3 |
| `docs/releases/rel1ar-public-history-remediation.md` | 3 |
| `docs/releases/rel1ar-runner-identity-remediation.md` | 3 |

Their name portion now identifies the new repository while their IDs belong to the private archive,
so they are broken/misleading. MIG3 must turn old IDs into unlinked historical text/local evidence
and add fresh new-repository run links for current claims.

## 43. Old PR links inventory

`docs/security/dependabot-review-2026-08.md` contains 12 live links: six old PR links and six comment
anchors for PRs #1–#6. MIG3 must remove the live dependency while retaining the reviewed dependency
decisions and PR numbers as historical text where useful.

## 44. Migration-document publicability classification

| Document | Classification | Reason/action |
| --- | --- | --- |
| MIG0 | `KEEP PUBLIC` | feasibility rationale, official sources and neutral IDs; no secret or personal data |
| MIG1 | `KEEP PUBLIC` | truthful container-isolation evidence; archive name/ID are operational, not credentials |
| MIG2 | `KEEP PUBLIC` | clean-history and baseline proof; no credential or personal host/path data |

Because these reports are already in canonical Git history, deleting only their latest-tree copies
would not make prior content secret. They were therefore written to be safely publishable. Before
MIG4, perform one final privacy/content review and neutralize only claims that became stale.

## 45. Public documentation self-containment gaps

The public tree still depends on 27 old-run and 12 old-PR/comment hyperlinks. Current CI claims also
lack replacement hosted-run evidence from the new repository. `SECURITY.md` still describes the
private-state reporting boundary and must be updated only after PVR is enabled/tested. Migration and
roadmap files intentionally mention the private archive as historical topology but contain no link
requiring access to it.

## 46. Fresh clone result

`PASS`. An authenticated fresh clone from `git@github.com:yravelo/postgres-bulk.git` completed with
`main` checked out, a clean worktree and only normal local/remote `main` refs. Wrapper/POM discovery
found the expected Maven reactor and modules; documentation/adoption audit checked 240 relative
targets and passed.

## 47. Fresh-clone HEAD/history verification

For the first-push verification clone:

```text
HEAD: 4cd10ed3d318337173f8b40ceae45c313bee7e31
commits: 113
root: e2161df6813eb38810e012a5460b986c2d8c4afc
tags: 0
unexpected refs: 0
personal email occurrences: 0
```

The final documentation push is verified with the same checks before MIG2 closure.

## 48. Fresh-clone Gitleaks

`PASS`. Gitleaks 8.30.1 history mode scanned all 113 first-push commits from the new repository and
found no leak. A final clone/scan after both documentation pushes confirms the 115-commit closure
state.

## 49. Old archive state

```text
full name: yravelo/postgres-bulk-private-archive
repository ID: 1339652660
visibility: PRIVATE
default branch: main
PRs: 6
Actions runs: 196
archived: false
last pushed_at: 2026-08-25T22:33:31Z
```

The unchanged `pushed_at` proves MIG2 made no archive push.

## 50. Old archive runner state

```text
runner registrations: 1
name: postgres-bulk-ci-01
status: online / idle
```

It remains on the archive for rollback and was not moved.

## 51. New repo runner state

```text
repository-level runner registrations: 0
```

## 52. Final upstream tracking

Local `main` tracks `origin/main` only. After each authorized documentation push, local HEAD and that
tracking ref are synchronized. `archive/main` remains a separately named archive observation ref and
is not the upstream.

## 53. Accidental archive-push prevention

The archive fetch URL remains available for rollback inspection, while its local push URL is the
literal non-repository value `DISABLED`. Any ordinary `git push archive` therefore fails locally.
Restoring archive write capability would require a visible, explicit configuration change.

## 54. Local validation

The closure state passes:

```text
git diff --check
./scripts/check-documentation.sh
./scripts/check-security.sh fast
./scripts/check-release-security-preflight.sh technical
./scripts/check-release-security-preflight.sh rel1
./scripts/check-secrets.sh history
fresh-clone documentation/history/ref/Gitleaks checks
```

The technical preflight runs after the final push with `main == origin/main`; no temporary
substitution for the canonical remote is required.

## 55. Documentation changes

Created this MIG2 report and updated the documentation index, release roadmap, MIG1 report and REL1-A
audit with observed evidence. Old run/PR links were intentionally inventoried rather than mass-edited;
their evidence replacement belongs to MIG3 when new hosted CI results exist.

## 56. Git commits

MIG2 migrated the two pending local commits shown in section 3 and adds two documentation closure
commits:

```text
docs(release): record clean main migration [skip actions]
docs(release): finalize canonical remote mapping [skip actions]
```

All use the approved author/committer identity
`Yusnier Blanco Ravelo <29708813+yravelo@users.noreply.github.com>`. The exact final SHA is recorded
in the handoff and becomes synchronized `origin/main`.

## 57. Final Git state

Expected after the closure push: clean local `main`, synchronized with `origin/main`; archive
tracking remains at `archive/main`; no local or remote tag was created. The handoff records the
observed SHA/status and final remote map.

## 58. Final GitHub state

```text
new repository: yravelo/postgres-bulk (ID 1346700826), PRIVATE
new default branch: main
new clean canonical commits: 115
new unexpected refs/PRs/artifacts/registered runners/secrets/variables: 0
new MIG2-created workflow runs/jobs: 5/5, Dependabot dynamic only

old archive: yravelo/postgres-bulk-private-archive (ID 1339652660), PRIVATE
old PRs/runs/runner: 6/196/1, intact
```

## 59. Remote actions performed

```text
old repository renamed: already done
new repository created: already done
clean main pushed to new repo: yes
Git refs other than main pushed: no
new repository visibility: PRIVATE
old archive visibility: PRIVATE
runner registered to new repo: no
Actions secrets created: no
repository made public: no
old repository deleted: no
workflow runs deleted: no
GitHub Support contacted: no
GitHub PVR enabled: no
tag created: no
Benchmarks executed: no
Release executed: no
Central upload: no
publication: no
REL1-B started: no
```

Additionally, one still-running new Dependabot job was cancelled; no run record was deleted.

## 60. MIG2 Definition of Done assessment

`PASS`, subject only to the final mechanical commit/push/read-back checks recorded by the handoff.
Pending commits were preserved; IDs and remotes were verified; clean main alone was pushed; history,
privacy, refs and fresh clone passed; no old GitHub resource was inherited; approved metadata and
read-only token baseline are present; credentials and runner registrations remain zero; old links
are inventoried; the archive is unchanged/private; remotes/upstream are unambiguous.

## 61. Remaining blockers/work

```text
CI/security recreation
public-safe PR architecture
old run/PR documentation cleanup and replacement evidence
ruleset/branch protection after hosted checks exist
public activation and PVR/CodeQL verification
old archive eventual separately authorized deletion
```

The five Dependabot discovery records are valid new-repository history, not inherited history.

## 62. MIG3 entry criteria

```text
new PRIVATE repo has canonical clean main: PASS
history/privacy checks PASS: PASS
refs clean: PASS
old GitHub metadata absent: PASS
baseline metadata recreated: PASS
old archive intact: PASS
remotes safe: PASS
```

MIG3 must reverify these before changing workflows, Actions/security settings or archive runner
state.

## 63. Exact next action

Prepare, but do not execute automatically:

```text
REL1-MIG3 — CI, Security & Public-Trust Baseline Recreation
```

## Boundary statement

```text
REL1-MIG2 status: DONE
clean main pushed: yes
new repository private: yes
new repository HEAD matches local: yes
old PRs inherited: 0
old refs/pull inherited: 0
old Actions history inherited: 0
new runner registered: no
old archive private/intact: yes
repository public: no
old archive deleted: no
REL1-B started: no
tag created: no
Central upload: no
publication activated: no
```
