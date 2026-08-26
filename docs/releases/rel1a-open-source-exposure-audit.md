# REL1-A full Git history, privacy and open-source exposure audit

Audit date: 2026-08-25  
Audited source: `43fc6f5fdcb5a4c216c9e5a8898c7eb77be22cb4`
Repository: `yravelo/postgres-bulk` (`PRIVATE`)  
Decision: **OPEN-SOURCE ACTIVATION NO-GO — REMOTE IDENTITY BLOCKERS PENDING**

## REL1-MIG3B controlled-activation addendum

MIG3B supersedes only the MIG4 entry conclusion in the MIG3 addendum below. It reverified that the
private hosted-CI failure is external billing only and completed the local-equivalence,
public-content and hosted-only trust audits. The repository remains private and MIG3 remains
technically incomplete, but controlled MIG4 entry is now `READY` under the separate authorization
`AUTHORIZE_NEW_REPOSITORY_PUBLIC_ACTIVATION`. Public Build, Compatibility 11/11, Security and
anonymous verification remain mandatory before MIG4 can pass. See the
[MIG3B activation bridge](rel1-mig3b-public-hosted-ci-activation-bridge.md).

## REL1-MIG3 public-trust addendum

MIG3 supersedes the historical self-hosted workflow architecture described later in this audit.
The new canonical repository now fixes every workflow job to `ubuntu-latest`; public PR paths are
read-only and secret-free, and no persistent runner is registered there. Workflow fixtures pass,
but MIG3 remains `BLOCKED` because GitHub's owner-account billing/spending gate rejected remote
Build and all 11 Compatibility jobs before execution. Security was not dispatched against the same
known gate, so MIG4 was `NOT READY` at MIG3 closure. See the
[MIG3 report](rel1-mig3-ci-security-public-trust-baseline.md).

## REL1-MIG2 clean-history addendum

MIG2 pushed only canonical clean `main` to the new private repository and independently verified its
history, refs, noreply identities and Gitleaks result from a fresh clone. No old PR, PR ref, Actions
history, runner registration or credential was inherited. The private archive remains unchanged and
contains the isolated GitHub-managed historical blockers. CI/public-trust recreation and old-link
cleanup remain MIG3 work; the repository is still private and REL1-B has not started. See the
[MIG2 clean main migration](rel1-mig2-clean-main-push-baseline-recreation.md).

## REL1-MIG1 repository-isolation addendum

MIG1 retained the old GitHub repository and all GitHub-managed historical metadata privately as
`yravelo/postgres-bulk-private-archive` (ID `1339652660`) and created a separate empty private
`yravelo/postgres-bulk` (ID `1346700826`). The new repository inherited no pull requests, PR refs,
Actions runs/jobs/logs/artifacts, runner or issues. This isolates the blockers instead of deleting
them. The new repository is still empty and private: no clean `main` was pushed and REL1-B has not
started. See the [MIG1 repository container migration](rel1-mig1-repository-container-migration.md).

## REL1-A-R R6 authorized-contact addendum

R6 operated only within the authorized GitHub Support contact scope, reverified six synthetic PR refs,
58 affected runs and 270 affected jobs, and prepared the exact sanitized single-ticket request.
The official portal requires interactive owner authentication unavailable in the execution
environment; therefore the request is `NOT_SUBMITTED`, no case ID exists and owner manual action
is required. Both blockers remain `PENDING`; no run/ref/history/visibility mutation occurred and
REL1-B has not started. See
[R6 GitHub Support contact](rel1ar-github-support-remediation.md).

## REL1-A-R R5 remediation-plan addendum

R5 reconstructed the two remaining GitHub-managed blockers without destructive action. Historical
job metadata remains publicly queryable after a visibility change and contains
`<OLD_RUNNER_NAME_REDACTED>` in 270 jobs across 58 runs. GitHub still advertises six read-only
synthetic PR head refs that retain pre-rewrite personal email metadata, while cached old objects
also remain authenticated-accessible. Both blockers remain `PENDING`; the repository remains
`PRIVATE` and REL1-B has not started. See
[R5 GitHub-managed exposure plan](rel1ar-github-managed-exposure-plan.md).

## REL1-A-R R4C remediation addendum

