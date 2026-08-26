# REL2 — Maven Central 0.1.0 publication

Date: 2026-08-26. This is the sanitized evidence of record for the first stable publication. It
contains no token, passphrase, private-key material, recovery location or authorization header.

## 1. REL2 result

**COMPLETE.** Release `0.1.0` is public in Maven Central and verified independently.

## 2. Initial public state

Before upload, the ten planned `io.github.yravelo:*:0.1.0` POM URLs returned HTTP 404. The source
repository was public, but Maven Central contained no supported project release.

## 3. Current HEAD

The release source was frozen at `9d05829ae66e54be82b33728bd6f56f8318f4b7a`. Documentation-only
closure commits after publication do not change the immutable tag or published payloads.

## 4. EP-02 initial and final state

EP-02 began pending and ended **PASS** after the owner's sanitized confirmation of a successful
offline recovery exercise. Commit `253fb61` records only the outcome.

## 5. Fingerprint and recovery

The restored signing identity matched full fingerprint
`11545CD242C9575DF408AC08F83D364143C798A3`; a test signature passed and transient recovery material
was removed. Secret values and storage locations were never recorded.

## 6. EP-03 initial and final state

EP-03 began pending and ended **PASS** after exactly one owner-local Maven `central` server was
configured in a mode-`0600` settings file. Commit `9d05829` records the sanitized result.

## 7. Authentication evidence

An unauthenticated Publisher API probe returned 401. The same intentionally nonexistent deployment
returned a non-authentication 404 when authenticated, proving token acceptance without upload or
mutation and without exposing credentials.

## 8. Tooling and policy

Signing remained local and offline-key-bound; remote workflows remained secret-free. Upload used
the Central Publisher API with `publishingType=USER_MANAGED`, preserving the separate upload and
publication gates.

## 9. Frozen source SHA

The only authorized source SHA was `9d05829ae66e54be82b33728bd6f56f8318f4b7a`, synchronized with
`origin/main` before candidate generation.

## 10. Candidate result

The stable `0.1.0` candidate passed clean-tree, exact-version, inventory, dependency, SAST, SBOM,
documentation, reproducibility, consumer and signature-fixture gates.

## 11. Build

Local full-reactor verification passed. Remote Build run `32948936688` passed on the frozen SHA.

## 12. Compatibility

Remote Compatibility run `32948936597` passed all 11 supported matrix combinations.

## 13. Security

Remote Security run `32950077719` passed the full security release preflight on the frozen SHA.

## 14. CodeQL

Remote CodeQL run `32948936717` passed and the repository had zero open CodeQL alerts.

## 15. Gitleaks

Current-tree and full-history Gitleaks checks passed. Remote logs were also scanned without privacy
or secret findings.

## 16. Reproducibility

Two clean builds produced byte-identical primary artifacts; generated SBOMs were semantically
equivalent.

## 17. Artifact inventory

The Central inventory contains 46 payloads: one parent POM plus nine module POMs, binary JARs,
sources JARs, Javadoc JARs and attached CycloneDX JSON SBOMs.

## 18. Publishable modules

The ten coordinates are `postgres-bulk-parent`, `postgres-bulk-core`, `postgres-bulk-pgjdbc`,
`postgres-bulk-hibernate`, `postgres-bulk-spring-data`, `postgres-bulk-spring-data-jdbc`,
`postgres-bulk-spring-boot-autoconfigure`, `postgres-bulk-spring-boot-autoconfigure-jdbc`,
`postgres-bulk-spring-boot-starter` and `postgres-bulk-spring-boot-starter-data-jdbc`, all under
`io.github.yravelo:0.1.0`.

## 19. SBOM and license result

Nine attached module SBOMs plus one aggregate SBOM passed. The aggregate described 55 external
production components with zero unknown licenses and zero policy blockers.

## 20. OSV result

OSV evaluated 138 dependencies: 133 passed, five matched explicitly accepted warnings and zero
were blocking.

## 21. Static-analysis result

The Java static-analysis gate completed with zero findings.

## 22. Signed candidate

The candidate contains 46 Central payload signatures plus three signed evidence files. Signing
added detached `.asc` files only and did not alter any payload byte.

## 23. Signature verification

All 49 detached signatures passed in an ephemeral verifier keyring against full fingerprint
`11545CD242C9575DF408AC08F83D364143C798A3`; all 46 signed payloads matched unsigned staging.

