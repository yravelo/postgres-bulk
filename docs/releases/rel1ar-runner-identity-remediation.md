# REL1-A-R R4C runner identity remediation

Audit date: 2026-08-25
Repository: `yravelo/postgres-bulk` (`PRIVATE`)
Decision: **R4C DONE; OPEN-SOURCE ACTIVATION NO-GO**

The former runner and host identities are represented only as `<OLD_RUNNER_NAME_REDACTED>` and
`<OLD_HOSTNAME_REDACTED>`. Registration tokens and private rollback contents are not recorded.

## 1. R4C result

`DONE`. The operating-system hostname and repository-level Actions runner were neutralized without
changing the service account, directories, labels, trust boundary or publication state.

## 2. Authorizations consumed

Exactly `AUTHORIZE_OS_HOSTNAME_CHANGE` and `AUTHORIZE_RUNNER_REREGISTRATION` were consumed.

## 3. Initial Git/repository state

`HEAD == origin/main == 6cceb76f054cdb4a0348f9e54f019f50a42d6f99`, worktree clean,
repository `PRIVATE`, one repository-level runner `online` and `busy=false`.

## 4. Initial hostname classification

`PERSONAL`; the exact value matched the R3 private evidence and is not reproduced.

## 5. Target hostname

`postgres-bulk-ci-01`.

## 6. Backup/rollback preparation

A mode-`0700` root-only backup preserved the two host configuration files and sanitized runner
service/registration metadata during the operation. Tokens were excluded. Automatic hostname
rollback was armed for immediate validation failure; official re-registration of the former runner
identity was the runner rollback path.

## 7. Hostname change mechanism/result

`PASS`. `hostnamectl set-hostname postgres-bulk-ci-01` changed static and active identity. The old
hostname has zero occurrences in active hostname configuration.

## 8. `/etc/hosts` change

`PASS`. Only the relevant old-host token was replaced with `postgres-bulk-ci-01`; localhost, IPv6
and unrelated entries were preserved. The old value has zero remaining active mappings.

## 9. Host/local resolution validation

`PASS`: `hostname`, `hostnamectl`, `/etc/hostname` and local resolution agree on
`postgres-bulk-ci-01`.

## 10. Network validation

`PASS`: GitHub API and Maven Central returned successfully after the hostname change.

## 11. Docker validation before runner mutation

`PASS`: socket access, daemon access under the dedicated account and a disposable PostgreSQL image
container succeeded.

## 12. Runner initial state

One repository-level runner was `online`, idle and registered under
`<OLD_RUNNER_NAME_REDACTED>`, with the exact dedicated labels.

## 13. Runner removal mechanism/result

`PASS`. The official `svc.sh stop`/`svc.sh uninstall` and `config.sh remove` sequence removed the
old repository registration. `.runner` was never edited manually.

## 14. Registration token handling

Repository remove and registration tokens were requested only immediately before use, passed via
an in-memory pipe to the administrative process, never printed, committed or persisted, and left
to expire naturally.

## 15. Runner re-registration result

`PASS`. The official unattended configurator created one repository-level runner for
`yravelo/postgres-bulk`; GitHub assigned a new runner ID and reported it online/idle.

## 16. Final runner display name

`postgres-bulk-ci-01`.

## 17. Labels

GitHub reports `self-hosted`, `Linux`, `X64`, `postgres-bulk-ci`. Workflow selectors remain
`[self-hosted, linux, x64, postgres-bulk-ci]`; zero new-job label mismatches were found.

## 18. Service/autostart

`PASS`: the official neutral-name systemd service is active and enabled, with no custom restart
override.

## 19. Dedicated account/security posture

`PASS`: the dedicated non-root service account, Docker-group membership, install ownership,
workspace ownership and repository-only scope are unchanged.

## 20. Credential audit

`PASS`: Repository Actions Secrets `0`; SSH private keys `0`; OpenPGP private-key files `0`; GitHub
CLI credentials absent; Git credential file/helper absent; service secret-like environment entries
`0`. Runner-internal credentials remain the expected official mode-scoped files.

## 21. Workspace/Maven settings audit

`PASS`: the dedicated workspace is unchanged, all 11 checkouts use
`persist-credentials: false`, persistent Maven settings are absent and runner-temp settings after
CI are `0`.

## 22. Minimal validation run

