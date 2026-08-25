# REL1-A-R R3 Actions log privacy remediation

Audit date: 2026-08-25
Repository: `yravelo/postgres-bulk` (`PRIVATE`)
Decision: **R3 DONE; OPEN-SOURCE ACTIVATION NO-GO**

Exact runner, host and local-path values are deliberately absent from this report. The private
working inventory uses only run IDs and sanitized classifications.

## 1. R3 result

`DONE`. The historical Actions-log blocker B3a was remediated selectively. Useful workflow-run
metadata was retained, no run or artifact was deleted, and unrelated logs were preserved.

## 2. Authorization consumed

Only `AUTHORIZE_ACTIONS_LOG_DELETION` was consumed, and only for logs classified `DELETE_LOGS`.

## 3. Repository state

The repository remained `PRIVATE` throughout. R3 did not change visibility, refs, runner
registration, host identity, releases, publication state or REL1-B state.

## 4. Total workflow runs inventoried

`196` runs were inventoried before the documentation closure commit. The exact machine-readable
working inventory recorded run ID, workflow, event, branch, head SHA, timestamps, status,
conclusion, log state, artifact count, history class and privacy class for every run.

## 5. Workflows inventoried

| Workflow | Runs |
| --- | ---: |
| Build | 75 |
| Compatibility | 75 |
| Security | 11 |
| Dependabot Updates | 35 |
| Benchmarks | 0 |
| Release candidate | 0 |

## 6. Runs with privacy-sensitive exposure

`58` runs on `main`, created from 2026-08-24T20:29:36Z through 2026-08-25T19:39:45Z, contained
privacy-sensitive self-hosted-runner log content. The affected set comprised 23 Build, 24
Compatibility and 11 Security runs, totalling 708 extracted log files in the private audit copy.

Affected Build run IDs:

```text
32890808627, 32888286164, 32874447679, 32871784539, 32871560008, 32864144166,
32853342089, 32850719665, 32844647431, 32834114478, 32830783489, 32828408419,
32820778617, 32816671391, 32811776793, 32809621905, 32807512454, 32807210122,
32789710887, 32784673382, 32783977432, 32777288319, 32774191694
```

Affected Compatibility run IDs:

```text
32890808601, 32888286201, 32874447650, 32871784542, 32871560090, 32864142986,
32853342285, 32850719735, 32844647437, 32834114482, 32830783535, 32828408347,
32820778544, 32816671463, 32811776782, 32809621923, 32807512546, 32807210132,
32806701660, 32789710893, 32784673356, 32783977325, 32777288331, 32774191674
```

Affected Security run IDs:

```text
32890829062, 32888331084, 32876520783, 32873968105, 32864172565, 32853390508,
32850787710, 32844682218, 32834158236, 32830801067, 32828466698
```

## 7. Exposure categories — sanitized

All 58 affected runs exposed `<PERSONAL_RUNNER_NAME_REDACTED>`,
`<PERSONAL_HOSTNAME_REDACTED>` and `<PERSONAL_PATH_REDACTED>` in log content. The personal username
token occurred within the runner identity; it is not reproduced. No additional private host
identifier was established. Generic Linux, Docker, Java, CPU/RAM, PostgreSQL, neutral runner labels
and generic paths were not sufficient for classification.

## 8. Classification counts

| Classification | Count |
| --- | ---: |
| `DELETE_LOGS` | 58 |
| `KEEP` | 125 |
| `REVIEW` | 0 |
| `LOGS_ALREADY_UNAVAILABLE` | 13 |

The 13 pre-existing empty log archives were runs `32809750233`, `32809750221`, `32807264659`,
`32807264656`, `32806701653`, `32769951408`, `32769951385`, `32769502422`, `32769502306`,
`32769459270`, `32769459178`, `32769345298` and `32769345221`.

## 9. Actions artifact inventory

The repository-wide official Actions artifact inventory was `0` before and after remediation,
including expired/active entries returned by the API. Consequently every affected run had
artifact count `0`.

## 10. Artifact deletion required

`no`. No artifact was deleted.

## 11. Run and job metadata exposure assessment

Run metadata did not contain the private runner name or hostname. Historical job metadata does
retain `<PERSONAL_RUNNER_NAME_REDACTED>` for 270 of 298 jobs across all 58 affected runs, plus the
neutral `Default` group and `self-hosted`, `linux`, `x64`, `postgres-bulk-ci` labels. Machine
hostname and local path were confined to logs. Job metadata is not log content and is not mutable
under R3; it remains part of the separate runner-identity blocker and must be addressed in the
runner-neutralization plan without implying that re-registration retroactively edits old jobs.

## 12. Log deletion mechanism

The official GitHub REST endpoint `DELETE /repos/{owner}/{repo}/actions/runs/{run_id}/logs` was used.
Whole-run deletion was not used.

## 13. Logs deletion attempted

`58`, exactly the `DELETE_LOGS` set.

## 14. Logs deletion succeeded

`58` official API calls returned success.

## 15. Logs already unavailable