## 24. Checksums

`SHA256SUMS` has SHA-256 `6acae6c25d47feb764589b63987cdef1a18b64cc7de408f260936598c392c4e2`;
the inventory is `f08d164ee50643c48dd2a23e8d1e19904a6812513699de6375c78566f8104053`; the
aggregate SBOM is `227c9dff1e6c9566bb1bf70692adc50ab20a4181b4366ecedc70055a46d27dc4`.

## 25. Tag authorization

The owner supplied `AUTHORIZE_V0_1_0_SIGNED_TAG_CREATION`. That gate applied only to creating and
pushing `v0.1.0` over the frozen SHA.

## 26. Tag creation and verification

Annotated tag object `b77fb1bda8de5ecd73da51f0ef7b7b05ff6d86a8` resolves exactly to the frozen
SHA. Its RSA/SHA-512 OpenPGP signature and release UID passed independent verification. GitHub's
`unknown_key` badge reflects that the public key was not added to the GitHub account.

## 27. Upload authorization

The owner supplied `AUTHORIZE_MAVEN_CENTRAL_UPLOAD`. It authorized one exact bundle upload and did
not authorize publication.

## 28. Upload and deployment ID

The uploaded 1,238,558-byte bundle has SHA-256
`8da79fe7ed9eeec0728fc25cd90585dbae03a6f88b7cd0af804ea54ab58ceb30`. Central accepted it once as
deployment `04f5f426-9074-4077-87b2-ff838b57638a`.

## 29. Central validation

The deployment reached `VALIDATED` before publication, with zero validation errors and the expected
ten coordinates.

## 30. Automatic publication

Automatic publication was disabled: the deployment used `USER_MANAGED`. No upload request could
consume the publication gate implicitly.

## 31. Publication authorization

The owner separately supplied `AUTHORIZE_MAVEN_CENTRAL_PUBLICATION` after validation completed.

## 32. Publication result

The authorized publication request returned HTTP 204 and deployment
`04f5f426-9074-4077-87b2-ff838b57638a` reached terminal state `PUBLISHED` on 2026-08-26.

## 33. Public coordinates

All ten POM URLs became anonymously available from Maven Central with HTTP 200. The published group,
artifact IDs and version match the authorized inventory exactly.

## 34. External resolution

A fresh isolated Maven repository resolved all ten coordinates from Maven Central. No local project
artifact or `mavenLocal` repository participated.

## 35. JPA consumer

The standalone JPA example resolved stable `0.1.0`, built and passed its tests from Central.

## 36. JDBC consumer

The standalone Spring Data JDBC example resolved stable `0.1.0`, built and passed both tests from
Central.

## 37. GitHub Release

**NOT CREATED.** Repository governance requires separate explicit authorization for a GitHub
Release, and REL2 received no such authorization. The signed Git tag is public independently.

## 38. README and documentation

README installation examples now use stable `0.1.0` and `mavenCentral()`. The changelog, acceptance
criteria, roadmap, readiness and signing/provenance records describe the completed publication.

## 39. Final CI

The release-source Build, Compatibility, Security and CodeQL gates are all **PASS**. The
documentation closure is revalidated locally and through normal hosted CI after commit.

## 40. Commits and final repository state

The release source remains immutable at the signed tag. EP-02 and EP-03 use sanitized coherent
commits; publication documentation is committed separately on `main`, with no generated candidate
or credential material tracked.

## 41. Definition of Done

EP-02/EP-03, exact-SHA freeze, candidate, signatures, tag, upload, validation, manual publication,
public byte comparison, isolated coordinate resolution, JPA/JDBC consumers and documentation are
all **PASS**.

## 42. Verdict

**REL2 COMPLETE — Maven Central 0.1.0 publication PASS.** No immutable component requires repair or
replacement.

## 43. REL3 readiness

REL3 entry criteria are satisfied, but REL3 was not started automatically and no REL3 action is
authorized by this report.

## 44. Remote-actions boundary

| Action | Result |
| --- | --- |
| Tag created | YES |
| Tag pushed | YES |
| Central upload | YES |
| Central publication | YES |
| GitHub Release created | NO |
| Repository visibility changed | NO |
| Repository secrets created | NO |
| REL3 started | NO |