Build run [`32890808627`](https://github.com/yravelo/postgres-bulk/actions/runs/32890808627),
attempt 2, completed successfully in 5m08s before Compatibility or Security was allowed to run.

## 23. Old runner identity in new logs

`0` exact occurrences across the minimal run and the final three-workflow audit.

## 24. Old hostname in new logs

`0` exact occurrences.

## 25. Old personal path in new logs

`0`. The generic dedicated runner path remains visible as normal checkout/bootstrap context and is
not an owner identity or personal home path.

## 26. New job metadata `runner_name`

All `13/13` new jobs report `postgres-bulk-ci-01`; zero unexpected names were found.

## 27. Historical job metadata inventory

The exact `filter=all` re-audit covered 58 historical runs and 311 total job records: 270 old jobs
across all 58 runs retain `<OLD_RUNNER_NAME_REDACTED>`, 13 new jobs use the neutral name and 28 jobs
have no runner name. Historical job metadata contains no old hostname.

## 28. Historical job metadata verdict

`OWNER DECISION REQUIRED — BLOCKER`. The metadata remains queryable and the official job response
contains `runner_name`. Public resources can be queried without authentication; log/artifact
retention does not claim to remove run/job metadata. GitHub exposes no supported job-metadata-only
deletion: the documented destructive mechanism is whole-run deletion, which R4C did not authorize.

## 29. Additional R4 log deletions

`0`. All R4C-generated logs were neutral, so no remote log deletion was necessary. Temporary local
audit copies were removed after sanitized counts were recorded.

## 30. Workflow/script output audit

`PASS`: project workflows/scripts contain zero explicit output commands for hostname, runner name,
`whoami`, workspace variables or `pwd`. GitHub-generated bootstrap identity is now neutral.

## 31. Trust guard regression

`PASS`: the deterministic audit and 24 fixtures retain trusted push or owner same-repository PR as
the only self-hosted paths; fork, Dependabot and untrusted actors remain denied.

## 32. Dependabot regression

`PASS`: no auto-merge and no automatic untrusted code execution on the persistent runner.

## 33. Remote Build

`PASS`, run `32890808627`, attempt 2, one job.

## 34. Remote Compatibility

`11/11 PASS`, run
[`32890808601`](https://github.com/yravelo/postgres-bulk/actions/runs/32890808601), attempt 2.

## 35. Remote Security

`PASS`, run [`32890829062`](https://github.com/yravelo/postgres-bulk/actions/runs/32890829062),
attempt 2.

## 36. New CI privacy audit

Across 13 log files/jobs: old runner name `0`, old hostname `0`, personal owner path `0`, neutral
bootstrap runner/machine identity present, 13 neutral job metadata records and zero label mismatch.

## 37. Docker/Testcontainers cleanup

`PASS`: labelled containers `0`, networks `0`, volumes `0`; no global prune was used.

## 38. Maven settings cleanup

`PASS`: persistent publishing settings absent and temporary settings `0` after CI.

## 39. Runner version/support

`2.336.0`, the current supported upstream release at audit time; no update was performed.

## 40. Documentation changes

This report was created. Runner operations, Actions hardening, public-history remediation and the
REL1-A exposure audit were reconciled. No production code or workflow changed.

## 41. Git commits

One documentation-only commit using the approved noreply author/committer records R4C. The exact
SHA is reported in the task handoff because a commit cannot contain its own SHA.

## 42. Final Git state

Required closure: clean synchronized `main`, repository `PRIVATE`; exact SHA is reported after the
documentation-only synchronization.

## 43. Actions historical log blocker

`REMEDIATED`.

## 44. Runner identity blocker

`REMEDIATED` for active hostname, runner display identity, new logs and new job metadata.

## 45. Historical job metadata blocker

`PENDING — OWNER DECISION REQUIRED` for 270 jobs across 58 runs.

## 46. PR synthetic refs blocker

`PENDING`; six GitHub-managed synthetic PR refs were not touched.

## 47. Remote actions performed

```text
repository visibility changed: no
OS hostname changed: yes
runner removed/re-registered: yes
Actions logs additionally deleted: no
workflow runs deleted: no
Actions artifacts deleted: no
public CI activated: no
GitHub PVR enabled: no
tag created: no
Benchmarks executed: no
Release executed: no
Central upload: no
publication: no
REL1-B started: no
```

## 48. Definition of Done assessment

`DONE`. Both authorizations were consumed within scope; host, registration, service, security,
minimal privacy test, full CI, cleanup and documentation gates passed. Historical job metadata is
explicitly separated rather than misrepresented as runner-neutralization failure.

## 49. Remaining blockers

- Owner decision on historical job metadata; whole-run deletion would require separate explicit
  authorization.
- Read-only remediation/support plan for the six GitHub-managed synthetic PR refs.

## 50. Open-source activation verdict

`OPEN-SOURCE ACTIVATION NO-GO` while either historical blocker remains.

## 51. Exact next action

Prepare a read-only remediation plan for historical job metadata and GitHub synthetic
`refs/pull/*`. Do not delete runs or contact GitHub Support automatically.

## Boundary statement

```text
REL1-A-R R4C status: DONE
OS hostname identity: NEUTRAL
runner display identity: NEUTRAL
Actions historical logs: REMEDIATED
runner identity blocker: REMEDIATED
historical job metadata: PENDING
PR synthetic refs clean: pending
Build: PASS
Compatibility: 11/11
Security: PASS
repository public: no
REL1-B started: no
tag created: no
Central upload: no
publication activated: no
```
