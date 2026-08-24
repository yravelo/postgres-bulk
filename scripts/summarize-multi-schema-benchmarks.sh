#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 INPUT.json OUTPUT.csv" >&2
  exit 2
fi

jq -r '
  def method: (.benchmark | split(".") | last);
  def class: (.benchmark | split(".") | .[-2]);
  def operation:
    if (class | contains("Insert")) then "insert"
    elif (class | contains("Lookup")) then "lookup"
    else "target-resolution" end;
  def api:
    if class == "MultiSchemaJpaInsertBenchmarks" or class == "MultiSchemaJpaLookupBenchmarks"
      then "spring-data-jpa"
    elif (method | startswith("lowLevel")) then "pgjdbc"
    elif (method | startswith("springDataJdbc")) then "spring-data-jdbc"
    else "core" end;
  def variant:
    if method == "runtimeTarget" or (method | contains("RuntimeTarget")) then "runtime"
    elif method == "defaultTarget" or (method | contains("DefaultTarget")) then "default"
    elif method == "samePrebuiltTarget" then "same"
    else "many" end;
  def metric:
    {
      operation: operation,
      api: api,
      variant: variant,
      size: (.params.size // .params.cardinality),
      unit: .primaryMetric.scoreUnit,
      score: .primaryMetric.score,
      error: .primaryMetric.scoreError,
      allocation: (.secondaryMetrics["gc.alloc.rate.norm"].score // 0)
    };
  map(metric) as $rows |
  ["operation", "api", "size", "unit", "default_score", "default_error",
   "runtime_score", "runtime_error", "delta", "delta_percent", "rows_per_second_default",
   "rows_per_second_runtime", "default_bytes_per_op", "runtime_bytes_per_op",
   "allocation_delta_bytes"] ,
  ($rows
    | group_by([.operation, .api, .size])[]
    | . as $group
    | ($group | map(select(.variant == "default" or .variant == "same")) | first) as $default
    | ($group | map(select(.variant == "runtime" or .variant == "many")) | first) as $runtime
    | [
        $default.operation,
        $default.api,
        $default.size,
        $default.unit,
        $default.score,
        $default.error,
        $runtime.score,
        $runtime.error,
        ($runtime.score - $default.score),
        (if $default.score == 0 then 0 else (($runtime.score - $default.score) * 100 / $default.score) end),
        (if $default.operation == "target-resolution" then "" else (($default.size | tonumber) * 1000 / $default.score) end),
        (if $runtime.operation == "target-resolution" then "" else (($runtime.size | tonumber) * 1000 / $runtime.score) end),
        $default.allocation,
        $runtime.allocation,
        ($runtime.allocation - $default.allocation)
      ])
  | @csv
' "$1" > "$2"
