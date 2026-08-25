# REL1-MIG3 — CI, security and public-trust baseline recreation

Date: 2026-08-26  
Repository: `yravelo/postgres-bulk`  
Implementation commit: `c99627dc95b2e57af051d9e461d53f2a8e9cca9e`  
Result: **BLOCKED**

MIG3 implemented the public-safe CI and security baseline in the new canonical repository while it
remains private. All local product and security validation passed. The remote Definition of Done is
not complete because GitHub rejected every GitHub-hosted job before its first step with an account
billing/spending-limit annotation. This is an external execution gate, not a product, test or
workflow-selection failure. MIG4 is therefore not ready.

## 1. Repository baseline and isolation

The initial new repository state was:

| Item | Initial value |
| --- | --- |
| Repository ID | `1346700826` (`R_kgDOUEUGGg`) |
| Visibility/default branch | PRIVATE / `main` |
| Initial HEAD | `448fa84efd461cc29c69ade6027ced06c988e6b8` |
| Clean-history commits | 115 |
| Tags / PRs / `refs/pull/*` | 0 / 0 / 0 |
| Artifacts / runners | 0 / 0 |
| Repository secrets / variables / Dependabot secrets | 0 / 0 / 0 |
| Inherited GitHub metadata | none |

The old rollback repository remained `yravelo/postgres-bulk-private-archive`, repository ID
`1339652660` (`R_kgDOT9l6NA`), PRIVATE, default branch `main`, with its last push unchanged at
`2026-08-25T22:33:31Z`. Its repository-scoped runner `postgres-bulk-ci-01` (ID `22`) remained
online, idle and attached only to that archive. No archive push, settings change, visibility change
or deletion occurred. Local remote `archive` remains fetch-only and its push URL remains
`DISABLED`.

The five initial `dynamic` records are GitHub-internal Dependabot update jobs created from the
migrated configuration, not inherited workflow history: `32909635629`, `32909634448`,
`32909634177` and `32909634099` succeeded; `32909634108` was cancelled. They created no PR and
could not select the archive runner.

## 2. Workflow inventory and classification

All workflows have top-level `contents: read`, SHA-pinned Actions and
`persist-credentials: false`. None consumes a secret, uses an environment, uses
`pull_request_target`, calls a reusable/composite workflow or declares a service container.

| Workflow | Trigger and trust | Runner | Artifacts/cache | Concurrency and inputs | Classification |
| --- | --- | --- | --- | --- | --- |
| Build | `push` to `main`; every `pull_request`, including fork and Dependabot code | `ubuntu-latest` | Maven cache; no artifact | per workflow/ref, cancel previous; no inputs | `PUBLIC_PR_SAFE` |
| Compatibility | `push` to `main`; every `pull_request`; full 11-lane coverage | `ubuntu-latest` | Maven cache; no artifact | per workflow/ref, cancel previous; no inputs | `PUBLIC_PR_SAFE` |
| Security | Monday 04:17 UTC and manual dispatch; trusted repository revision | `ubuntu-latest` | Maven cache; no artifact; full-history checkout | serialized; no inputs | `TRUSTED_ONLY` |
| Benchmarks | manual profile choice only | `ubuntu-latest` | Maven cache; raw JMH artifact, 14 days | serialized; validated profile input | `TRUSTED_ONLY` |
| Release candidate | owner-only manual dispatch from default branch; immutable SHA input | `ubuntu-latest` | Maven cache; inspected candidate, 7 days | serialized; version/SHA/confirmation inputs | `MANUAL_PRIVILEGED` |

The Release workflow remains candidate-only: it has no signing or publication credentials and no
tag, Release, Central upload or publication step. Benchmarks and Release were not executed.

Shell interpolation was audited. Untrusted event fields are not interpolated into shell scripts;
matrix and dispatch inputs cross explicit environment variables and dispatch values are validated.
The Maven cache contains dependencies only and does not introduce a credential boundary.

## 3. Hosted-runner feasibility and trust architecture

GitHub documents that standard GitHub-hosted runners are free and unlimited for public
repositories, while private repositories consume the account allowance. `ubuntu-latest` currently
maps to an official Ubuntu image with Docker client/server support. The local Testcontainers suite
validated the same Docker-based execution model with PostgreSQL 15–18.

References:

