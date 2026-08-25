# REL1-MIG0 clean repository migration feasibility

Date: 2026-08-26

Decision: **MIGRATION RECOMMENDED**

Mode: **READ-ONLY / PLAN-ONLY**

## MIG1 execution addendum

MIG1 consumed the separately granted rename and creation authorizations on 2026-08-26. The old
repository is now the private `yravelo/postgres-bulk-private-archive` with its original ID
`1339652660`; a new private, empty `yravelo/postgres-bulk` exists with ID `1346700826`. No Git ref
was pushed and the archive remains intact. See the
[MIG1 repository container migration](rel1-mig1-repository-container-migration.md).

This report evaluates replacing the current private GitHub repository with a clean repository that
ultimately occupies the same public URL. It does not authorize or perform a rename, repository
creation, push, settings mutation, visibility change, deletion, release or publication.

The conclusions distinguish documented GitHub behavior from plan inferences. The principal official
references are GitHub's documentation for [renaming repositories][rename], [creating an empty
repository][create], [repository visibility and forks][visibility], [GitHub Actions billing][billing],
[GitHub-hosted runner images][runner-images], [removing self-hosted runners][runner-remove],
[security and analysis settings][security-settings], [CodeQL default setup][codeql], [private
vulnerability reporting][pvr], [rulesets][rulesets], [deleting repositories][delete] and [restoring
repositories][restore]. Repository-resource isolation is also checked against the repository-scoped
REST models for [workflow runs][runs-api], [pull requests][pulls-api], [Git references][refs-api] and
[Actions artifacts][artifacts-api].

[rename]: https://docs.github.com/en/repositories/creating-and-managing-repositories/renaming-a-repository
[create]: https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/adding-locally-hosted-code-to-github
[visibility]: https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/managing-repository-settings/setting-repository-visibility
[billing]: https://docs.github.com/en/billing/concepts/product-billing/github-actions
[runner-images]: https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md
[runner-remove]: https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/remove-runners
[security-settings]: https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/enabling-features-for-your-repository/managing-security-and-analysis-settings-for-your-repository
[codeql]: https://docs.github.com/en/code-security/how-tos/find-and-fix-code-vulnerabilities/configure-code-scanning/configure-code-scanning
[pvr]: https://docs.github.com/en/code-security/how-tos/report-and-fix-vulnerabilities/configure-vulnerability-reporting/configure-for-a-repository
[rulesets]: https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets
[delete]: https://docs.github.com/en/repositories/creating-and-managing-repositories/deleting-a-repository
[restore]: https://docs.github.com/en/repositories/creating-and-managing-repositories/restoring-a-deleted-repository
[runs-api]: https://docs.github.com/en/rest/actions/workflow-runs
[pulls-api]: https://docs.github.com/en/rest/pulls/pulls
[refs-api]: https://docs.github.com/en/rest/git/refs
[artifacts-api]: https://docs.github.com/en/rest/actions/artifacts

## 1. MIG0 result

`DONE`. A clean replacement is feasible and preferable to making the current repository public.
Every remote mutation remains deferred to separately authorized MIG1+ work.

## 2. Mode

```text
READ-ONLY / PLAN-ONLY
```

Only local documentation was changed and committed. Git and GitHub inspection was read-only.

## 3. Current repository state

The 2026-08-26 authenticated inventory found:

| Item | Current value |
| --- | --- |
| Owner/name | `yravelo/postgres-bulk` |
| Repository identity | ID `1339652660`; node ID `R_kgDOT9l6NA` |
| Visibility/default branch | `PRIVATE`; `main` |
| Current source | `7c66bc82c7ab32c4e0320eb6839a25c15fd2f8f3` before this report |
| Fork state | standalone repository; 0 forks/network count 0 |
| Issues/PRs | 0 issues; 6 historical PRs; 0 open issues |
| Releases/tags | 0/0 |
| Branches | one GitHub branch, `main`; not protected |
| Actions | enabled; 196 historical runs; 0 retained artifacts; default token read; PR approval disabled |
| Actions policy | all actions allowed; SHA-pinning setting disabled |
| Secrets/variables | 0 Actions secrets; 0 Actions variables |
| Environment | one empty `maven-central` environment, no protection rules |
| Runner | repository runner ID 22, `postgres-bulk-ci-01`, online/idle, Linux x64 |
| Webhooks/deploy keys | 0/0 |
| Rules | rulesets and branch-protection APIs unavailable to the current private plan; `main` unprotected |
| Dependency security | dependency/vulnerability alerts enabled; Dependabot alerts 0; automated fixes enabled |
| Code scanning | no available alert inventory in the current private plan |
| Metadata | description/homepage empty; topics empty |
| Surfaces | issues/projects enabled; discussions/wiki/pages disabled |
| Merge policy | merge, squash and rebase enabled; auto-merge and delete-head disabled |

