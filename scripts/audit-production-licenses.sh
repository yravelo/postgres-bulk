#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(cd -- "${SCRIPT_DIR}/.." && pwd)
MAVEN_PROJECT=${REPOSITORY_ROOT}/code/postgres-bulk-parent
MAVEN=${MAVEN_PROJECT}/mvnw
AUDIT_DIRECTORY=${REPOSITORY_ROOT}/target/release-audit
REPORT=${AUDIT_DIRECTORY}/third-party-licenses.txt
VERSION=${1:-0.1.0}
MODULES=(
  postgres-bulk-core
  postgres-bulk-pgjdbc
  postgres-bulk-hibernate
  postgres-bulk-spring-data-jdbc
  postgres-bulk-spring-data
  postgres-bulk-spring-boot-autoconfigure
  postgres-bulk-spring-boot-starter
  postgres-bulk-spring-boot-autoconfigure-jdbc
  postgres-bulk-spring-boot-starter-data-jdbc
)

mkdir -p "${AUDIT_DIRECTORY}"
: > "${REPORT}"

for module in "${MODULES[@]}"; do
  (
    cd "${MAVEN_PROJECT}"
    "${MAVEN}" --batch-mode --no-transfer-progress \
      -Drevision="${VERSION}" \
      -Dlicense.includedScopes=compile,runtime \
      -Dlicense.failOnMissing=true \
      -pl "${module}" \
      org.codehaus.mojo:license-maven-plugin:2.7.1:add-third-party
  )
  module_report=${MAVEN_PROJECT}/${module}/target/generated-sources/license/THIRD-PARTY.txt
  if [[ ! -f "${module_report}" ]]; then
    echo "License report was not generated for ${module}." >&2
    exit 1
  fi
  {
    echo "## ${module}"
    cat "${module_report}"
    echo
  } >> "${REPORT}"
done

if rg -ni 'unknown license|missing license|no license' "${REPORT}"; then
  echo "Incomplete license metadata found; inspect ${REPORT}." >&2
  exit 1
fi

echo "Production dependency license audit: PASS"
echo "Report: ${REPORT}"
