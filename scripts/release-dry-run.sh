#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(cd -- "${SCRIPT_DIR}/.." && pwd)
MAVEN_PROJECT=${REPOSITORY_ROOT}/code/postgres-bulk-parent
MAVEN=${MAVEN_PROJECT}/mvnw
EXAMPLE_POM=${REPOSITORY_ROOT}/examples/spring-boot-basic/pom.xml
VERSION=${1:-0.1.0}
STAGING_DIRECTORY=${REPOSITORY_ROOT}/target/release-staging
CONSUMER_REPOSITORY=${REPOSITORY_ROOT}/target/release-consumer-m2
AUDIT_DIRECTORY=${REPOSITORY_ROOT}/target/release-audit

if [[ ! "${VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?$ ]] \
    || [[ "${VERSION}" == *SNAPSHOT* ]]; then
  echo "A non-SNAPSHOT SemVer release version is required, got: ${VERSION}" >&2
  exit 1
fi

for generated_directory in "${STAGING_DIRECTORY}" "${CONSUMER_REPOSITORY}"; do
  if [[ -e "${generated_directory}" ]]; then
    echo "Refusing to reuse generated directory: ${generated_directory}" >&2
    echo "Remove it explicitly before starting a new dry-run." >&2
    exit 1
  fi
done

mkdir -p "${STAGING_DIRECTORY}" "${CONSUMER_REPOSITORY}" "${AUDIT_DIRECTORY}"

(
  cd "${MAVEN_PROJECT}"
  "${MAVEN}" --batch-mode --no-transfer-progress \
    -Prelease \
    -Drevision="${VERSION}" \
    -DaltDeploymentRepository="local-staging::file://${STAGING_DIRECTORY}" \
    clean deploy
)

"${SCRIPT_DIR}/inspect-release-staging.sh" "${VERSION}" "${STAGING_DIRECTORY}"

"${MAVEN}" --batch-mode --no-transfer-progress \
  -f "${EXAMPLE_POM}" \
  -Pstaged \
  -Drevision="${VERSION}" \
  -Dpostgres-bulk.staging-url="file://${STAGING_DIRECTORY}" \
  -Dmaven.repo.local="${CONSUMER_REPOSITORY}" \
  -DoutputFile="${AUDIT_DIRECTORY}/staged-consumer-dependency-tree.txt" \
  clean verify dependency:tree

DEPENDENCY_TREE=${AUDIT_DIRECTORY}/staged-consumer-dependency-tree.txt
if rg -n 'SNAPSHOT|postgres-bulk-benchmarks' "${DEPENDENCY_TREE}" \
    || tail -n +2 "${DEPENDENCY_TREE}" | rg -n 'io\.github\.yravelo\.examples:'; then
  echo "Forbidden dependency found in staged consumer dependency tree." >&2
  exit 1
fi
if rg -n 'org\.testcontainers:.*:(compile|runtime)' "${DEPENDENCY_TREE}"; then
  echo "Testcontainers leaked into a production scope." >&2
  exit 1
fi
if rg -n 'spring-boot-starter-actuator' "${DEPENDENCY_TREE}"; then
  echo "Actuator became mandatory for the staged consumer." >&2
  exit 1
fi

echo "Local release staging and isolated external consumer: PASS"