The repository is clean and synchronized with `origin/main` at the inventory boundary. REL1-B has
not started.

## 4. Current Git refs inventory

Local refs before this report:

```text
refs/heads/main           7c66bc82c7ab32c4e0320eb6839a25c15fd2f8f3
refs/remotes/origin/HEAD  7c66bc82c7ab32c4e0320eb6839a25c15fd2f8f3
refs/remotes/origin/main  7c66bc82c7ab32c4e0320eb6839a25c15fd2f8f3
refs/presec6/pr2          87748657b84917c866411b9d8ad385d5de61dd79
refs/presec6/pr6          8da18883698b170b5e98a1792a570bf0f08bebff
```

GitHub exposes `refs/heads/main`, no tags and six synthetic pull-request heads,
`refs/pull/1/head` through `refs/pull/6/head`. The two `refs/presec6/*`, every remote-tracking ref,
every `refs/pull/*` and any ref discovered later other than the approved source are excluded.

## 5. Clean canonical source refs

The sole migration source is `refs/heads/main`, including all clean rewritten commits reachable
from it: 111 commits at the pre-report boundary plus the MIG0 documentation commit. Names, approved
noreply emails, dates, messages, trees, code, ADRs, documentation, security evidence and benchmarks
are preserved through normal Git reachability.

No tags are approved. In particular, `v0.1.0` does not exist and must not be created or migrated.

## 6. Current GitHub metadata/settings inventory

Git-tracked files move with `main`; GitHub repository resources and settings do not. MIG2 must
recreate only the selected metadata, Actions policy, security controls and branch rules listed in
sections 22 and 32. The old repository retains its six PRs, 196 runs, run logs/jobs and runner record
under the archive name. The new repository must begin with none of them.

## 7. Rename semantics

GitHub documents that renaming preserves repository data and redirects web and Git operations from
the old name to the renamed repository. Local remotes should nevertheless be updated. Calls to an
Action hosted by the renamed repository are not redirected. Issues and pull requests remain resources
of the renamed old repository.

For the proposed M1 rename, the old repository and its ID remain intact at
`yravelo/postgres-bulk-private-archive`; initially the old web/Git name redirects there.

## 8. Can old name be reused immediately?

```text
yes
```

GitHub's rename documentation explicitly describes creating a new repository under the previous
name and the resulting loss of redirects. That establishes supported same-owner name reuse. GitHub
does not publish a zero-second availability SLA, so MIG1 must poll for availability and fail closed
if transient propagation delays creation. No disposable or real repository experiment was needed.

## 9. Redirect behavior after name reuse

Creating `yravelo/postgres-bulk` supersedes the rename redirect. GitHub warns that redirects to the
renamed repository are permanently lost when a new repository or fork is created at the old location.
Consequently, the final web, HTTPS Git, SSH Git and REST name resolve to the **new** repository, not
the private archive.

This also means stale clones that still use the final URL will target the new repository. They must
not receive permission to force-push or add unreviewed refs.

## 10. Final URL continuity assessment

`PASS`, with a deliberate identity discontinuity. The following final names remain unchanged for
users and Maven metadata:

```text
https://github.com/yravelo/postgres-bulk
https://github.com/yravelo/postgres-bulk.git
git@github.com:yravelo/postgres-bulk.git
```

Continuity is name-level only. Repository ID, PR numbers, issues, Actions URLs, stars/watchers and
other platform resources restart on the new repository.