- [GitHub Actions billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions)
- [Workflow runner syntax](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax)
- [Ubuntu hosted-runner image inventory](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md)

The selected architecture is option A: **no self-hosted runner in the new repository**. Every job
has the structurally fixed selector `runs-on: ubuntu-latest`; no actor, owner, fork or repository
condition can redirect a job to persistent infrastructure. Therefore fork PRs, Dependabot PRs,
external actors, same-repository owner PRs, controlled `main` and manual workflows all remain on
ephemeral GitHub-hosted infrastructure. Untrusted PR jobs receive no secret and only a read-only
token. This retains the complete Build and 11-lane Compatibility coverage on every PR instead of
introducing a reduced fast matrix.

The trusted path is also hosted because no present workload justifies adding persistent attack
surface. The old runner remains an archive-only rollback asset until separately decommissioned.
Runner registration to the new repository was **not** performed and is not required by this
architecture. If the owner chooses the self-hosted alternative, the exact separate authorization is
`AUTHORIZE_RUNNER_REGISTRATION_TO_NEW_REPO`.

## 4. Workflow regression gates

`scripts/check-workflow-security.py` now fails unless every workflow job is exactly
`ubuntu-latest`, every workflow is secret-free, and Build/Compatibility execute every PR without
an actor/repository guard. Existing checks continue to enforce read-only permissions, pinned
Actions, safe triggers, release constraints and `persist-credentials: false`.

The 25-fixture regression suite explicitly proves:

- fork PR, Dependabot, external actor and owner same-repository PR select hosted infrastructure;
- controlled `main` and Security dispatch select hosted infrastructure;
- a self-hosted selector or actor guard fails;
- `pull_request_target`, write permissions and secrets fail;
- Build/Compatibility coverage and Release/Benchmark trust boundaries cannot silently regress.

Both the gate and all 25 fixtures pass locally. This is runner-selection evidence; it is not
misrepresented as remote product execution.

## 5. GitHub Actions and dependency settings

| Control | Before MIG3 | After MIG3 |
| --- | --- | --- |
| Actions | enabled; all Actions allowed | enabled; selected Actions only |
| SHA pin enforcement | disabled | enabled |
| Allowed Actions | unrestricted | GitHub-owned plus `actions/checkout@*`, `actions/setup-java@*`, `actions/upload-artifact@*`; verified marketplace Actions disabled |
| Default token | read-only | read-only |
| Approve PR reviews | disabled | disabled |
| Repository secrets / variables | 0 / 0 | 0 / 0 |
| Dependabot secrets | 0 | 0 |

The public-fork approval endpoint is unavailable while the repository is private (HTTP 422).
MIG4 must select approval for all external contributors before or with public activation. GitHub
warns that self-hosted runners should be used cautiously with fork workflows; the hosted-only
design removes that exposure independently of approval policy.

References:

- [Managing Actions and fork policies](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/enabling-features-for-your-repository/managing-github-actions-settings-for-a-repository)
- [Approving fork workflow runs](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/approve-runs-from-forks)
- [Actions permissions API](https://docs.github.com/en/rest/actions/permissions)

The dependency graph and vulnerability alerts are enabled. Dependabot security updates report
`enabled: true, paused: false`; five weekly Maven/Actions lanes remain configured and auto-merge
remains disabled. No Dependabot PR was created or merged during MIG3.

## 6. Security features and MIG4 plan

| Control | PRIVATE NOW | ENABLE WHEN PUBLIC (MIG4) |
| --- | --- | --- |
| Gitleaks full history | active local/Build/Security gate | retain |
| OSV exact dependency inventory | active, fail closed | retain |
| SpotBugs + FindSecBugs | active in `verify` | retain |
| CycloneDX + license integrity | active, per-artifact and aggregate | retain |
| Workflow-security fixtures | active | retain |
| Scheduled/manual Security | defined on hosted runner | rerun after billing gate; retain schedule |
| Dependency graph/alerts/security updates | enabled | retain and monitor |
| CodeQL | unavailable/not enabled on current private plan | enable default setup, then validate findings |
| Dependency Review | deferred | add SHA-pinned PR workflow with read-only permission |
| Secret scanning and push protection | unavailable (HTTP 404) | enable and test after availability changes |
| Private vulnerability reporting | unavailable while private (HTTP 404) | enable and perform an independent benign test |
| OpenSSF Scorecard | deferred | evaluate/enable only after public activation |
| Rulesets/branch protection | unavailable on current private plan (HTTP 403) | create public `main` ruleset after required check contexts exist |

CodeQL and Dependency Review are additive public controls; their deferral does not weaken the
current seven-module SAST or dependency gates. PVR retains the already verified external private
reporting mailbox until its public GitHub channel is enabled and independently tested.

The MIG4 ruleset plan is: prohibit force-push and deletion; require a PR for `main` without locking
out the single maintainer; require successful Build and all 11 Compatibility contexts after those
contexts exist on the public repository; retain an explicit emergency/admin bypass; do not require
the scheduled Security workflow as a per-PR check. Rules and required-check names must be verified
before enforcement.

## 7. Documentation and public surface

Twenty-seven live links to old Actions runs and twelve live old PR/comment links were removed.
Where useful, local documents retain the historical run/PR number and textual result without a
live dependency on the private archive. No remaining link matching the old repository's Actions or
PR URL form exists. Archive references that explain migration provenance remain intentionally
textual; no external reader needs archive access to understand the project or its evidence.

Migration-document classification:

| Documents | Classification | Rationale |
| --- | --- | --- |
| REL1-MIG0, MIG1, MIG2 and this MIG3 report | `KEEP_PUBLIC` | sanitized decision/evidence trail for the clean repository |
| REL1-A exposure audit | `KEEP_PUBLIC` | threat and acceptance evidence; superseding MIG3 addendum is explicit |
| R1 public-history, Actions-log, dry-run, runner, GitHub-managed exposure and support reports | `KEEP_PUBLIC` after MIG3 sanitation | no secrets or personal host paths; live private-archive dependencies removed |
| Documents requiring `REMOVE_BEFORE_PUBLIC` | none | no public-harmful content found |
| `ARCHIVE_ONLY` documents | none | operational secrets remain outside Git |

The README/public surface audit passes: purpose, snapshot Maven coordinates, Java/Spring/PostgreSQL
compatibility, JPA and JDBC examples, multi-schema operation, transaction semantics, limitations,
security reporting and Apache-2.0 license are present. `SECURITY.md`, `CONTRIBUTING.md`, `LICENSE`,
`CHANGELOG.md`, release notes and release-readiness documentation are present. The repository was
not made public.

## 8. Validation evidence

### Remote execution on the new repository

Push `c99627dc95b2e57af051d9e461d53f2a8e9cca9e` intentionally selected hosted runners:

| Workflow | Run | Observed result |
| --- | --- | --- |
| Build | `32911307673` | FAIL before first step; job label `ubuntu-latest`, `runner_id: 0`, empty runner name, zero steps |
| Compatibility | `32911307744` | 0/11; all 11 jobs label `ubuntu-latest`, `runner_id: 0`, empty runner name, zero steps |
| Security | not dispatched | NOT_RUN because the already-proven account-level gate would reject the same hosted selector |

Every rejected job has the GitHub annotation:

> The job was not started because recent account payments have failed or your spending limit needs
> to be increased. Please check the 'Billing & plans' section in your settings

No repository code or workflow step ran. Consequently, these runs prove correct hosted selection
but cannot satisfy the required remote Build, Compatibility or Security product gates.

The new-CI privacy audit passes for the evidence that exists: personal runner name 0, personal
hostname 0, personal path 0 and old archive identity 0. The jobs have no runner name and produced no
step logs. Repository artifacts, runners, secrets, variables and Dependabot secrets remain zero.

### Local product and security validation

The complete local validation passed:

- Spotless check, `test`, `verify`, `clean verify` and `install` across the reactor;
- Testcontainers/PostgreSQL integration suites, with zero residual labeled containers, networks or
  volumes after completion;
- continuous security validation: 16/16 gates PASS;
- Gitleaks full-history scan: 210 locally reachable commits/refs, zero finding;
- OSV: 138/138 exact versions, five accepted WARN records, zero BLOCK;
- SAST: seven production-module reports, zero untriaged finding;
- CycloneDX/license: nine artifact SBOMs plus aggregate, 55 external production components, zero
  unknown and zero BLOCK;
- workflow-security and all 25 negative/selection fixtures;
- documentation/link audit: 247 relative targets after this report was added;
- strict Javadocs and public API baseline;
- technical release security preflight and REL1 preflight.

No benchmark was needed or executed. Docker/Testcontainers feasibility is PASS and cleanup is PASS.

## 9. Public activation readiness

| Gate | State |
| --- | --- |
| Clean canonical Git history | PASS |
| Public documentation/self-containment | PASS |
| Security reporting | PASS via verified external channel; PVR planned for MIG4 |
| Untrusted PR CI architecture | READY — hosted-only, read-only, secret-free |
| Trusted CI architecture | READY in design; remote execution BLOCKED by account billing |
| Dependabot | READY — configured, alerts/updates enabled, no auto-merge |
| Private security baseline | PASS locally |
| Public-only security features | explicit MIG4 activation plan |
| Ruleset | explicit MIG4 plan; unavailable while private |
| Secrets and publication credentials | PASS — zero |
| Persistent runner isolation | PASS — archive-only, never selected in new repository |
| Anonymous clone verification | planned for MIG4 after visibility changes |
| Remote Build / Compatibility / Security | BLOCKED / 0 of 11 / NOT_RUN |

Verdict: **NOT READY FOR MIG4**. The repository is technically arranged to protect untrusted PRs,
but MIG3 cannot be `DONE` without successful remote execution on the new canonical repository.

## 10. Changed files and commits

Implementation commit `c99627d` (`ci(security): enforce hosted-only public trust boundary`) changed:

- `.github/workflows/build.yml`, `.github/workflows/compatibility.yml` and
  `.github/workflows/security.yml`;
- `scripts/check-workflow-security.py` and `scripts/test-workflow-security.py`;
- `config/security/continuous-security-policy.json` and
  `config/security/security-baseline-policy.json`;
- `CONTRIBUTING.md` and `docs/architecture/compatibility-evidence.md`;
- `docs/security/continuous-security-validation.md`, `dependabot-review-2026-08.md`,
  `secrets-and-actions-hardening.md`, `security-baseline-closure.md` and
  `self-hosted-runner.md`;
- `docs/releases/rel0-final-release-readiness.md`, `rel1a-open-source-exposure-audit.md`,
  `rel1ar-public-history-remediation.md` and `rel1ar-runner-identity-remediation.md`.

This report, the documentation index, release roadmap, REL1 audit and release-readiness addendum are
recorded by the MIG3 closure documentation commit. All commits use
`29708813+yravelo@users.noreply.github.com`.

The final handoff must have a clean `main`, `HEAD == origin/main`, only `refs/heads/main` in the new
remote, no tag or PR ref, and both remotes unchanged. These conditions are rechecked after the
closure commit is pushed.

## 11. Remote actions and Definition of Done

```text
new repository visibility changed to PUBLIC: no
old archive visibility changed: no
old archive deleted: no
runner registered to NEW: no
public/untrusted CI architecture implemented: yes
repository secrets created: no
Central/GPG credentials created: no
GitHub PVR enabled: no
tag created: no
Benchmarks executed: no
Release executed: no
Central upload: no
publication: no
REL1-B started: no
```

MIG3 satisfies the repository, architecture, runner isolation, settings, dependency/security,
documentation, fixture, local validation, privacy and archive-isolation requirements. It does not
satisfy remote Build PASS, Compatibility 11/11 PASS or Security PASS. The Definition of Done is
therefore **BLOCKED**.

Remaining blocker: the owner account must resolve the GitHub Actions payment/spending-limit gate.
Exact next action: open GitHub **Billing & plans**, correct the failed payment or increase/enable the
Actions spending limit, then rerun Build and Compatibility on the final `main` commit and manually
dispatch Security. Only after Build PASS, Compatibility 11/11 PASS, Security PASS and a clean log
audit may MIG3 be closed and `REL1-MIG4 — Public Repository Activation & Anonymous External
Verification` be proposed. MIG4 must not execute automatically.

## Boundary statement

```text
REL1-MIG3 status: BLOCKED
new repository private: yes
clean history: PASS
public-safe untrusted PR CI: READY
persistent self-hosted exposed to untrusted PR: no
Build: FAIL
Compatibility: 0/11
Security: NOT_RUN
repository secrets: 0
old archive private/intact: yes
MIG4 entry: NOT_READY
repository public: no
old archive deleted: no
tag created: no
Central upload: no
publication activated: no
```
