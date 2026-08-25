# Security policy

## Supported versions

PostgreSQL Bulk has not published a supported release. `main` and `0.1.0-SNAPSHOT` are development
states and receive no public support commitment.

After the first release, this single-maintainer project will support only the newest patch in the
current `0.1.x` line. Publishing a replacement patch ends support for older `0.1.x` patches unless
an advisory explicitly states a transition period. Multiple maintenance branches are not promised.

| Version | Supported |
| --- | --- |
| `main` / snapshots | No — development only |
| `0.1.x` | Not published; newest patch only after activation |
| older patch lines | No, unless an advisory explicitly says otherwise |

## Reporting a vulnerability

Report vulnerabilities privately to **postgresbulk-security@proton.me**. This Proton Mail channel
is controlled exclusively by the project owner and was verified on 2026-08-25: MFA and recovery
are configured, external delivery passed, and a reply from the security account was received by
the external tester. Only this sanitized result is public; authentication and message metadata are
not retained in the repository.

Do not send vulnerability details to the Git commit email, open a GitHub Issue, start a public
Discussion, or post them in another public forum. Use the address above for initial intake. If a
report requires live secrets, private data or other unusually sensitive evidence, first send a
minimal description and agree on a safer transfer method.

When the repository becomes public, REL1 must reevaluate and preferably enable GitHub Private
Vulnerability Reporting, test it from an independent account and retain this verified mailbox as a
fallback. PVR is not enabled while the repository remains private.

## What a private report should include

When reporting, include only the information needed to reproduce and assess the report:

- affected released version or exact commit;
- affected module and Maven coordinate;
- expected security impact and affected confidentiality, integrity or availability;
- minimal reproduction and required preconditions, with sensitive values removed;
- whether exploitation is known or suspected;
- a proposed mitigation or fix, if available;
- safe contact method, disclosure preference and optional credit preference.

Do not send live credentials, private keys, personal data, production database contents or a
weaponized public proof of concept. Agree a safer evidence-transfer method first when those would
otherwise be necessary.

## Response and coordinated disclosure

The owner is the current triage and response owner. Receipt will be acknowledged as soon as
practical; no 24/7 service or fixed response/remediation deadline is promised. Reports move through
private validation, impact classification, remediation and disclosure coordination. Duplicate or
non-applicable reports are closed privately with a reason when possible.

Reporter and owner coordinate disclosure timing based on impact, exploitation, fix readiness and
availability of a patched release. There is no fixed embargo. Reporter credit is optional and is
published only with consent. A GitHub Security Advisory and CVE are considered for a real security
issue affecting a published release and external consumers; an unpublished pre-release defect does
not receive an artificial CVE.

The full process and state model are in
[Vulnerability response and repository governance](docs/security/vulnerability-response-and-governance.md).
Containment procedures are in the
[Incident response runbook](docs/security/incident-response-runbook.md).

## Before the first release

A vulnerability found before publication stops the candidate. The owner fixes or mitigates it,
invalidates previous candidate evidence and reruns every affected security, compatibility,
inventory, reproducibility and signing gate. SBOMs, checksums, release inventory and signatures
are regenerated from the new clean source commit. No previous candidate is reused.

## Security update verification

After publication, a security fix normally produces a new `0.1.x` patch. Consumers should verify:

1. the exact Maven group `io.github.yravelo` and expected `postgres-bulk-*` artifact;
2. the release tag and source commit stated by the advisory;
3. the artifact checksum and detached OpenPGP signature;
4. signer fingerprint `11545CD242C9575DF408AC08F83D364143C798A3` against the tracked public
   key and current signing policy;
5. the affected and patched version ranges in the advisory.

The public key is
[`docs/security/keys/postgres-bulk-release-11545CD242C9575DF408AC08F83D364143C798A3.asc`](docs/security/keys/postgres-bulk-release-11545CD242C9575DF408AC08F83D364143C798A3.asc).
Never trust a short key ID or a signature whose fingerprint differs from the current reviewed
policy. Detailed commands are in the governance document.

## Official distribution identity

Before publication, the only official project repository is
`https://github.com/yravelo/postgres-bulk`; it is private and Maven Central distribution is not
active. After separately authorized publication, the official Maven group is
`io.github.yravelo`. Treat look-alike repositories, Maven groups or unsigned release files as
suspect. Preserve URLs and hashes without executing the artifact, then use the configured private
channel and the relevant GitHub or Maven Central abuse process.

## Credential or signing-material exposure

Stop the affected workflow, signing or release activity before cleanup. Revoke or rotate the
affected credential, preserve sanitized evidence, and do not rely on log masking or history
rewrites. Passphrase-only exposure is assessed separately from private-key exposure; if private key
control may also be lost, revoke the key and publish the revocation. See the incident runbook for
GitHub, Central, OpenPGP, runner, workstation, repository and artifact procedures.
