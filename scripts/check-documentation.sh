#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(cd -- "${SCRIPT_DIR}/.." && pwd)

python3 "${SCRIPT_DIR}/check-markdown-links.py" "${REPOSITORY_ROOT}"

PUBLIC_ADOPTION_DOCS=(
  "${REPOSITORY_ROOT}/README.md"
  "${REPOSITORY_ROOT}/SECURITY.md"
  "${REPOSITORY_ROOT}/docs/user-guide/getting-started.md"
  "${REPOSITORY_ROOT}/docs/user-guide/spring-data-jdbc.md"
  "${REPOSITORY_ROOT}/docs/architecture/spring-data-jdbc-boot-autoconfiguration.md"
)

if rg -n '0\.1\.0-SNAPSHOT|not published to Maven Central|distribution is not active' \
  "${PUBLIC_ADOPTION_DOCS[@]}"; then
  echo "Public adoption documentation must describe the stable Maven Central release." >&2
  exit 1
fi

for starter in postgres-bulk-spring-boot-starter postgres-bulk-spring-boot-starter-data-jdbc; do
  if ! rg -U -q \
    "<artifactId>${starter}</artifactId>[[:space:]]*<version>0\\.1\\.0</version>" \
    "${REPOSITORY_ROOT}/README.md" \
    "${REPOSITORY_ROOT}/docs/user-guide/getting-started.md"; then
    echo "Stable 0.1.0 installation snippet missing for ${starter}." >&2
    exit 1
  fi
done

if rg -n '<artifactId>postgres-bulk-(core|pgjdbc|hibernate|spring-data(-jdbc)?|spring-boot-autoconfigure(-jdbc)?|benchmarks)</artifactId>' \
  "${REPOSITORY_ROOT}/examples/spring-boot-basic/pom.xml" \
  "${REPOSITORY_ROOT}/examples/spring-boot-data-jdbc/pom.xml"; then
  echo "Standalone examples must consume only their PostgreSQL Bulk starter." >&2
  exit 1
fi

echo "Public production types:"
rg -n '^public (final )?(class|interface|record|enum) ' \
  "${REPOSITORY_ROOT}/code/postgres-bulk-parent" \
  --glob '*.java' \
  | rg '/src/main/java/' \
  | rg -v '/postgres-bulk-benchmarks/'

echo "Documentation/adoption audit: PASS"
