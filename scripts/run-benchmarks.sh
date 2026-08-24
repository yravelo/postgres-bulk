#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "${script_dir}/.." && pwd)"
maven_dir="${repo_dir}/code/postgres-bulk-parent"
jar_file="${maven_dir}/postgres-bulk-benchmarks/target/benchmarks.jar"
results_dir="${repo_dir}/docs/benchmarks/raw"
profile="${1:-smoke}"
label="${2:-${profile}}"
java_bin="${JAVA_HOME:+${JAVA_HOME}/bin/}java"

case "${profile}" in
  smoke|baseline|large|multi-schema-smoke|multi-schema-baseline) ;;
  *)
    echo "usage: $0 {smoke|baseline|large|multi-schema-smoke|multi-schema-baseline} [result-label]" >&2
    exit 2
    ;;
esac

mkdir -p "${results_dir}"
(
  cd "${maven_dir}"
  ./mvnw -q -pl postgres-bulk-benchmarks -am package -DskipTests
)

common=(
  -Dbenchmark.postgres.image="${POSTGRES_VERSION:-15.18-alpine}"
  -Dlogging.level.root=WARN
  -jar "${jar_file}"
)

case "${profile}" in
  smoke)
    "${java_bin}" "${common[@]}" '.*Benchmarks.*' \
      -wi 0 -i 1 -r 100ms -f 0 -p size=10 -p batchSize=10 \
      -rf json -rff "${results_dir}/${label}.json"
    ;;
  baseline)
    "${java_bin}" "${common[@]}" '.*Benchmarks.*' \
      -prof gc -rf json -rff "${results_dir}/${label}.json"
    ;;
  large)
    "${java_bin}" "${common[@]}" \
      '(InsertBenchmarks\.(jdbcBatch|postgresBulkCopy)|JdbcInsertBenchmarks\.(jdbcBatch|postgresBulkJdbc|lowLevelCopy))' \
      -wi 1 -i 3 -f 1 -p size=1000000 -prof gc \
      -rf json -rff "${results_dir}/${label}.json"
    ;;
  multi-schema-smoke)
    "${java_bin}" -Dbenchmark.multi-schema=true "${common[@]}" \
      '(MultiSchema.*Benchmarks|RuntimeTargetResolutionBenchmarks).*' \
      -wi 0 -i 1 -r 100ms -f 0 -p size=10 -p cardinality=100 \
      -rf json -rff "${results_dir}/${label}.json"
    ;;
  multi-schema-baseline)
    "${java_bin}" -Dbenchmark.multi-schema=true "${common[@]}" \
      '(MultiSchema.*Benchmarks|RuntimeTargetResolutionBenchmarks).*' \
      -prof gc -rf json -rff "${results_dir}/${label}.json"
    ;;
  *)
    echo "usage: $0 {smoke|baseline|large|multi-schema-smoke|multi-schema-baseline} [result-label]" >&2
    exit 2
    ;;
esac