R4C changed the active OS hostname and repository runner display identity to
`postgres-bulk-ci-01` using the supported host and official runner procedures. Build passed,
Compatibility passed 11/11 and Security passed. Across all 13 new jobs/logs, the old runner name,
old hostname and personal owner path each occurred zero times; new job metadata is fully neutral.

The active runner identity blocker is `REMEDIATED`. Historical job metadata remains queryable and
contains `<OLD_RUNNER_NAME_REDACTED>` in 270 jobs across 58 runs; it is an `OWNER DECISION REQUIRED`
blocker because no job-metadata-only deletion is supported. Six synthetic PR refs remain pending,
so activation remains `NO-GO`. See
[R4C runner identity remediation](rel1ar-runner-identity-remediation.md).

## REL1-A-R R3 remediation addendum

R3 selectively deleted the logs of all 58 runs in the exact `DELETE_LOGS` set. The complete
196-run post-delete pass found 58 unavailable and 138 preserved log endpoints with zero mismatch;
run metadata/results remain, and no run or artifact was deleted. Known privacy-sensitive
historical Actions logs remaining: `0`.

The runner display name remains in historical job metadata for affected runs and the active
runner/host identity is not neutralized. Those facts belong to the separate runner-identity
blocker. Six synthetic PR refs also remain pending. The repository therefore remains private and
open-source activation remains `NO-GO`. See
[R3 Actions log remediation](rel1ar-actions-log-remediation.md).

## REL1-A-R R2B remediation addendum

The original audit below is retained as historical evidence, with project commit references mapped
to their rewritten equivalents. Its email and signing-path classifications are superseded by
`docs/releases/rel1ar-public-history-remediation.md`: the owner selected `REWRITE`, controlled
`main` now uses `29708813+yravelo@users.noreply.github.com`, historical document occurrences use
`<PERSONAL_EMAIL_REDACTED>`, and the personal signing/home path is neutralized.

Open-source activation remains `NO-GO`. GitHub-managed synthetic PR refs still require separate
GitHub Support handling, while Actions-log deletion and runner identity remediation remain
explicitly unauthorized and `PENDING`. REL1-B has not started.

## Scope and decision

This is the exhaustive, read-only activation audit required before REL1-B. It covers every commit
reachable from the local and fetched GitHub refs, every unique historical blob, current project
content, GitHub repository metadata and available Actions logs. The audit included the preceding
documentation-only cleanup commit. The commit that adds this report is necessarily outside the
report's self-audited SHA and is verified separately by the local and remote closure gates.

The repository must not be made public yet. Two high-severity privacy exposures require owner
action: historical Git metadata publishes the owner's personal email and old file versions publish
the exact owner home/signing path; retained GitHub Actions logs publish the personal workstation
hostname and owner-specific runner name. The Git history requires an explicitly authorized rewrite
before public activation. A separate, explicitly authorized GitHub log cleanup and neutral runner
identity are also required. No credential or private key was found.

## Mandatory findings

### 1. REL1-A result

`DONE` as an audit; activation result is `NO-GO`. REL1-B is not started.

### 2. Audited SHA

`43fc6f5fdcb5a4c216c9e5a8898c7eb77be22cb4` on `main`, including the safe HEAD cleanup.

### 3. Git topology/ref inventory

- Default/current branch: `main`; remote: `origin` (`yravelo/postgres-bulk`).
- GitHub branches: only `main`; no tags, releases, submodules or LFS configuration.
- Root commit: `e2161df6813eb38810e012a5460b986c2d8c4afc`; merge commits: zero.
- GitHub advertises `refs/pull/1/head` through `refs/pull/6/head`; all six closed Dependabot PR
  heads were included. The audit intentionally used the broader local/fetched `--all` boundary.
- `git fsck --full --no-reflogs` found two dangling local commits/blobs. They are unreachable and
  absent from advertised remote refs; they must not be pushed or treated as public history.

### 4. Commit count

The audited ref set contains 112 unique reachable commits; `main` contains 101. Each commit's SHA,
author, committer, date, subject and changed-path count was traversed. There are no merges. The
largest change is `241ad0` with 185 paths, explained by the project identity/package migration.

### 5. Author/committer inventory

