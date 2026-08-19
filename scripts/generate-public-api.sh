#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(cd -- "${SCRIPT_DIR}/.." && pwd)
MAVEN_PROJECT=${REPOSITORY_ROOT}/code/postgres-bulk-parent
BASELINE=${REPOSITORY_ROOT}/docs/releases/0.1.0-public-api.txt
MODE=${1:-print}

MODULES=(
  postgres-bulk-core
  postgres-bulk-pgjdbc
  postgres-bulk-hibernate
  postgres-bulk-spring-data-jdbc
  postgres-bulk-spring-data
  postgres-bulk-spring-boot-autoconfigure
  postgres-bulk-spring-boot-starter
)

temporary=$(mktemp)
trap 'rm -f "${temporary}"' EXIT

{
  echo "# PostgreSQL Bulk 0.1.0 public binary API"
  echo "# Generated with scripts/generate-public-api.sh and javap -public."
  for module in "${MODULES[@]}"; do
    classes=${MAVEN_PROJECT}/${module}/target/classes
    if [[ ! -d "${classes}" ]]; then
      if [[ "${module}" == "postgres-bulk-spring-boot-starter" ]]; then
        echo
        echo "## ${module}"
        echo "# Dependency-only starter: no production classes."
        continue
      fi
      echo "Missing compiled classes for ${module}; run the 0.1.0 release build first." >&2
      exit 1
    fi

    echo
    echo "## ${module}"
    while IFS= read -r class_file; do
      class_name=${class_file#${classes}/}
      class_name=${class_name%.class}
      class_name=${class_name//\//.}
      output=$(javap -public -classpath "${classes}" "${class_name}" 2>/dev/null)
      if printf '%s\n' "${output}" | rg -q '^public '; then
        printf '%s\n' "${output}"
      fi
    done < <(find "${classes}" -type f -name '*.class' | LC_ALL=C sort)
  done
} > "${temporary}"

case "${MODE}" in
  print)
    cat "${temporary}"
    ;;
  --check)
    diff -u "${BASELINE}" "${temporary}"
    echo "Public API baseline: PASS"
    ;;
  *)
    echo "Usage: $0 [print|--check]" >&2
    exit 2
    ;;
esac
