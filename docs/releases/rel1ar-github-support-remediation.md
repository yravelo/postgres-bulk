# REL1-A-R R6 GitHub Support remediation contact

Preparation date: 2026-08-25T22:31:22Z
Repository: `yravelo/postgres-bulk` (`PRIVATE`)
Decision: **BLOCKED — MANUAL OWNER ACTION REQUIRED; OPEN-SOURCE ACTIVATION NO-GO**

Former personal values are deliberately represented only by placeholders. No credential, private
backup location or exact former runner, host, email or filesystem-path value is versioned here.

## 1. R6 result

`BLOCKED — MANUAL OWNER ACTION REQUIRED`. The current blockers were reverified and the exact
single-ticket request is ready, but this execution environment has no authenticated interactive
session for the GitHub Support portal and no connected capability that can submit a Support case.
The request was not sent and no ticket ID exists.

## 2. Authorization consumed

```text
AUTHORIZE_GITHUB_SUPPORT_CONTACT
```

The authorization was applied only to the official-channel attempt and preparation of the exact
owner-submittable message. It did not authorize or cause workflow-run deletion, ref mutation,
history rewrite, visibility change or any other remediation.

## 3. Initial repository and Git state

`HEAD == origin/main == 461161e4ee186f2f2d4297df22e5e4c5867f3f8e`, worktree clean,
default branch `main`, repository `PRIVATE`, 196 workflow runs and zero Actions artifacts.

## 4. Synthetic refs confirmed

`6`, unchanged from R5. GitHub advertises `refs/pull/1/head` through
`refs/pull/6/head` with the exact heads included in section 11. Only `main` is an ordinary remote
branch; no owner mutation was attempted.

## 5. Historical affected runs confirmed

`58/58` remain available and each contains at least one historical job with the former runner
display name.

## 6. Historical affected jobs confirmed

The authenticated `filter=all` recheck returned 311 jobs: 270 with
`<OLD_RUNNER_NAME_REDACTED>`, 13 with the current neutral name and 28 with no runner name.
Artifacts remain `0`; three neutral current-attempt log archives are available and old sensitive
logs remain unavailable.

## 7. Support channel used

