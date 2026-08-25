#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(cd -- "${SCRIPT_DIR}/.." && pwd)
VERSION=${1:-0.1.0}
CANDIDATE_ROOT=${2:-${REPOSITORY_ROOT}/target/signed-release-candidate}
GNUPG_HOME=${3:-}
EXPECTED_SOURCE_COMMIT=${4:-$(git -C "${REPOSITORY_ROOT}" rev-parse HEAD)}

if [[ -z "${GNUPG_HOME}" ]]; then
  echo "Usage: $0 <version> <candidate-root> <verification-gnupghome> [expected-source-commit]" >&2
  exit 2
fi

exec python3 "${SCRIPT_DIR}/check-release-signatures.py" \
  --staging "${CANDIDATE_ROOT}/staging" \
  --evidence-directory "${CANDIDATE_ROOT}/evidence" \
  --inventory "${CANDIDATE_ROOT}/evidence/release-inventory.json" \
  --checksums "${CANDIDATE_ROOT}/evidence/SHA256SUMS" \
  --gnupghome "${GNUPG_HOME}" \
  --expected-source-commit "${EXPECTED_SOURCE_COMMIT}"
