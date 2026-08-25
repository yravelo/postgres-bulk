# Trusted self-hosted CI runner

## Purpose and scope

Build and Compatibility use one repository-scoped self-hosted runner because GitHub-hosted jobs
were rejected before execution by the account billing/spending-limit state. This is a zero-cost
operational control for the private, single-maintainer repository `yravelo/postgres-bulk`; it does
not change repository visibility or plan and does not relax any test or security gate.

Only `.github/workflows/build.yml` and `.github/workflows/compatibility.yml` select this runner.
Benchmarks and Release remain on `ubuntu-latest` and manual `workflow_dispatch`; the runner has no
publication role and must never receive Central or OpenPGP material.

## Host and account boundary

The reviewed host is Ubuntu Linux x86-64 with enough CPU, memory and disk for the Maven reactor,
Temurin 17/21/25 installations, Docker, Testcontainers and PostgreSQL containers. The official
GitHub Actions runner is installed outside the development checkout:

| Property | Reviewed value |
| --- | --- |
| Service account | `postgres-bulk-runner`, locked non-root system account, no login shell |
| Home | `/home/postgres-bulk-runner`, mode `0750` |
| Runner directory | `/home/postgres-bulk-runner/actions-runner` |
| Work directory | `/home/postgres-bulk-runner/actions-runner/_work` |
| Registration | repository-level, only `yravelo/postgres-bulk` |
| Labels | `self-hosted`, `linux`, `x64`, `postgres-bulk-ci` |
| Service | official `svc.sh` systemd unit, enabled at boot |

The service account has no interactive password, sudo role, GitHub PAT, developer SSH key, GPG
home, GitHub CLI configuration, Git credential store or Maven `settings.xml`. Its runner-internal
credential files are mode `0600`. The one-time repository registration token was passed directly
to the official configurator and was not logged, committed, documented or retained as a secret.
Repository Actions Secrets remained at zero during setup.

## Docker trust implication

The service account belongs to the local `docker` group because Testcontainers needs the Docker
socket. Docker access is approximately root-equivalent: code running as this account can control
containers and may be able to affect the host. This machine and runner are therefore trusted
infrastructure, not a sandbox for arbitrary contributors. No inbound port is required or opened by
the runner; it initiates outbound TLS connections to GitHub and to the existing build dependency,
scanner and container registries.

Testcontainers/Ryuk owns normal container cleanup. After a failed or cancelled run, inspect only
project/Testcontainers residue by image, name and the `org.testcontainers=true` label. Remove only
confirmed residue; never use host-wide `docker system prune`, because the Docker daemon coexists
with local development workloads.

## Workflow selection and PR trust policy

Every Build and Compatibility job requires the exact label set above. A job-level fail-safe guard
allows a normal `push` event, and allows a `pull_request` only when both conditions hold:

- `github.actor == 'yravelo'`;
- `github.event.pull_request.head.repo.full_name == github.repository`.

Thus a trusted owner branch PR from this repository may run. A fork or any other actor's PR is
skipped and cannot automatically execute on the host, even though the `pull_request` trigger stays
visible. This policy assumes the current private, single-maintainer repository. Stop the runner and
review the boundary before adding collaborators, changing ownership/visibility, accepting external
contributions or adding a new trigger.

The deterministic workflow-security gate enforces the exact labels and guard, the 11 Compatibility
lanes, `contents: read`, SHA-pinned Actions, non-persistent checkout credentials and explicit
`clean: true`. It also keeps Benchmarks and Release on the reviewed GitHub-hosted runner. Build and
Compatibility reference no repository secrets.

## Workspace and Maven cache policy

The runner uses only its own `_work`; it never reuses the maintainer's development checkout.
`actions/checkout` explicitly performs a clean checkout and retains no credentials. Generated
reactor files may remain until the next checkout, when `clean: true` removes them. The runner temp
directory is job-scoped; after an abnormal termination, inspect it before returning the service to
use.

`actions/setup-java` retains the existing Maven cache behavior. The service account's local
`~/.m2/repository` and the Actions cache may contain public dependencies and locally built project
snapshots. Any generated Maven `settings.xml` is forced into the job-scoped `runner.temp`, not the
persistent home, and contains no publishing credentials. This is acceptable only because all eligible
jobs are trusted, secret-free repository jobs and every reactor command uses `clean`. On a suspected
cache or host compromise, stop and deregister the runner first, then discard the affected workspace
and cache selectively before rebuilding them from reviewed sources.

