# Release roadmap

This roadmap separates technical readiness, repository activation, Central publication and
post-publication verification. Completion of one phase recommends the next; it never authorizes or
starts it automatically.

## REL0 — Final Release Readiness

**Status:** `DONE` on 2026-08-25.

Purpose: reconcile the complete private repository, candidate artifacts, security baseline,
documentation, external prerequisites and residual risks into one release decision.

Exit criteria:

- full local build, security, reproducibility, candidate and clean-room checks pass;
- Build is PASS, Compatibility is 11/11 PASS and manually dispatched Security is PASS for the
  closure state;
- the current EP-01 state remains represented accurately and fail-closed if it regresses;
- no visibility change, tag, Release, Central upload or publication occurs.

Evidence: [REL0 final release-readiness audit](../releases/rel0-final-release-readiness.md).
Remote closure: Build `32850719665` PASS, Compatibility `32850719735` 11/11 PASS and Security
`32850787710` PASS for commit `bc288f27a01aa427bb5fd78f38997698b2c4e6d6`.

## REL1 — Open Source Repository Activation

**Status:** `IN PROGRESS — MIG4 DONE`.

REL1-MIG0 completed its read-only feasibility analysis on 2026-08-26 and recommended a clean
replacement. MIG1 and MIG2 are `DONE`: the original repository is retained as private
`yravelo/postgres-bulk-private-archive`, and the distinct private final-name repository contains
only canonical clean `main` plus its approved basic metadata. See the
[MIG2 report](../releases/rel1-mig2-clean-main-push-baseline-recreation.md). MIG3 is `BLOCKED`:
its hosted-only public-trust architecture, settings, security gates and documentation are complete,
but GitHub rejected Build and all 11 Compatibility jobs before their first step because the owner
account's billing/spending gate prevents hosted execution. Security was not dispatched against the
same known gate. See the [MIG3 report](../releases/rel1-mig3-ci-security-public-trust-baseline.md).
MIG3B is `DONE`: it reverified that external billing is the only blocker and completed the
hosted-only trust, local-equivalence, public-content, settings, transaction and containment bridge.
MIG4 is `DONE`: the clean canonical repository is public, hosted Build/Compatibility/Security and
anonymous verification pass, and the open-source activation verdict is `GO`. See the
[MIG3B report](../releases/rel1-mig3b-public-hosted-ci-activation-bridge.md).
The complete activation evidence is in the
[MIG4 report](../releases/rel1-mig4-public-activation-and-external-verification.md).
Archive deletion is a much later, separately gated operation.

Entry criteria:

- REL0 is `DONE`;
- EP-01 private vulnerability reporting is `PASS`: approved, configured and benign end-to-end
  delivery/reply tests passed on 2026-08-25;
- the REL1 preflight passes;
- the public-PR isolation strategy and public-readiness work are documented.

Scope: run the fresh full-history privacy/license audit, implement public/fork PR isolation, review
public metadata and community surfaces, reevaluate/test PVR and only then activate the approved
open-source boundary. REL1 does not create `v0.1.0`, upload to Central or publish a GitHub Release.

## REL2 — Maven Central 0.1.0

**Status:** `NOT STARTED`.

Entry criteria:

- REL1 is complete and the selected source SHA is authorized;
- EP-02 offline OpenPGP recovery verification is `PASS`;
- EP-03 nominal Central token is available through the approved local boundary;
- the real-key signed candidate is reproduced and verified against the approved fingerprint;
- tag creation, Central upload and Portal publication each receive separate authorization.

Scope: bind the authorized source commit to `v0.1.0`, reproduce the signed candidate, upload it,
inspect Central validation and activate publication manually. `autoPublish=false` remains required.

## REL3 — Post-Publication Verification

**Status:** `NOT STARTED`.

Entry criteria: REL2 publication has completed successfully.

Scope: verify public Central resolution, signatures, checksums, POM/SBOM metadata, JPA and JDBC
consumer adoption, public documentation and GitHub release notes; record any incident without
attempting to replace immutable components.

## Current handoff

REL0 remains `DONE`; MIG0, MIG1, MIG2, MIG3B and MIG4 are `DONE`. The clean final-name repository is
public and **OPEN-SOURCE ACTIVATION: GO**. MIG4 resolved the former private hosted-runner billing
gate through public GitHub-hosted execution; MIG3 remains historical evidence of that earlier
blocked state. The old private archive remains the rollback source and was not deleted. REL1-B has
not started. EP-02 and EP-03 remain `PENDING` REL2 prerequisites. The next separately authorized
step is MIG5; this handoff does not authorize a tag, GitHub Release, Central upload, Maven
publication or archive deletion.