| Role | Identity | Count | First/last | Classification |
| --- | --- | ---: | --- | --- |
| Author | `Yusnier Blanco Ravelo <PERSONAL_EMAIL_REDACTED>` | 97 | 2026-08-18 / 2026-08-25 | SUPERSEDED BY R2B REWRITE |
| Author | `dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>` | 15 | 2026-08-25 / 2026-08-25 | APPROVED FOR PUBLIC |
| Committer | `Yusnier Blanco Ravelo <PERSONAL_EMAIL_REDACTED>` | 100 | 2026-08-18 / 2026-08-25 | SUPERSEDED BY R2B REWRITE |
| Committer | `GitHub <noreply@github.com>` | 12 | 2026-08-25 / 2026-08-25 | APPROVED FOR PUBLIC |

### 6. Owner email exposure assessment

`<PERSONAL_EMAIL_REDACTED>` was present in immutable author/committer metadata throughout the reachable
history. It is personal data, not a project secret. Classification: `OWNER DECISION REQUIRED`,
severity `HIGH PRIVACY/SECURITY` because publication makes it permanently cloneable.

For future commits GitHub supports a private noreply identity. If the owner chooses it, the expected
account-specific form is `29708813+yravelo@users.noreply.github.com`; changing future configuration
does not remove prior addresses. See [GitHub email concepts](https://docs.github.com/en/account-and-profile/concepts/email-addresses)
and the [noreply address reference](https://docs.github.com/en/account-and-profile/reference/email-addresses-reference).

### 7. Owner identity decision required

The owner must explicitly choose `KEEP HISTORY AS-IS` or `REWRITE HISTORY BEFORE PUBLIC` for the
personal email. Independently, the historical exact home/signing path makes the audit recommendation
`REWRITE HISTORY BEFORE PUBLIC`. No identity or Git configuration was changed automatically.

### 8. Commit-message privacy audit

All subjects and bodies were inspected for credentials, clients, employers, internal tickets,
private URLs, paths and temporary notes. Messages are professional and phase-oriented. The phrase
“private reporting” is legitimate security-policy context; Dependabot bodies contain only public
upstream release material. Classification: `SAFE TO PUBLISH`.

### 9. Historical path inventory

`git rev-list --objects --all` produced 713 unique historical paths and 1,140 unique reachable
blobs. Every path name and every unique blob was scanned, including prior versions and deletions.

### 10. High-risk filename findings

No `.env`, credential file, private key, keystore, Maven `settings.xml`, dump, backup, packet
capture, database, archive or local IDE configuration was found in reachable history. Matches for
`docs/security/secrets-and-actions-hardening.md` and `examples/.../schema.sql` are legitimate names,
not exposures. Classification: `SAFE TO PUBLISH`.

### 11. Deleted-file audit

The only relevant deleted reachable source path was an old pgJDBC `package-info.java`; its content
was package Javadoc only. No sensitive deleted file was found. Classification: `SAFE TO PUBLISH`.

### 12. Full-history Gitleaks

Gitleaks 8.30.1 scanned all 112 commits and approximately 4.48 MB: `PASS`, no leak found. Manual
history and pattern review supplemented this result.

### 13. Private-key audit

No RSA, EC, OpenSSH, PGP private-key, PKCS12 or keystore material was found. The committed OpenPGP
artifact is a public key only. Classification: `SAFE TO PUBLISH`.

### 14. Token/credential audit

No GitHub, Central/Sonatype, cloud, database, JWT, Bearer or Basic-auth secret was found. Compose
credentials are explicitly disposable localhost development values. Credential rotation required:
`no`.

### 15. Absolute-path audit

Old reachable versions expose an exact owner home path ending in
`.local/share/postgres-bulk-release/gnupg` in signing instructions. Current HEAD no longer contains
the owner-home prefix, but deletion from HEAD does not remove history. The exact value is
intentionally redacted here. Classification: `HISTORY REWRITE REQUIRED`; severity
`HIGH PRIVACY/SECURITY`.

`/home/postgres-bulk-runner` is an intentional runner layout, `/tmp/postgres-bulk-jdks/...` is a
reproducibility path and `/home/private` is synthetic test data. These do not contain credentials.

### 16. Host/system identity audit

Reachable Git content contains no personal hostname, MAC, VPN endpoint, private network address or
container identifier. Available GitHub Actions logs do expose a personal workstation hostname, an
owner-specific runner name, runner group and runner home. Exact values are intentionally redacted
here. Classification: `BLOCKER`, severity `HIGH PRIVACY/SECURITY`.

### 17. Email/personal-data audit

Outside Git metadata, only `fixture@example.invalid`, `security@example.invalid` and the authorized
project contact `postgresbulk-security@proton.me` occur. No telephone number, street address or
other obvious personal identifier was found. Owner Git metadata remains the decision in items 6–7.

### 18. Company/internal information audit

No employer, client, internal project, ticket, VPN, private registry or internal Maven repository
reference was found. Classification: `SAFE TO PUBLISH`.

### 19. Private-repository reference audit

No dependency, submodule, URL, issue, PR or discussion points to another private repository. The
six current-repository Dependabot PRs contain public dependency information only.

### 20. Binary/blob inventory

No reachable blob is binary, archive, database, compiled class or JAR. Largest blobs are benchmark
raw JSON: J8 about 419 KB and MS8 about 354 KB; the largest source is about 63 KB. They are useful
text evidence, not generated release artifacts.

### 21. Repository/history size

`git count-objects -vH` reports 2,746 loose objects and 12.55 MiB, with no garbage. Reachable object
types are 1,140 blobs, 111 pre-cleanup commits plus the audited cleanup commit, and 1,473 trees.
History health is acceptable; the privacy rewrite requirement is unrelated to size.

### 22. Generated-artifact history

No `target/`, compiled class/JAR, staged release, generated SBOM, scanner report, IDE output or Codex
attachment is reachable. Classification: `SAFE TO PUBLISH`.

### 23. Benchmark evidence audit

J8/MS8 CSV/JSON/raw evidence contains deliberate CPU/RAM, OS/kernel, JVM, PostgreSQL and image-tag
details needed for reproducibility. It uses localhost, generic JVM/temp paths and disposable test
credentials. No user, hostname, home path, private IP, environment secret, container ID or daemon
credential was found. Classification: `SAFE TO PUBLISH`.

### 24. Security evidence audit

No token, registration secret, personal recovery contact, private backup location or undisclosed
incident data is versioned. Sanitized account-control/MFA/recovery outcomes are appropriate; the
detailed evidence remains owner-controlled and private.

### 25. Runner information exposure

The repository documents a generic dedicated account and persistent-runner risk model, which is
appropriate. Retained Actions logs are not: 44 runs / 227 log files include the personal hostname
and owner-specific runner name. Before visibility change, the owner must authorize deletion of the
affected run logs, rename the runner and host to neutral identities, rerun trusted gates and rescan
the new logs. Deleting logs can preserve run metadata; do not delete or alter evidence without that
authorization. See [workflow log deletion](https://docs.github.com/en/rest/actions/workflow-runs)
and [workflow run logs](https://docs.github.com/en/actions/how-tos/monitor-workflows/use-workflow-run-logs).

### 26. OpenPGP exposure audit

Only the public identity and fingerprint `11545CD242C9575DF408AC08F83D364143C798A3` are current. No
private key, passphrase, revocation certificate or offline backup location is present. The old exact
owner `GNUPGHOME` path is the item 15 history finding, not key material.

### 27. Security reporting exposure audit

`postgresbulk-security@proton.me` is the explicitly authorized public contact. No personal recovery
contact is exposed. The future PVR channel supplements this address and must be enabled/tested only
after public activation starts.

### 28. Maven developer metadata privacy

POM developer identity is `yravelo` with the public GitHub profile and no personal email. SCM and
project URLs point to `yravelo/postgres-bulk`. Classification: `SAFE TO PUBLISH`.

### 29. POM public metadata readiness

Group, artifact names, descriptions, URL, SCM, Apache-2.0 license and developer fields are complete
and consistent. The SCM tag `v${revision}` is a planned release convention, not a claim that a tag
exists. No private repository or mirror is configured. Result: `PASS`.

### 30. README open-source readiness

README explains purpose, supported stacks, installation, JPA/JDBC examples, limitations, security,
contribution and license. It clearly uses `0.1.0-SNAPSHOT` and states that artifacts are not yet
published to Maven Central. Result: `PASS` for content; public-state metadata changes belong to
REL1-B.

### 31. Root-file readiness

`README.md`, `LICENSE`, `SECURITY.md`, `CONTRIBUTING.md`, `CHANGELOG.md`, `.gitignore` and
`.gitattributes` are present. Apache-2.0 is recognized and appropriate. No third-party attribution
requires a `NOTICE`; an empty file must not be created.

### 32. CONTRIBUTING readiness

Build, test, JDK, Docker, PR and security guidance is complete. REL1-A removed stale “reporting
channel pending” language in separate commit `82ee79a`; current content contains no personal path.
Result: `PASS`.

### 33. SECURITY readiness

The policy defines supported-version state, private reporting, response expectations, disclosure
and the verified Proton channel without exposing recovery details. Result: `PASS`; PVR remains a
REL1-B activation/test action.

### 34. Code of Conduct decision

`ADD BEFORE PUBLIC`. The repository intends to accept Issues and later external contributions, so a
standard, enforceable community code is appropriate. It was not added automatically because choice
of policy/contact is an owner governance decision. Severity: `LOW HYGIENE`.

### 35. CHANGELOG/release-notes decision

Keep both. `CHANGELOG.md` is the concise chronological index; `docs/releases/0.1.0.md` provides the
candidate's detailed notes. The unreleased date/status is honest and does not imply publication.

### 36. Docs stale/private-language audit

Current private-state and owner-only release statements are accurate until REL1-B. The one stale
reporting-channel statement was cleaned. Historical phase/security documents are legitimate design
evidence; REL1-B must update only claims that become false when visibility changes.

### 37. Internal prompt/work artifact audit

No Codex prompt, attachment, internal instruction, TODO scratchpad, IDE state or local work artifact
is reachable. Classification: `SAFE TO PUBLISH`.

### 38. Example-data audit

Examples use synthetic products, customers and tenant schemas. No production/personal data is
present. Result: `PASS`.

### 39. Docker/Compose audit

Compose exposes PostgreSQL only on localhost development port 5432 and uses documented disposable
passwords. Mounts and images are public/generic; no private registry or host-specific mount exists.
Result: `PASS`.

### 40. Private dependency/submodule/LFS audit

No private Maven repository/mirror, Git submodule, Git LFS object/configuration or external private
resource is required. Result: `PASS`.

### 41. Current workflow public-risk audit

Build and eleven Compatibility jobs use the persistent self-hosted runner, but every
`pull_request` job has the exact owner-branch/repository guard. Security is schedule/manual only;
Benchmarks and Release are manual on GitHub-hosted runners. No `pull_request_target` exists and no
release secret is configured. If visibility changed unchanged, external PR validation would be
skipped rather than run on self-hosted infrastructure.

### 42. Public PR self-hosted risk verdict

`PASS` for isolation: untrusted/fork/Dependabot code cannot execute on the personal persistent
runner under current guards. It is not yet a useful public CI path because external PRs receive no
Build/Compatibility coverage. GitHub recommends GitHub-hosted runners for untrusted public code;
see [secure use of Actions](https://docs.github.com/en/actions/reference/security/secure-use).

### 43. Proposed public CI architecture

- External/fork/Dependabot PR: standard GitHub-hosted `ubuntu-latest`, read-only token, no secrets,
  no persistent cache carrying sensitive data, Maven tests plus bounded Testcontainers coverage.
- Trusted `main`: existing controlled self-hosted path with exact guards and neutral runner identity.
- Release: owner-dispatched, local controlled signing; public CI never receives private key material.

This is the REL1-B design only; it has not been implemented.

### 44. GitHub-hosted public repo cost/availability

Standard GitHub-hosted runners are free and unlimited for public repositories; larger runners are
charged and artifact/cache storage remains plan-limited. `ubuntu-latest` is a full VM with Docker
and supports the project's Testcontainers lane; do not use the limited `ubuntu-slim` image for it.
Sources: [GitHub-hosted runners](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)
and [Actions billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions).

### 45. Dependabot public behavior

Keep bounded weekly updates and no auto-merge. Treat Dependabot PR code as untrusted and run it only
on the proposed hosted PR lane. Existing closed PRs/comments contain no private information.

### 46. Actions repository-settings audit

Default workflow token permission is read; workflow approval from Actions is disabled; auto-merge
is off. All actions are currently allowed, while repository policy verifies immutable SHA pins.
Private-fork workflow execution/approval settings are disabled. No repository secrets, deploy keys,
hooks, retained artifacts or deployments were found; the `maven-central` environment has no secret
or protection rule. REL1-B should evaluate “selected actions” without weakening pinned-SHA checks.

### 47. Branch protection/rulesets assessment

The private Free repository currently has no branch protection/ruleset; the APIs are unavailable in
that state. Public repositories can use them on GitHub Free. REL1-B should protect `main`, block
force-push/deletion, require PRs, required Build/security checks and resolved conversations, and
keep bypass minimal. A single-maintainer project should not invent an impossible independent-review
count. See [rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets)
and [protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches).

### 48. CodeQL assessment

`ENABLE IN REL1-B` after public visibility, using Java/Kotlin default setup or immutable pinned
init/build/analyze actions. Public CodeQL usage is free. Preserve SpotBugs/FindSecBugs as a distinct
gate rather than duplicating or replacing it. See [CodeQL](https://docs.github.com/en/code-security/concepts/code-scanning/codeql/codeql-cli).

### 49. Dependency Review assessment

`ENABLE IN REL1-B` for public PRs, alongside existing full-reactor OSV validation. It can block new
vulnerable dependencies before merge and is available for public repositories. See
[Dependency Review](https://docs.github.com/en/code-security/concepts/supply-chain-security/dependency-review).

### 50. PVR assessment

`ENABLE AND TEST IN REL1-B`, after the visibility change makes the feature available. Test reporter
and maintainer flows with sanitized content; retain Proton as fallback. Do not enable during REL1-A.
See [Private Vulnerability Reporting](https://docs.github.com/en/code-security/how-tos/report-and-fix-vulnerabilities/configure-vulnerability-reporting).

### 51. OpenSSF Scorecard assessment

`DEFER` until public state is stable. Use it as an informational signal and remediation input, not
as a total-score release gate.

### 52. Attestations/provenance assessment

`DEFER TO REL2` or a post-public release candidate. Public repositories can use artifact
attestations, but they supplement rather than replace the owner-controlled OpenPGP signing model.
See [artifact attestations](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations).

### 53. Security settings transition plan

| Capability | Current private state | Public availability | REL1-B action | Reason |
| --- | --- | --- | --- | --- |
| Hosted PR CI | Absent | Standard runners free | Add unprivileged lane | Validate untrusted PRs safely |
| Self-hosted CI | Trusted guarded only | Available | Preserve guards; neutralize identity | Prevent persistence/host exposure |
| Ruleset/protection | Unavailable/no rule | Free | Enable on `main` | Prevent destructive/direct changes |
| CodeQL | Disabled | Free | Enable | First-party code scanning |
| Dependency Review | Disabled | Available | Enable | PR supply-chain delta gate |
| Secret scanning | Private feature disabled | Available | Enable/verify | Continuous leak detection |
| PVR | Unavailable/disabled | Available | Enable and test | GitHub-native private reporting |
| Scorecard | Not configured | Available | Defer/informational | Avoid premature vanity gate |
| Attestations | Not configured | Available | Defer to REL2 | Release provenance supplement |

### 54. Repository description recommendation

`PostgreSQL COPY-based bulk insert and lookup for Spring Data JPA and JDBC.` Do not apply until
REL1-B.

### 55. Repository topics recommendation

`postgresql`, `java`, `spring-boot`, `spring-data-jpa`, `spring-data-jdbc`, `jdbc`, `bulk-insert`,
`copy`. Do not apply during REL1-A.

### 56. Homepage recommendation

Leave empty initially; README/docs are the canonical destination. Do not invent a project site.

### 57. Issues recommendation

Keep enabled for public bugs/features. Direct security reports to Proton and, after activation, PVR.

### 58. Discussions recommendation

Keep disabled initially; enable only when there is enough community demand and moderation capacity.

### 59. Wiki recommendation

Keep disabled; versioned documentation is the maintained source of truth.

### 60. Forking implications

Public visibility permits anyone to fork/clone and retain the full history. Cleanup after exposure
cannot recall those copies. See [repository visibility](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/managing-repository-settings/setting-repository-visibility)
and [forks](https://docs.github.com/en/pull-requests/reference/forks).

### 61. History rewrite required

`yes`. Remove the exact personal home/signing path before public exposure. The rewrite also provides
the mechanism for removing the personal email if the owner chooses that option. No rewrite was
executed.

### 62. History rewrite impact if applicable

An authorized `git filter-repo`-style rewrite changes every affected/downstream SHA, invalidates
existing commit-signature associations and historical CI/evidence identifiers, requires coordinated
force-push and clone replacement, and may leave immutable pull-request refs/caches. REL0/security
inventories and all local/remote gates must be regenerated on the rewritten root. GitHub Support
cannot be assumed to erase ordinary privacy data. Follow GitHub's
[sensitive-data history guidance](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository),
but execute nothing without explicit owner authorization and a tested backup/coordination plan.

### 63. Credential rotation required

`no`. No real credential/private key was detected. Log/path cleanup is privacy hardening, not token
rotation.

### 64. Legal/IP technical review

No copied vendor source, unknown copyright header, untracked third-party bundle or attribution gap
was found. Maven Wrapper/upstream notices are preserved; dependency licenses are not copied-source
licenses. Apache-2.0 is consistent. Result: `PASS` as a technical review, not legal advice.

### 65. Name/trademark collision review

Web/GitHub search finds many descriptive “postgres bulk” projects and adjacent .NET/Python/Java
libraries, but no evident identical established Java/Spring project identity. The name is generic
and weakly distinctive; no legal/trademark opinion is made. Classification: `INFO`.

### 66. Maven coordinate collision review

Official Maven Central search returned no artifact under `io.github.yravelo` and no
`postgres-bulk-core` collision. Planned coordinates appear available as of the audit date; this is
not a reservation or publication claim.

### 67. Search/public-perception review

Searches for `postgres-bulk` and `yravelo postgres bulk` show the current repository plus unrelated
descriptive projects, with no strong identity confusion. The public-facing name, GitHub repository,
Maven group and SCM URLs are internally consistent.

### 68. Current HEAD cleanup performed

Commit `82ee79a` replaced stale private-reporting “pending” language in `CONTRIBUTING.md` with the
verified project channel and private-evidence boundary. It changed no code, history or GitHub state.

### 69. Cleanup still required

- GitHub Support handling of old objects retained by synthetic PR refs and cached views.
- Separately authorized deletion of affected Actions run logs; neutral runner/host identity; fresh
  log scan.
- Add a Code of Conduct before opening, with owner-selected enforcement contact.

### 70. Owner decisions required

1. Authorize the prepared GitHub Support request for synthetic PR/cached history handling.
2. Authorize affected Actions-log deletion and choose neutral host/runner identities.
3. Select the Code of Conduct and enforcement contact.

### 71. Exposure finding matrix

| Finding | Classification | Severity | Activation effect |
| --- | --- | --- | --- |
| Personal email in controlled `main` metadata/content | REMOVED BY R2B | HIGH PRIVACY/SECURITY | PASS for controlled history |
| Exact owner home/signing path in controlled `main` | REMOVED BY R2B | HIGH PRIVACY/SECURITY | PASS for controlled history |
| Old objects in synthetic PR refs/caches | PENDING SUPPORT ACTION | HIGH PRIVACY/SECURITY | BLOCKER |
| Personal hostname/runner name in Actions logs | REMOVED BY R3 | HIGH PRIVACY/SECURITY | PASS for log content |
| Active runner identity and new CI metadata/logs | REMOVED BY R4C | HIGH PRIVACY/SECURITY | PASS |
| Historical job metadata runner name | OWNER DECISION REQUIRED | HIGH PRIVACY/SECURITY | BLOCKER |
| Missing Code of Conduct | CLEANUP BEFORE PUBLIC | LOW HYGIENE | REL1-B entry cleanup |
| No secrets/private keys/credentials | SAFE TO PUBLISH | INFO | PASS |
| Product/docs/examples/benchmarks/security evidence | SAFE TO PUBLISH | INFO | PASS |
| External PR blocked from self-hosted runner | SAFE TO PUBLISH | INFO | PASS isolation |
| External PR hosted validation absent | CLEANUP BEFORE PUBLIC | MEDIUM CLEANUP | Implement in REL1-B |

No `CRITICAL EXPOSURE` was detected.

### 72. REL1-A report location

`docs/releases/rel1a-open-source-exposure-audit.md`.

### 73. Scripts/auditors created

None committed. Temporary read-only/redacted analysis scripts were kept outside the repository and
are not part of product history. Existing maintained repository gates supplied the reproducible
secret/security checks.

### 74. Regression fixtures

No new auditor was committed, so no new fixture set is required. Existing security gates already
cover synthetic secret/key/path cases; this audit additionally reviewed all historical blobs and
GitHub logs without storing sensitive excerpts.

### 75. Local security/preflight validation

`PASS`. Documentation/link validation checked 226 relative targets. `check-security.sh full`
passed policy, fixtures, current/full-history Gitleaks, 138/138 OSV inventory with zero blockers,
reactor/Testcontainers, SAST, SBOM/license, OpenPGP-public-key and runner-health gates. The technical
release preflight and REL1 preflight both passed on synchronized private `main`. The corrected
report also passed `check-security.sh fast` after redacting exact exposure values.

### 76. Remote Build

`PASS` for `30113f20a9a567173f6ad14bc19fc77041dfe1e5`, run
`32871784539` (historical run ID `32871784539`). An earlier report
run `32871560008` correctly failed because the draft repeated the exact private path; the value was
redacted and the gate passed on the corrected state.

### 77. Remote Compatibility

`PASS`, all 11/11 jobs for `30113f20a9a567173f6ad14bc19fc77041dfe1e5`, run
`32871784542` (historical run ID `32871784542`).

### 78. Remote Security

`PASS` for `30113f20a9a567173f6ad14bc19fc77041dfe1e5`, manually dispatched run
`32873968105` (historical run ID `32873968105`). No Benchmarks or
Release workflow was dispatched.

### 79. Git commits

- `82ee79a docs(security): refresh verified reporting guidance`
- `3dedc05 docs(release): record REL1-A exposure audit`
- `b83e5c0 docs(release): redact REL1-A exposure evidence`
- Remote-evidence closure: the commit containing this final evidence update.

No rewrite or force-push commit was made.

### 80. Final Git state

The validated `b83e5c0` state was clean, synchronized with `origin/main` and private. The
evidence-only closure commit must end in the same clean/synchronized/private state and receives the
normal remote Build/Compatibility checks plus a manually dispatched Security check.

### 81. Remote actions performed

```text
repository visibility changed: no
GitHub PVR enabled: no
public CI activated: no
history rewritten: no
force push performed: no
tag created: no
Benchmarks executed: no
Release executed: no
Central upload: no
publication: no
REL1-B started: no
```

### 82. Definition of Done assessment

`PASS` for REL1-A audit completion: the exhaustive scope, report, local validation and remote
Build/Compatibility/Security evidence are complete. Activation remains intentionally blocked;
findings do not make the audit itself incomplete.

### 83. Final open-source activation verdict

```text
OPEN-SOURCE ACTIVATION NO-GO
NO-GO — OWNER DECISION REQUIRED
```

### 84. Blocking findings

Retained historical job-metadata runner identity and six GitHub-managed synthetic PR refs. Active
runner/host identity, controlled `main` email/path history and Actions log content are remediated.

### 85. Owner decisions required before REL1-B

Decide how to handle historical job metadata, resolve synthetic PR refs, and select a Code of
Conduct/enforcement contact. REL1-B cannot start merely by accepting the remaining risk implicitly.

### 86. REL1-B entry criteria

- History rewrite authorized, rehearsed, executed and fully rescanned; or a new explicit audit
  decision that explains why the path is acceptable (this report recommends rewrite).
- Personal email decision recorded and implemented.
- Affected Actions logs removed with authorization; runner identity neutralized and replacement
  logs scanned (`DONE`). Historical job metadata remains separately pending.
- Local full security, technical and REL1 preflights pass on rewritten final history.
- Remote Build, Compatibility 11/11 and Security pass on final synchronized `main`.
- Code of Conduct selected; hosted untrusted-PR CI, ruleset, CodeQL, Dependency Review and PVR
  activation steps prepared for the controlled REL1-B transition.

### 87. Next recommended action

Do **not** start `REL1-B — Public Repository Activation & External Verification`. Prepare a
read-only plan for historical job metadata and synthetic PR-ref remediation. Do not delete runs or
contact GitHub Support automatically. Only a subsequent explicit GO may name REL1-B as the next
action.

## Boundary statement

```text
REL1-A status: DONE
full history audited: yes
full-history secrets: PASS
private-key exposure: PASS
privacy exposure: PARTIALLY REMEDIATED; REMOTE IDENTITY BLOCKERS PENDING
author/email decision: IMPLEMENTED
history rewrite required: completed
public PR self-hosted isolation ready: yes
open-source activation verdict: NO-GO
repository public: no
REL1-B started: no
tag created: no
Central upload: no
publication activated: no
```

The governing standard is permanent exposure: **are we comfortable with this being permanently
public now?** On the audited state, the answer is no.