`13` runs had empty archives before remediation and were left unchanged.

## 16. Logs deletion failures

`0`. No unattempted or failed run was counted as successful.

## 17. Workflow runs deleted

`0`.

## 18. Actions artifacts deleted

`0`.

## 19. Post-delete verification

All 58 deleted-log endpoints returned `404`; all 58 run metadata endpoints remained available;
their `status` and `conclusion` values matched the pre-delete inventory. A complete 196-run endpoint
pass produced 58 unavailable and 138 available log endpoints, with zero classification mismatch.
The remote run count remained 196 and artifact count remained 0.

## 20. Privacy-sensitive logs remaining

`0` known historical privacy-sensitive logs. B3a is remediated. The job-metadata identity residue
described in section 11 is not represented as a log-remediation success and remains separately
blocked.

## 21. Current-canonical vs superseded CI evidence

The inventory classified 3 runs as `CURRENT-CANONICAL`, 3 as
`HISTORICAL-PROCESS-EVIDENCE` and 190 as `SUPERSEDED-HISTORY`. The current-canonical Build,
Compatibility and Security results remain successful, but their sensitive logs were deleted. The
three process-evidence runs also remain successful. No old run is claimed to validate a rewritten
SHA merely because its metadata survives.

## 22. Documentation links/evidence impact

Twenty documented run links point to affected runs and four to unaffected runs. Affected links are
retained because run metadata and results still exist; the documents must not promise downloadable
logs. This report supersedes prior counts of 44 or 52 affected runs.

## 23. Workflow log-minimization findings

GitHub's self-hosted job bootstrap routinely emits runner display name, machine name and workspace
paths before repository steps execute. Project workflows and scripts must not add explicit
printing of runner display name, OS hostname, personal home or workspace paths. Repository-step
redaction alone cannot suppress GitHub's bootstrap output; neutral runner/host identity is required.

## 24. Safe workflow/script changes performed

`none`. Altering runner identity, trust architecture or gates is outside R3. The documentation
commit uses an Actions skip directive so a documentation-only push does not recreate the exposure.

## 25. Local validation

The R3 documentation closure requires `git diff --check`, documentation/link audit,
workflow-security audit and tests, security-policy regression tests, technical release preflight
and REL1 preflight. Exact results are recorded in the task handoff after the self-referential report
commit is synchronized.

## 26. Remote CI handling

No Build, Compatibility, Security, Benchmarks or Release run was manually regenerated merely to
delete logs. The documentation-only closure intentionally skips Actions while the personal runner
identity remains active; the successful current run metadata is retained.

## 27. Documentation changes

This report was added. The [public-history remediation report](rel1ar-public-history-remediation.md)
and [REL1-A exposure audit](rel1a-open-source-exposure-audit.md) were reconciled to the R3 result.

## 28. Git commits

One ordinary documentation-only commit records R3 using the approved noreply identity. Its exact
SHA is reported in the task handoff because a commit cannot truthfully contain its own SHA.

## 29. Final Git state

The required closure state is a clean `main` with `HEAD == origin/main` and GitHub visibility
`PRIVATE`; its exact SHA is reported after synchronization.

## 30. Actions historical log blocker

`REMEDIATED`.

## 31. Runner identity blocker

`PENDING`. R3 neither renamed nor re-registered the runner and did not change the OS hostname.

## 32. PR synthetic refs blocker

`PENDING`. R3 did not mutate GitHub-managed PR refs.

## 33. Remote actions

```text
repository visibility changed: no
Actions logs deleted: yes
workflow runs deleted: no
Actions artifacts deleted: no
runner re-registered: no
OS hostname changed: no
GitHub PVR enabled: no
public CI activated: no
tag created: no
Benchmarks executed: no
Release executed: no
Central upload: no
publication: no
REL1-B started: no
```

## 34. Definition of Done assessment

`DONE` for R3 after clean synchronized validation: exact inventory and classification completed;
artifacts audited; only authorized logs deleted; failures handled fail-closed; log unavailability
and retained results verified; documentation reconciled; and known sensitive logs reduced to zero.

## 35. Remaining blockers

- Neutralize the active runner and host identity, including explicit treatment of immutable
  historical job metadata.
- Resolve the six GitHub-managed synthetic PR refs through the separately governed path.

## 36. Open-source activation verdict

`NO-GO`. The repository must remain private while runner identity and synthetic PR refs are pending.

## 37. Exact next step

Prepare the runner-neutralization plan and request the separate authorization:

```text
AUTHORIZE_RUNNER_REREGISTRATION
```

## Boundary statement

```text
REL1-A-R R3 status: DONE
Actions personal identity logs: REMEDIATED
workflow runs deleted: no
Actions artifacts deleted: no
runner identity neutralized: no
PR synthetic refs clean: pending
full-history secrets: PASS
repository public: no
REL1-B started: no
tag created: no
Central upload: no
publication activated: no
```

R3 authorization was limited to selective historical log deletion. It did not authorize run or
artifact deletion, runner mutation, visibility change or REL1-B activation.
