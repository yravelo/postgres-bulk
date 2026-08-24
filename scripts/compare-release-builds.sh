#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(cd -- "${SCRIPT_DIR}/.." && pwd)
MAVEN_PROJECT=${REPOSITORY_ROOT}/code/postgres-bulk-parent
MAVEN=${MAVEN_PROJECT}/mvnw
VERSION=${1:-0.1.0}
OUTPUT_ROOT=${REPOSITORY_ROOT}/target/reproducibility
POLICY=${REPOSITORY_ROOT}/config/security/sbom-policy.json
mapfile -t MODULES < <(
  python3 - "${POLICY}" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    policy = json.load(stream)
for artifact in policy["publishable_artifacts"]:
    print(artifact)
PY
)
if [[ "${#MODULES[@]}" -ne 9 ]]; then
  echo "Reproducibility inventory must contain exactly nine publishable artifacts." >&2
  exit 1
fi

if [[ -e "${OUTPUT_ROOT}" ]]; then
  echo "Refusing to reuse generated directory: ${OUTPUT_ROOT}" >&2
  exit 1
fi
mkdir -p "${OUTPUT_ROOT}/build-1" "${OUTPUT_ROOT}/build-2"

build_and_capture() {
  local destination=$1
  (
    cd "${MAVEN_PROJECT}"
    "${MAVEN}" --batch-mode --no-transfer-progress \
      -Prelease -Drevision="${VERSION}" clean package
  )
  mkdir -p "${destination}/postgres-bulk-parent"
  cp "${MAVEN_PROJECT}/.flattened-pom.xml" \
    "${destination}/postgres-bulk-parent/postgres-bulk-parent-${VERSION}.pom"
  for module in "${MODULES[@]}"; do
    mkdir -p "${destination}/${module}"
    cp "${MAVEN_PROJECT}/${module}/target/${module}-${VERSION}.jar" "${destination}/${module}/"
    cp "${MAVEN_PROJECT}/${module}/target/${module}-${VERSION}-sources.jar" "${destination}/${module}/"
    cp "${MAVEN_PROJECT}/${module}/target/${module}-${VERSION}-javadoc.jar" "${destination}/${module}/"
    cp "${MAVEN_PROJECT}/${module}/.flattened-pom.xml" \
      "${destination}/${module}/${module}-${VERSION}.pom"
  done
  (
    cd "${destination}"
    find . -type f ! -name SHA256SUMS -print0 \
      | LC_ALL=C sort -z \
      | xargs -0 sha256sum > SHA256SUMS
  )
}

build_and_capture "${OUTPUT_ROOT}/build-1"
build_and_capture "${OUTPUT_ROOT}/build-2"

diff -u "${OUTPUT_ROOT}/build-1/SHA256SUMS" "${OUTPUT_ROOT}/build-2/SHA256SUMS"

"${SCRIPT_DIR}/generate-sbom.sh" "${VERSION}" "${OUTPUT_ROOT}/sbom-1"
"${SCRIPT_DIR}/generate-sbom.sh" "${VERSION}" "${OUTPUT_ROOT}/sbom-2"
SBOM_COMPARE_ARGS=(
  --directory "${OUTPUT_ROOT}/sbom-1"
  --compare "${OUTPUT_ROOT}/sbom-2"
  --version "${VERSION}"
)
OSV_INVENTORY=${REPOSITORY_ROOT}/target/security/dependency-inventory.json
if [[ -f "${OSV_INVENTORY}" ]]; then
  SBOM_COMPARE_ARGS+=(--osv-inventory "${OSV_INVENTORY}")
fi
python3 "${SCRIPT_DIR}/check-sbom.py" "${SBOM_COMPARE_ARGS[@]}"
echo "Release artifact and semantic SBOM reproducibility: PASS"
