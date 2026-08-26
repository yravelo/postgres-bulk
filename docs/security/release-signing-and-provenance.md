# Release signing, inventory and provenance

**Estado:** SEC5 implementation complete and REL2 publication complete. Release `0.1.0` was signed
from the frozen clean commit, uploaded once and manually published through separately authorized
gates. This document grants no authority for any future release operation.

## Release identity

The dedicated release identity is:

| Field | Reviewed value |
| --- | --- |
| UID | `PostgreSQL Bulk Release (io.github.yravelo)` |
| Primary fingerprint | `11545CD242C9575DF408AC08F83D364143C798A3` |
| Algorithm | RSA 3072, primary signing/certification key |
| Created | 2026-08-24 |
| Expires | 2028-08-23 |
| Signature digest | SHA-512; verifier rejects SHA-1 and unknown digests |
| Private location | owner-controlled dedicated `GNUPGHOME`, outside Git and the runner |
| Public export | [`keys/postgres-bulk-release-11545CD242C9575DF408AC08F83D364143C798A3.asc`](keys/postgres-bulk-release-11545CD242C9575DF408AC08F83D364143C798A3.asc) |

The UID deliberately contains no personal email. The passphrase was entered only through GnuPG
pinentry and is not passed in argv, environment variables, Maven settings, repository secrets,
logs or chat. GnuPG generated the revocation certificate in the private key home. Its path and
contents are private and must never be committed or uploaded as workflow evidence.

Before any publication, the owner must export the passphrase-protected secret key to encrypted
removable media, copy the revocation certificate to separate offline media, verify both copies,
and remove any transient export. The private-key backup and passphrase must not share storage.
Repeat that recovery check annually and before key rotation. Repository backup, cloud sync and
the persistent self-hosted runner are not offline backup.

REL2 recorded the owner's explicit sanitized confirmation on 2026-08-26 that the protected secret
key backup and separately stored revocation material are recoverable, an isolated restoration and
test signature succeeded, the restored identity matched fingerprint
`11545CD242C9575DF408AC08F83D364143C798A3`, and transient restored material was removed. This
evidence records only the outcome: no private key, passphrase, backup/revocation content, storage
location or screenshot entered Git, logs or chat. `EP-02` is therefore `PASS`.

Only the public key may be sent to a keyserver. Central currently lists
`keyserver.ubuntu.com`, `keys.openpgp.org` and `pgp.mit.edu`; the public key was sent to the Ubuntu
and OpenPGP servers. The Ubuntu endpoint had not propagated the key during the immediate SEC5
check. `keys.openpgp.org` returned the key material but, as expected for an unverified no-email UID,
stripped the UID. SEC5 verified that returned object directly and matched the complete 40-character
fingerprint; a short key ID is never sufficient:

```bash
curl --fail --output /tmp/postgres-bulk-release-public-key.asc \
  https://keys.openpgp.org/vks/v1/by-fingerprint/11545CD242C9575DF408AC08F83D364143C798A3
gpg --batch --with-colons --show-keys /tmp/postgres-bulk-release-public-key.asc
```

The repository export is independently inspectable with `gpg --show-keys --with-fingerprint`.
Keyserver or repository availability is not identity proof by itself; the approved fingerprint in
`config/security/release-signing-policy.json` is the fail-closed trust anchor.

## Exact release inventory

Central requires every deployed file to have a detached ASCII-armored `.asc` signature. For this
reactor that means:

| Class | Files | Signatures |
| --- | ---: | ---: |
| Parent support POM | 1 | 1 |
| Nine module POMs | 9 | 9 |
| Nine binary JARs | 9 | 9 |
| Nine sources JARs | 9 | 9 |
| Nine Javadoc JARs | 9 | 9 |
| Nine attached CycloneDX JSON SBOMs | 9 | 9 |
| **Central bundle total** | **46** | **46** |
| Aggregate SBOM, `release-inventory.json`, `SHA256SUMS` | 3 evidence files | 3 |

The aggregate SBOM is retained evidence and is not a Central artifact. Benchmarks and the JPA/JDBC
examples are consumers only and are forbidden from both inventories. Signatures and checksum
files do not themselves receive checksum/signature companions, matching Central's requirements.

`release-inventory.json` binds each coordinate, relative filename, type/classifier, SHA-256,
detached signature and signature SHA-256, SBOM relation, publishability role, version, planned
`v0.1.0` tag and exact source commit. The candidate inventory recorded `tag_created=false` because
it was generated before the independently authorized tag operation. `SHA256SUMS` covers the 46
unsigned payload bytes plus aggregate SBOM and inventory; the manifest, checksum list and aggregate
are then signed. Generated evidence stays under `target/signed-release-candidate/` and is not
committed.

## Maven and local ceremony

The pinned Apache Maven GPG Plugin `3.2.8` lives in `local-signing`, separate from both normal
`release` packaging and `central-publish`. It uses GnuPG, the full approved fingerprint,
`bestPractices=true`, SHA-512 and `gpg-agent`. No Maven configuration stores a passphrase. REL2
created the Central bundle from the already verified signed staging tree without rebuilding or
changing any payload. The upload used the Publisher API with `USER_MANAGED` publication so upload
and publication remained separate owner-authorized operations.

Run the real local ceremony only on the owner-controlled signing workstation:

```bash
./scripts/test-release-signatures.py
./scripts/signed-release-dry-run.sh 0.1.0 \
  /path/to/dedicated/release-gnupg
```

