#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(cd -- "${SCRIPT_DIR}/.." && pwd)
MAVEN_PROJECT=${REPOSITORY_ROOT}/code/postgres-bulk-parent
MAVEN=${MAVEN_PROJECT}/mvnw
POLICY=${REPOSITORY_ROOT}/config/security/sbom-policy.json
VERSION=${1:-0.1.0}
OUTPUT_DIRECTORY=${2:-${REPOSITORY_ROOT}/target/sbom/${VERSION}}

if [[ ! "${VERSION}" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo "A stable non-SNAPSHOT SemVer is required, got: ${VERSION}" >&2
  exit 1
fi
if [[ -e "${OUTPUT_DIRECTORY}" ]]; then
  echo "Refusing to reuse SBOM output directory: ${OUTPUT_DIRECTORY}" >&2
  exit 1
fi

mapfile -t PUBLISHABLE_MODULES < <(
  python3 - "${POLICY}" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    policy = json.load(stream)
for artifact in policy["publishable_artifacts"]:
    print(artifact)
PY
)
if [[ "${#PUBLISHABLE_MODULES[@]}" -ne 9 ]]; then
  echo "SBOM policy must define exactly nine publishable artifacts." >&2
  exit 1
fi
MODULE_LIST=$(IFS=,; echo "${PUBLISHABLE_MODULES[*]}")
SELECTED_PROJECTS=":postgres-bulk-parent,${MODULE_LIST}"
mkdir -p "${OUTPUT_DIRECTORY}/dependency-trees"
python3 "${SCRIPT_DIR}/test-sbom-auditor.py"

(
  cd "${MAVEN_PROJECT}"
  "${MAVEN}" --batch-mode --no-transfer-progress \
    -Prelease \
    -Drevision="${VERSION}" \
    -DskipTests \
    -Dspotbugs.skip=true \
    -pl "${MODULE_LIST}" -am \
    clean install
)

for module in "${PUBLISHABLE_MODULES[@]}"; do
  (
    cd "${MAVEN_PROJECT}"
    "${MAVEN}" --batch-mode --no-transfer-progress \
      -Drevision="${VERSION}" \
      -f "${module}/pom.xml" \
      org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree \
      -Dscope=runtime \
      -DoutputType=json \
      -DoutputFile=target/sbom-dependency-tree.json
  )
done

(
  cd "${MAVEN_PROJECT}"
  "${MAVEN}" --batch-mode --no-transfer-progress \
    -Drevision="${VERSION}" \
    -DskipTests \
    -Dspotbugs.skip=true \
    -Dcyclonedx.skip=false \
    -Dcyclonedx.skipAttach=true \
    -DoutputReactorProjects=false \
    -pl "${SELECTED_PROJECTS}" \
    package \
    org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom
)

for module in "${PUBLISHABLE_MODULES[@]}"; do
  bom=${MAVEN_PROJECT}/${module}/target/${module}-${VERSION}.cdx.json
  tree=${MAVEN_PROJECT}/${module}/target/sbom-dependency-tree.json
  if [[ ! -f "${bom}" || ! -f "${tree}" ]]; then
    echo "Missing generated SBOM or Maven tree for ${module}." >&2
    exit 1
  fi
  cp "${bom}" "${OUTPUT_DIRECTORY}/${module}-${VERSION}.cdx.json"
  cp "${tree}" "${OUTPUT_DIRECTORY}/dependency-trees/${module}-${VERSION}.json"
done

aggregate=${MAVEN_PROJECT}/target/postgres-bulk-parent-${VERSION}.cdx.json
if [[ ! -f "${aggregate}" ]]; then
  echo "Missing aggregate SBOM: ${aggregate}" >&2
  exit 1
fi
cp "${aggregate}" "${OUTPUT_DIRECTORY}/postgres-bulk-${VERSION}-aggregate.cdx.json"

AUDIT_ARGS=(--directory "${OUTPUT_DIRECTORY}" --version "${VERSION}")
OSV_INVENTORY=${REPOSITORY_ROOT}/target/security/dependency-inventory.json
if [[ -f "${OSV_INVENTORY}" ]]; then
  AUDIT_ARGS+=(--osv-inventory "${OSV_INVENTORY}")
elif [[ "${REQUIRE_OSV_INVENTORY:-false}" == "true" ]]; then
  echo "Required OSV dependency inventory is missing: ${OSV_INVENTORY}" >&2
  exit 1
fi
python3 "${SCRIPT_DIR}/check-sbom.py" "${AUDIT_ARGS[@]}"

echo "CycloneDX evidence generation: PASS"
echo "Output: ${OUTPUT_DIRECTORY}"