## 11. Repository-ID isolation assessment

`PASS`, subject to an observed MIG1 verification gate. A newly created repository receives a new
repository ID; the current ID `1339652660` remains with the renamed archive. GitHub's REST resources
for runs, jobs, artifacts, pulls, issues and refs are repository-scoped, and Actions artifact payloads
explicitly carry `repository_id`.

No GitHub document promises resource migration merely from reusing a name. The strong conclusion
from those documented models is that platform resources remain attached to the old repository ID.
MIG1 must verify rather than assume: `new.id != 1339652660`, and every new-repository count must be
zero before any workflow is enabled.

## 12. Old PR inheritance

`No`. The six old PRs remain with the archive repository ID. MIG1 acceptance requires `0` pull
requests in all states on the new repository. This is a repository-creation operation, not a transfer
or fork, so no PR relationship is established.

## 13. Old refs/pull inheritance

`No`. GitHub-generated `refs/pull/*` are not part of the explicit `main:main` refspec and are never
pushed. The new repository must expose no `refs/pull/*` until it receives its own pull request.

## 14. Old Actions runs/jobs inheritance

`No`. The 196 old runs, their jobs and logs stay with the archive. The new Actions API baseline must
be zero runs/jobs/artifacts before deliberate validation. Workflow YAML migrates as Git content;
execution records do not.

## 15. Old issues inheritance

`No`. The current repository has no issues, but the new issue tracker is still a distinct empty
resource. Enabling Issues recreates the capability, not its records.

## 16. Fork-network implications

The current repository is not a fork and has zero forks, so there is no fork network to migrate or
detach. The new repository must be created standalone, not from a template or fork. GitHub documents
that private-to-public visibility changes detach private forks; the MIG4 preflight must reconfirm
that the new repository still has zero forks before the visibility change.

## 17. New repository initial visibility recommendation

Create it `PRIVATE`. This permits identity/settings/CI verification without exposing an incomplete
configuration or clean history. Public visibility is a separate MIG4 authorization and gate.

## 18. New repo initialization strategy

Create a completely empty repository: no README, `.gitignore`, license, template or import. GitHub
recommends avoiding those initializers when adding existing local code because they introduce a
competing root commit. Set `main` as the default after the explicit first push.

## 19. Git push strategy

Use a dedicated clean clone and a new remote, then push only the audited branch:

```bash
git push origin-new refs/heads/main:refs/heads/main
```

Before the push, compare `git rev-parse main`, commit count, reachable email inventory, full-history
Gitleaks result and a main-only bundle against the approved source. Do not push tags or wildcard
refspecs.

## 20. Why `--mirror` is prohibited

`--mirror` includes refs outside canonical `main`, including local `refs/presec6/*`, remote-tracking
refs or other future refs. It defeats the resource-isolation objective and can publish historical
objects that the clean branch intentionally excludes. Only a literal main-to-main refspec is safe.

## 21. Tags strategy

Push no tags. Re-audit and authorize each release tag separately in REL2. `v0.1.0`, GitHub Release
creation, signing and Central publication remain prohibited.

## 22. New repository metadata recreation inventory

MIG2 must explicitly create and then read back:

- description and eight topics from sections 23–24; empty homepage;
- Issues enabled; Projects, Discussions, Wiki and Pages disabled initially;
- `main` default; squash merge enabled; merge commits disabled; rebase optional after policy review;
  auto-merge disabled; head-branch deletion enabled;
- Actions initially disabled, then hosted-safe; default `GITHUB_TOKEN` read-only and no PR approval;
- no secrets, variables, deploy keys, webhooks or release environment;
- dependency graph, Dependabot alerts/security updates and version-update configuration;
- a public-plan ruleset for `main`, after new check names have appeared;
- CodeQL, Dependency Review, secret scanning/push protection where the plan makes them available,
  PVR and Scorecard at the public gate;
- no copied runner registration, run history, artifacts, caches, environment or repository ID.

Every mutable setting needs a post-write GET/read-back record in MIG2–MIG4.

## 23. Description recommendation

```text
High-throughput PostgreSQL bulk insert and lookup for Java, Spring Data JPA and Spring Data JDBC.
```

