# REL1-MIG4 public activation and external verification

## MIG5B final-closure addendum

MIG5B consumed the separate exact deletion authorization, removed the archive runner registration,
stopped/disabled its dedicated service, deleted only the old private archive and removed the local
archive remote. The public repository identity and clean history remained intact and all hosted,
security and anonymous checks pass. REL1 and clean repository migration are now `COMPLETE`; REL2
is `NOT STARTED`. See the
[MIG5B closure report](rel1-mig5b-archive-deletion-and-rel1-closure.md).

## MIG5 decommission-readiness addendum

MIG5 subsequently proved that the old private archive is no longer a source, documentation,
CI/security, audit or REL2 dependency. Its remaining GitHub metadata is safe to lose, its dedicated
runner is decommissionable, and no private backup is recommended. The archive remains private and
undeleted pending the exact separate authorization
`AUTHORIZE_OLD_PRIVATE_REPOSITORY_DELETION`. See the
[MIG5 readiness report](rel1-mig5-old-private-archive-decommission-readiness.md).

Date: 2026-08-26  
Canonical repository: `https://github.com/yravelo/postgres-bulk`  
Operational evidence commit: `dd63b0d2a0cdbfe033b94afc07ef1a9d2c648752`

## 1. MIG4 result

`DONE`. The clean canonical repository was activated publicly and passed the required hosted,
anonymous, privacy, trust and adoption gates. **OPEN-SOURCE ACTIVATION: GO.**

## 2. Authorization consumed

The visibility mutation consumed the exact owner authorization
`AUTHORIZE_NEW_REPOSITORY_PUBLIC_ACTIVATION`. It authorized MIG4 only; no downstream release or
archive-deletion authority was inferred.

## 3. Pre-activation state

Immediately before activation, `yravelo/postgres-bulk` was private, defaulted to `main`, contained
118 canonical commits at `d0bd563d7fb6db23e6b88570d2e0f4a8a9a507bd`, had no tag, PR, artifact,
environment, deployment key, repository runner, secret or variable, and was synchronized locally.

## 4. Private preflight

The private technical and REL1 preflights passed. Local full Build-equivalent, Compatibility
11/11-equivalent and Security validation were already PASS; the remaining remote constraint was
the documented private-account hosted-runner billing gate.

## 5. Privacy preflight

Full-history and current-tree Gitleaks passed. Canonical history contained only the owner GitHub
noreply identity and Dependabot noreply identity, with zero personal email, owner home path,
private-key marker or exact archive SSH URL occurrence.

## 6. CI trust preflight

All jobs selected `ubuntu-latest`; workflow permissions were read-only by default; actions were
immutable-SHA pinned; PR jobs received no repository secret; and the canonical repository had zero
self-hosted runners. Security fixtures and the hosted-only public-PR threat model passed.

## 7. Visibility activation

The repository visibility was changed once from `PRIVATE` to `PUBLIC` through the GitHub API after
the exact authorization. No repository rename, transfer, recreation or history rewrite occurred.

## 8. Repository identity before and after

The repository retained database ID `1346700826`, GraphQL node ID `R_kgDOUEUGGg`, full name
`yravelo/postgres-bulk` and default branch `main` across the visibility change.

## 9. HEAD before and after

Visibility activation did not mutate Git data: HEAD remained
`d0bd563d7fb6db23e6b88570d2e0f4a8a9a507bd` immediately before and after the API operation.
Subsequent commits contain only the audited hosted-prerequisite, Dependency Review and closure
changes described below.

## 10. Public URL

The canonical public URL is `https://github.com/yravelo/postgres-bulk`; anonymous Git and HTTP API
access both resolve to repository ID `1346700826`.

## 11. Archive visibility

`yravelo/postgres-bulk-private-archive`, repository ID `1339652660`, remains `PRIVATE`. Its default
branch and last pushed timestamp (`2026-08-25T22:33:31Z`) were unchanged by MIG4.

## 12. Actions settings

Actions permits selected actions, permits GitHub-owned actions, requires immutable SHA pinning,
uses read-only workflow-token permissions and cannot approve pull-request reviews. Fork workflows
require approval for all external contributors.

## 13. Dependency graph and Dependabot

The dependency graph and Dependabot security updates are enabled. Five weekly update lanes remain
configured, security updates are unpaused and automatic merge remains disabled. Repository and
Dependabot secret inventories are both zero.

## 14. Private Vulnerability Reporting

GitHub Private Vulnerability Reporting is enabled as the preferred channel. The previously
verified `postgresbulk-security@proton.me` mailbox remains the fallback; no sensitive test payload
or authentication metadata is stored in Git.

## 15. CodeQL

CodeQL default setup is enabled for Java/Kotlin with the default query suite, standard hosted
runner, weekly schedule and remote threat model. Setup run `32925122247` and incremental run
`32925429869` passed; the open CodeQL alert count was zero at closure.

