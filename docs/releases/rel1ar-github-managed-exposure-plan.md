# REL1-A-R R5 GitHub-managed historical exposure remediation plan

Audit date: 2026-08-26
Repository: `yravelo/postgres-bulk` (`PRIVATE`)
Mode: **READ-ONLY**
Decision: **R5 DONE — REMEDIATION PLAN READY; OPEN-SOURCE ACTIVATION NO-GO**

Exact former runner, host, email and owner-path values are deliberately absent. This report
separates official documentation, observed API behavior, inference and unknown platform behavior.

## R6 authorized-contact addendum

R6 reverified the exact R5 baseline under `AUTHORIZE_GITHUB_SUPPORT_CONTACT`: repository
`PRIVATE`, six synthetic PR refs, 58 affected runs and 270 affected jobs. The official GitHub
Support portal requires interactive owner authentication that is unavailable in the execution
environment, so no request was submitted and no case ID exists. An exact sanitized single-ticket
message is ready for owner submission; B4 and B5 remain `PENDING` and no destructive action was
taken. See [R6 GitHub Support contact](rel1ar-github-support-remediation.md).

## 1. R5 result

`DONE`. The two remaining GitHub-managed blockers were reconstructed and a minimal, conditional
remediation plan is ready. No destructive remediation was performed.

## 2. Mode

`READ-ONLY`. The only repository mutation authorized by R5 is this documentation-only commit.

## 3. Current repository and Git state

Initial `HEAD == origin/main == 79290b17943378d500650ca1963b5b5852d6d7dd`; worktree clean,
default branch `main`, repository `PRIVATE`, 196 workflow runs, zero Actions artifacts, zero tags
and zero releases. R5 did not change visibility, runner, hostname or GitHub-managed resources.

## 4. Workflow runs re-inventoried

The authenticated REST re-inventory queried run metadata, all job attempts with `filter=all`, log
availability and artifacts for the exact 58-run R3 set. All 58 remain available and completed:

| Workflow | Runs | Jobs | Old-name jobs | Neutral jobs | Blank `runner_name` |
| --- | ---: | ---: | ---: | ---: | ---: |
| Build | 23 | 24 | 23 | 1 | 0 |
| Compatibility | 24 | 275 | 236 | 11 | 28 |
| Security | 11 | 12 | 11 | 1 | 0 |
| **Total** | **58** | **311** | **270** | **13** | **28** |

Events are 47 `push` and 11 `workflow_dispatch`; creation spans 2026-08-24T20:29:36Z through
2026-08-25T19:39:45Z. Conclusions are 52 success, 2 failure and 4 cancelled. Fifty-five runs have
one attempt; three have two attempts.

## 5. Historical affected runs

All `58/58` runs still contain at least one job whose metadata has the former runner display name.
The 55 older runs are superseded. The three R4C canonical runs are also affected because their old
attempt and neutral attempt share one run ID.

## 6. Historical affected jobs

`270/311` jobs expose `<OLD_RUNNER_NAME_REDACTED>`, `13/311` expose the neutral name and `28/311`
have an empty runner name. No historical job metadata contains the former OS hostname or owner
path; those values were log-only and the sensitive old logs remain deleted.

## 7. Persisted job metadata fields

