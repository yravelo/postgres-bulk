# REL1-A-R history rewrite dry-run

Audit date: 2026-08-25

Audited source: `7750d462dbdba0f69c9462d45de57aea7709d8c9`

Repository: `yravelo/postgres-bulk` (`PRIVATE`)

Decision: **DRY-RUN PASS — DESTRUCTIVE OWNER AUTHORIZATION REQUIRED**

## R2B supersession notice

This R1 report is retained as dry-run evidence. R2B subsequently authorized an additional exact
historical document-content rule from the personal email to `<PERSONAL_EMAIL_REDACTED>`. Therefore
the projected `a5e73f956966...` SHA below is superseded and must not be used as canonical history.
The executed strategy and current status are recorded in
`docs/releases/rel1ar-public-history-remediation.md`.

This report records a disposable, combined history-rewrite rehearsal. It does not authorize or
perform a remote rewrite. The report commit is necessarily outside its self-audited source SHA; a
real authorized rewrite must start from the then-current local `main` and will therefore produce a
different final SHA.

## Mandatory results

### 1. Task result

`PASS`. One `git-filter-repo` operation replaced the selected Git metadata email and neutralized
the personal signing/home path in a disposable full-ref clone. All equivalence, privacy, build,
security, API and documentation gates passed.

### 2. Current real HEAD

The source HEAD was `7750d462dbdba0f69c9462d45de57aea7709d8c9`; `origin/main` had the same
SHA. Neither the real repository history nor the remote was rewritten.

### 3. Reachable commit count before

The broad local audit boundary contained 115 unique commits. GitHub's advertised `main` plus six
`refs/pull/*/head` refs contained 110. The additional five were two stale local PR snapshots and
three detached security-review worktree heads. They were included deliberately so the rehearsal
covered every locally relevant reachable object.

### 4. Personal email occurrences before

`<PERSONAL_EMAIL_REDACTED>` occurred as author email 100 times and committer email 103 times, in
103 unique commits. The first occurrence was 2026-08-18T16:45:15+02:00 and the last was
2026-08-25T18:51:23+02:00. It was reachable from `main` and every GitHub PR ref.

### 5. Approved replacement email

The exact owner-authorized value is:

```text
29708813+yravelo@users.noreply.github.com
```

### 6. Replacement identity validation