## 16. Dependency Review

`.github/workflows/dependency-review.yml` is a public pull-request gate. It uses read-only contents
permission and SHA-pinned `actions/dependency-review-action` v5.0.0, checks vulnerabilities and
licenses, fails at moderate severity, emits no PR comments and receives no write permission.

## 17. Main protection

`main` is protected after the closure push: pull requests and conversation resolution are
required, force push and deletion are forbidden, and the hosted Build baseline, 11 Compatibility
jobs and Dependency Review are required checks. Required approvals are zero for the
single-maintainer project and administrative emergency recovery remains possible.

## 18. Build trigger

Visibility activation reran the canonical Build and subsequent fix-forward pushes triggered it
normally on the public repository. The final operational run is
[`32925430081`](https://github.com/yravelo/postgres-bulk/actions/runs/32925430081).

## 19. Build result

Build `32925430081` is `PASS` on GitHub-hosted infrastructure. An earlier public run correctly
found that the current hosted image lacked PyYAML and ripgrep; MIG4 fixed both audit prerequisites
by exact version/checksum and reran the unchanged gates successfully.

## 20. Compatibility trigger

The operational Dependency Review push triggered the complete canonical Compatibility workflow,
run [`32925429985`](https://github.com/yravelo/postgres-bulk/actions/runs/32925429985).

## 21. Compatibility result

Compatibility `32925429985` is `PASS`, 11/11: Java 17/21/25, Boot minimum/default, PostgreSQL
15/16/17/18, Hibernate minimum/maximum and pgJDBC minimum/maximum boundaries all passed.

## 22. Security trigger

Full Security was manually dispatched on the public operational commit, as required, as run
[`32925758807`](https://github.com/yravelo/postgres-bulk/actions/runs/32925758807).

## 23. Security result

Security `32925758807` is `PASS`. It executed the full continuous-security and hosted-runner
resilience pipeline without weakening, suppressing or retrying a failed security gate.

## 24. Hosted-runner evidence

The activation runs report GitHub-hosted runner names and Ubuntu hosted labels. Workflow YAML,
job API metadata and sanitized logs agree; no canonical job selected `[self-hosted]`.

## 25. Canonical runner count

The authenticated repository-runner inventory is zero after activation. The archive's one private
runner was neither moved nor registered to the canonical repository.

## 26. CI privacy audit

Hosted Build, Compatibility, Security and CodeQL job metadata/logs were scanned for the personal
email, personal home path, archive runner identity and private-key markers. No forbidden occurrence
was found; ordinary ephemeral `/home/runner` paths are GitHub-hosted infrastructure, not owner data.

## 27. Anonymous method

Verification used a fresh temporary directory, unset GitHub tokens, disabled credential helpers
and terminal prompts, and cloned the public HTTPS URL without a logged-in GitHub session.

## 28. Anonymous result

Anonymous clone and anonymous `git ls-remote` both passed. No credential prompt, cached helper or
authenticated fallback was used.

## 29. Anonymous HEAD, count and root

The operational anonymous clone resolved the expected canonical HEAD and 120 commits before the
Dependency Review/closure commits. The final closure audit resolved synchronized `origin/main`,
122 commits and the expected Maven multi-module root.

## 30. Anonymous refs

Anonymous `ls-remote` exposed only `HEAD` and `refs/heads/main`; zero tags and zero synthetic PR
refs were inherited.

## 31. Anonymous privacy audit

Full-history/current-tree Gitleaks passed from the anonymous clone. Author inventory and targeted
searches found zero personal email, personal home path, private-key marker or exact archive SSH URL.
Neutral historical documentation of the archive/runner containment remains intentional.

## 32. Public API audit

Unauthenticated repository API data reports the expected public ID, name, default branch and
Apache-2.0 license. Authenticated restricted inventories confirm zero canonical runners, secrets,
variables, deployment keys and environments.

## 33. Old pull requests

The new repository has zero pull requests and inherited zero synthetic PR ref. Historical archive
pull requests remain contained by the private archive boundary.

## 34. Old Actions history

Every Actions run visible in the public repository was created in the new repository. Zero old
archive run, job, log or artifact was inherited.

## 35. Old runner metadata

The canonical repository exposes zero old runner metadata. The neutral archive runner remains only
in the private archive and is outside the public trust boundary.

## 36. Landing page

The anonymous landing surface resolves publicly with the project description, Apache-2.0 license,
README, Issues and Security surfaces. Wiki, Discussions and Projects remain intentionally off.

## 37. README audit

README installation, compatibility, examples, security and no-release caveats are internally
consistent. It does not claim that a Central artifact or supported release already exists.

## 38. Documentation-link audit

The documentation-link gate passed for all 252 audited links before the closure addition and was
rerun after it. The docs index links this report and public security guidance points to PVR plus the
verified fallback mailbox.

## 39. Clean-room verification

A complete sequential clean-room build from the anonymous clone passed: reactor `clean verify`,
local install, both standalone examples and the isolated JDBC consumer.

## 40. Spring Data JPA example

`examples/spring-boot-basic` resolved only the clean-room-installed snapshot and passed its full
verification from the anonymous clone.

## 41. Spring Data JDBC example

`examples/spring-data-jdbc-basic` resolved only the clean-room-installed snapshot and passed its
full verification from the anonymous clone.

## 42. Isolated consumers

The isolated Spring Data JDBC consumer passed without accidental JPA/Hibernate/product benchmark
dependencies. The public adoption boundary therefore matches the declared modular graph.

## 43. Pull-request trust boundary

Public pull requests execute only ephemeral GitHub-hosted jobs with read-only default permissions,
no repository secrets and approval required for every external contributor. No public PR can reach
the private archive runner through canonical workflow labels.

## 44. Dependabot trust boundary

Dependabot receives no repository secret and cannot auto-merge. Its update PRs must pass the same
hosted Build, Compatibility and Dependency Review controls as other changes.

## 45. REL1-A re-audit

The clean-repository migration resolves the historical exposure blockers: no private archive PR
ref, Actions history, runner metadata or rewritten object crossed into the new public container.
REL1-A's historical body is retained, with a MIG4 addendum recording the superseding `GO` result.

## 46. Technical preflight

The final technical release/security preflight passed on the synchronized closure branch. It did
not import private signing material, create a tag or contact Maven Central.

## 47. REL1 preflight

The final REL1 preflight passed with public visibility, canonical identity, privacy, reporting and
hosted-trust evidence reconciled.

## 48. Activation decision

All mandatory MIG4 gates are green. **OPEN-SOURCE ACTIVATION: GO.**

## 49. Containment decision

Rollback containment remains intact: the archive is private, its runner is not reachable from the
canonical workflows, and the local `archive` remote remains fetch-only with push disabled.

## 50. Archive after MIG4

The archive remains `PRIVATE`, ID `1339652660`, and retains its historical metadata only behind
that boundary. MIG4 made no archive commit, settings or visibility mutation.

## 51. Archive deletion

The old archive was **not deleted**. Deletion or retirement requires a later explicit authorization
and is not implied by a successful public activation.

## 52. Release boundary

MIG4 created no tag, GitHub Release, signed candidate, Central deployment or Maven publication. It
stored no Central token, OpenPGP private key or passphrase in GitHub.

## 53. Documentation changes

This closure updates the docs index, release roadmap/readiness, REL1-A addendum, compatibility and
continuous-security evidence, runner boundary and root security policy. Historical audit bodies
remain available and are explicitly labelled as superseded where appropriate.

## 54. Commits

MIG4's fix-forward commits are `df5a08b` (pinned PyYAML), `91404ae` (pinned ripgrep), and `dd63b0d`
(public Dependency Review). The final documentation commit is titled
`docs(release): close MIG4 public activation [skip actions]`; its own immutable SHA is the public
`main` tip containing this report.

## 55. Final Git state

Final `main` contains 122 commits, matches `origin/main`, has no uncommitted change and has zero
tag. The canonical remote still targets `yravelo/postgres-bulk`; the archive push URL is disabled.

## 56. Exact remote Actions statement

Build hosted: `PASS`; Compatibility hosted: `11/11 PASS`; Security hosted: `PASS`; CodeQL hosted:
`PASS`; Dependency Review: configured as required PR check. Every cited operational job used
GitHub-hosted infrastructure and no canonical repository runner or secret.

## 57. Definition of Done

Authorization, identity preservation, public settings, hosted CI, security features, anonymous
clone, privacy/history, clean-room adoption, public metadata, documentation, branch protection and
archive containment all pass. MIG4's Definition of Done is satisfied.

## 58. Remaining work

Operational monitoring and future dependency updates continue. Release signing recovery, Portal
token acquisition, candidate selection, tag, GitHub Release and Central publication remain later
gates and are not authorized by this report.

## 59. MIG5 readiness

MIG5 may be evaluated as the next separately scoped phase because the public activation baseline is
now green. Readiness is not execution authority.

## 60. Next action

Stop at the MIG4 boundary and request the exact MIG5 instruction before any further repository or
release mutation.

## Boundary statement

```text
REL1-MIG4 status: DONE
new repository public: yes
old archive private: yes
Build hosted: PASS
Compatibility hosted: 11/11
Security hosted: PASS
anonymous clone: PASS
public privacy audit: PASS
public trust boundary: PASS
old metadata inherited: 0
OPEN-SOURCE ACTIVATION: GO
old archive deleted: no
tag created: no
Central upload: no
Maven publication: no
```
