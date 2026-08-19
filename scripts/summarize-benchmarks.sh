#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 INPUT.json OUTPUT.csv" >&2
  exit 2
fi

jq -r '
  ["benchmark", "size", "batch_size", "ms_per_op", "error", "rows_per_second", "bytes_per_op"],
  (.[] |
    .primaryMetric.score as $score |
    (.params.size // "") as $size |
    [
      (.benchmark | split(".") | .[-2:] | join(".")),
      $size,
      (.params.batchSize // ""),
      $score,
      .primaryMetric.scoreError,
      (if ($size | tostring | length) > 0 then (($size | tonumber) * 1000 / $score) else "" end),
      (.secondaryMetrics["gc.alloc.rate.norm"].score // "")
    ]) | @csv
' "$1" > "$2"
