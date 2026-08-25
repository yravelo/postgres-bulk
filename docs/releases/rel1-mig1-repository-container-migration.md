# REL1-MIG1 repository container migration

Date: 2026-08-26

Result: **DONE**

## MIG2 execution addendum

MIG2 first separated `origin-new` from guarded `origin-old`, then finalized the verified canonical
mapping as `origin` (new repository) and guarded `archive` (old repository). It pushed only canonical
`main`, verified 113 clean pre-report commits from a fresh clone and recreated the approved basic
metadata. The new repository inherited no old PR/ref/Actions state and still has zero registered
runners, secrets and variables. The archive remains private and unchanged. See the
[MIG2 clean main migration](rel1-mig2-clean-main-push-baseline-recreation.md).

This report records the authorized GitHub container-identity migration. MIG1 renamed the existing
private repository and created a distinct, empty private repository at the final project URL. It did
not migrate Git history, push a ref, configure CI, change visibility, delete data or start REL1-B.

## 1. MIG1 result

`DONE`. The old GitHub repository is retained privately as
`yravelo/postgres-bulk-private-archive`; the final name `yravelo/postgres-bulk` belongs directly to
a new private, empty repository. Repository IDs and resource inventories prove isolation.

## 2. Authorizations consumed

```text
AUTHORIZE_OLD_REPO_RENAME
AUTHORIZE_NEW_REPO_CREATION
```

Only the exact rename and empty private-repository creation were performed. Neither authorization
was interpreted as permission to push, configure, publish or delete.

## 3. Initial local Git state

```text
branch: main
HEAD: 9eeae8d7ce790a19b6f0d1094914f543f7552f9f
origin/main: 7c66bc82c7ab32c4e0320eb6839a25c15fd2f8f3
ahead/behind: ahead 1 / behind 0
working tree: clean
origin URL: git@github.com:yravelo/postgres-bulk.git
```

Documentation, security-fast and REL1 checks passed before mutation. Because MIG0 intentionally
was not pushed, the technical preflight ran successfully in a temporary clean clone whose local
bare `origin/main` was the exact candidate HEAD; no GitHub ref was changed.

## 4. MIG0 commit preservation

The MIG0 commit remained the local `main` tip throughout the remote container operations and is an
ancestor of this report commit. It was not pushed to the archive or new repository. The local
checkout is the canonical MIG2 source, so MIG0 is preserved without unnecessary old-repository
activity.

## 5. Initial old repository identity

| Property | Baseline |
| --- | --- |
| Full name | `yravelo/postgres-bulk` |
| Repository ID | `1339652660` |
| Node ID | `R_kgDOT9l6NA` |
| Visibility | `PRIVATE` |
| Default branch | `main` |
| Archived/fork/forks | no / no / 0 |
| Issues/PRs | 0 / 6 |
| Releases/tags | 0 / 0 |
| Actions runs/artifacts | 196 / 0 |
| Runner registrations | 1: `postgres-bulk-ci-01`, online and idle |
| Description/homepage/topics | empty / empty / empty |

The old source branch was `7c66bc82c7ab32c4e0320eb6839a25c15fd2f8f3`; the local-only MIG0
commit was not part of this GitHub baseline.

## 6. Archive target availability

`PASS`. The authenticated repository lookup for
`yravelo/postgres-bulk-private-archive` returned HTTP 404 before mutation. No alternate archive name
was invented.

## 7. Rename result

`PASS`. The API rename changed only `name` from `postgres-bulk` to
`postgres-bulk-private-archive`. GitHub returned the same repository and node IDs, private
visibility, `main` default branch, non-archived state and zero forks.

## 8. Old archive final name

```text
yravelo/postgres-bulk-private-archive
```

## 9. Old archive visibility

```text
PRIVATE
```

It remains unarchived to avoid complicating rollback. No other setting was intentionally changed.

## 10. Old archive repository ID preserved

`yes` — before and after the rename: `1339652660` / `R_kgDOT9l6NA`.

## 11. Old archive PR/Actions metadata preserved

`yes`. Post-rename authenticated checks found:

```text
pull requests: 6
refs/pull/*: 6
Actions runs: 196
Actions artifacts: 0
repository runner registrations: 1
```

The runner remained `postgres-bulk-ci-01`, online and idle. The run count was 196 both before and
after mutation, so the rename generated no unexpected workflow run.

## 12. Old name became available

`yes`. After the rename, the canonical authenticated owner-repository listing returned no repository
whose name was `postgres-bulk`. The subsequent exact creation succeeded without a workaround.

## 13. New repository creation result

`PASS`. GitHub created a new repository through the user repository endpoint with only the exact
name and `private=true`. README, license, `.gitignore`, template, import and auto-initialization were
all omitted.

## 14. New repository full name