Official target: [GitHub Support portal](https://support.github.com/contact). GitHub documents that
ticket creation requires selecting an authenticated owner/account and submitting through the
portal. This environment can open public documentation but cannot complete the interactive owner
authentication or submit the form. No public Issue, Discussion, email or unofficial channel was
used. See [creating a GitHub Support ticket](https://docs.github.com/en/support/contacting-github-support/creating-a-support-ticket).

## 8. Support request submitted

`no`.

## 9. Ticket/case ID

`not available — request not submitted`.

## 10. Submission timestamp

`not available — request not submitted`. Package preparation timestamp:
`2026-08-25T22:31:22Z`.

## 11. Sanitized request scope and exact owner-submittable text

Paste the following as one initial request. Do not attach files or add exact personal values unless
Support specifically confirms they are necessary in the private case.

Subject:

```text
Private repository: post-rewrite PR refs/cached objects and Actions job metadata privacy
```

Body:

```text
Repository: yravelo/postgres-bulk (currently PRIVATE)
Future intent: public open-source activation, but visibility will not change until cleanup is
verified.

Canonical rewritten branch: refs/heads/main
Current rewritten HEAD: 461161e4ee186f2f2d4297df22e5e4c5867f3f8e
First changed old commit / rewrite boundary:
8d446d63f23a8af84f6a79a23d398128b0112f56
Old pre-rewrite main tip: 7750d462dbdba0f69c9462d45de57aea7709d8c9

The main history was rewritten before publication. The rewrite removed
<PERSONAL_EMAIL_REDACTED> from Git metadata and historical file content and removed
<PERSONAL_PATH_REDACTED> signing/home-path content. Canonical main has passed exact privacy
checks and full-history Gitleaks. There are no LFS objects, remaining ordinary old branches/tags,
or forks.

Six GitHub-managed, read-only pull-request head refs still retain pre-rewrite commits:
  PR 1 / refs/pull/1/head cc44c9c88a8a6d4ab7dee650e755af0795944743
  PR 2 / refs/pull/2/head 87748657b84917c866411b9d8ad385d5de61dd79
  PR 3 / refs/pull/3/head 69f3fbb942d817da622601b96e929b553864e7db
  PR 4 / refs/pull/4/head 5536cab947ae1be19b0741ae28cf813dfef69966
  PR 5 / refs/pull/5/head c56f28d6888ac9ad5957998c172aa71ffb2fa2f7
  PR 6 / refs/pull/6/head 8da18883698b170b5e98a1792a570bf0f08bebff

These refs are not owner-mutable and retain the former personal email in commit metadata. The
old pre-rewrite tip also remains available by authenticated SHA and has cached historical content
from before the path redaction. This is privacy-sensitive historical personal data, not a leaked
credential, private key, or active secret.

Can GitHub Support remove or purge these six GitHub-managed refs/pull/* and the related cached
historical views/objects before the repository is made public? If this case is eligible, please
dereference or delete the affected PR references, remove cached views, and run the applicable
server-side garbage collection/purge. Please do not modify canonical main or current history.

Separately, 58 historical GitHub Actions workflow runs contain 270 job records whose
runner_name field retains <OLD_RUNNER_NAME_REDACTED>. The privacy-sensitive old logs were already
deleted, Actions artifacts are zero, the active runner and hostname are neutral, and current
canonical Build, Compatibility (11/11), and Security evidence is neutral.

Is there any GitHub-supported way for Support to remove or redact runner_name from those
historical workflow job records while preserving the 58 workflow run records? If not, is deleting
the complete workflow runs the only supported remediation? We are asking only about capability;
we are not requesting or authorizing workflow-run deletion in this ticket.

Please also tell us:
1. whether the privacy-sensitive non-secret history is eligible for Support cleanup;
2. whether you need the exact redacted personal values, full affected run ID list, PR URLs, owner
   confirmation, or any other narrowly scoped evidence;
3. whether Git-history cleanup and Actions metadata must be split into separate cases; and
4. how to verify completion while the repository remains private.

No credential, token, 2FA code, private key, session cookie, recovery code, or private backup is
included.
```

## 12. Git refs cleanup requested

`yes`, in the prepared request; `not submitted`.

## 13. Cached old objects cleanup requested

`yes`, in the prepared request; `not submitted`.

## 14. Actions job metadata remediation requested

`yes`, as the exact granular-capability question; `not submitted`.

## 15. Workflow run deletion requested

`no`. The package explicitly says the ticket neither requests nor authorizes run deletion.

## 16. Sensitive values shared with Support

`none`, because no Support contact occurred. The prepared message includes only placeholders plus
non-credential technical object identifiers. If Support later requires an exact personal value,
provide only that category inside the private ticket and never version it.

## 17. Credentials shared

`no`.

## 18. Support response received

`no`.

## 19. Support response summary

`none — request not submitted`.

## 20. PR refs remediation status

`PENDING`.

## 21. Historical job metadata status

`PENDING`.

## 22. Granular job-metadata remediation supported

`unknown`.

## 23. Whole-run deletion required according to Support

`unknown`. No workflow run may be deleted without the separate exact authorization
`AUTHORIZE_WORKFLOW_RUN_DELETION`.

## 24. Additional information requested by Support

`none — no ticket or response`. The prepared request asks Support whether it needs full run IDs,
exact personal values, PR URLs or owner confirmation rather than oversharing them initially.

## 25. Fork and cached-object notes

Repository metadata and the forks endpoint report `0` forks and network count `0`. All six old PR
heads and the old pre-rewrite tip remain authenticated-accessible. `Unreachable from main` does
not mean purged from GitHub storage; cleanup requires Support confirmation plus technical
verification.

## 26. Documentation changes

This report was created. The R5 plan and REL1-A audit were updated only to record the authorized
contact attempt, manual-submission blocker and unchanged B4/B5 status.

## 27. Local validation

Required sequence: `git diff --check`, documentation/link audit and security fast before commit;
technical release preflight and REL1 preflight after the clean synchronized documentation commit.

## 28. Git commit

One documentation-only `[skip actions]` commit using
`29708813+yravelo@users.noreply.github.com`. Exact SHA is reported after commit because a commit
cannot contain its own SHA.

## 29. Final Git state

Required closure: clean synchronized `main`, repository `PRIVATE`, no new Actions run, B4/B5
pending and REL1-B not started.

## 30. Remote actions performed

```text
GitHub Support contacted: no
workflow runs deleted: no
Actions logs deleted: no
Actions artifacts deleted: no
synthetic refs mutated by owner: no
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

The read-only GitHub API/ref rechecks and an ordinary documentation push are not historical
resource remediation.

## 31. R6 Definition of Done assessment

The manual-channel fallback is prepared: blockers reverified, R5 package reused and refreshed,
one exact sanitized request covers both scopes, no credentials are included, and no destructive
action occurred. External contact remains incomplete, so R6 is `BLOCKED` rather than claiming
`REQUEST SUBMITTED` or remediation.

## 32. Remaining blockers

- Owner must sign in to the official GitHub Support portal and submit section 11 once.
- Support must classify/act on the six PR refs and cached old objects.
- Support must answer whether historical `runner_name` can be removed without deleting runs.
- If granular metadata remediation is unavailable, the owner must separately decide between
  authorized deletion of the 58 runs and remaining private/using an alternative migration.

## 33. Current open-source activation verdict

`OPEN-SOURCE ACTIVATION NO-GO`.

## 34. Exact next action

The owner must submit section 11 through the
[official GitHub Support portal](https://support.github.com/contact), then record only the case ID,
submission timestamp, status and sanitized response. Do not open a duplicate ticket. Do not delete
runs or make the repository public.

## Boundary statement

```text
REL1-A-R R6 status: BLOCKED
GitHub Support contact: NOT_SUBMITTED
PR synthetic refs: PENDING
historical job metadata: PENDING
workflow runs deleted: no
repository public: no
REL1-B started: no
tag created: no
Central upload: no
publication activated: no
```
