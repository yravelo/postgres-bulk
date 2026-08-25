# REL1-A-R R2B public-history remediation

Audit date: 2026-08-25  
Repository: `yravelo/postgres-bulk` (`PRIVATE`)  
Decision: **OPEN-SOURCE ACTIVATION NO-GO**

This report records the authorized, exact-scope rewrite of controlled `main`. It never records the
original personal email or signing path. Private backup locations, rewrite inputs and GitHub
Support evidence remain outside versioned content.

## 1. Task result

`DONE`. The controlled rewrite, guarded remote update, post-push verification and fresh private
Build, Compatibility and Security evidence all passed. Open-source activation remains `NO-GO`
because the explicitly out-of-scope remote identity blockers remain pending.

## 2. Authorization consumed

The owner supplied both exact authorizations required for this operation:

```text
AUTHORIZE_PERSONAL_EMAIL_CONTENT_REDACTION_IN_HISTORY
AUTHORIZE_HISTORY_REWRITE_AND_FORCE_PUSH
```

The first authorization was consumed by the exact historical document-content rule. The second was
consumed by the single guarded `origin/main` update described below. Neither authorization was used
for any other remote mutation.

## 3. Pre-operation HEAD/origin state

```text
local main: 37f3b90471c6a27d96e962cc038df0822678a1ef
origin/main: 7750d462dbdba0f69c9462d45de57aea7709d8c9
local ahead: 1 documentation commit
worktree: clean
repository visibility: PRIVATE
```

## 4. Personal email metadata occurrences before

Across the 104-commit canonical base, the redacted owner identity appeared 100 times as author and
100 times as committer. The local documentation commit added one occurrence in each role.

## 5. Personal email document-content occurrences before

Exactly `3` unique historical blobs contained the personal email as document text. All were
versions of `docs/releases/rel1a-open-source-exposure-audit.md`, reachable from canonical `main`.
The sanitized inventory is:

| Blob | Change commit(s) | Purpose | Reachability |
| --- | --- | --- | --- |
| `3bea90ed5468661500aa698d7f8f25c0377c3ec1` | `7750d462dbd` | REL1-A privacy classification | `main` |
| `d2b3952acdba4afaf89ae7cc00b3f7b1b4736984` | `b83e5c0037ec`, `3dedc05680aa` | REL1-A identity evidence | `main` |
| `f3247ae95a6000119229db1a8bc48c9c2b6e647d` | `7750d462dbd`, `b83e5c0037ec` | REL1-A exposure summary | `main` |

The source value is represented only as `<PERSONAL_EMAIL_REDACTED>`.

## 6. Signing-path occurrences before

The exact personal signing path appeared in 9 unique historical blobs. Within canonical `main`,
signing/home-path content affected 16 commits; one already affected report blob also contained the
owner-home prefix. Exact values are intentionally redacted.

## 7. Combined rewrite strategy

One official `git-filter-repo` v2.47.0 operation applied an exact mailmap rule plus exact blob
replacement rules for the personal email, signing path and residual owner-home prefix. The command
used `--sensitive-data-removal --no-fetch --force`. Names, dates, messages, legitimate emails and
unrelated content were not rewritten.

## 8. Exact content redaction rule

The only newly authorized document rule was:

```text
exact personal email -> <PERSONAL_EMAIL_REDACTED>
```

No substring, domain-wide or fuzzy replacement was used.

## 9. Backup/rebuild strategy

A mode-`0600` private bare backup and verified main bundle were created under an ephemeral private
temporary directory. The final candidate was rebuilt from that backup in one combined rewrite,
not continued from the earlier rehearsal. The legitimate dry-run report was then recreated as a
new commit without joining old and rewritten histories. Backup refs will never be pushed.

## 10. Candidate HEAD after combined rewrite