```text
yravelo/postgres-bulk
```

## 15. New repository visibility

```text
PRIVATE
```

## 16. New repository ID

```text
repository ID: 1346700826
node ID: R_kgDOUEUGGg
```

## 17. Repository IDs distinct

`yes`:

```text
old archive: 1339652660
new repository: 1346700826
```

This is a new GitHub container, not a transfer, fork or rename alias.

## 18. New repo empty

`yes`. The repository reports size 0, zero branches and no workflows. SSH `git ls-remote` completed
successfully with empty output. The Git refs endpoint reports the expected `409 Git Repository is
empty`, rather than returning old refs.

## 19. New repo initial commit count

```text
0
```

There is no default-branch ref. GitHub metadata currently reports the empty-repository placeholder
default branch name `master`; MIG2 will push explicit `main` and then set/verify `main` as default.

## 20. New repo PR count

```text
0
```

## 21. New repo refs/pull count

```text
0
```

The matching-ref endpoint's empty-repository 409 and the empty SSH ref advertisement together prove
there is no `refs/pull/*` namespace yet.

## 22. New repo workflow runs/jobs

```text
0 / 0
```

The run collection is empty. With no run, no workflow job or log can exist.

## 23. New repo Actions artifacts

```text
0
```

Actions caches are also `0` and repository workflows are `0`.

## 24. New repo forks

```text
0
```

The repository is standalone (`fork=false`).

## 25. New repo issues/releases

```text
issues and pull-request issue records: 0
releases: 0
tags: 0
```

## 26. New repo runner registrations

```text
0
```

The old runner was not moved or registered to the new repository.

## 27. New repo secrets/variables

```text
Actions secrets: 0
Actions variables: 0
environments: 0
deploy keys: 0
webhooks: 0
```

No credential or release environment was created.

## 28. Final desired URL verification

`PASS`. An authenticated lookup of `https://api.github.com/repos/yravelo/postgres-bulk` returns
new repository ID `1346700826` and HTML URL:

```text
https://github.com/yravelo/postgres-bulk
```

The name now resolves directly to the new container, not the archive ID.

## 29. SSH/HTTPS clone URL verification

The new repository metadata reports:

```text
git@github.com:yravelo/postgres-bulk.git
https://github.com/yravelo/postgres-bulk.git
```

The SSH URL was additionally queried read-only and advertised zero refs with exit status 0. No
clone URL was used for a push.

## 30. Redirect behavior after name reuse

```text
old-name redirect superseded by new repo: yes
new URL ambiguity: no
```

Before recreation, the old API name resolved to archive ID `1339652660` through GitHub's rename
handling. After recreation, the same exact repository name returns new ID `1346700826`; the archive
is reachable only under its explicit new name.

## 31. Old archive URL verification

`PASS`. The authenticated API and SSH ref advertisement at
`yravelo/postgres-bulk-private-archive` return old ID `1339652660`, `main` at
`7c66bc82c7ab32c4e0320eb6839a25c15fd2f8f3` and the six old PR refs.

## 32. Unexpected new workflow runs

```text
0
```

The archive also remained at 196 runs. No workflow was manually activated and no settings were
changed.

## 33. Local clean main preservation

`PASS`. Local `main` preserved MIG0 at `9eeae8d7ce790a19b6f0d1094914f543f7552f9f`
before this documentation change. No rewrite, fetch, reset or remote change occurred. The canonical
main history uses only the approved owner noreply email plus GitHub/Dependabot noreply identities.

The literal local `origin` URL was intentionally left unchanged. Its meaning has changed: because
the old name now belongs to the new repository, it is unsafe for blind fetch/push until MIG2 creates
explicit remotes.

## 34. Full-history privacy/Gitleaks quick check

`PASS`. Gitleaks 8.30.1 history mode scanned 206 commits across the broader local ref set and found
no leak. Security-fast also passed its current-tree scan and policy/fixture controls. The broader
scan includes local excluded refs; passing it is stricter than the canonical-main secret boundary.

## 35. Remote-handling risk assessment

`HIGH until separated`. Local `origin` still spells the final name and therefore now targets the
empty new repository, while local `origin/main` is a stale tracking ref for the archived old
repository. A fetch could delete or confuse that tracking state; a push could migrate history
prematurely. MIG1 intentionally performs neither.

MIG2 must resolve and display both repository IDs immediately before any write and must never infer
identity from the local remote name alone.

## 36. Proposed remote names for MIG2

```text
origin-old -> git@github.com:yravelo/postgres-bulk-private-archive.git
origin-new -> git@github.com:yravelo/postgres-bulk.git
```

MIG2 should first rename the existing local remote to `origin-old`, update it to the explicit archive
URL, add `origin-new`, run read-only identity/ref checks and only then consider the separately
authorized explicit main push.

