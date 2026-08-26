# REL1-MIG3B — Public-hosted CI activation bridge

Date: 2026-08-26
Repository: `yravelo/postgres-bulk`
Audited private HEAD: `481d22a55f3f2f6cd4a457a88eab85b5ea7f5d56`
Result: **DONE — READY FOR CONTROLLED MIG4 PUBLIC ACTIVATION**

MIG3B proves that the only reason GitHub-hosted CI cannot execute before activation is the owner
account's private-repository billing/spending gate. It does not close MIG3's remote Build,
Compatibility or Security requirements. Instead, it defines a fail-closed MIG4 transaction in
which those checks are the first mandatory post-public gates. The repository remained private
throughout this work.

## 1. MIG3B result

`DONE`. The billing classification is `BLOCKED_EXTERNAL_BILLING_ONLY`; the hosted-only trust
boundary, local equivalence, public-content audit, settings plan, activation sequence and
containment plan all pass. Entry into controlled MIG4 is ready but not authorized.

## 2. Initial repository state

`main` was clean and synchronized at `481d22a55f3f2f6cd4a457a88eab85b5ea7f5d56`
(117 commits). New repository ID `1346700826` was PRIVATE with default branch `main`, zero PRs,
PR refs, runners, artifacts, repository secrets, repository variables and Dependabot secrets.
Only `refs/heads/main` and no tags existed remotely. The five versioned workflows were unchanged.

Actions was enabled with selected Actions only, SHA pinning required, default token read-only and
PR-review approval disabled. Dependency graph/vulnerability alerts and Dependabot security updates
were enabled; security updates reported enabled and unpaused, and auto-merge remained disabled.

## 3. MIG3 blocked runs reverified

The final MIG3 runs were re-read through GitHub's API:

| Workflow | Run | Jobs | Runner evidence | Result |
| --- | --- | --- | --- | --- |
| Build | `32912622596` | 1 | label `ubuntu-latest`; `runner_id: 0`; empty runner name; zero steps | blocked before execution |
| Compatibility | `32912622530` | 11 | every label `ubuntu-latest`; every `runner_id: 0`; empty runner names; zero steps | 0/11 executed |
| Security | none | n/a | active workflow with schedule and `workflow_dispatch` | intentionally NOT_RUN against the known account gate |

The check annotation remained:

> The job was not started because recent account payments have failed or your spending limit needs
> to be increased. Please check the 'Billing & plans' section in your settings

No job was assigned, no workflow step ran and no product, test or security control failed.

## 4. Billing blocker classification

**`BLOCKED_EXTERNAL_BILLING_ONLY`**. GitHub's annotation is account-level and precedes runner
assignment. It is consistent across Build and all 11 Compatibility jobs on both MIG3 commits.

## 5. Any secondary blockers

None found. All five repository workflows are active. Their YAML parses, triggers match `main`,
every runner label is supported, repository Actions is enabled, allowed Actions cover every pinned
use, permissions are read-only and the workflow-security gate passes.

A noncanonical experiment that combined `spotless:check test verify clean verify install` in one
Maven invocation caused Flatten to remove the POM generated earlier in that same invocation. The
five standard invocations were then run separately and all passed. This was an operator command
composition error, not a repository or CI blocker.

## 6. Official public hosted-runner policy

GitHub states that standard GitHub-hosted runners are free and unlimited for public repositories;
private repositories consume the owner's included allowance and then billed usage. Public standard
usage remains subject to GitHub Actions limits, concurrency availability, acceptable-use and Terms
of Service; “unlimited” is not a guarantee of immediate scheduling under abuse or platform limits.

