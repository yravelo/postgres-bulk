# Continuous security validation and operational resilience

**SEC7 status:** implementation complete on 2026-08-25; remote closure evidence is recorded after
the implementation commit reaches `main`. SEC6 remains `PARTIALLY DONE`: its private reporting
channel is pending. That owner action does not block SEC7, SEC8 or technical `REL0` evaluation, but
it continues to block `REL1` and any supported public release.

## Control model

SEC7 composes the existing SEC1–SEC6 controls; it adds no scanner and no secret-bearing service.
The machine-readable source of truth is
`config/security/continuous-security-policy.json`. It owns the workflow and POM inventories,
module classifications, active tool versions, gate placement, failure classes, expiry warning
windows, runner minimums, OpenPGP public identity and the explicit reporting-channel boundary.

`scripts/check-security.sh` is the canonical entry point:

| Mode | Intended use | Controls |
| --- | --- | --- |
| `fast` | Build preflight and local changes | policy/tool/module/workflow drift, expiry fixtures, workflow regression fixtures, Dependabot policy and current-tree Gitleaks |
| `full` | weekly/manual Security workflow | fast plus runner preflight, full-history Gitleaks, OSV, clean reactor/Testcontainers, SAST report audit, SBOM/license reconciliation, docs/API, public-key preflight and post-run Docker residue check |
| `release` | local technical release preparation | full plus clean/synchronized-main technical release preflight; it does not sign, tag, upload or publish |

Build uses `fast` once and retains its existing canonical dependency, reactor, SAST, SBOM, consumer
and documentation steps. Compatibility does not duplicate expensive security analysis across its
11 lanes. The weekly Security job uses `full`; the release candidate retains its deeper staging
and reproducibility work and now runs the technical security preflight. This distribution keeps
coverage visible without paying the same scan in every matrix lane.

## Scheduled Security workflow

`.github/workflows/security.yml` runs every Monday at `04:17 UTC` and supports an explicit manual
dispatch. It uses only the trusted repository-scoped runner labels
`[self-hosted, linux, x64, postgres-bulk-ci]`, declares `contents: read`, has a 150-minute timeout,
serializes runs without cancelling one in progress, checks out full history without credentials
and references zero `secrets.*`. Checkout and Java setup use the same full-SHA allow-list as the
other workflows. The deterministic workflow auditor now requires exactly five workflows and
rejects schedule drift, a secret reference, an event-dependent bypass or a runner-boundary change.

Scheduled validation intentionally does not run the 11-lane Compatibility matrix. The normal
Build and Compatibility workflows remain the supported-boundary evidence for every `main` change;
Security exercises one clean Java 17/PostgreSQL 15.18 reactor with Docker/Testcontainers and every
security control that benefits from fresh history/network data.

## Gate ownership, freshness and drift

Every gate records an owner, exact command, applicable modes, CI placement and failure class. Each
active standalone tool or Maven scanner records an exact version, canonical source and update
mechanism. The policy checker reconciles:

- Gitleaks, OSV and Maven dependency-plugin versions with their checksum-pinned wrappers;
- SpotBugs, its engine, FindSecBugs and CycloneDX with the parent POM and build-tool inventory;
- CycloneDX with its SBOM policy;
- all 14 POM paths and all 12 reactor module entries;
- the nine publishable and three non-publishable artifacts across continuous-security, SBOM and
  signing policies;
- all workflow names/triggers and all gate command paths;
- the tracked OpenPGP public-key path and full fingerprint.

A new POM, module, workflow or security tool therefore fails closed until it receives an explicit
classification and placement. Historical Java-file counts are not pinned: adding source is normal,
while Maven/module discovery determines whether the code is covered by the reactor and SAST policy.

Dependabot remains the normal update proposal mechanism for Actions and Maven tooling. Scanner
binary upgrades are reviewed against the upstream release, architecture assets and SHA-256 before
changing the policy and wrapper together. The Actions runner keeps official auto-update enabled;
weekly health checks reject a detected version below the reviewed minimum rather than disabling
GitHub's update enforcement.

## Expiry and stale-policy gates

Accepted dependency risks, SAST exclusions and license reviews/exceptions remain narrow and
fail-closed:

- an expired accepted risk or license record fails the policy gate;
- a review entering the 30-day window emits a visible warning;
- OSV triage fails when an accepted-risk advisory/dependency/version is absent, changes scope or
  disappears without removal of the stale record;
- the SAST auditor requires exactly six `Bug + Class + Method` entries, current review dates and a
  real matching source class/method, so renamed or deleted code invalidates its exclusion;
- the SBOM/license auditor requires the exact current coordinate, version and license set, making
  obsolete license exceptions fail during full validation.