GitHub's read-only REST response identifies account `yravelo` with numeric ID `29708813`. The
replacement therefore matches GitHub's documented post-2017
`ID+USERNAME@users.noreply.github.com` format. See the official
[email address reference](https://docs.github.com/en/account-and-profile/reference/email-addresses-reference)
and [email concepts](https://docs.github.com/en/account-and-profile/concepts/email-addresses).
This validation did not inspect or change the owner's account email settings.

For future commits, configure only the intended repository after the rewrite:

```text
git config user.email 29708813+yravelo@users.noreply.github.com
```

No global Git configuration was changed during this task.

### 7. Signing-path occurrences before

`<PERSONAL_SIGNING_PATH_REDACTED>` appeared in 9 unique historical blobs across 21 commits, 77
commit/file snapshots and 5 paths. It was reachable from `main`, GitHub PR refs 2 and 6, and local
security-review snapshots. A historical REL1-A report blob also retained the owner-home prefix in
the same signing-path discussion.

### 8. Rewrite tool

The rehearsal used the official `git-filter-repo` v2.47.0 script, source revision `a40bce548d2c`,
downloaded to an ephemeral `/tmp` tool directory. This meets GitHub's current recommendation to
use v2.47 or later for sensitive-data removal. References:
[GitHub history-removal guidance](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)
and [git-filter-repo](https://github.com/newren/git-filter-repo).

### 9. Combined rewrite strategy

A single `git-filter-repo --sensitive-data-removal --no-fetch` invocation used:

- a mailmap rule changing only the exact owner author/committer email to the approved noreply;
- an exact text replacement from the redacted signing path to `<LOCAL_SIGNING_PATH>`; and
- an exact replacement of its residual owner-home prefix to `<OWNER_HOME>`.

The home-prefix rule was added after the first privacy re-audit found one residual historical line
in the already affected REL1-A report context. No other email, path, name, date, message or content
rule was used.

### 10. Disposable clone/dry-run location

The original full-ref mirror, rewritten bare mirror, worktree and mapping files lived only below an
ephemeral `/tmp/postgres-bulk-rel1ar.<random>/` directory. The rewrite used `--no-fetch`; it did not
contact or mutate `origin`.

### 11. Dry-run result

`PASS`. `git-filter-repo` parsed and rewrote 115 of 115 commits in one operation. The first changed
commit was the repository root. LFS was not in use.

### 12. Reachable commit count after

115. No reachable commit was dropped or added inside the modeled ref boundary.

### 13. Personal email occurrences after

```text
0
```

Author occurrences: 0; committer occurrences: 0; affected reachable commits: 0.

### 14. Approved noreply occurrences after

The approved noreply occurs 100 times as author and 103 times as committer, in the same 103 unique
commits previously carrying the personal email. All other legitimate email values were unchanged.

### 15. Signing-path occurrences after

```text
0
```

The exact signing path and the residual owner-home prefix both have zero reachable blob matches.

### 16. Neutral replacement used

`<LOCAL_SIGNING_PATH>` occurs in 9 rewritten historical blobs. `<OWNER_HOME>` occurs in one of
those already affected historical report blobs. Both are explicit placeholders, not productive
filesystem locations.

### 17. Commits changed — email only

88 commits changed directly only in author/committer email metadata.

### 18. Commits changed — content only

6 commits changed directly only through the approved path redaction.

### 19. Commits changed — both

15 commits changed directly in both email metadata and expected redacted content. A further 6
commits had no direct metadata/tree edit but necessarily received new SHAs because an ancestor
changed. Consequently all 115 commit SHAs changed.

The 21 content-affected commits comprise 77 commit/file pairs. Programmatic old/new blob comparison
found zero unexpected edits after applying the two exact replacement rules.

### 20. Old/new HEAD

```text
old:         7750d462dbdba0f69c9462d45de57aea7709d8c9
dry-run new: a5e73f95696653ad678b3278789651ecdf1604aa
```

The dry-run SHA is a projection for the self-audited source only, not the SHA to push later.

### 21. Root commit old/new

```text
old: 8d446d63f23a8af84f6a79a23d398128b0112f56
new: e2161df6813eb38810e012a5460b986c2d8c4afc
```

### 22. Ref impact matrix

All SHAs below are unambiguous 12-character abbreviations from the ephemeral mapping. Every modeled
ref changes because the root metadata changes.

| Ref | Old | Dry-run new | Real remote handling |
| --- | --- | --- | --- |
| `refs/heads/main` | `7750d462dbd` | `a5e73f956966` | Force-push only after authorization |
| `refs/pull/1/head` | `cc44c9c88a8a` | `59b0e78246dd` | Read-only GitHub synthetic ref |
| `refs/pull/2/head` | `87748657b849` | `7306b3d4eb1d` | Read-only GitHub synthetic ref |
| `refs/pull/3/head` | `69f3fbb942d8` | `6fbc00424f48` | Read-only GitHub synthetic ref |
| `refs/pull/4/head` | `5536cab947ae` | `cdff1566ad4b` | Read-only GitHub synthetic ref |
| `refs/pull/5/head` | `c56f28d6888a` | `4199383de728` | Read-only GitHub synthetic ref |
| `refs/pull/6/head` | `8da18883698b` | `d9b62c8bdde0` | Read-only GitHub synthetic ref |
| local stale PR 1 | `cc44c9c88a8a` | `59b0e78246dd` | Local only; discard/reclone |
| local stale PR 2 | `bba5ae82adad` | `6558d8e57e72` | Local only; discard/reclone |
| local stale PR 3 | `69f3fbb942d8` | `6fbc00424f48` | Local only; discard/reclone |
| local stale PR 4 | `5536cab947ae` | `cdff1566ad4b` | Local only; discard/reclone |
| local stale PR 5 | `c56f28d6888a` | `4199383de728` | Local only; discard/reclone |
| local stale PR 6 | `f19b2c9ea6e3` | `3d7da522d6a9` | Local only; discard/reclone |
| local pre-SEC6 PR 2 | `87748657b849` | `7306b3d4eb1d` | Local only; discard/reclone |
| local pre-SEC6 PR 6 | `8da18883698b` | `d9b62c8bdde0` | Local only; discard/reclone |
| detached audit PR 1 | `0bb32b882c37` | `4e5b69d6f277` | Local only; discard worktree |
| detached audit PR 2 | `17911c2dfdef` | `4caddc28eeb1` | Local only; discard worktree |
| detached audit PR 6 | `681e59414861` | `6ac480fe9c5f` | Local only; discard worktree |

`origin/HEAD` and `origin/main` duplicate the main mapping and were not mutated.

### 23. PR refs handling assessment

All six PRs are closed; PRs 1, 2, 5 and 6 were merged, PRs 3 and 4 were closed without merge. Their
same-repository Dependabot source branches no longer exist, but GitHub retains synthetic
`refs/pull/*/head` refs. GitHub documents these refs as read-only, so they cannot be force-pushed.

After an authorized main rewrite, the owner must request GitHub Support to dereference/purge the old
PR objects and cached views before public activation. GitHub's published support policy is focused
on sensitive credentials and does not guarantee removal for privacy-only data. If Support declines,
public activation remains blocked and repository replacement/migration requires a separate owner
decision. This is an activation constraint, not a failure of the private main rewrite rehearsal.

### 24. Productive Java/API impact

No productive source changed. Old and rewritten final HEAD have the identical tree object
`3f9c1092e2cc00542f4eab3cd481d330f787cded`; Java path lists and blob IDs are identical. Public API
diff: 0. Behavioral diff: 0.

### 25. Full-history Gitleaks

Gitleaks 8.30.1 scanned all 115 rewritten commits and approximately 4.52 MB: `PASS`, no leak found.

### 26. Privacy re-audit

`PASS` for the rewrite targets: personal email 0, signing path 0 and owner-home prefix 0. No private
key marker, credential-bearing URL, private repository URL, personal hostname or new personal-data
blocker was found in rewritten Git content. The only broad private/internal URL-pattern match was a
public SLSA project documentation link and was classified as a false positive.

The pre-existing GitHub Actions log identity exposure is outside Git content and remains pending.

### 27. Build validation

The rewritten candidate passed Spotless, `test`, `verify`, `clean verify` and `install`, including
the mandatory Testcontainers integration tests. The first sandboxed test attempt could not access
the Docker socket; the legitimate rerun with Docker access passed. No release was generated.

### 28. Security validation

`./scripts/check-security.sh full`, technical release preflight and REL1 preflight all passed. This
covered full-history secrets, OSV evaluation (138/138 dependencies; 5 warnings, 0 blockers), static
analysis, SBOM/license integrity, release security checks and continuous-security validation.

### 29. API/Javadocs/docs validation

The checked-in 0.1.0 public API baseline passed. Maven Javadocs passed. The documentation/link audit
passed with 226 checked links. No API or documentation semantic change results from the rewrite.

### 30. Candidate/clean-room impact

The rewritten final tree is object-identical to the original final tree, and a disposable worktree
from that history passed the full candidate build/security suite. This is stronger than a source
text comparison: the complete final repository snapshot is unchanged. No final release candidate,
tag, GitHub Release or publication was created.

### 31. Release inventory impact

The repository has no tags, GitHub Releases or release artifacts to migrate. Existing local Maven
install outputs were validation-only and are not versioned. Any future release inventory must use
the post-rewrite SHAs.

### 32. Signing evidence impact

Twelve reachable GitHub/Dependabot commits currently carry Git commit signatures. Because their
commit objects change, those signatures cannot survive; the rehearsal contains zero signed commits.
This does not change artifact-signing policy, public keys or signing fingerprints, but historical
commit-signature evidence must not be represented as valid after the rewrite. New post-rewrite CI
and future signed release evidence are required.

### 33. CI evidence impact

The 73 Build, 73 Compatibility and 9 Security workflow runs (155 total) refer to pre-rewrite SHAs.
The last validated source evidence includes Build `32874447679`, Compatibility `32874447650` and
Security `32876520783`; after rewriting they remain historical evidence only and do not validate
the new SHA. Fresh post-rewrite Build, Compatibility and Security runs are mandatory before public
activation or release work.

### 34. Documentation SHA references requiring refresh

The real rewrite must mechanically update project-SHA evidence, without changing its meaning, in:

- `docs/releases/rel0-final-release-readiness.md`
- `docs/releases/rel1a-open-source-exposure-audit.md`
- `docs/releases/release-readiness.md`
- `docs/security/continuous-security-validation.md`
- `docs/security/dependabot-review-2026-08.md`
- `docs/security/dependency-vulnerability-management.md`
- `docs/security/java-static-analysis.md`
- `docs/security/sbom-and-license-integrity.md`
- `docs/security/self-hosted-runner.md`
- `docs/benchmarks/methodology.md`
- `docs/benchmarks/ms8-multi-schema.md`
- `docs/plans/implementation-plan.md`
- `docs/plans/release-acceptance-criteria.md`
- `docs/plans/release-roadmap.md`
- `docs/plans/security-roadmap.md`

The full SHAs in `docs/security/secrets-and-actions-hardening.md` are immutable third-party Action
pins, not project commits, and must not be rewritten through the project commit mapping.

### 35. Actions log blocker state

```text
PENDING
```

Fifty-two retained workflow runs were previously associated with the personal runner identity.
No log was deleted. This remains an independent public-activation blocker.

### 36. Runner identity blocker state

```text
PENDING
```

The owner-specific runner/workstation identity was not changed or re-registered. This remains an
independent public-activation blocker.

### 37. Files created/modified

Versioned: only `docs/releases/rel1ar-history-rewrite-dry-run.md`. All mailmap, replacement,
mapping, mirror and candidate files are ephemeral under `/tmp` and are not part of the repository.

### 38. Git state

```text
origin/main changed: no
history rewritten remotely: no
force push performed: no
```

The local documentation commit that contains this self-referential report is outside audited SHA
`7750d462...`; this is expected and is why the eventual authorized rewrite must rerun from the
then-current local main.

### 39. Remote actions performed

```text
repository visibility changed: no
Actions logs deleted: no
runner re-registered: no
tag created: no
Release executed: no
Central upload: no
publication: no
REL1-B started: no
```

Remote access was read-only: repository/ref/PR/workflow metadata inspection and a disposable clone.
No push, settings mutation, workflow dispatch or release action occurred.

### 40. Definition of Done assessment

`PASS` for REL1-A-R1. The exact identity decision was applied to the design; all email/path/ref
inventories were reconstructed; the combined disposable rewrite produced zero target exposure;
equivalence, Gitleaks, privacy, build, security, API and docs passed; downstream evidence impact is
documented; the remote remains private and unchanged; destructive authorization remains pending.

### 41. New blockers discovered

No new blocker prevents the explicitly scoped private-history rewrite. The first pass exposed one
residual owner-home prefix; the same disposable combined strategy was corrected and revalidated to
zero matches. The following known post-rewrite activation blockers remain:

- GitHub Support handling of read-only PR refs/cached objects, with no guaranteed privacy purge;
- Actions log remediation; and
- neutral runner identity and fresh post-rewrite CI evidence.

None authorizes publication, release work or REL1-B.

### 42. Destructive authorization status

```text
PENDING OWNER AUTHORIZATION
```

### 43. Exact next authorization requested

The successful rehearsal supports requesting only the following next gate. It does not infer it and
does not request Actions-log deletion or runner re-registration yet.

## Boundary statement

```text
owner email decision: REWRITE
approved replacement email: 29708813+yravelo@users.noreply.github.com
history rewrite dry-run: PASS
old personal email reachable after dry-run: 0
old signing path reachable after dry-run: 0
productive behavior changed: no
origin/main changed: no
force push performed: no
Actions log remediation: PENDING
runner identity remediation: PENDING
repository public: no
REL1-B started: no
```

`AUTHORIZE_HISTORY_REWRITE_AND_FORCE_PUSH`