The rebuilt candidate, including the cleanly recreated dry-run report, was
`9c3dd02fd7f85157adddb3fc0f7504946c0eccd8`. The canonical base maps to
`9288b4478e8117da066e24ecb5afb56d39f3ed24`; the root maps to
`e2161df6813eb38810e012a5460b986c2d8c4afc`.

## 11. Personal email metadata occurrences after

`0` author occurrences and `0` committer occurrences.

## 12. Personal email content occurrences after

`0` historical blob-content occurrences.

## 13. Total old personal email reachable after

`0` across controlled candidate `main` metadata and reachable blob content.

## 14. Approved noreply preserved

`yes`. `29708813+yravelo@users.noreply.github.com` remains the exact approved owner identity.

## 15. Security reporting email preserved

`yes`. `postgresbulk-security@proton.me` remains unchanged.

## 16. Signing-path occurrences after

`0` for both the exact signing path and residual owner-home prefix.

## 17. Commit count before/after

The local pre-operation `main` and the rebuilt pre-report candidate both contain 105 commits. The
canonical base contains 104 rewritten commits; the dry-run documentation commit was recreated as
commit 105. The remediation report created ordinary commit 106; this final status refresh is an
ordinary post-rewrite documentation commit and does not alter rewritten history.

## 18. Rewritten commit count

`git-filter-repo` parsed and rewrote 104 canonical commits. Because the root changed, all 104
canonical SHAs changed; the local dry-run report was recreated with a new SHA. Direct
classification: 86 metadata-only commits, 3 email-content commits, 16 signing-content commits and
14 commits touching more than one authorized scope. Exact old/new tree comparison found zero
unexpected edits.

## 19. Ref impact

Only controlled `refs/heads/main` was updated. There are no tags. Local backup and audit refs
remained private and were not pushed. All GitHub-managed `refs/pull/*` remained outside the
writable ref set.

## 20. Synthetic PR-ref status

GitHub still advertises six read-only synthetic PR head refs that reference old commits. They
cannot be rewritten by a normal push. GitHub Support cleanup is `PENDING`; public activation stays
blocked if any privacy-sensitive old object remains reachable through them.

## 21. Productive/API equivalence

`PASS`. Programmatic comparison reports productive Java diff `0`, public API diff `0`, unexpected
content diff `0`, and functional behavior diff `0`. All content differences are the three exact
authorized privacy rules or post-rewrite evidence refreshes.

## 22. Full-history Gitleaks/privacy audit

`PASS`. Gitleaks 8.30.1 scanned all 106 candidate commits and about 4.55 MB with no leak. Exact
personal-email/path checks, private-key markers, credential-bearing URLs, token patterns, private
repository references and absolute-path privacy checks passed. A broad URL heuristic matched only
the public SLSA documentation reference and was classified as a false positive.

## 23. Build/test validation

`PASS`: Spotless, `test`, `verify`, `clean verify` and `install`, including required Testcontainers
integration tests. No required test was skipped.

## 24. Security validation

`PASS`: `./scripts/check-security.sh full`. This included full-history Gitleaks, OSV evaluation of
138/138 dependencies (5 warnings, 0 blockers), static analysis, SBOM/license integrity, release
security checks and continuous validation.

## 25. API/Javadocs/docs validation

`PASS`: the checked-in 0.1.0 public API baseline and Maven Javadocs passed. The post-commit
documentation audit checked 226 links successfully.

## 26. Reproducibility

`PASS`: two clean release-shaped builds produced matching artifacts and semantically equivalent
SBOMs. Nothing was signed, tagged, released or published.

## 27. Technical release preflight

`PASS`. Policy, local public-key, clean-worktree and exact candidate/ref binding checks passed.

## 28. REL1 preflight

`PASS`. All REL1 policy gates passed on the final local candidate.

## 29. Force-push method/result

`PASS`. A single explicit `--force-with-lease` updated only `refs/heads/main`, leased against
`7750d462dbdba0f69c9462d45de57aea7709d8c9`. The forced update was
`7750d462dbdba0f69c9462d45de57aea7709d8c9 -> 8a4923757442ecd2291d1a08809ef8b3f036c0d9`.
No backup, tag or synthetic PR ref was pushed.

