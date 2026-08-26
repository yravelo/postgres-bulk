# CI runner trust boundary

## Canonical repository policy

The canonical repository `yravelo/postgres-bulk` uses standard GitHub-hosted `ubuntu-latest`
runners for every workflow. Build and all 11 Compatibility lanes run for `pull_request` and pushes
to `main`; Security runs on its weekly schedule or explicit dispatch. Benchmarks and the
candidate-only Release workflow remain manual. No canonical workflow selects `self-hosted` or the
historical `postgres-bulk-ci` label.

This is a structural trust boundary, not an actor-name allow-list:

```text
fork / Dependabot / external / same-repository PR
                     |
                     v
          fresh GitHub-hosted VM
          contents: read
          repository secrets: none
          persistent credentials: none
          self-hosted labels: impossible
```

A controlled `main` push uses the same ephemeral runner path and retains the full Build plus
11-lane Compatibility coverage. There is no reduced PR matrix and no performance gate.

## Enforced invariants

`scripts/check-workflow-security.py` and its adversarial fixtures fail closed when:

- any workflow selects something other than `ubuntu-latest`;
- a Build or Compatibility job adds an actor/repository `if` guard that removes the public PR path;
- `pull_request_target`, `workflow_run` or `repository_dispatch` is introduced;
- a workflow requests write permissions or references `secrets.*`;
- checkout persists credentials or an Action is not pinned to an approved full SHA;
- Compatibility no longer expands to the reviewed 11 lanes.

Repository-level Actions settings add read-only default token permissions, full-SHA enforcement
and a selected GitHub-owned Actions policy. The versioned gate is narrower still: it allows only
the reviewed SHAs of checkout, setup-java and upload-artifact.

## Docker and Testcontainers

The reviewed x64 Ubuntu GitHub-hosted image includes Docker client/server. CI runs a PostgreSQL
container smoke before full Security validation, and Maven integration tests use Testcontainers.
`scripts/check-runner-health.sh` snapshots Testcontainers-labelled containers, networks and
volumes before the run and proves that no new labelled object remains afterwards. Each hosted job
receives a fresh VM, so persistent host or cache contamination is not part of the canonical path.

Never add a repository secret merely to make Testcontainers work. Never use host-wide Docker prune
as a cleanup mechanism.

## Decommissioned archive runner

MIG5B removed the repository-level registration for `postgres-bulk-ci-01` before deleting the old
private archive. The dedicated service is inactive, dead and disabled; no listener process remains.
The installation filesystem and host were preserved because unrelated non-runner host use was not
ruled out and deleting them was unnecessary. The canonical repository and every other inspected
owner repository retain zero repository runners. The obsolete local `archive` remote was removed.

The retired runner must never be registered to the canonical repository or exposed to public PR
code without a new explicit authorization and separate threat review. All canonical workflows
remain fixed to GitHub-hosted infrastructure.

## Public activation

MIG4 set the public fork-workflow approval policy to require approval for all external contributors
and re-audited the effective settings. Approval is defense in depth: even an approved external
workflow still runs only on GitHub-hosted infrastructure with read-only token permissions and no
repository secrets.

Private Vulnerability Reporting, CodeQL, secret scanning with push protection, Dependency Review
and `main` protection were also activated by MIG4. They do not weaken or replace this hosted-only
boundary.

## Incident response

If a canonical job unexpectedly reports a self-hosted label, persistent hostname/path, write token
or secret access, cancel it, preserve sanitized metadata, disable the affected workflow and follow
the [runner compromise runbook](incident-response-runbook.md#self-hosted-runner-compromise). The
historical archive runner is no longer registered or active; any unexpected resurrection must be
treated as a new incident and stopped before credential review/rotation.

## Official references

- [GitHub-hosted runners](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)
- [Actions billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions)
- [Approving runs from forks](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/approve-runs-from-forks)
- [Secure use reference](https://docs.github.com/en/actions/reference/security/secure-use)
- [Ubuntu runner image inventory](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md)