Apply in MIG2, not MIG0.

## 24. Topics recommendation

```text
postgresql
java
spring-boot
spring-data
jdbc
jpa
bulk-insert
copy
```

They accurately cover the published modules without promising a release that does not yet exist.

## 25. Issues/Discussions/Wiki recommendation

Enable Issues for defect and feature intake. Keep Discussions and Wiki disabled until there is a
moderation/content need; canonical documentation stays versioned. Disable repository Projects and
Pages initially because neither is used. Reassess these surfaces after public stabilization.

## 26. Public CI architecture

All routine Build, Compatibility and Security validation for pushes and external PRs should use
standard GitHub-hosted `ubuntu-24.04`/`ubuntu-latest` runners, `contents: read`, no secrets and no
write-capable token. Fork PR code must never reach a persistent self-hosted host.

Trusted main uses the same hosted validation. Benchmarks remain manual and non-release. Signing and
Central work remain a separate, authorization-gated REL2 path with no pull-request trigger.

## 27. GitHub-hosted public runner availability/cost

GitHub documents that standard GitHub-hosted runners are free for public repositories. Larger
runners remain billed. Therefore routine CI can operate without paid minutes after MIG4. While the
new repository is private, validation consumes the owner's included private quota; keep MIG3
deliberate and bounded.

Concurrency, job-duration and storage limits still apply. The design uses standard x64 Ubuntu, not
larger or `ubuntu-slim` runners.

## 28. Docker/Testcontainers feasibility

`PASS`. GitHub's current Ubuntu 24.04 runner image inventory includes Docker client/server, Maven and
Java tooling. That supports the project's PostgreSQL Testcontainers integration coverage on hosted
x64 Ubuntu. MIG3 must prove it with the complete Build and 11-cell Compatibility jobs; documentation
alone is not the acceptance result.

## 29. Self-hosted runner role after migration

The neutral `postgres-bulk-ci-01` runner has no routine role in the public repository. It must not be
registered there during MIG1–MIG4. A future dedicated benchmark need requires a separate threat
model and authorization; it must never accept fork PRs or release secrets.

## 30. Runner migration plan

Keep the current runner registered only to the private archive through early rollback. Once hosted
Build, Compatibility and Security pass on the new repository, stop its service, remove its
repository-level archive registration using GitHub's documented removal flow, and verify zero
archive runners. Do not reuse its registration token or metadata. Re-registration anywhere is a
separate operation.

If rollback occurs before detachment, the runner remains available to the restored old repository.
If rollback occurs after detachment, restoring it requires fresh explicit registration credentials.

## 31. Actions first-run safety plan

1. Create the new private repository empty and immediately disable Actions before any push.
2. Verify no repository runner, secret, variable, environment, workflow run or artifact exists.
3. On the clean local branch, create the MIG2 workflow-safety commit: replace self-hosted labels in
   Build/Compatibility/Security with standard hosted Ubuntu, add deliberate dispatch support, and
   update the workflow-security policy/tests that currently require the private self-hosted labels.
4. Run local workflow/security checks; push only `main` while Actions remains disabled.
5. Configure read-only workflow permissions and the allowed-action policy.
6. Enable Actions, manually dispatch one workflow at a time and inspect runner labels/permissions.
7. Only after successful neutral runs, add required status checks to the `main` ruleset.

No first push can be assigned to the old runner because it is not registered to the new repository;
disabling Actions additionally prevents misleading queued runs.

## 32. Security features recreation plan

The exact fail-closed order is:

1. private empty repository, Actions disabled, zero credentials and identity-baseline checks;
2. main-only push with hosted-safe workflows;
3. read-only workflow token, fork-approval policy and Actions allow policy;
4. dependency graph, Dependabot alerts, security updates and tracked `.github/dependabot.yml`;
5. hosted CI execution, full-history Gitleaks and security-fast/full validation;
6. `main` ruleset: block deletion/force push, require PR and successful Build/Compatibility/Security
   checks for future changes, restrict bypass;
7. immediately after MIG4 public visibility: confirm dependency review, enable CodeQL default setup
   for Java/Kotlin, supported secret scanning/push protection, Scorecard and PVR;
