# Incident response runbook

**Owner:** `yravelo`. **Scope:** PostgreSQL Bulk source, dependencies, build chain, GitHub, trusted
runner, local signing workstation, OpenPGP identity and release artifacts. This runbook does not
authorize a tag, upload, publication or public disclosure.

Private incident records must not be committed. Copy the versioned templates to owner-controlled
restricted storage and retain only sanitized public evidence after coordinated disclosure.

## Common response sequence

| Phase | Required action | Evidence | Exit criteria |
| --- | --- | --- | --- |
| Receive | assign private record ID, timestamp, source and owner | intake metadata, never live secrets | owner can safely access report |
| Triage | reproduce, identify asset/version/scope/reachability/exploitation | exact SHA/GAV/advisory, sanitized reproduction | severity and `BLOCK/WARN/INFORMATIONAL` decided |
| Contain | stop affected CI/signing/release/distribution and revoke access as needed | action timeline, relevant run/deployment IDs | further exposure credibly stopped |
| Remediate | fix root cause, rotate/revoke/reprovision, add regression coverage | reviewed diff, rotation/revocation receipt, hashes | affected control restored from trusted state |
| Verify | rerun affected gates and compatibility from clean source | run IDs, checksums, SBOM/inventory/signatures | no open blocker; candidate bound to trusted SHA |
| Coordinate | agree reporter credit, guidance and disclosure sequence | private communication log | safe fix/mitigation ready or early warning justified |
| Disclose | publish patch/advisory when applicable | release/advisory IDs and final timeline | affected users have actionable guidance |
| Close | record residual risk, owner and follow-up date | post-incident review | exit criteria met and lessons assigned |

An incident may skip public disclosure only when no published user can be affected and there is no
public corrective action. It never skips containment, verification or private evidence retention.

## Immediate evidence rules

Preserve exact commit SHA, workflow/run/job IDs, advisory/CVE/GHSA/OSV identifiers, artifact and
container hashes, release inventory, signer fingerprint, relevant repository setting changes,
sanitized logs and an ordered UTC timeline. Record who collected each item and its SHA-256 when
practical. Keep original sensitive evidence restricted and read-only.

Do not commit an undisclosed report, reporter identity, secret, token, private key, passphrase,
production data, runner credential file, raw memory/disk image or unsanitized log. Do not paste it
into Issues, PRs, Actions artifacts or chat. Public records are created only after disclosure and
contain the minimum necessary facts.

## Dependency vulnerability

**Trigger:** OSV/Dependabot/report identifies an affected runtime, test, benchmark, example or
build dependency.

**Actions:**

1. stop release work; keep the finding `BLOCK` until scope and applicability are known;
2. capture exact resolved version, Maven paths, direct owner/BOM, scope and supported consumers;
3. check reachability, default configuration, exploitation and supported fixed versions;
4. update the owning direct dependency/BOM within the supported generation; avoid isolated
   Framework/Data/Hibernate/Log4j overrides merely to silence a scanner;
5. remove a stale accepted-risk record when the finding disappears; never carry it to a new
   coordinate/version silently;
6. rerun OSV, SBOM/license reconciliation, Build and compatibility boundaries affected.

**Evidence:** advisory aliases, resolved trees, scope/reachability rationale, fix release notes,
gate output and remote run IDs.

**Exit:** zero untriaged blocker, supported fixed owner selected or exact unexpired accepted risk,
and affected artifacts/consumers verified.

## Build-chain compromise

This procedure covers a Maven plugin, GitHub Action, scanner binary, Maven Wrapper or Docker image.

**Trigger:** upstream compromise/advisory, checksum/signature drift, unexpected behavior, mutable
reference movement or execution outside the reviewed inventory.

**Actions:**

1. stop Build/Compatibility/Release and mark every affected candidate invalid;
2. isolate the runner/workstation used and preserve hashes/logs without executing the suspect again;
3. identify first/last affected commit, run and candidate; inspect whether credentials or source
   could be read or changed;
4. remove/pin/replace the component using official source and independently verified checksum/SHA;
5. purge only confirmed affected caches/workspaces after evidence collection; a persistent runner
   compromise follows the full runner procedure below;
6. reconstruct from a trusted commit and clean dependency/cache state, then rerun all security,
   build, compatibility, SBOM, reproducibility and signing gates.

**Exit:** trusted tool/image identity restored, exposed credentials rotated, clean rebuild matches
reviewed source and no affected candidate remains distributable.

## Secret exposure matrix

| Secret | Containment | Recovery / exit |
| --- | --- | --- |
| GitHub token/PAT/session | revoke token, stop affected workflow, review account/repository events and access | new least-privilege token only if needed; unauthorized changes removed through reviewed commits/settings |
| future Central token | revoke in Publisher Portal, stop upload/publish, inspect deployments and Portal activity | affected draft deployment dropped; new named token only on trusted workstation after authorization |
| GPG passphrase only | change passphrase on trusted host; determine whether private key was accessible and inspect signatures | exclusive key control and no unauthorized signature demonstrated; otherwise escalate to key compromise |
| OpenPGP private key | stop signing/releases; use offline certificate to revoke; publish revocation; identify affected signatures | new identity/fingerprint reviewed, public key distributed, candidates rebuilt/re-signed; affected releases disclosed |
| runner registration/removal token | stop service and remove runner registration; token is treated as exposed even if short-lived | fresh checksum-verified registration on a trusted reprovisioned host |

Revocation/rotation happens before Git cleanup. GitHub log masking and deletion from the latest
commit do not make a secret safe. Rewriting shared history requires a separate incident decision
after revocation and preservation.

### Central token detail

