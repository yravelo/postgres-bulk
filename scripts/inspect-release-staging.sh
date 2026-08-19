#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(cd -- "${SCRIPT_DIR}/.." && pwd)
VERSION=${1:-0.1.0}
STAGING_DIRECTORY=${2:-${REPOSITORY_ROOT}/target/release-staging}
AUDIT_DIRECTORY=${REPOSITORY_ROOT}/target/release-audit

PUBLISHED_MODULES=(
  postgres-bulk-core
  postgres-bulk-pgjdbc
  postgres-bulk-hibernate
  postgres-bulk-spring-data
  postgres-bulk-spring-boot-autoconfigure
  postgres-bulk-spring-boot-starter
)

if [[ ! -d "${STAGING_DIRECTORY}" ]]; then
  echo "Local staging directory does not exist: ${STAGING_DIRECTORY}" >&2
  exit 1
fi

mkdir -p "${AUDIT_DIRECTORY}"
HASH_REPORT=${AUDIT_DIRECTORY}/staged-sha256.txt
: > "${HASH_REPORT}"

PARENT_DIRECTORY=${STAGING_DIRECTORY}/io/github/yravelo/postgres-bulk-parent/${VERSION}
PARENT_POM=${PARENT_DIRECTORY}/postgres-bulk-parent-${VERSION}.pom
test -f "${PARENT_POM}"
sha256sum "${PARENT_POM}" >> "${HASH_REPORT}"

for module in "${PUBLISHED_MODULES[@]}"; do
  module_directory=${STAGING_DIRECTORY}/io/github/yravelo/${module}/${VERSION}
  for suffix in .pom .jar -sources.jar -javadoc.jar; do
    artifact=${module_directory}/${module}-${VERSION}${suffix}
    if [[ ! -f "${artifact}" ]]; then
      echo "Missing staged artifact: ${artifact}" >&2
      exit 1
    fi
    sha256sum "${artifact}" >> "${HASH_REPORT}"
  done

  pom=${module_directory}/${module}-${VERSION}.pom
  if rg -n 'SNAPSHOT|postgres-bulk-benchmarks|spring-boot-basic|<repositories>|<pluginRepositories>|<systemPath>' "${pom}"; then
    echo "Unsafe content found in staged POM: ${pom}" >&2
    exit 1
  fi

  manifest_version=$(unzip -p "${module_directory}/${module}-${VERSION}.jar" META-INF/MANIFEST.MF \
    | tr -d '\r' \
    | sed -n 's/^Implementation-Version: //p')
  if [[ "${manifest_version}" != "${VERSION}" ]]; then
    echo "Unexpected Implementation-Version in ${module}: ${manifest_version:-missing}" >&2
    exit 1
  fi
done

if find "${STAGING_DIRECTORY}" -type f \
    \( -path '*postgres-bulk-benchmarks*' -o -path '*spring-boot-basic*' \) \
    | rg .; then
  echo "A non-publishable module was deployed to local staging." >&2
  exit 1
fi

LC_ALL=C sort -o "${HASH_REPORT}" "${HASH_REPORT}"
echo "Staged artifact inspection: PASS"
echo "SHA-256 report: ${HASH_REPORT}"
