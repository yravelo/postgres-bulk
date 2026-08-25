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

**Status:** `NOT STARTED`.

REL1-MIG0 completed its read-only feasibility analysis on 2026-08-26 and recommends replacing the
current private repository with a clean private repository at the same final URL. MIG1 has not
started: rename and new-repository creation each require the explicit owner authorizations named in
the [MIG0 report](../releases/rel1-mig0-clean-repository-migration-feasibility.md). Archive deletion
is a much later, separately gated operation.

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

REL0 remains `DONE`. EP-01 is `PASS`, so the readiness decision is **READY FOR REL1**. REL1-MIG0 is
`DONE` in read-only/plan-only mode with `MIGRATION RECOMMENDED`; MIG1 and REL1-B are not started.
EP-02 and EP-03 remain `PENDING` REL2 prerequisites. No downstream phase is active.
