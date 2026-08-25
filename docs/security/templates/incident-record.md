# Security incident and post-incident record — TEMPLATE

> Keep the filled private record outside Git until coordinated disclosure. Store live secrets,
> private keys, raw images and unsanitized logs only in restricted evidence storage.

## Identification

- Private incident ID:
- Detected/reported at (UTC):
- Owner: `yravelo`
- Trigger/source:
- Incident class: dependency / build chain / secret / OpenPGP / runner / workstation / repository / artifact
- Severity and disposition:
- Current status:

## Timeline

| UTC time | Actor | Event/action | Evidence reference |
| --- | --- | --- | --- |
| | | | |

## Scope and impact

- First/last known affected commit, run or release:
- Affected assets and trust boundaries:
- Consumer exposure:
- Known/suspected exploitation:
- Confidentiality/integrity/availability impact:
- Credentials/keys potentially exposed:

## Containment

- CI/release/signing stopped:
- Runner/service/registration action:
- Credential/token rotation or revocation:
- Candidate/deployment invalidation:
- Reporter/user coordination:
- Containment exit evidence:

## Evidence inventory

| Evidence | Restricted location | SHA-256 / identifier | Collected by/time | Sanitized public form |
| --- | --- | --- | --- | --- |
| | | | | |

## Root cause and remediation

- Root cause:
- Contributing conditions:
- Trusted recovery point:
- Fix/reprovision/rotation performed:
- Regression/control changes:
- Gates and run IDs:
- Artifact/SBOM/inventory/signature verification:

## Disclosure and closure

- Reporter credit preference/consent:
- Advisory/GHSA/CVE/release identifiers:
- Affected/patched range and upgrade guidance:
- Disclosure time/channel:
- Closure criteria verified by/date:
- Residual risk and owner/review date:

## Post-incident review

- What happened and why:
- What detected it:
- Which controls succeeded or failed:
- Response delays or unsafe assumptions:
- Corrective actions, owners and deadlines:
- Documentation/training changes:
- Sanitized review publication decision:
