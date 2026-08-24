#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(cd -- "${SCRIPT_DIR}/.." && pwd)
VERSION=${1:-0.1.0}
CANDIDATE_ROOT=${2:-${REPOSITORY_ROOT}/target/signed-release-candidate}
GNUPG_HOME=${3:-<LOCAL_SIGNING_PATH>}

exec python3 "${SCRIPT_DIR}/check-release-signatures.py" \
  --staging "${CANDIDATE_ROOT}/staging" \
  --evidence-directory "${CANDIDATE_ROOT}/evidence" \
  --inventory "${CANDIDATE_ROOT}/evidence/release-inventory.json" \
  --checksums "${CANDIDATE_ROOT}/evidence/SHA256SUMS" \
  --gnupghome "${GNUPG_HOME}"