## 37. Documentation/run-link inventory impact

The 27 old Actions-run links and 12 old PR/comment links identified by MIG0 now use a URL whose
repository name identifies the new empty container. They therefore no longer locate archive
resources and are broken/misleading. MIG1 does not mass-rewrite them. MIG2/MIG3 must replace current
evidence with new neutral runs and localize sanitized historical evidence before public activation.

## 38. Rollback readiness

`READY, but not authorized for execution`. Both containers remain private and the complete old
GitHub repository is intact. If MIG2 fails before public activation, a conceptual rollback can
quarantine or delete the new private repository and rename the archive back. Renaming/deleting the
new repository and restoring the old name each require explicit, target-specific future authority;
MIG1 performs none of them.

## 39. Files created/modified locally

```text
created:  docs/releases/rel1-mig1-repository-container-migration.md
modified: docs/releases/rel1-mig0-clean-repository-migration-feasibility.md
modified: docs/releases/rel1a-open-source-exposure-audit.md
modified: docs/plans/release-roadmap.md
modified: docs/README.md
```

Only documentation is changed.

## 40. Git commit/push behavior

The MIG1 report is committed locally with the approved noreply author and `[skip actions]`. It is
intentionally not pushed: the unchanged local `origin` name now resolves to the empty new repository,
and MIG1 does not authorize any ref migration. MIG2 will preserve both local documentation commits
when it explicitly pushes canonical `main` after safe remote separation.

## 41. Final local Git state

Expected after this documentation commit: clean local `main`, two commits ahead of the stale local
`origin/main` (`9eeae8d` MIG0 plus MIG1), and no behind commit. The literal remote configuration is
unchanged. The final handoff records the observed MIG1 commit SHA and status.

## 42. Final GitHub repository state

```text
old: yravelo/postgres-bulk-private-archive
old repository ID: 1339652660
old visibility: PRIVATE
old main/PR/Actions/runner state: intact

new: yravelo/postgres-bulk
new repository ID: 1346700826
new visibility: PRIVATE
new Git history/resources/runner/credentials: empty
```

The new baseline defaults are: Issues and Projects enabled; Wiki and Discussions disabled; Actions
allowed-actions `all`; SHA-pinning setting false; default workflow permission `read`; workflow PR
approval false; Dependabot alerts and automated security fixes disabled; rulesets unavailable on the
current private plan; vulnerability alerts/dependency analysis disabled with
`security_and_analysis=null`; branch protection not applicable because no branch exists. These are
observed defaults, not approved final settings. MIG2/MIG3 must configure the selected baseline.

## 43. Remote actions performed

```text
old repository renamed: yes
new repository created: yes
new repository visibility: PRIVATE
old archive visibility: PRIVATE
Git refs pushed to new repo: no
repository made public: no
old repository deleted: no
runner registered to new repo: no
Actions settings changed: no
workflow runs deleted: no
GitHub Support contacted: no
tag created: no
Benchmarks executed: no
Release executed: no
Central upload: no
publication: no
REL1-B started: no
```

## 44. MIG1 Definition of Done assessment

`PASS`. Initial Git and old-repository baselines were captured; MIG0 is preserved; the archive name
was free; the exact rename and exact empty private creation succeeded; identities, URL ownership,
clone endpoints, old metadata retention and new resource isolation were observed; the archive and
local clean main remain rollback sources; no prohibited ref, visibility, runner, settings or deletion
operation occurred.

## 45. Remaining risks/blockers

- Local `origin` now resolves to the new repository and must not be used before explicit separation.
- The new repository has unconfigured defaults and no branch; it is not ready for CI or public use.
- Current workflows still target the old self-hosted trust model and must be made hosted-safe locally
  together with their policy tests before the first push/activation.
- Old Actions/PR links embedded in docs now point at the new container name and require bounded
  cleanup/replacement.
- Public visibility, PVR, runner detachment and every deletion remain unauthorized.

## 46. MIG2 entry criteria

```text
new private repo exists: PASS
new repo empty: PASS
old private archive intact: PASS
local clean main ready: PASS after MIG1 report commit
remotes explicitly separated before any push: REQUIRED
```

MIG2 must revalidate both IDs and all zero/intact baselines immediately before proceeding.

## 47. Exact next action

Prepare, but do not execute automatically:

```text
REL1-MIG2 — Clean Main Push & Repository Baseline Recreation
```

## Boundary statement

```text
REL1-MIG1 status: DONE
old repository renamed: yes
old archive private: yes
new repository created: yes
new repository private: yes
new repository empty: yes
old GitHub metadata inherited: no
clean main pushed: no
old repository deleted: no
repository public: no
REL1-B started: no
tag created: no
Central upload: no
publication activated: no
```