Observed in every returned job record, although nullable where appropriate: job `id`, `run_id`,
`name`, `status`, `conclusion`, `started_at`, `completed_at`, `runner_id`, `runner_name`,
`runner_group_id`, `runner_group_name`, `labels` and `steps` with their names, numbers, timestamps,
statuses and conclusions. The [official workflow-jobs response schema](https://docs.github.com/en/rest/actions/workflow-jobs)
also includes `runner_name`, labels and steps.

## 8. Public unauthenticated visibility assessment

**Documented fact:** GitHub says the job get/list endpoints can be used without authentication for
public resources, and a PRIVATE-to-PUBLIC change makes Actions history and logs visible to everyone.
See [workflow jobs REST API](https://docs.github.com/en/rest/actions/workflow-jobs) and
[repository visibility consequences](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/managing-repository-settings/setting-repository-visibility).

**Observed API behavior:** an anonymous request against a current public GitHub repository returned
five jobs, with `runner_name` present and non-empty in all five. The target repository itself was
not made public and therefore was not anonymously tested.

## 9. `runner_name` public exposure verdict

`PUBLICLY QUERYABLE AFTER PUBLIC`. This follows directly from the public-resource authentication
rule plus the documented and observed job payload. The 270 former names are an activation blocker.

## 10. Individual job metadata deletion capability

`NO DOCUMENTED OWNER CAPABILITY`. The official job API exposes get, list and log-download
operations, but no edit, redact or delete operation for a job or `runner_name`. The absence of a
documented endpoint does not prove an internal GitHub capability does not exist.

## 11. Whole-run deletion capability

`SUPPORTED, NOT EXECUTED`. `DELETE /repos/{owner}/{repo}/actions/runs/{run_id}` permanently deletes
a completed run and requires Actions write permission. See
[workflow run REST API](https://docs.github.com/en/rest/actions/workflow-runs) and
[deleting a workflow run](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/delete-a-workflow-run).

## 12. Whole-run deletion consequences

Deleting all 58 runs removes their run pages/API records and the 311 child job records, including
the 270 exposed names, 13 neutral jobs, 28 blank jobs, all attempts, timestamps, conclusions and
step evidence. Three currently available neutral log archives are lost; the other 55 logs are
already unavailable. Associated artifacts would also be deleted, but the observed count is zero.
GitHub explicitly documents artifact deletion with a deleted run; whether every related Checks API
record disappears is `UNKNOWN` and belongs in post-delete verification.

Retention is not an alternative metadata remedy: GitHub's configurable retention applies to
artifacts and log files, not run/job metadata. See
[Actions retention settings](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/enabling-features-for-your-repository/managing-github-actions-settings-for-a-repository).

## 13. Canonical neutral CI evidence available

Current neutral evidence is sufficient while retained: Build `32890808627` attempt 2 passed one
job, Compatibility `32890808601` attempt 2 passed 11/11, and Security `32890829062` attempt 2
passed one job, all on rewritten commit `6f70623c3ba75a5dd231235073ef65224e50c5e3`, an ancestor of
current `main`. All 13 jobs have the neutral runner name and neutral logs.

It is not sufficient *after deletion* because all three IDs are in the 58-run delete set. Before
any deletion, create a new ordinary evidence-refresh commit on private `main` so Build and
Compatibility obtain distinct run IDs, dispatch Security on the same SHA, verify 13 neutral jobs
and privacy-clean logs, and record those replacement IDs. Re-running the existing runs is
insufficient because another attempt remains under the same deletable run ID.

## 14. Documentation links affected

There are 23 direct hyperlinks to affected runs across six files: 3 each in the REL0 report,
REL1-A audit, R2B report, R4C report and continuous-security report, plus 8 in the Dependabot
review. Deletion without a documentation pass leaves all 23 targets unavailable.

Classification after deletion:

- `REPLACE`: the 3 R4C canonical links and corresponding plain references, using the future fresh
  neutral Build/Compatibility/Security IDs.
- `REMOVE LIVE LINK, RETAIN SANITIZED HISTORICAL ID`: the other 20 links, annotating that privacy
  remediation deleted the remote run while preserving the historical result claim as documentary
  evidence.
- `BROKEN AFTER DELETE`: all 23 if the required documentation update is omitted.

## 15. OPTION A — delete affected runs

Privacy result: removes the only documented owner-accessible container for the 270 exposed job
records. Evidence cost: loses 58 run pages, 311 jobs, 55 superseded results plus the current 13-job
neutral evidence, and invalidates 23 direct links. Preconditions: generate and verify three fresh
canonical runs, preserve a sanitized final inventory, update documentation, and receive explicit
`AUTHORIZE_WORKFLOW_RUN_DELETION`. Irreversible; verify every run/job endpoint afterward.

## 16. OPTION B — keep affected runs

Evidence result: preserves all historical and current run evidence. Privacy result: 270 former
runner names remain anonymously queryable after PUBLIC, so the repository must stay private unless
GitHub Support provides and performs a job-metadata-only remedy or the owner explicitly revises the
privacy acceptance decision. Merely waiting for log/artifact retention does not close the blocker.

## 17. GitHub Support job-metadata capability

`UNKNOWN — ASK SUPPORT`. No official documentation found for redacting or purging historical job
metadata without deleting its workflow run. GitHub's sensitive-data cleanup guidance discusses
Git objects, PR references and cached views, not Actions job rows.

## 18. Workflow-run recommendation

Use a Support-first conditional plan. Ask once whether GitHub can redact/purge the 270 job records
without deleting runs. If GitHub confirms and performs a granular remedy, keep the runs. If GitHub
cannot, generate independent neutral evidence and then choose Option A; deletion is the only
documented owner-controlled route that closes public queryability.

## 19. Owner decision required for runs

`yes`, but not yet. The deletion decision should be taken after Support answers the granular-remedy
question and after replacement neutral CI exists. R5 does not consume deletion authorization.

## 20. Synthetic PR ref count

Expected `6`; observed `6`. GitHub advertises only six `head` refs and zero `merge` refs.

## 21. Synthetic ref inventory — sanitized

| Ref | Head SHA | State | Result | Source | Base | Current-main ancestor |
| --- | --- | --- | --- | --- | --- | --- |
| `refs/pull/1/head` | `cc44c9c88a8a` | closed | merged | same-repo Dependabot | `main` | no |
| `refs/pull/2/head` | `87748657b849` | closed | merged | same-repo Dependabot | `main` | no |
| `refs/pull/3/head` | `69f3fbb942d8` | closed | unmerged | same-repo Dependabot | `main` | no |
| `refs/pull/4/head` | `5536cab947ae` | closed | unmerged | same-repo Dependabot | `main` | no |
| `refs/pull/5/head` | `c56f28d6888a` | closed | merged | same-repo Dependabot | `main` | no |
| `refs/pull/6/head` | `8da18883698b` | closed | merged | same-repo Dependabot | `main` | no |

The deleted source branches are absent; only `main` is a normal remote branch.

## 22. Old history reachable through refs

`yes`. Each head is outside rewritten `main` and traverses 70–90 pre-rewrite commits. Each chain
contains the former personal email in immutable author/committer metadata. Authenticated Git fetch
and commit API checks succeeded for all six heads while the repository remains private.

## 23. Privacy-sensitive blobs reachable through refs

`no known sensitive blob; privacy-sensitive commit metadata remains reachable`. Targeted scans of
every commit/tree reachable from each head found neither the old signing-path blob nor personal
email in file content. The prior exhaustive audit found no other sensitive blob category. However,
all six chains retain the former email in commit objects, so the refs still preserve personal data.

Separately, the old pre-rewrite main tip remains retrievable by authenticated SHA and contains the
historical signing-path content. That cached-object exposure is not provided by the six PR heads
and must be included independently in the Support request.

## 24. Owner delete/force-push capability

`no` for `refs/pull/*`. GitHub documents the namespace as read-only and rejects pushes to it. The
owner can neither force-push nor delete those refs through normal Git. See
[checking out pull requests locally](https://docs.github.com/en/pull-requests/how-tos/review-pull-requests/checking-out-pull-requests-locally)
and [sensitive-data removal](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository).

The ordinary pull-request REST surface supports closing/updating, not owner deletion of a PR or
its synthetic refs. No destructive probe was attempted.

## 25. Public visibility implications

**Documented fact:** PR head refs can be fetched, GitHub retains changes for inactive pull requests,
and public code/Actions history becomes visible to everyone. **Inference:** if the six refs and
cached objects remain when the repository becomes PUBLIC, unauthenticated users will be able to
fetch/navigate the pre-rewrite commits that are currently available only with private-repository
read access. Anonymous verification is intentionally deferred until REL1-B, after cleanup.

## 26. Closed/merged PR ref persistence

**Observed:** all six PRs have been closed since 2026-08-25, four merged and two unmerged, yet all
six head refs persist. **Documented:** GitHub stores inactive PR changes and provides a fetch path;
history-rewrite guidance explicitly anticipates affected read-only PR refs.

## 27. Source branch deletion effect

Insufficient for cleanup. All six Dependabot branches are already absent while all six PR head
refs remain advertised. GitHub also documents that changes of an inactive PR remain remotely
available. Deleting or restoring source branches was not attempted.

## 28. GitHub Support cleanup guidance

After the controlled refs are rewritten, GitHub instructs the owner to give Support the repository,
affected-PR count, first changed commit(s) and LFS status. If eligibility and prerequisites are
met, Support can dereference/delete affected PRs, run server garbage collection and remove cached
views. See [fully removing sensitive data from GitHub](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository#fully-removing-the-data-from-github).

## 29. Support eligibility for this privacy case

`UNKNOWN — MATERIAL RISK OF DECLINE`. GitHub explicitly says Support will not remove non-sensitive
data and frames assistance around sensitive data whose risk cannot be mitigated by credential
rotation. The findings here are personal email/host/path data, not a live secret. R5 cannot claim
eligibility; the sanitized request must ask for a classification without reproducing the values.

## 30. Support data required

- Repository: `yravelo/postgres-bulk`, currently private.
- Rewritten controlled branch: `refs/heads/main`; current tip recorded at ticket time.
- Rewrite confirmation and first changed old commit:
  `8d446d63f23a8af84f6a79a23d398128b0112f56`.
- Six affected PR numbers, refs and full old head SHAs.
- Old pre-rewrite tip: `7750d462dbdba0f69c9462d45de57aea7709d8c9`.
- Sanitized reason categories: former personal email in commit metadata; former personal
  signing/home path in cached historical content.
- Fork count `0`, ordinary old branches/tags `0`, LFS objects `0`.
- Requested actions: PR dereference, cached-view cleanup, server garbage collection, and an answer
  about granular Actions job-metadata redaction.

## 31. One-ticket strategy assessment

`recommended for first contact`. One sanitized ticket can request PR/cached-object cleanup and ask
whether job metadata has an internal granular remedy, minimizing repeated disclosure and letting
GitHub route the two product areas. Do not imply they are the same backend operation. Accept a
Support-requested split into separate tickets; never let the unanswered Actions question delay
eligible Git-object cleanup.

## 32. Fork inventory

`0` forks from both repository metadata and the forks endpoint; network count `0`. This satisfies
the documented no-fork prerequisite as currently observable. Recheck immediately before Support
acts and before any visibility transition.

## 33. Cached old-commit accessibility

Authenticated API checks while PRIVATE returned available for all six PR heads, the old root and
the old pre-rewrite main tip. No sensitive commit URL is printed. Anonymous access is currently
blocked by repository privacy; after PUBLIC, continued availability is the documented/inferred
risk unless Support purges the references and cache.

## 34. Garbage-collection limitations

`unreachable from current main != purged from GitHub storage`. A rewrite and force-push only move
controlled refs. PR refs, forks, direct-SHA cached views and server object retention can keep old
objects available. Only GitHub controls server garbage collection; local `git gc` cannot change it,
and Support cleanup cannot recall third-party clones.

## 35. Candidate remediation plan A

1. Keep the repository private.
2. Create and verify fresh distinct neutral Build, Compatibility and Security runs on one current
   private `main` SHA; record sanitized evidence.
3. Update all 23 affected run links according to section 14.
4. With `AUTHORIZE_WORKFLOW_RUN_DELETION`, delete exactly the 58 runs in section 38 and verify
   run/job unavailability with zero partial failures.
5. With separate `AUTHORIZE_GITHUB_SUPPORT_CONTACT`, submit section 39 for PR refs, cached objects
   and server GC; verify authenticated cleanup.
6. Re-run full-history privacy/Gitleaks, security, technical and REL1-A gates. Stay private.

## 36. Candidate remediation plan B

1. Keep the repository private and keep all 58 runs.
2. With `AUTHORIZE_GITHUB_SUPPORT_CONTACT`, submit the sanitized package and ask for granular job
   metadata remediation as well as PR/cached-object cleanup.
3. If Support removes/redacts the 270 names, verify fields without deleting runs and close B4.
4. If Support cannot, B4 remains blocking; either return to Candidate A with a separate deletion
   authorization or record a new explicit owner risk decision. Do not become public implicitly.

## 37. Recommended remediation plan

`Candidate B first, conditional Candidate A`. It applies the minimal destructive action principle:
ask whether evidence can be preserved before deleting it, while using the same authorized Support
contact needed for B5. If no granular job remedy exists, Candidate A becomes the deterministic
closure path after replacement CI evidence is created. If Support declines privacy-only Git-object
cleanup, public activation remains blocked and repository replacement/migration becomes a separate
owner decision.

## 38. Exact workflow run IDs proposed for deletion if required

Build (23):

```text
32890808627, 32888286164, 32874447679, 32871784539, 32871560008, 32864144166,
32853342089, 32850719665, 32844647431, 32834114478, 32830783489, 32828408419,
32820778617, 32816671391, 32811776793, 32809621905, 32807512454, 32807210122,
32789710887, 32784673382, 32783977432, 32777288319, 32774191694
```

Compatibility (24):

```text
32890808601, 32888286201, 32874447650, 32871784542, 32871560090, 32864142986,
32853342285, 32850719735, 32844647437, 32834114482, 32830783535, 32828408347,
32820778544, 32816671463, 32811776782, 32809621923, 32807512546, 32807210132,
32806701660, 32789710893, 32784673356, 32783977325, 32777288331, 32774191674
```

Security (11):

```text
32890829062, 32888331084, 32876520783, 32873968105, 32864172565, 32853390508,
32850787710, 32844682218, 32834158236, 32830801067, 32828466698
```

The list is a proposal only. R5 did not call the delete endpoint.

## 39. Sanitized GitHub Support request package

```text
Subject: Private repository — post-rewrite PR refs/cached objects and Actions metadata guidance

Repository: yravelo/postgres-bulk (PRIVATE)
Rewritten branch: refs/heads/main
First changed old commit: 8d446d63f23a8af84f6a79a23d398128b0112f56
Old pre-rewrite tip: 7750d462dbdba0f69c9462d45de57aea7709d8c9
Affected PR count: 6
Affected refs/old heads:
  refs/pull/1/head cc44c9c88a8a6d4ab7dee650e755af0795944743
  refs/pull/2/head 87748657b84917c866411b9d8ad385d5de61dd79
  refs/pull/3/head 69f3fbb942d817da622601b96e929b553864e7db
  refs/pull/4/head 5536cab947ae1be19b0741ae28cf813dfef69966
  refs/pull/5/head c56f28d6888ac9ad5957998c172aa71ffb2fa2f7
  refs/pull/6/head 8da18883698b170b5e98a1792a570bf0f08bebff
Forks: 0; remaining ordinary old refs/tags: 0; LFS: none.

The rewrite replaced <PERSONAL_EMAIL_REDACTED> in commit metadata/content and
<PERSONAL_SIGNING_PATH_REDACTED> in historical content. These are privacy-sensitive personal
identifiers, not credentials. Please confirm whether this case is eligible for cleanup. If so,
please dereference/delete the affected PR references, remove cached views, and run server garbage
collection for the old objects.

Separately, 270 historical Actions job records across 58 workflow runs retain
<OLD_RUNNER_NAME_REDACTED>. Is an internal redaction/purge of runner_name or those job records
possible without deleting the complete workflow runs? No values, logs or secrets are attached;
we can provide private identifiers only if Support confirms they are necessary and safe.

Please tell us how to verify completion while the repository remains PRIVATE and whether these
two requests must be split into separate tickets.
```

## 40. Required owner authorization #1

```text
AUTHORIZE_GITHUB_SUPPORT_CONTACT
```

Scope: submit the sanitized package, allow GitHub to evaluate/perform eligible PR-ref, cached-view
and server-GC cleanup, and obtain a written answer on job-metadata capability. Do not change
repository visibility.

## 41. Required owner authorization #2

```text
AUTHORIZE_WORKFLOW_RUN_DELETION
```

Conditional scope: only the exact 58 IDs in section 38, only after independent neutral CI and
documentation preparation, and only if granular remediation is unavailable or rejected. It is not
requested or consumed by R5.

## 42. Post-remediation verification plan

1. Before action: recheck PRIVATE visibility, clean synchronized `main`, zero forks, exact 58-run
   and six-ref inventories, and fresh neutral CI on one new canonical SHA.
2. After any run deletion: authenticated run, attempt and job endpoints for all 58 must be
   unavailable; run count must fall by exactly 58; artifacts remain zero; unaffected runs remain;
   determine whether Checks records remain; all 23 documentation links must be reconciled.
3. After Support action: `git ls-remote origin 'refs/pull/*'` returns zero affected refs; PR/cached
   old commit API lookups are unavailable; old objects cannot be fetched by SHA; `main` and its tree
   remain unchanged; forks remain zero.
4. Re-run exact personal email/path checks, all-ref object reachability, full-history Gitleaks,
   documentation links, security fast/full as appropriate, technical preflight and REL1 preflight.
5. Keep repository private. Anonymous verification and public CI belong only to a later authorized
   REL1-B transition after every private/authenticated check passes.

## 43. Documentation impact

R5 creates this report and adds one REL1-A link while preserving both blockers. A later remediation
must update the R3/R4C/R2B/REL1-A reports, REL0 release evidence, continuous-security evidence,
Dependabot review, self-hosted runner guide, release/security roadmaps and any remaining plain run
IDs whose live evidence was deleted. No such future-state claim is made in R5.

## 44. Files created/modified

- Created `docs/releases/rel1ar-github-managed-exposure-plan.md`.
- Updated `docs/releases/rel1a-open-source-exposure-audit.md` only to link R5 and preserve blockers.

## 45. Local validation

Validation sequence: `git diff --check`, documentation/link audit and security fast before commit;
then technical release preflight and REL1 preflight from the clean synchronized documentation
commit. Exact results are reported after execution.

## 46. Git commit

One documentation-only commit with `29708813+yravelo@users.noreply.github.com` and `[skip actions]`
records R5. The exact SHA is reported in the handoff because a commit cannot contain its own SHA.

## 47. Final Git state

Required closure is clean synchronized `main`, repository `PRIVATE`, historical metadata and PR
refs still pending. Exact final SHA is reported after synchronization.

## 48. Remote actions

```text
workflow runs deleted: no
Actions logs deleted: no
Actions artifacts deleted: no
GitHub Support contacted: no
synthetic refs mutated: no
history rewritten: no
runner changed: no
repository visibility changed: no
public CI activated: no
GitHub PVR enabled: no
tag created: no
Benchmarks executed: no
Release executed: no
Central upload: no
publication: no
REL1-B started: no
```

## 49. R5 Definition of Done assessment

`DONE` after validation and synchronization: exact runs/jobs/refs/forks were reconstructed;
visibility and owner deletion capability were established; evidence loss was quantified; neutral
evidence and its deletion coupling were identified; Support guidance and eligibility uncertainty
were documented; both options and separate authorizations were prepared; and no destructive action
was executed.

## 50. Final remediation readiness

`REMEDIATION PLAN READY`. Platform eligibility and job-granular capability remain unknown by their
nature, but the plan resolves both outcomes fail-closed and does not require more R5 investigation.

## 51. Remaining owner decisions

- Authorize the sanitized GitHub Support interaction.
- After Support answers, choose whether to delete the 58 runs if no granular remedy exists.
- If Support rejects privacy-only Git-object cleanup, choose continued private operation or a
  separately planned clean repository replacement/migration; do not publish with implicit risk.

## 52. Exact next authorization requested

Request only:

```text
AUTHORIZE_GITHUB_SUPPORT_CONTACT
```

Do not combine it with workflow-run deletion. The latter remains conditional and separately
governed.

## Boundary statement

```text
REL1-A-R R5 status: DONE
mode: READ-ONLY
historical job metadata exposure: CONFIRMED
workflow run deletion required: owner-decision
GitHub Support required for job metadata: unknown
synthetic PR refs: 6
old history reachable via PR refs: yes
GitHub Support required for PR refs: yes
remediation plan ready: yes
repository public: no
REL1-B started: no
workflow runs deleted: no
GitHub Support contacted: no
tag created: no
Central upload: no
publication activated: no
```