8. test anonymous read/clone, a benign private vulnerability report and all public security APIs;
9. reevaluate attestations/provenance in REL2 when release artifacts exist.

GitHub documents that dependency graph and Dependency Review are enabled for public repositories,
that CodeQL default setup is available to public repositories with Actions enabled, and that PVR is
available to public repository administrators.

## 33. Repository secrets plan

Expected count through REL1: `0`. Do not create Maven Central, Portal, GPG, GitHub PAT or runner
secrets. Do not recreate the empty `maven-central` environment until REL2 has its own authorized
secret-boundary design. Every PR workflow must remain valid with an empty secret set.

## 34. New Actions-history baseline expectation

Before MIG3:

```text
workflow runs: 0
jobs: 0
logs: 0
artifacts: 0
caches: 0
```

After MIG3, only newly generated neutral validation records are acceptable. Any old run ID or count
is a hard stop and evidence that the wrong repository was queried.

## 35. SCM URL impact

The parent POM and documentation already use the intended final URL, so no final SCM/project URL
change is required. During the private overlap, operators must use unambiguous `origin-old` and
`origin-new` remotes; public documentation must never link to the archive name.

## 36. Old Actions-run links inventory

There are 27 live old-run links:

| File | Count |
| --- | ---: |
| `docs/architecture/compatibility-evidence.md` | 4 |
| `docs/security/continuous-security-validation.md` | 3 |
| `docs/security/dependabot-review-2026-08.md` | 8 |
| `docs/releases/rel0-final-release-readiness.md` | 3 |
| `docs/releases/rel1a-open-source-exposure-audit.md` | 3 |
| `docs/releases/rel1ar-public-history-remediation.md` | 3 |
| `docs/releases/rel1ar-runner-identity-remediation.md` | 3 |

Once the old name belongs to the new repository, those URLs query nonexistent new-repository run
IDs and break. MIG2 must remove their hyperlinks, retain sanitized run IDs/conclusions as historical
text where useful, and replace current acceptance evidence with new hosted-run links.

## 37. Old PR links inventory

`docs/security/dependabot-review-2026-08.md` contains 12 live links concerning old PRs #1–#6: six
PR links and six anchored comment links. MIG2 must remove the URLs/anchors while retaining PR
numbers, dependency decisions and locally reviewable evidence. The new repository must not imply
that those PR numbers exist there.

## 38. Documentation self-containment plan

Before MIG4, run a repository-wide URL audit and ensure no public claim depends on archive access.
Convert old GitHub UI evidence into sanitized local tables; generate fresh clean-repository CI links
for current claims; update `SECURITY.md` from private-state language to tested PVR plus fallback
instructions; retain stable final SCM URLs. No GitHub commit/tree/blob URL currently needs cleanup.

The private archive may support owner audit/rollback, but it is not a public documentation backend.

## 39. Migration checkpoints M0–M10

| Checkpoint | State and exit gate |
| --- | --- |
| M0 | Current private repository untouched; MIG0 committed; authorizations absent |
| M1 | Old repository renamed private; archive ID/metadata intact; name available |
| M2 | New empty private repository created; new ID and zero-resource baseline proven |
| M3 | Hosted-safe canonical `main` explicitly pushed; no other refs/tags |
| M4 | Metadata, Actions policy, dependency security and provisional rules recreated |
| M5 | New hosted CI/security/full-history/privacy checks pass; docs self-contained |
| M6 | Separately authorized new repository visibility changed to public |
| M7 | Anonymous clone/API/docs/examples, hosted CI, CodeQL/PVR/security settings pass |
| M8 | Condition-based stabilization/rollback observation complete |
| M9 | Owner separately supplies `AUTHORIZE_OLD_PRIVATE_REPOSITORY_DELETION` |
| M10 | Archive deleted and final public state reverified; never part of MIG1–MIG4 |

Each checkpoint records old/new repository IDs, names, visibility, refs and gate result before
advancing.

## 40. Rollback before new repo creation