The script fails unless the worktree is clean, fetch succeeds, `HEAD == origin/main`, stable version
is exactly reviewed and the approved secret key is present. It builds unsigned and signed staging
independently, compares SHA-256 for all 46 payload files, signs three supplemental evidence files,
and invokes the verifier. Signing may add only `.asc`; it may not change a POM, JAR or SBOM byte.

The separate Central credential boundary became ready on 2026-08-26. The owner confirmed the
`io.github.yravelo` namespace and configured exactly one `central` server in owner-local Maven
settings with mode `0600`. A no-upload Publisher API status probe returned `401` without credentials
and a non-authentication `404` for an intentionally nonexistent deployment when authenticated,
proving token acceptance without creating or mutating a deployment. No credential value,
identifier or Authorization header was printed, logged or versioned; `EP-03` is `PASS`.

The gate verifies exact inventory, existence, cryptographic validity, full approved fingerprint,
RSA signature algorithm, SHA-256-or-stronger digest, declared hashes and checksum completeness.
Regression fixtures cover valid, missing signature, wrong signer, tampered content, wrong checksum,
unexpected artifact, SNAPSHOT and benchmark leakage. Fixture private keys are generated ephemerally
under a temporary directory and removed; no fixture or real private key is tracked.

## REL2 publication record

Signed annotated tag `v0.1.0` points to source commit
`9d05829ae66e54be82b33728bd6f56f8318f4b7a`; its tag object is
`b77fb1bda8de5ecd73da51f0ef7b7b05ff6d86a8`. Independent verification against the tracked public
key passed with the full approved fingerprint, RSA and SHA-512. GitHub displays `unknown_key`
because the release key is not registered in the account; that UI result does not replace the
independent cryptographic verification and no account key was added without separate authority.

The exact Central bundle contained 184 files: 46 payloads, 46 detached signatures and 92 mandatory
MD5/SHA-1 checksum companions. Its SHA-256 is
`8da79fe7ed9eeec0728fc25cd90585dbae03a6f88b7cd0af804ea54ab58ceb30`. Deployment
`04f5f426-9074-4077-87b2-ff838b57638a` reached `VALIDATED` with zero validation errors before the
separate publication authorization and then reached terminal state `PUBLISHED`. All 184 public
files were fetched anonymously from Maven Central and matched the authorized bundle byte-for-byte;
clean JPA and JDBC consumers resolved all ten coordinates and passed. No GitHub Release, repository
secret, visibility change or REL3 action was performed. The complete evidence is in the
[REL2 publication report](../releases/rel2-maven-central-0.1.0-publication.md).

## CI and provenance decision

The Release workflow is candidate-only and secret-free. It can validate source, dependencies,
SAST, SBOMs, docs, reproducibility, consumers and auditor fixtures, but cannot sign or upload.
The former `central-upload` job and four repository-secret references were removed. This avoids
placing a long-lived release key on a persistent Docker-capable self-hosted runner or exposing it to
the complete Maven build graph on a remote ephemeral runner. No Actions signing secret exists.

GitHub artifact attestations are not enabled and no SLSA level is claimed; a commit field in a
custom JSON manifest is useful provenance but is not SLSA provenance. Sigstore is also not enabled
because it would add a second identity and transparency-log model without replacing Central's
OpenPGP rule.

The minimum provenance baseline is therefore: exact clean source commit, reviewed version and
planned tag, immutable dependency/action versions, reproducible payload comparison, per-artifact
CycloneDX evidence, SHA-256 inventory, approved OpenPGP fingerprint, verified detached signatures,
auditor results and remote Build/Compatibility run IDs. Reassess native attestations and Sigstore
if repository visibility or plan changes; do not backfill a SLSA claim from this baseline.

## Compromise, rotation and workstation policy

If private-key exposure is suspected: stop signing and all release activity; preserve non-secret
evidence; revoke the key using the offline certificate; publish the revocation to every used
keyserver; update this policy with a new fingerprint; rotate any related Portal token; audit Git,
runner and workstation; rebuild from a clean approved commit; and communicate the affected release
range before resuming. Never delete evidence or rewrite shared history before revocation/triage.

The executable containment, passphrase-only distinction, historical-artifact treatment and exit
criteria are in the SEC6
[incident response runbook](incident-response-runbook.md#openpgp-private-key-compromise).

Rotate before expiry, on loss of exclusive control, workstation compromise, passphrase exposure,
maintainer/identity change or algorithm-policy change. Overlap public keys only long enough to sign
the transition statement. Historical fingerprints remain documented as revoked/retired and are
never silently replaced. A key cannot be allow-listed around the verifier.

The signing workstation must be owner-controlled, patched, encrypted at rest and free of
unreviewed build hooks. Sign from a fresh clone/clean synchronized commit, with network use limited
to dependency fetch, Git fetch and public-key distribution. Never sign on the persistent CI runner,
inside a general-purpose container, over an untrusted remote shell or while shell tracing is on.

## Authoritative references

- [Central publication requirements](https://central.sonatype.org/publish/requirements/)
- [Central OpenPGP guidance and supported keyservers](https://central.sonatype.org/publish/requirements/gpg/)
- [Apache Maven GPG Plugin](https://maven.apache.org/plugins/maven-gpg-plugin/)
- [GitHub artifact-attestation availability](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations)
