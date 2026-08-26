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

**Status:** `COMPLETE` on 2026-08-26.

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
MIG5 completed decommission/readiness mode before deletion: source, documentation, CI/security,
audit and REL2 were proven independent of the old private archive; its metadata was safe to lose
and its runner had a bounded decommission plan. At that checkpoint the archive remained private
and undeleted. See the
[MIG5 report](../releases/rel1-mig5-old-private-archive-decommission-readiness.md). MIG5B consumed the
separate deletion authorization, removed the archive runner registration, stopped/disabled its
service, deleted only the old private repository, removed the obsolete local remote and revalidated
the public repository. See the
[MIG5B closure](../releases/rel1-mig5b-archive-deletion-and-rel1-closure.md). Clean repository
migration and REL1 are complete.

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

REL0 remains `DONE` and REL1 is `COMPLETE`; clean repository migration is `COMPLETE` and the old
private archive is `DELETED`. The canonical final-name repository remains public and healthy with
**OPEN-SOURCE ACTIVATION COMPLETE**. EP-02 offline recovery remains `PENDING` and EP-03 remains
`PENDING` with its Portal token `MISSING`, so REL2 is `NOT STARTED`. The next phase is
`REL2 — Maven Central 0.1.0 Publication`, but this handoff authorizes no key operation, token, tag,
GitHub Release, Central upload or Maven publication.
