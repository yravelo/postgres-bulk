#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(cd -- "${SCRIPT_DIR}/.." && pwd)
MAVEN_PROJECT=${REPOSITORY_ROOT}/code/postgres-bulk-parent
MAVEN=${MAVEN_PROJECT}/mvnw
POLICY=${REPOSITORY_ROOT}/config/security/release-signing-policy.json
VERSION=${1:-0.1.0}
GNUPG_HOME=${2:-<LOCAL_SIGNING_PATH>}
CANDIDATE_ROOT=${REPOSITORY_ROOT}/target/signed-release-candidate
UNSIGNED_STAGING=${CANDIDATE_ROOT}/unsigned-staging
SIGNED_STAGING=${CANDIDATE_ROOT}/staging
EVIDENCE=${CANDIDATE_ROOT}/evidence

if [[ ! "${VERSION}" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo "A stable SemVer release version is required, got: ${VERSION}" >&2
  exit 1
fi
if [[ -n "$(git -C "${REPOSITORY_ROOT}" status --short)" ]]; then
  echo "A real signed candidate requires a clean worktree." >&2
  exit 1
fi
git -C "${REPOSITORY_ROOT}" fetch --quiet origin main
HEAD_COMMIT=$(git -C "${REPOSITORY_ROOT}" rev-parse HEAD)
REMOTE_COMMIT=$(git -C "${REPOSITORY_ROOT}" rev-parse origin/main)
if [[ "${HEAD_COMMIT}" != "${REMOTE_COMMIT}" ]]; then
  echo "A real signed candidate requires HEAD == origin/main." >&2
  exit 1
fi
if [[ -e "${CANDIDATE_ROOT}" ]]; then
  echo "Refusing to reuse signed candidate directory: ${CANDIDATE_ROOT}" >&2
  exit 1
fi

FINGERPRINT=$(python3 - "${POLICY}" <<'PY'
import json
import sys
print(json.load(open(sys.argv[1], encoding="utf-8"))["approved_signer_fingerprint"])
PY
)
if [[ ! -d "${GNUPG_HOME}" ]]; then
  echo "Dedicated release GNUPGHOME does not exist: ${GNUPG_HOME}" >&2
  exit 1
fi
AVAILABLE_FINGERPRINT=$(GNUPGHOME="${GNUPG_HOME}" gpg --batch --with-colons --list-secret-keys \
  "${FINGERPRINT}" | sed -n 's/^fpr:::::::::\([^:]*\):$/\1/p' | head -n 1)
if [[ "${AVAILABLE_FINGERPRINT}" != "${FINGERPRINT}" ]]; then
  echo "The approved release secret key is not available in the dedicated GNUPGHOME." >&2
  exit 1
fi

mkdir -p "${UNSIGNED_STAGING}" "${SIGNED_STAGING}" "${EVIDENCE}"

(
  cd "${MAVEN_PROJECT}"
  "${MAVEN}" --batch-mode --no-transfer-progress \
    -Prelease \
    -Drevision="${VERSION}" \
    -DaltDeploymentRepository="unsigned-staging::file://${UNSIGNED_STAGING}" \
    clean deploy
)

# Prime gpg-agent through pinentry; no passphrase enters argv, environment, Maven settings, or logs.
printf 'postgres-bulk signed release dry-run %s\n' "${HEAD_COMMIT}" > "${EVIDENCE}/signing-agent-prime.txt"
GNUPGHOME="${GNUPG_HOME}" gpg --batch --yes --armor --detach-sign \
  --local-user "${FINGERPRINT}" --digest-algo SHA512 \
  --output "${EVIDENCE}/signing-agent-prime.txt.asc" "${EVIDENCE}/signing-agent-prime.txt"

(
  cd "${MAVEN_PROJECT}"
  GNUPGHOME="${GNUPG_HOME}" "${MAVEN}" --batch-mode --no-transfer-progress \
    -Prelease,local-signing \
    -Drevision="${VERSION}" \
    -Dgpg.keyname="${FINGERPRINT}" \
    -DaltDeploymentRepository="signed-staging::file://${SIGNED_STAGING}" \
    clean deploy
)

python3 "${SCRIPT_DIR}/compare-release-signing.py" \
  --unsigned "${UNSIGNED_STAGING}" --signed "${SIGNED_STAGING}" --version "${VERSION}"

"${SCRIPT_DIR}/generate-sbom.sh" "${VERSION}" "${EVIDENCE}/sbom-work"
cp "${EVIDENCE}/sbom-work/postgres-bulk-${VERSION}-aggregate.cdx.json" \
  "${EVIDENCE}/postgres-bulk-${VERSION}-aggregate.cdx.json"

python3 "${SCRIPT_DIR}/generate-release-inventory.py" \
  --staging "${SIGNED_STAGING}" \
  --version "${VERSION}" \
  --source-commit "${HEAD_COMMIT}" \
  --output "${EVIDENCE}/release-inventory.json" \
  --require-signatures \
  --aggregate-sbom "${EVIDENCE}/postgres-bulk-${VERSION}-aggregate.cdx.json"

python3 - "${CANDIDATE_ROOT}" "${SIGNED_STAGING}" "${EVIDENCE}" <<'PY'
import hashlib
import json
import sys
from pathlib import Path

root, staging, evidence = map(Path, sys.argv[1:])
inventory = json.loads((evidence / "release-inventory.json").read_text(encoding="utf-8"))
files = [staging / item["filename"] for item in inventory["artifacts"]]
files.extend(
    [
        evidence / "release-inventory.json",
        next(evidence.glob("postgres-bulk-*-aggregate.cdx.json")),
    ]
)
with (evidence / "SHA256SUMS").open("w", encoding="utf-8") as stream:
    for path in sorted(files):
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        stream.write(f"{digest}  {path.relative_to(root).as_posix()}\n")
PY

for evidence_file in \
  "${EVIDENCE}/postgres-bulk-${VERSION}-aggregate.cdx.json" \
  "${EVIDENCE}/release-inventory.json" \
  "${EVIDENCE}/SHA256SUMS"; do
  GNUPGHOME="${GNUPG_HOME}" gpg --batch --yes --armor --detach-sign \
    --local-user "${FINGERPRINT}" --digest-algo SHA512 \
    --output "${evidence_file}.asc" "${evidence_file}"
done

rm -f "${EVIDENCE}/signing-agent-prime.txt" "${EVIDENCE}/signing-agent-prime.txt.asc"
"${SCRIPT_DIR}/check-release-signatures.sh" "${VERSION}" "${CANDIDATE_ROOT}" "${GNUPG_HOME}"
echo "Signed release dry-run: PASS (no tag, upload, or publication)"