## Service operation and monitoring

The official service is enabled at boot. The generated unit follows the upstream `svc.sh` default
and currently has `Restart=no`; no custom daemon or systemd override was added. A host reboot starts
it automatically, while a runtime crash requires operator inspection and restart.

Run these local administrative commands from the runner directory or through systemd:

```bash
sudo systemctl status actions.runner.yravelo-postgres-bulk.postgres-bulk-owner-ubuntu.service
sudo systemctl start actions.runner.yravelo-postgres-bulk.postgres-bulk-owner-ubuntu.service
sudo systemctl stop actions.runner.yravelo-postgres-bulk.postgres-bulk-owner-ubuntu.service
sudo systemctl restart actions.runner.yravelo-postgres-bulk.postgres-bulk-owner-ubuntu.service
sudo journalctl -u actions.runner.yravelo-postgres-bulk.postgres-bulk-owner-ubuntu.service
```

Also confirm the runner is `online` and not unexpectedly busy in the repository Actions settings.
Do not paste diagnostic logs into public channels without checking paths and metadata.

## Updates, incident response and removal

The installed baseline is the current official x64 release verified against the SHA-256 published
in the upstream release notes. Official runner auto-update remains enabled. Review upstream runner
releases and service logs periodically; do not pin an obsolete binary or disable the platform's
30-day update enforcement.

If unexpected code runs, credentials may be exposed, Docker state looks unowned, or the runner
behaves anomalously:

1. stop the systemd service and prevent new jobs from being assigned;
2. mark/remove the runner in repository settings;
3. preserve only non-sensitive logs needed for diagnosis;
4. rotate any potentially reachable credential before cleanup;
5. audit the dedicated home, `_work`, Maven cache and Docker objects;
6. reinstall/re-register from the checksum-verified official package before reuse.

The SEC6 [runner-compromise runbook](incident-response-runbook.md#self-hosted-runner-compromise)
adds evidence, credential-rotation and exit criteria. Workspace cleanup alone is explicitly
insufficient after malicious execution.

For planned removal, use the ephemeral removal token and commands generated by the repository's
runner settings, then use official `svc.sh stop` and `svc.sh uninstall`. Remove the dedicated
account/home only after registration, service, workspaces and evidence have been checked. Never
store a registration/removal token in this repository, documentation or shell profile.

## Limitations

- Repository-level runner groups are not used; the dedicated repository registration and label
  provide routing isolation on the current plan.
- The host is persistent rather than an ephemeral VM, so workspace/cache hygiene and incident
  response are operational responsibilities.
- Docker group membership materially weakens host isolation and is acceptable only with the
  owner-only/fail-safe PR boundary.
- The official service has boot autostart but no automatic restart-on-failure override.
- Hosted-runner billing independence applies only to Build and Compatibility. It does not activate
  or execute Benchmarks, Release, signing, provenance, upload or publication.

## SEC4R validation evidence

For commit `6d6556b92a123b9720d39bcafef73a9bdf369119`, Build run `32774191694` passed every security,
reactor, SBOM/license, consumer and documentation step on `postgres-bulk-owner-ubuntu`.
Compatibility run `32774191674` passed all 11 lanes on the same runner and SHA. A post-run audit
found zero Testcontainers-labelled containers, networks or volumes and confirmed the runner stayed
online with zero Repository Actions Secrets.

That audit also found an empty, credential-free `~/.m2/settings.xml` created by `setup-java` despite
`overwrite-settings: false`. It was removed, and the workflows/gate now require `settings-path`
under `runner.temp` so subsequent jobs cannot persist it in the service account home. No token or
credential field was present in the removed file.

## Official references

- [Adding self-hosted runners](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/add-runners)
- [Using self-hosted runners in a workflow](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/use-in-a-workflow)
- [Configuring the runner as a service](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/configure-the-application)
- [Self-hosted runner reference](https://docs.github.com/en/actions/reference/runners/self-hosted-runners)
- [Actions runner releases and checksums](https://github.com/actions/runner/releases)