`scripts/test-security-policy.py` contains focused passing/failing fixtures for expired accepted
risks, SAST exclusions, license exceptions, warning-window behavior and the technical-versus-REL1
reporting boundary. Existing OSV, SBOM and signing fixture suites keep their more detailed semantic
coverage.

## Runner and Docker resilience

`scripts/check-runner-health.sh` checks required commands, Maven Wrapper executability, free disk,
Docker API access and a real `postgres:15.18-alpine` image smoke command. On GitHub Actions it also
checks the reviewed minimum runner version when discoverable and rejects persistent Git/Maven/GitHub
CLI credential files or OpenPGP private-key material in the service account.

Before a full run, the script records only Docker objects labelled `org.testcontainers=true`.
After the clean reactor it fails if the run left any new labelled container, network or volume.
Existing unrelated developer Docker objects are neither removed nor treated as SEC7-owned. The
control never invokes a host-wide prune. Testcontainers and Ryuk remain responsible for normal
cleanup; a residue failure requires targeted inspection under the existing runner incident runbook.

The full reactor is the PostgreSQL/Testcontainers behavior smoke test. Docker API denial, missing
runtime tools, insufficient disk and new residue are infrastructure failures, not product findings.
The gate remains red until the host is healthy and the same commit is rerun.

## Failure and outage classification

The orchestrator prints a classification at every start/failure:

| Class | Meaning and response |
| --- | --- |
| `CODE` | tests, docs or public API regressed; fix the commit |
| `SECURITY` | secret, finding, expiry or policy decision failed; triage/fix, never bypass |
| `CONTROL` | inventory, workflow, fixture or configured invariant drifted; update code and policy together after review |
| `INFRASTRUCTURE` | runner, disk, Docker or required command unavailable; restore the trusted host and rerun unchanged |
| `SECURITY_OR_EXTERNAL` | a network-backed gate can represent a real finding or tool/service outage; inspect its fail-closed diagnostic before retrying |
| `RELEASE_CONTROL` | release identity, worktree or source synchronization failed; release preparation stops |

OSV, GitHub/keyserver or registry outages never become green through `continue-on-error`. A bounded
retry of the unchanged SHA is acceptable after confirming external/runner health. Repeated failure
is investigated and recorded; release freshness is never replaced by an old successful result or
an unversioned emergency bypass. Generated detailed evidence remains under ignored `target/` and
must be reviewed before sharing because paths and dependency metadata can be sensitive.

## OpenPGP and release preflights

`scripts/check-public-key.sh local` parses the tracked public export with GnuPG, requires the full
approved fingerprint and a live expiry. The policy warns 365 days before expiry and blocks within
90 days. `remote` may retrieve only public material from the configured HTTPS fingerprint URL; it
requires the exact fingerprint but tolerates the keyserver's documented removal of the unverified
UID and its expiry-bearing self-signature. The complete tracked export remains the expiry source of
truth. Remote retrieval is optional for local work and enabled in the full scheduled workflow. A
keyserver result is availability evidence, never a replacement trust anchor.

The release boundaries are intentionally separate:

- `scripts/check-release-security-preflight.sh technical` requires policy/key PASS, a clean tree,
  a successful `origin/main` fetch and `HEAD == origin/main`. The private reporting channel may be
  pending.
- `scripts/check-release-security-preflight.sh rel1` fails while that channel is pending. It is the
  activation boundary for the first supported public release.

Neither preflight imports a private key, asks for a passphrase, executes signing fixtures with real
material, creates a tag, runs Release, uploads a Central bundle or publishes. Existing signing
regression fixtures continue to generate private keys only inside ephemeral temporary directories.

## Operation and troubleshooting

From the repository root:

```bash
./scripts/check-security.sh fast
./scripts/check-security.sh full
./scripts/check-security.sh release
./scripts/check-release-security-preflight.sh technical
./scripts/check-release-security-preflight.sh rel1  # expected BLOCK while channel is pending
```

The target budget is under two minutes for `fast`, under 30 minutes for a warm `full` run and no
more than the workflow's 150-minute hard timeout when caches/images are cold. Network-backed tools
may be slower, but no timing threshold suppresses a valid result. Operator diagnostics must name
the failed gate/class without printing secrets, complete scanner JSON, private paths or credentials.

## Boundary and handoff

SEC7 does not configure a private reporting address and does not mark SEC6 `DONE`. Repository
visibility remains private. Benchmarks, Release, signing, tag creation, Central upload and
publication remain unexecuted. After local and remote SEC7 gates are green, the next exact phase is
`SEC8 — Security Baseline Technical Closure`; this document does not start it.