## 30. Final HEAD/origin state

Immediately after the forced update, both `HEAD` and `origin/main` were
`8a4923757442ecd2291d1a08809ef8b3f036c0d9`; the worktree was clean and GitHub reported the
repository `PRIVATE`. This report's final status refresh necessarily creates a later ordinary
documentation-only HEAD; its exact remote binding and fresh CI are recorded in the task handoff to
avoid a self-referential commit claim.

## 31. SHA-dependent evidence refreshed

Project SHA references in release readiness, security closure, roadmap, benchmark methodology and
REL1-A evidence were mapped to rewritten commits. The prior projected dry-run SHA is retained only
as explicitly superseded historical evidence. The unsigned 46-artifact local release inventory and
isolated staged JPA/JDBC consumer validation bind to the candidate commit; no artifact was signed or
published.

## 32. Remote Build

`PASS` on rewritten evidence commit `8a4923757442ecd2291d1a08809ef8b3f036c0d9`, private Build run
[`32888286164`](https://github.com/yravelo/postgres-bulk/actions/runs/32888286164). The ordinary
closure commit is revalidated separately in the task handoff.

## 33. Remote Compatibility

`PASS` 11/11 on rewritten evidence commit `8a4923757442ecd2291d1a08809ef8b3f036c0d9`, private
Compatibility run
[`32888286201`](https://github.com/yravelo/postgres-bulk/actions/runs/32888286201). The ordinary
closure commit is revalidated separately in the task handoff.

## 34. Remote Security

`PASS` on rewritten evidence commit `8a4923757442ecd2291d1a08809ef8b3f036c0d9`, manually dispatched
private Security run
[`32888331084`](https://github.com/yravelo/postgres-bulk/actions/runs/32888331084). The ordinary
closure commit is revalidated separately in the task handoff.

## 35. GitHub PR refs blocker

`PENDING` GitHub Support cleanup or equivalent owner-approved resolution.

## 36. Actions log blocker

`PENDING`.

## 37. Runner identity blocker

`PENDING`.

## 38. Files created/modified

Created `docs/releases/rel1ar-public-history-remediation.md`. Refreshed the dry-run, REL1-A,
release-readiness, roadmap, security-evidence and benchmark-methodology documents whose project SHA
bindings changed. No productive source file changed.

## 39. Remote actions performed

```text
repository visibility changed: no
history rewritten: yes
force push performed: yes
Actions logs deleted: no
runner re-registered: no
GitHub PVR enabled: no
public CI activated: no
tag created: no
Benchmarks executed: no
Release executed: no
Central upload: no
publication: no
REL1-B started: no
```

## 40. Definition of Done assessment

`DONE`. Every R2B definition-of-done gate passed. The remaining PR-ref, Actions-log and runner
items are intentionally outside this authorization and continue to block open-source activation.

## 41. Remaining blockers

- GitHub-managed PR refs cleanup.
- Historical Actions log privacy cleanup.
- Neutral runner identity and fresh validation after that separate change.

## 42. Final open-source activation verdict

```text
OPEN-SOURCE ACTIVATION NO-GO
```

## 43. Exact next action

After R2B itself succeeds, prepare the sanitized Actions log deletion inventory and request the
separate authorization:

```text
AUTHORIZE_ACTIONS_LOG_DELETION
```

Do not delete logs automatically.

## Boundary statement

```text
REL1-A-R R2B status: DONE
personal email metadata exposure: REMOVED
personal email content exposure: REMOVED
historical signing path: REMOVED
history rewrite completed: yes
force push performed: yes
PR synthetic refs clean: no
Actions personal identity logs: PENDING
runner identity neutralized: no
full-history secrets: PASS
repository public: no
REL1-B started: no
tag created: no
Central upload: no
publication activated: no
```
