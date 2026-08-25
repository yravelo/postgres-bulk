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

## Historical archive runner

The private rollback archive `yravelo/postgres-bulk-private-archive` still owns one repository-level
runner named `postgres-bulk-ci-01`. It is not registered in the canonical repository, is not
selected by canonical workflow YAML and was not moved during MIG3. The local `archive` remote is
fetch-only with push URL `DISABLED`.

The archive runner remains trusted persistent infrastructure with Docker/root-equivalent reach.
It must never be registered to the canonical repository or exposed to public PR code without a new
explicit authorization and a separate threat review. Its eventual stop, deregistration and host
decommission are later rollback-retirement work; archive deletion is not part of this baseline.

## Public activation

When the repository becomes public, MIG4 must set the public fork-workflow approval policy to
require approval for all external contributors and re-audit the effective settings. Approval is
defense in depth: even an approved external workflow still runs only on GitHub-hosted infrastructure
with read-only token permissions and no repository secrets.

Private Vulnerability Reporting, public-plan rulesets/branch protection and any public-only
security features are also MIG4 steps. They do not weaken or replace this hosted-only boundary.

## Incident response

If a canonical job unexpectedly reports a self-hosted label, persistent hostname/path, write token
or secret access, cancel it, preserve sanitized metadata, disable the affected workflow and follow
the [runner compromise runbook](incident-response-runbook.md#self-hosted-runner-compromise). If the
archive runner is suspected compromised, stop/deregister it and rotate reachable credentials before
cleanup; workspace cleanup alone is insufficient.

## Official references

- [GitHub-hosted runners](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)
- [Actions billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions)
- [Approving runs from forks](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/approve-runs-from-forks)
- [Secure use reference](https://docs.github.com/en/actions/reference/security/secure-use)
- [Ubuntu runner image inventory](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md)