At M1, if the name is unavailable or creation cannot proceed, stop and rename
`postgres-bulk-private-archive` back to `postgres-bulk` under the bounded rollback scope of
`AUTHORIZE_OLD_REPO_RENAME`. Verify ID `1339652660`, visibility, `main`, PRs and Actions history.
The temporary rename redirect then becomes irrelevant because the original object is back.

## 41. Rollback after new private repo creation

Do not delete implicitly. Keep both repositories private and stop. With separate authorization,
rename the failed new repository to a quarantine name, then rename the archive back; this preserves
both objects. Deleting the failed new private repository is an alternative only under an explicit,
target-specific deletion authorization. Recheck remotes before any subsequent push.

## 42. Rollback after PUBLIC

Public exposure cannot be undone: third parties may retain clones, commit IDs and URLs even if the
new repository later becomes private or is renamed. A technical rollback can stop writes, make the
new repository private, quarantine its name and restore the archive, but cannot retract already
published Git objects. Therefore M6 requires stricter evidence than pre-public checkpoints and the
archive remains private as a recovery source.

## 43. Old archive retention strategy

Retain the archive until **all** conditions hold, not merely until a date:

- M7 anonymous clone/API, docs/examples and public settings pass;
- all expected hosted Build, 11/11 Compatibility, Security and CodeQL results pass;
- full-history privacy/Gitleaks and public-ref inventory remain clean;
- PVR reporter/maintainer flow and fallback channel pass;
- no missing evidence/resource is discovered during at least one normal maintenance/security cycle;
- backups and rollback records identify both repository IDs;
- the owner reviews the deletion impact and separately authorizes it.

Time is only a secondary safeguard; satisfying a fixed number of days alone is insufficient.

## 44. Old archive isolation strategy

Keep `postgres-bulk-private-archive` private, archived/read-only after rollback confidence is
established, excluded from new development, public links, releases and publication. Disable Actions
after new CI passes and detach the runner deliberately. Maintain zero new secrets/webhooks/deploy
keys. Owner access exists solely for evidence and rollback.

## 45. Old repository deletion consequences

Deletion removes the archive's issues, PRs, Actions resources, settings and repository identity from
normal access; private forks would also be deleted. GitHub documents that some deleted repositories
may be restorable within 90 days, but restoration is conditional, can take time and does not restore
team permissions. That is emergency recovery, not a retention policy. The final URL already belongs
to the new repository, so deleting the archive must not be used as a name-reuse step.

## 46. Final deletion authorization gate

The only acceptable gate is:

```text
AUTHORIZE_OLD_PRIVATE_REPOSITORY_DELETION
```

It is intentionally not requested in MIG0 or MIG1.

## 47. Migration vs Support comparison

| Criterion | Clean migration | GitHub Support cleanup |
| --- | --- | --- |
| Privacy certainty | High for the new public ID; old metadata remains private | Dependent on Support's ability/scope to remove managed history |
| Complexity | Higher: settings, CI and docs must be rebuilt | Lower if Support accepts and completes all requested deletion |
| Destructive risk | Controlled by retaining archive; final deletion separately gated | May delete managed evidence within the only repository |
| GitHub dependency | Ordinary documented operations; low after name reuse | High; R6 is blocked pending manual owner submission/eligibility |
| Evidence preservation | Full old repository retained privately during stabilization | Depends on exactly what Support removes |
| URL continuity | Final name preserved, repository ID changes | Name and repository ID preserved |
| Rollback | Strong before public; archive remains intact | Limited once Support deletes managed resources |
| Operational burden | High but deterministic and testable | Lower implementation burden, uncertain queue/outcome |

Support remains a valid fallback, but it is not required to unblock a clean public repository.

## 48. Migration risks

1. transient name reuse or redirect surprise;
2. a stale clone or wrong remote pushes old/unwanted refs;
3. repository settings/security controls are omitted;
4. a first workflow targets self-hosted infrastructure or gains excess permission;
5. old run/PR links become broken or misleading;
6. the private archive is accidentally exposed or used for development;
7. the archive is deleted before public stabilization;
8. private-to-public activation reveals an unscanned object or unsafe fork behavior;
9. public rollback is misunderstood as revoking already cloned data.

## 49. Mitigations

