#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(cd -- "${SCRIPT_DIR}/.." && pwd)

python3 "${SCRIPT_DIR}/check-markdown-links.py" "${REPOSITORY_ROOT}"

if rg -n '<artifactId>postgres-bulk-(core|pgjdbc|hibernate|spring-data|spring-boot-autoconfigure)</artifactId>' \
  "${REPOSITORY_ROOT}/examples/spring-boot-basic/pom.xml"; then
  echo "The standalone example must consume only the PostgreSQL Bulk starter." >&2
  exit 1
fi

echo "Public production types:"
rg -n '^public (final )?(class|interface|record|enum) ' \
  "${REPOSITORY_ROOT}/code/postgres-bulk-parent" \
  --glob '*.java' \
  | rg '/src/main/java/' \
  | rg -v '/postgres-bulk-benchmarks/'

echo "Documentation/adoption audit: PASS"
