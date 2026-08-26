# REL1-MIG5B archive deletion and REL1 closure

Date: 2026-08-26  
Canonical repository: `https://github.com/yravelo/postgres-bulk`  
Deletion target: old private repository ID `1339652660`

## 1. MIG5B result

`DONE`. The authorized old archive runner/deletion transaction completed, the canonical public
repository remained healthy, and REL1/clean repository migration are `COMPLETE`.

## 2. Authorization consumed

```text
AUTHORIZE_OLD_PRIVATE_REPOSITORY_DELETION
```

The authorization was consumed only for runner retirement, deletion of the exact old private
repository, local archive-remote cleanup and REL1 closure. It did not authorize REL2.

## 3. Initial public canonical state

`yravelo/postgres-bulk` was `PUBLIC`, default branch `main`, protected by 13 strict checks, at
`5572b9792f09ccc4beaa7cea6a8a358ad0594e5d`, with zero tags, Releases, repository runners, secrets
and variables. Build, Compatibility 11/11, Security, CodeQL and anonymous validation were PASS.

## 4. Initial archive state

`yravelo/postgres-bulk-private-archive` was `PRIVATE`, deletion-ready, source/evidence dependency
`none`, metadata `SAFE TO LOSE`, and backup recommendation `none`. Its only runner was online,
idle and registered at repository level.

## 5. Public repository ID

`1346700826` (`R_kgDOUEUGGg`).

## 6. Archive repository ID

`1339652660` (`R_kgDOT9l6NA`).

## 7. IDs distinct

`PASS`. The exact DELETE target and canonical repository had distinct full names, database IDs,
node IDs and visibility. The canonical public repository was explicitly recorded as `NOT TARGET`.

## 8. Final destructive preflight

`PASS`: clean synchronized worktree, `HEAD == origin/main`, archive fetch-only/push-disabled,
zero tag, full-history Gitleaks PASS, 260-link documentation audit PASS, technical release preflight
PASS and REL1 preflight PASS. Identity was queried again immediately before deletion.

## 9. Archive runner initial state

`postgres-bulk-ci-01`, runner ID `22`, Linux x64, `online`, `busy=false`, registered only to the old
archive. The public and four other inspected owner repositories each had zero repository runner.

## 10. Runner host usage classification

`PRESERVE HOST`. One dedicated postgres-bulk Actions service/listener existed and no other GitHub
runner registration/process was found, but unrelated non-runner use of the host was not ruled out.
No host, Docker state, global service or runner home was deleted.

## 11. Runner removal mechanism

The official repository-runner REST DELETE removed runner ID `22`; the archive runner API then
reported `0`. Because local sudo required interactive authentication, the owner ran the bounded
`systemctl disable --now` command for the exact dedicated unit. Final service state is inactive,
dead and disabled with no listener process. See GitHub's
[runner removal procedure](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/remove-runners).

## 12. Removal token handling

No runner removal token was generated, printed, persisted or versioned. The official REST removal
used the existing GitHub CLI credential from the OS keyring. Repository and environment secrets
created: `0`; no credential value appears in Git or this report.

## 13. Archive runner removal result

`PASS`. Registration removed, listener absent and exact service inactive/dead/disabled. The
canonical repository runner inventory remained zero.

## 14. Archive runner count before deletion

```text
0
```

Deletion did not begin until this result was verified.

## 15. Public repo self-hosted runner count

```text
0
```

No runner was moved or registered to public.

## 16. Exact deletion target

```text
yravelo/postgres-bulk-private-archive
```

The last pre-DELETE response was ID `1339652660`, visibility `PRIVATE`, default branch `main`.

## 17. Archive deletion result

`PASS`. The first API attempt failed closed with HTTP 403 because the OAuth credential lacked
`delete_repo`; no repository was changed. GitHub CLI then obtained that exact additional scope via
the official device authorization flow, identities were rechecked, and the second exact DELETE
completed successfully. No ambiguous remote or repository selector was used.

## 18. Archive post-delete verification