| Risk | Exact mitigation |
| --- | --- |
| Name/redirect | M1 poll plus ID/name checks; stop and restore old name if M2 cannot begin |
| Wrong push | separate clean clone; `origin-old`/`origin-new`; literal `main:main`; no `--mirror`; server ref audit |
| Lost settings | declarative inventory, ordered API writes and read-back evidence at M4 |
| CI trust | Actions disabled before push; no new runner/secrets; hosted workflow commit; deliberate dispatch |
| Broken links | 27-run/12-PR inventory; localize evidence; create fresh new-repo validation links |
| Archive exposure | private + archived, no links/releases/development, Actions disabled after rollback gate |
| Early deletion | condition-based M8 plus exact M9 deletion authorization |
| Hidden content/forks | clean bundle, full-history Gitleaks, explicit remote-ref/fork checks immediately before M6 |
| Public rollback limit | owner GO at M6 acknowledges irreversible third-party cloning |

## 50. MIGRATION verdict

```text
MIGRATION RECOMMENDED
```

The final name is reusable, platform resources are isolated by the new repository identity, an
explicit main-only push is sufficient, settings and hosted CI can be recreated, pre-public rollback
is viable, documentation can be made self-contained and the archive can remain private. The result
is conditional on observed M1/M2 ID and zero-resource checks; any mismatch stops the migration.

## 51. Proposed MIG1–MIG5 phases

- **MIG1 — Rename Old Repo & Create Clean Private Replacement:** execute M1–M2 only; prove IDs,
  name routing and empty baseline.
- **MIG2 — Push Clean Main & Recreate Metadata/Settings:** prepare hosted-safe workflows, push only
  `main`, remove stale external evidence links and recreate selected settings.
- **MIG3 — CI/Security Validation on New Private Repo:** deliberate hosted runs, privacy/Gitleaks,
  ruleset and security validation; detach old runner only after PASS.
- **MIG4 — Public Activation & Anonymous Verification:** separately authorize visibility, enable
  public security features/PVR and complete M6–M8.
- **MIG5 — Old Private Archive Decommission/Delete:** only after the retention gates and a separate
  destructive authorization; complete M9–M10.

## 52. Exact next owner authorization(s)

```text
AUTHORIZE_OLD_REPO_RENAME
AUTHORIZE_NEW_REPO_CREATION
```

They authorize only MIG1's named operations and the old-name restoration portion of rollback. They
do not authorize any deletion, push, visibility change, runner change or public activation.

## 53. Execution sequence

Non-executed command/API plan:

```text
1. Re-run M0 clean-tree, source-SHA, ref, privacy and GitHub inventory checks.
2. PATCH old repository name -> postgres-bulk-private-archive; keep private.
3. GET archive; assert id=1339652660 and old metadata/counts intact.
4. POST a new private, non-initialized postgres-bulk repository.
5. GET new repository; assert new id, private, empty, standalone and zero resources.
6. Disable Actions on the new repository; set default workflow permission read.
7. Prepare and locally validate the hosted-safe MIG2 commit in a clean migration clone.
8. Add explicit origin-old and origin-new; verify their repository IDs before write.
9. git push origin-new refs/heads/main:refs/heads/main
10. Verify server exposes only refs/heads/main and no tags/refs/pull.
11. Recreate selected metadata/security settings with read-back after each group.
12. Enable Actions and dispatch hosted Build, Compatibility and Security deliberately.
13. Complete MIG3 gates; request separate MIG4 visibility authorization.
```

No `--mirror`, wildcard refspec, tag push, import, transfer or remote replacement is permitted.

## 54. Verification checklist