- [GitHub Actions billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions)
- [GitHub-hosted runners reference](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)
- [Billing and usage limits](https://docs.github.com/en/actions/concepts/billing-and-usage)

## 7. Standard hosted runner used

Every job in all five workflows uses exactly `ubuntu-latest`. On public repositories this is a
standard Linux x64 hosted runner, not a custom or larger runner.

## 8. Public hosted-runner billing expectation

After `PRIVATE → PUBLIC`, these standard jobs are expected not to require owner-paid Actions
minutes. This expectation must be verified by actual scheduling during MIG4; if GitHub still
rejects them, MIG4 becomes `BLOCKED` and must not fall back to persistent self-hosted execution.

## 9. Larger runner usage

**No.** No workflow uses a larger-runner label, runner group, static IP or custom image. GitHub
documents that larger runners remain billed even for public repositories.

- [Actions runner pricing](https://docs.github.com/en/billing/reference/actions-runner-pricing)

## 10. Visibility-change workflow trigger behavior

GitHub exposes a distinct `public` Actions event for a private-to-public transition. None of this
repository's workflows declares it, so changing visibility will not automatically rerun blocked
jobs. Current triggers are only `push`, `pull_request`, `schedule` and `workflow_dispatch`.

- [Events that trigger workflows — `public`](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#public)

## 11. Post-public Build trigger/rerun plan

Immediately after public settings are checked, rerun failed jobs for Build run `32912622596`:

```bash
gh run rerun 32912622596 --repo yravelo/postgres-bulk --failed
```

GitHub reruns with the original actor, SHA and ref. The run is within the documented 30-day rerun
window. Do not create a no-op commit. The MIG3B closure commit is documentation-only and uses
`[skip actions]`; `481d22a` therefore remains the canonical product/trust implementation SHA.

## 12. Post-public Compatibility trigger/rerun plan

Rerun all failed Compatibility jobs at the same canonical SHA:

```bash
gh run rerun 32912622530 --repo yravelo/postgres-bulk --failed
```

Require 11/11 PASS with the expected names and hosted runner metadata. If the 30-day window has
expired, stop MIG4 and design a real, reviewable trigger change; do not invent an empty commit.

- [Re-running workflows and jobs](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/re-run-workflows-and-jobs)

## 13. Post-public Security trigger/rerun plan

Security has `workflow_dispatch`; run it on the then-current `main`:

```bash
gh workflow run security.yml --repo yravelo/postgres-bulk --ref main
```

Require full-history checkout, all 16 controls PASS and hosted runner/privacy evidence clean.

## 14. Hosted-only trust boundary audit

PASS. Every workflow job has a constant `runs-on: ubuntu-latest`; no condition can redirect a fork,
Dependabot or unknown actor. Build and Compatibility accept `pull_request` directly, use
`contents: read`, receive no secret and execute without an owner/actor gate. Security, Benchmarks
and Release are scheduled/manual trusted workloads but remain hosted as well.

## 15. Self-hosted references in NEW repo workflows

Zero. No workflow contains `self-hosted`, `postgres-bulk-ci` or `postgres-bulk-ci-01`. The only
such strings under the workflow-security scripts are negative fixtures and forbidden-pattern
messages. No persistent runner is registered to the new repository.

## 16. Fork PR simulation

PASS: the fixture selects `ubuntu-latest`, exposes no secret, grants no write token and cannot reach
a persistent host.

## 17. Dependabot PR simulation

PASS: Dependabot cannot select self-hosted infrastructure. The five real internal Dependabot jobs
also used GitHub Actions standard runners and no repository runner.

## 18. Unknown actor simulation

PASS: actor identity does not alter runner selection. Unknown external actors remain on hosted
infrastructure with read-only/no-secret posture.

## 19. Token/secrets posture

Top-level permissions are `contents: read`; no workflow references `${{ secrets.* }}` or an
environment. Repository secrets, variables and Dependabot secrets are all zero. Every checkout has
`persist-credentials: false`; every Action use is full-SHA pinned.

## 20. Workflow-security fixture result

PASS: five workflows audited and all 25 fixtures passed, including fork, Dependabot, unknown actor,
owner PR, trusted `main`, Security dispatch, self-hosted rejection, `pull_request_target`
rejection, write-permission rejection, secret rejection and mutable-Action rejection.

## 21. Local Build-equivalent result

PASS on Java 17 with Docker/Testcontainers. Separate `spotless:check`, `test`, `verify`,
`clean verify` and `install` invocations passed, followed by seven clean SAST reports, canonical
SBOM/license generation, both standalone examples, the isolated JDBC consumer, documentation/link
audit, strict Javadocs and public API baseline.

## 22. Local Compatibility-equivalent result

**11/11 PASS** using the exact workflow commands and boundaries:

- multi-schema JPA/JDBC/both-starters composition;
- Java 21 and Java 25 full reactors;
- Spring Boot/Data JDBC 3.5.0 minimum and dependency audit;
- PostgreSQL 16.14 and 17.10 full reactors;
- newest Java 21/Hibernate 6.6.55/pgJDBC 42.7.13/PostgreSQL 18.4 plus JDBC isolation;
- Hibernate 6.6.15 and 6.6.55 adapter lanes;
- pgJDBC 42.7.5 and 42.7.13 adapter lanes.

The Java 17 baseline/PostgreSQL 15.18 behavior is covered by the multi-schema composition and the
Build-equivalent reactor.

## 23. Local Security result

PASS: all 16 continuous-security gates. Gitleaks, workflow security, OSV (138/138 exact versions,
five accepted WARN, zero BLOCK), SAST (seven reports, zero untriaged), CycloneDX/license (nine
artifact SBOMs plus aggregate, 55 production components, zero unknown/BLOCK), documentation/API,
OpenPGP public-key and runner health all passed.

## 24. Remote hosted validation state

**BLOCKED WHILE PRIVATE.** Build has not passed remotely, Compatibility remains 0/11 remotely and
Security remains NOT_RUN. Local evidence is not a substitute.

## 25. New repository public-exposure audit

PASS. An independent single-branch clone from `origin` contained exactly `main` at `481d22a` and
117 commits. Gitleaks scanned all 117 with no leak. Only the approved noreply author and Dependabot
noreply author exist. Full-history and current-tree searches found zero personal email, personal
path or private-key marker.

All nine new Actions records were audited. The four billing-blocked workflow records contain no
step logs or runner identity. The five GitHub-internal Dependabot logs contain zero personal path,
personal email, archive identifier, old runner label or private-key marker.

## 26. Old metadata inherited

**No.** PRs and `refs/pull/*` are zero; the new repository has only nine new records (five internal
Dependabot and four MIG3 billing-blocked workflow runs). It contains no old run, job, artifact,
runner, issue or PR metadata.

## 27. MIG3 blocked NEW runs publicability

`KEEP_PUBLIC`. They are neutral evidence owned by the new repository: standard job names,
`ubuntu-latest`, empty runner identities, zero steps and an account-billing annotation. They expose
no host, credential, personal path, personal email or archive identity.

## 28. Documentation publicability closure

PASS after replacing two historical SSH transport URLs for the private archive with descriptive
fetch-only labels. Public documents may name the archive and neutral historical runner to explain
isolation, but no link or instruction requires archive access. Old Actions and PR links remain
removed; public understanding rests on versioned textual/local evidence.

## 29. Migration docs classification

| Documents | Classification |
| --- | --- |
| REL1-MIG0, MIG1, MIG2, MIG3 and MIG3B | `KEEP_PUBLIC` after the two transport sanitizations |
| REL1-A and R1 remediation reports | `KEEP_PUBLIC` as sanitized historical/security evidence |
| `REMOVE_BEFORE_PUBLIC` | none |

The old archive itself remains private; document publicability does not grant archive access.

## 30. README/public surface result

PASS. README, `SECURITY.md`, `CONTRIBUTING.md`, `LICENSE`, `CHANGELOG.md`, release notes, user guides,
JPA/JDBC examples, compatibility, multi-schema, transactions and limitations are present and
self-contained. Issues is the intended public collaboration surface.

## 31. Public settings already configured

- Issues enabled; Wiki, Discussions and Projects disabled;
- Actions enabled, selected Actions only, SHA pinning required;
- default token read-only and no PR-review approval permission;
- repository/fork/Dependabot secrets and variables zero;
- dependency graph, vulnerability alerts and security updates enabled;
- Dependabot five-lane weekly policy, no auto-merge;
- no repository runner and no artifact.

## 32. Settings to enable at/after PUBLIC

Immediately after visibility change and before reruns: verify identity/visibility, preserve the
current Actions allow-list/token policy, set fork workflow approval to **all external
contributors**, recheck secrets/runners, and verify Issues on plus Wiki/Discussions/Projects off.
Then enable/test PVR, Secret Protection/push protection, CodeQL default setup and Dependency Review.
Create the `main` ruleset only after successful check contexts are visible. OpenSSF Scorecard is an
optional later public hardening item, not an activation gate.

## 33. PVR plan

Enable GitHub Private Vulnerability Reporting immediately after public activation, verify the API
state, then perform a separately controlled benign independent submission/maintainer reply test.
The existing verified external mailbox remains the fail-safe channel until that round trip passes.

- [Configure private vulnerability reporting](https://docs.github.com/en/code-security/how-tos/report-and-fix-vulnerabilities/configure-vulnerability-reporting/configure-for-a-repository)

## 34. CodeQL plan

Enable CodeQL default setup for Java on standard GitHub-hosted infrastructure after visibility is
public, monitor the initial scan and triage every alert. Do not assign the archive runner. Existing
SpotBugs/FindSecBugs remains mandatory and complementary.

- [Configure CodeQL default setup](https://docs.github.com/en/code-security/how-tos/find-and-fix-code-vulnerabilities/configure-code-scanning/configure-code-scanning)

## 35. Dependency Review plan

After public availability, add a dedicated `pull_request` workflow using
`actions/dependency-review-action` at a reviewed full SHA, `ubuntu-latest`, `contents: read`, no
secret and `persist-credentials: false`; update the workflow gate/classification and fixtures before
requiring its check.

- [Dependency Review](https://docs.github.com/en/code-security/concepts/supply-chain-security/dependency-review)

## 36. Ruleset/branch protection plan

After Build and all Compatibility contexts have passed publicly, create a default-branch ruleset
first in disabled/evaluate form, verify it does not lock out the sole maintainer, then activate it:

- block force pushes and deletion;
- require pull requests with zero mandatory approvals initially for the single-maintainer model;
- require conversation resolution;
- require Build's baseline job plus all 11 uniquely named Compatibility jobs;
- retain an explicit repository-admin emergency bypass;
- do not require scheduled Security as a per-PR context.

Rulesets are available for public repositories on GitHub Free; the current private API correctly
returns HTTP 403. Required contexts must be captured from successful public runs before activation.

- [About rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets)

## 37. MIG4 transaction sequence

1. **P0:** rerun the final private state/content/settings/preflight audit; require clean synchronized
   `main`, zero secrets/runners and explicit authorization.
2. **P1:** change only `yravelo/postgres-bulk` from PRIVATE to PUBLIC.
3. **P2:** verify repository ID `1346700826`, owner/name, URL, default branch and public visibility.
4. **P3:** apply/verify all-external fork approval and immediately rerun Build `32912622596`.
5. **P4:** immediately rerun Compatibility `32912622530`; require 11/11.
6. **P5:** manually dispatch Security on current `main`.
7. **P6:** monitor all jobs, runner assignments, logs and artifacts to terminal results.
8. **P7:** clone anonymously into a clean temporary directory.
9. **P8:** use unauthenticated API/Git ref queries and repeat privacy/history checks.
10. **P9:** verify public PR policy and hosted-only selection through API/workflow/fixtures; do not
    create a malicious PR.
11. **P10:** reconcile settings/security features and issue final GO/NO-GO. Do not start REL1-B.

## 38. Stop conditions

Stop before P1 for any dirty/diverged Git state, identity mismatch, privacy finding, secret/runner
appearance, workflow-gate failure or missing authorization. After P1, stop at the first unexpected
visibility/identity, self-hosted selection, write/secret exposure, billing rejection, Build failure,
Compatibility result below 11/11, Security failure, privacy finding or anonymous-audit mismatch.
No later step may hide an earlier failure.

## 39. Post-public failure containment

Public visibility is immediately cloneable and cannot be undone historically. Making the repository
private again does not revoke prior clones. For a privacy, credential or trust-boundary finding:
disable Actions, make the repository private again if the MIG4 authorization covers containment,
preserve evidence, rotate any affected credential outside Git and fix forward. For a product-only
Build/Compatibility failure without exposure, stop the GO decision and fix forward; do not publish
or tag. If standard public hosted jobs remain billing-blocked, declare MIG4 `BLOCKED`, investigate
GitHub/account policy and never route untrusted work to self-hosted infrastructure.

- [Consequences of repository visibility changes](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/managing-repository-settings/setting-repository-visibility)

## 40. Anonymous verification plan

Without authentication verify the repository page, clone, README/docs, 118-commit closure history,
only `main`, no tag/PR refs, workflow files, Actions run/job metadata, security contact and license.
Scan author emails and every reachable blob/history for personal path/email, credentials and keys.
Confirm no old PR/run/runner metadata. Re-run trust fixtures and inspect settings/API; an actual
external PR requires a separate benign test design and is not needed for initial proof.

## 41. Old archive rollback/reference state

The old repository remains PRIVATE, unarchived and unchanged at its previous pushed timestamp. Its
single neutral repository runner remains online/idle and attached only there. The local `archive`
remote remains fetch-only with push `DISABLED`. It is a source/reference rollback asset, not a
privacy rollback after public exposure. Deletion belongs to MIG5 and requires
`AUTHORIZE_OLD_PRIVATE_REPOSITORY_DELETION`.

## 42. Release/publication boundary

Even after a successful MIG4: no `v0.1.0` tag, GitHub Release, Maven Central upload/publication,
Central/GPG credentials or REL1-B. EP-02 and EP-03 remain separate REL2 prerequisites.

## 43. Final local validation

PASS: `git diff --check`, documentation/link audit, independent 117-commit Gitleaks scan, Security
full, technical release preflight, REL1 preflight, public API, strict Javadocs, Build-equivalent and
Compatibility-equivalent 11/11. Testcontainers cleanup found zero labeled container, network or
volume.

## 44. Documentation changes

Created this report; added MIG3B superseding addenda to the MIG3 report, REL1-A audit and release
readiness; updated the release roadmap and documentation index; sanitized two private-archive SSH
transport examples. No product or workflow file changed.

## 45. Git commits

MIG3 implementation remains `c99627d`; MIG3 closure remains `481d22a`. MIG3B is recorded by one
purely documentary closure commit using `[skip actions]` so the known private billing rejection is
not duplicated. Identity is `Yusnier Blanco Ravelo <29708813+yravelo@users.noreply.github.com>`.

## 46. Final Git state

The post-commit handoff must be clean, `HEAD == origin/main`, contain only remote `main`, no tag/PR
ref, and retain `archive` as fetch-only/push-disabled. The final commit count is 118. These
conditions are rechecked after push.

## 47. Remote actions performed

```text
repository visibility changed: no
new repository made public: no
runner registered to NEW: no
old archive visibility changed: no
old archive deleted: no
repository secrets created: no
GitHub PVR enabled: no
tag created: no
Benchmarks executed: no
Release executed: no
Central upload: no
publication: no
REL1-B started: no
```

All remote activity in MIG3B was read-only.

## 48. MIG3B Definition of Done assessment

PASS. Billing was reverified as the sole blocker; current official policy supports standard public
hosted use; trust fixtures, Build-equivalent, Compatibility-equivalent 11/11, Security full,
content/privacy and publicability all pass; settings, transaction, containment and anonymous
verification plans are exact; archive isolation and all prohibitions remain intact.

## 49. MIG4 entry verdict

**READY FOR CONTROLLED MIG4 PUBLIC ACTIVATION.** MIG3 remains technically incomplete until public
Build PASS, Compatibility 11/11 PASS and Security PASS. MIG4 may not declare success before those
results and the anonymous/privacy audit pass.

## 50. Exact next authorization

```text
AUTHORIZE_NEW_REPOSITORY_PUBLIC_ACTIVATION
```

Do not change visibility without that exact new authorization.

## Boundary statement

```text
REL1-MIG3B status: DONE
new repository private: yes
billing blocker: EXTERNAL_ONLY
public hosted CI architecture: READY
local Build-equivalent: PASS
local Compatibility-equivalent: PASS
local Security: PASS
remote Build while private: BLOCKED
remote Compatibility while private: 0/11
remote Security while private: NOT_RUN
old archive private/intact: yes
MIG4 entry: READY
repository public: no
tag created: no
Central upload: no
publication activated: no
```
