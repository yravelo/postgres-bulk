#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(cd -- "${SCRIPT_DIR}/.." && pwd)
VERSION=${1:-0.1.0}
if [[ $# -ge 2 ]]; then
  SBOM_DIRECTORY=$2
elif [[ -f "${REPOSITORY_ROOT}/target/release-audit/sbom/postgres-bulk-${VERSION}-aggregate.cdx.json" ]]; then
  SBOM_DIRECTORY=${REPOSITORY_ROOT}/target/release-audit/sbom
else
  SBOM_DIRECTORY=${REPOSITORY_ROOT}/target/sbom/${VERSION}
fi
OSV_INVENTORY=${REPOSITORY_ROOT}/target/security/dependency-inventory.json

if [[ ! -f "${OSV_INVENTORY}" ]]; then
  echo "Required OSV dependency inventory is missing: ${OSV_INVENTORY}" >&2
  exit 1
fi

python3 "${SCRIPT_DIR}/check-sbom.py" \
  --directory "${SBOM_DIRECTORY}" \
  --version "${VERSION}" \
  --osv-inventory "${OSV_INVENTORY}"

echo "Canonical production dependency license audit: PASS"