- [ ] old archive is private and ID `1339652660`;
- [ ] new repository ID differs and `fork=false`;
- [ ] new pre-push commits/branches/tags/PRs/issues/runs/jobs/logs/artifacts/caches are zero;
- [ ] local source is clean canonical `main`; reachable identities use approved noreply addresses;
- [ ] full-history Gitleaks and repository link/privacy audit pass;
- [ ] post-push server refs equal only `refs/heads/main`; source/target SHA and count match;
- [ ] no tag, `refs/pull/*`, `refs/presec6/*` or remote-tracking ref is reachable by server ref;
- [ ] no secrets, variables, environment, hook, deploy key or self-hosted runner exists;
- [ ] metadata, Actions/security settings and rule read-backs equal the plan;
- [ ] Build and Security pass on hosted Ubuntu; Compatibility is 11/11 PASS with Testcontainers;
- [ ] new run/job labels are GitHub-hosted and new history contains no old IDs;
- [ ] 27 old-run and 12 old-PR links are removed/localized; fresh evidence is valid;
- [ ] POM SCM URLs resolve to the new repository;
- [ ] immediately before public: zero unexpected refs/forks and all MIG3 gates PASS;
- [ ] after public: anonymous clone/API/docs/examples, CodeQL, Dependency Review and PVR PASS;
- [ ] archive remains private until M9 exact authorization.

## 55. Report location

`docs/releases/rel1-mig0-clean-repository-migration-feasibility.md`

## 56. Documentation changes

This report is added to the documentation index. The release roadmap records MIG0 as completed and
the clean migration as recommended without starting REL1-B or MIG1. No historical run/PR link is
modified during plan-only MIG0; that bounded cleanup belongs to MIG2 so replacement evidence can be
recorded coherently.

## 57. Local validation

Required after the documentation commit:

```text
git diff --check
./scripts/check-documentation.sh
./scripts/check-security.sh fast
./scripts/check-release-security-preflight.sh technical
./scripts/check-release-security-preflight.sh rel1
```

Because a GitHub push is prohibited, the technical preflight must run after commit in a temporary
clean clone whose local bare `origin/main` is the exact MIG0 candidate. This exercises the preflight's
clean/synchronized invariant without mutating GitHub. The final handoff records actual outcomes. A
full reactor is not required because MIG0 changes only documentation and the requested
fast/preflight gates cover the affected policy boundary.

## 58. Git commit

Planned coherent commit:

```text
docs(release): recommend clean repository migration [skip actions]
```

Author identity is `Yusnier Blanco Ravelo <29708813+yravelo@users.noreply.github.com>`. No push is
authorized.

## 59. Final Git state

Expected after commit: clean local `main`, one MIG0 documentation commit ahead of `origin/main`, no
tag and no remote mutation. The handoff must replace this expectation with the observed SHA/status.

## 60. Remote actions performed

```text
old repository renamed: no
new repository created: no
new remote added: no
Git refs pushed: no
repository visibility changed: no
old repository deleted: no
GitHub Support contacted: no
workflow runs deleted: no
runner changed: no
public CI activated: no
GitHub PVR enabled: no
tag created: no
Benchmarks executed: no
Release executed: no
Central upload: no
publication: no
REL1-B started: no
```

Only read-only GitHub API/web documentation queries were performed.

## 61. MIG0 Definition of Done assessment

`PASS`, pending only the mechanical recording of final validation/commit results in the handoff.
The inventory, clean source, rename/name reuse, repository-ID isolation, PR/Actions isolation, fork
impact, private bootstrap, explicit push, settings/security recreation, hosted CI/Testcontainers,
runner plan, documentation impact, rollback/retention, migration comparison, risks, phases and exact
next authorizations are all defined. No prohibited remote action occurred.

## 62. Remaining blockers

MIG1 is blocked solely on the two owner authorizations in section 52. MIG4 later requires explicit
public-visibility authorization and acceptance of irreversible third-party cloning. MIG5 later
requires the exact deletion authorization in section 46. REL2 signing/token prerequisites remain
outside this migration and do not block MIG1–MIG4.

## 63. Exact next action

Prepare MIG1, but do not execute it until the owner supplies both:

```text
AUTHORIZE_OLD_REPO_RENAME
AUTHORIZE_NEW_REPO_CREATION
```

## Boundary statement

```text
REL1-MIG0 status: DONE
mode: READ-ONLY
clean migration feasible: yes
old name reusable: yes
final URL continuity: PASS
old GitHub metadata inherited by new repo: no
rollback before public: READY
migration verdict: RECOMMENDED
old repository renamed: no
new repository created: no
old repository deleted: no
repository public: no
REL1-B started: no
tag created: no
Central upload: no
publication activated: no
```
