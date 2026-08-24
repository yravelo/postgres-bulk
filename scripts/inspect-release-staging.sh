#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(cd -- "${SCRIPT_DIR}/.." && pwd)
VERSION=${1:-0.1.0}
STAGING_DIRECTORY=${2:-${REPOSITORY_ROOT}/target/release-staging}
AUDIT_DIRECTORY=${REPOSITORY_ROOT}/target/release-audit
POLICY=${REPOSITORY_ROOT}/config/security/sbom-policy.json

mapfile -t PUBLISHED_MODULES < <(
  python3 - "${POLICY}" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    policy = json.load(stream)
for artifact in policy["publishable_artifacts"]:
    print(artifact)
PY
)
if [[ "${#PUBLISHED_MODULES[@]}" -ne 9 ]]; then
  echo "Release inventory must contain exactly nine publishable artifacts." >&2
  exit 1
fi

if [[ ! -d "${STAGING_DIRECTORY}" ]]; then
  echo "Local staging directory does not exist: ${STAGING_DIRECTORY}" >&2
  exit 1
fi

mkdir -p "${AUDIT_DIRECTORY}"
HASH_REPORT=${AUDIT_DIRECTORY}/staged-sha256.txt
SBOM_HASH_REPORT=${AUDIT_DIRECTORY}/staged-sbom-sha256.txt
: > "${HASH_REPORT}"
: > "${SBOM_HASH_REPORT}"

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

  sbom=${module_directory}/${module}-${VERSION}-cyclonedx.json
  if [[ ! -f "${sbom}" ]]; then
    echo "Missing staged CycloneDX evidence: ${sbom}" >&2
    exit 1
  fi
  sha256sum "${sbom}" >> "${SBOM_HASH_REPORT}"

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

python3 - "${STAGING_DIRECTORY}" "${VERSION}" "${POLICY}" <<'PY'
import json
import sys
from pathlib import Path

staging = Path(sys.argv[1])
version = sys.argv[2]
with open(sys.argv[3], encoding="utf-8") as stream:
    modules = json.load(stream)["publishable_artifacts"]
prefix = Path("io/github/yravelo")
expected = {
    prefix / "postgres-bulk-parent" / version / f"postgres-bulk-parent-{version}.pom"
}
for module in modules:
    directory = prefix / module / version
    expected.update(
        {
            directory / f"{module}-{version}.pom",
            directory / f"{module}-{version}.jar",
            directory / f"{module}-{version}-sources.jar",
            directory / f"{module}-{version}-javadoc.jar",
            directory / f"{module}-{version}-cyclonedx.json",
        }
    )
actual = {
    path.relative_to(staging)
    for path in staging.rglob("*")
    if path.is_file()
    and (path.suffix in {".pom", ".jar"} or path.name.endswith("-cyclonedx.json"))
}
if actual != expected:
    missing = sorted(str(path) for path in expected - actual)
    unexpected = sorted(str(path) for path in actual - expected)
    raise SystemExit(
        f"Staged release inventory mismatch; missing={missing}, unexpected={unexpected}"
    )
PY

if find "${STAGING_DIRECTORY}" -type f \
    \( -path '*postgres-bulk-benchmarks*' -o -path '*spring-boot-basic*' \
       -o -path '*spring-boot-data-jdbc*' \) \
    | rg .; then
  echo "A non-publishable module was deployed to local staging." >&2
  exit 1
fi

LC_ALL=C sort -o "${HASH_REPORT}" "${HASH_REPORT}"
LC_ALL=C sort -o "${SBOM_HASH_REPORT}" "${SBOM_HASH_REPORT}"
if [[ $(wc -l < "${HASH_REPORT}") -ne 37 ]] \
    || [[ $(wc -l < "${SBOM_HASH_REPORT}") -ne 9 ]]; then
  echo "Unexpected primary/security evidence count in staging hash reports." >&2
  exit 1
fi
echo "Staged artifact inspection: PASS (37 primary artifacts + 9 CycloneDX evidence files)"
echo "SHA-256 report: ${HASH_REPORT}"
echo "SBOM SHA-256 report: ${SBOM_HASH_REPORT}"