Authenticated repository, pull-request and Actions-runs endpoints are unavailable. The archive is
no longer an active repository. This report makes no claim about immediate physical backend purge;
GitHub documents that some repositories may be restorable for a limited period. See
[repository deletion](https://docs.github.com/en/repositories/creating-and-managing-repositories/deleting-a-repository)
and [restoration](https://docs.github.com/en/repositories/creating-and-managing-repositories/restoring-a-deleted-repository).
Recovery is not the project rollback workflow.

## 19. Public canonical repo post-delete state

`yravelo/postgres-bulk` remains `PUBLIC`, default branch `main`, protected, accessible by API/web
and anonymous Git, with zero runner, tag or Release.

## 20. Public repository ID preserved

`PASS`: ID remains `1346700826`, node ID `R_kgDOUEUGGg`.

## 21. Public HEAD preserved

`PASS`: public HEAD remained `5572b9792f09ccc4beaa7cea6a8a358ad0594e5d` across runner removal
and archive deletion. The later `ee134a1631642dfefbc18d349c58d472ed71d433` commit only closes the
now-removed RR-02 security-policy state; the final documentation commit records this transaction.

## 22. Anonymous clone post-delete

`PASS`. A fresh credential-free HTTPS clone resolves the final public `main`, exposes only
`HEAD`/`refs/heads/main`, has zero tag and passes identity/path, Gitleaks history/current and
documentation checks.

## 23. Old metadata inheritance check

Old PR inheritance: `0`; old Actions inheritance: `0`; old runner metadata inheritance: `0`.
Deleting the separate archive did not copy any resource into the canonical repository.

## 24. Local archive remote cleanup

The fetch-only `archive` remote was first confirmed unreachable after deletion and then removed via
`git remote remove archive`. Its remote-tracking ref and obsolete Git configuration are gone.

## 25. Final remote mapping

```text
origin -> yravelo/postgres-bulk
```

Origin retains the canonical SSH fetch/push URL and was not rewritten.

## 26. Operational archive references remaining

```text
0
```

Git configuration, workflows, scripts and live machine security policy contain no archive
dependency. Public-safe historical documents retain conceptual names/IDs solely as migration
evidence; they are not links or operational inputs.

## 27. Runner service/host final state

Registration absent; service `inactive/dead/disabled`; listener absent. The host and runner
filesystem are preserved, with no global Docker prune or unrelated service mutation. Any future
registration would require new explicit authority and threat review.

## 28. Latest Build evidence

Hosted Build [`32940958320`](https://github.com/yravelo/postgres-bulk/actions/runs/32940958320)
is `PASS` on `ee134a1631642dfefbc18d349c58d472ed71d433`.

## 29. Latest Compatibility evidence

Hosted Compatibility
[`32940958344`](https://github.com/yravelo/postgres-bulk/actions/runs/32940958344) is `11/11 PASS` on
the same commit.

## 30. Latest Security evidence

Manually dispatched hosted Security
[`32941012127`](https://github.com/yravelo/postgres-bulk/actions/runs/32941012127) is `PASS` on the
same commit and validates the eight-risk post-archive baseline.

## 31. CodeQL state

CodeQL [`32940958224`](https://github.com/yravelo/postgres-bulk/actions/runs/32940958224) is `PASS`
on the same commit; open CodeQL alerts: `0`.

## 32. Public security/reporting state

Private Vulnerability Reporting, secret scanning/push protection and Dependabot security updates
are enabled. `main` retains 13 strict required checks, conversation resolution, disabled
force-push/deletion and zero mandatory approval for the single maintainer. Repository runners,
secrets and variables remain zero.

## 33. Final Gitleaks/history audit

`PASS`. Removing the archive remote reduced the local full-history scan to canonical public refs.
The fresh anonymous clone has only noreply owner/Dependabot identities, zero personal email/path,
and clean Gitleaks history/current results.

## 34. Documentation changes

MIG5B adds this closure report and updates the docs index, release roadmap/readiness, MIG4/MIG5
handoffs, acceptance criteria, runner/security governance and RR-02 baseline. Historical evidence
is retained without operational dependency or deleted private payload.

## 35. Git commits

- `ee134a1` — `security: retire archive runner residual risk`.
- Final documentation commit — `docs(release): close REL1 archive migration [skip actions]`.

The final commit's immutable SHA is the public `main` tip containing this report.

## 36. Final Git state

Local `main` is clean and synchronized with `origin/main`; `origin` is the only remote; canonical
history contains only public-safe noreply identities; tags: `0`.

## 37. REL1 final status

```text
COMPLETE
```

There is no remaining REL1 blocker.

## 38. Clean repository migration status

```text
COMPLETE
```

Canonical public identity, clean history and public trust boundary are permanent project state.

## 39. Old private archive status

```text
DELETED
```

The limited GitHub recovery capability is not treated as rollback or active project state.

## 40. REL2 status

```text
NOT STARTED
```

No release action was inferred from archive deletion.

## 41. EP-02 state

`PENDING`: offline OpenPGP backup/revocation recovery has not been verified. No key material was
read or modified by MIG5B.

## 42. EP-03 state

`PENDING — token MISSING`: no Maven Central Portal token or credential was created.

## 43. Remote actions performed

```text
old archive runner removed: yes
old archive deleted: yes
public repository visibility changed: no
public repository deleted: no
public repository history rewritten: no
self-hosted runner registered to public repo: no
repository secrets created: no
tag created: no
Benchmarks executed: no
Release executed: no
Central upload: no
Maven publication: no
REL2 started: no
```

## 44. MIG5B Definition of Done assessment

`PASS`. Destructive identity gates, runner removal, archive deletion/unavailability, canonical
identity/HEAD continuity, anonymous clone, local remote cleanup, operational-reference audit,
host safety, public health, documentation and REL1 closure all satisfy the required definition.

## 45. Final open-source activation verdict

```text
OPEN-SOURCE ACTIVATION COMPLETE
```

## 46. Exact next phase

```text
REL2 — Maven Central 0.1.0 Publication
```

REL2 is not started and requires its own prerequisites and explicit authorizations.

## Boundary statement

```text
REL1-MIG5B status: DONE
public canonical repo: HEALTHY
old archive runner removed: yes
old archive deleted: yes
archive remote removed: yes
anonymous clone: PASS
REL1: COMPLETE
clean migration: COMPLETE
repository public: yes
tag created: no
Central upload: no
Maven publication: no
REL2 started: no
```