A Central token authorizes deployment but does not sign artifacts. Revoke it independently from
the OpenPGP key. Inspect pending deployments; drop an unpublished affected deployment. Central
components already published are immutable, so token rotation cannot remove or alter them. Issue a
new token only after the upload workstation and source are trusted, and keep upload and Portal
publication separately authorized.

### Passphrase-only versus private-key exposure

Passphrase exposure alone does not prove private-key compromise. If the encrypted private key
remained exclusively on a trusted, inaccessible device, change the passphrase, inspect the device
and signing history, and document why revocation is unnecessary. If the key file, unlocked agent,
workstation or backup may also have been accessible—or evidence is inconclusive—treat the private
key as compromised and revoke it. Never weaken the passphrase merely to restore automation.

## OpenPGP private-key compromise

1. stop signing, candidate preparation, tagging, upload and publication;
2. preserve the suspected exposure timeline and all signed artifact hashes;
3. import/use the offline revocation certificate and publish the revocation through every used
   keyserver/distribution channel;
4. notify users of the affected or uncertain release range; do not silently replace the fingerprint;
5. create a new protected identity on a restored workstation with new expiry, backup and revocation;
6. update signing policy, tracked public key and verification documentation through review;
7. rebuild and re-sign only unpublished candidates from trusted source.

Historical Maven Central bytes cannot be replaced. A signature made before compromise remains a
record of the historical key, but trust in when/by whom it was made may be uncertain. Keep the old
fingerprint documented as revoked, publish an advisory/warning and ship a new patch signed by the
new identity. Never try to overwrite the old Central version.

## Self-hosted runner compromise

**Trigger:** unexpected code/job, anomalous Docker object/process/network activity, runner
credential exposure, unexplained workspace/cache modification or host integrity concern.

1. stop the systemd runner service so no new job is assigned;
2. remove/deregister the runner in repository settings;
3. consider the persistent host compromised because Docker access is root-equivalent;
4. preserve restricted logs and timeline; rotate any credential potentially reachable from host;
5. inspect repository settings, runner home, `_work`, Maven cache and Docker state from trusted media;
6. reprovision the host/account and reinstall the checksum-verified official runner package;
7. register with a fresh ephemeral token and restore only reviewed configuration/dependencies;
8. run a secret-free validation and confirm dedicated labels, online state and trust guards.

Workspace cleanup or `actions/checkout clean` alone is never sufficient after malicious execution.
Exit only when the host is reprovisioned/trusted, registration is fresh, exposed credentials are
rotated and the workflow policy passes.

## Signing-workstation compromise

Stop signing and disconnect the affected workstation from release work. Determine whether the
private key, gpg-agent, passphrase, revocation certificate, Central token, source checkout or
candidate bytes were accessible. Revoke the key if private material might be compromised and revoke
the Central token independently if present. Restore from trusted installation/media, verify offline
backup and recovery, fetch a reviewed source commit into a clean checkout, rebuild all evidence and
repeat signed-candidate verification. Exit only after workstation trust and key decision are
documented and every candidate since last known-good state is classified.

## Repository or maintainer-account compromise

**Trigger:** malicious/unknown main or history change, workflow mutation, force push, unexpected
collaborator/key/app, altered release/tag or suspicious owner-account activity.

1. stop runner, CI, signing and release activity;
2. secure the owner account and recovery methods; revoke sessions, tokens, deploy keys and apps
   whose integrity is uncertain;
3. capture remote refs/settings/audit evidence and identify a trusted commit using independent local
   clones, signed evidence and known-good hashes;
4. audit history, workflows, CODEOWNERS, security config, collaborators, webhooks and releases;
5. restore through explicit reviewed commits/refs without destroying original incident evidence;
6. rebuild candidates from trusted source, regenerate SBOM/inventory/checksums and re-sign;
7. disclose any published release or user impact.

Exit requires restored account/repository control, trusted `main`, reviewed settings, clean CI and
classification of every tag/candidate/artifact in the affected window.

## Artifact or Maven Central compromise

### Unpublished candidate or Portal deployment

If a staged artifact differs from its source-bound inventory, checksum, signature or reproducible
counterpart: do not distribute it; stop signing/upload; preserve the mismatched files and hashes;
drop/invalidate the candidate or unpublished Portal deployment; investigate source/build/tooling;
and rebuild all bytes and evidence from a clean trusted SHA. Never re-sign unexplained bytes.

### Published Central version

Maven Central does not permit modifying, replacing or deleting a published component. A published
vulnerable or corrupted version therefore receives:

1. containment guidance and a GHSA/CVE when applicable;
2. a new fixed patch version built, inventoried and signed from trusted source;
3. public warning identifying exact affected and patched ranges;
4. verification guidance for the new tag, checksum and signer fingerprint.

The old coordinates remain immutable. Do not reuse the version or present a changed local artifact
under the same GAV.

## Closure and post-incident review

Close only when the root cause and affected range are understood, containment holds, mandatory
credentials/keys are rotated or explicitly cleared, clean verification passes, users/reporters
receive appropriate guidance and residual work has owner/date. Complete a post-incident review for
Critical/High or any supply-chain, signing, runner, workstation, repository or artifact incident.

The review records what happened, why controls did/did not detect it, timeline, impact, response
quality, corrective actions, owners and deadlines. It is blameless but evidence-based. Publish a
sanitized version only after coordinated disclosure; retain the private original outside Git.

## Templates and references

- [Vulnerability triage template](templates/vulnerability-triage.md)
- [Incident and post-incident template](templates/incident-record.md)
- [Vulnerability response and governance](vulnerability-response-and-governance.md)
- [Maven Central immutability](https://central.sonatype.org/publish/requirements/immutability/)
- [GnuPG manual](https://gnupg.org/documentation/manuals/gnupg.pdf)
