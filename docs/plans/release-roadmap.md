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
- EP-01 remains visibly fail-closed if not configured;
- no visibility change, tag, Release, Central upload or publication occurs.

Evidence: [REL0 final release-readiness audit](../releases/rel0-final-release-readiness.md).
Remote closure: Build `32850719665` PASS, Compatibility `32850719735` 11/11 PASS and Security
`32850787710` PASS for commit `5f63b60e58a3fe23221eb47beef2f38f02cc26de`.

## REL1 — Open Source Repository Activation

**Status:** `NOT STARTED`.

Entry criteria:

- REL0 is `DONE`;
- EP-01 private vulnerability reporting is approved, configured and benign end-to-end tests pass;
- the REL1 preflight passes;
- a fresh full-history secret, privacy and public-metadata audit passes;
- public-PR runner isolation/governance is ready and the repository description/community surface
  is reviewed.

Scope: activate the repository's approved open-source boundary. REL1 does not create `v0.1.0`,
upload to Central or publish a GitHub Release.

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

The technical REL0 decision is **TECHNICALLY READY — REL1 BLOCKED BY EXTERNAL PREREQUISITE**.
EP-01 is the REL1 blocker. EP-02 and EP-03 belong to REL2. No downstream phase is active.
