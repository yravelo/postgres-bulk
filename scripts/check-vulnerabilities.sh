#!/usr/bin/env bash
set -euo pipefail

OSV_VERSION="2.5.1"
OSV_AMD64_SHA256="f9f25499a2c8cc367b3af45df2ea7eeca7fbccceab9c35079968f4b3652194be"
OSV_ARM64_SHA256="3d0f5aa5a6baa8eb32bcef247388e149ef6030a6634ccae6fa0d62681fb27a6d"
MAVEN_DEPENDENCY_PLUGIN_VERSION="3.8.1"

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
security_output="${repository_root}/target/security"
tool_cache_root="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/postgres-bulk-security-tools"

case "$(uname -m)" in
  x86_64 | amd64)
    osv_arch="amd64"
    osv_sha256="${OSV_AMD64_SHA256}"
    ;;
  aarch64 | arm64)
    osv_arch="arm64"
    osv_sha256="${OSV_ARM64_SHA256}"
    ;;
  *)
    echo "Unsupported OSV-Scanner architecture: $(uname -m)" >&2
    exit 1
    ;;
esac

mkdir -p "${tool_cache_root}" "${security_output}"
osv_binary="${tool_cache_root}/osv-scanner-${OSV_VERSION}-linux-${osv_arch}"
osv_url="https://github.com/google/osv-scanner/releases/download/v${OSV_VERSION}/osv-scanner_linux_${osv_arch}"

verify_osv() {
  printf '%s  %s\n' "${osv_sha256}" "${osv_binary}" | sha256sum --check --status
}

if [[ ! -x "${osv_binary}" ]] || ! verify_osv; then
  download_path="${osv_binary}.download"
  rm -f -- "${download_path}"
  curl --fail --location --silent --show-error "${osv_url}" --output "${download_path}"
  printf '%s  %s\n' "${osv_sha256}" "${download_path}" | sha256sum --check --status
  chmod 0755 "${download_path}"
  mv -- "${download_path}" "${osv_binary}"
fi
verify_osv

(
  cd "${repository_root}/code/postgres-bulk-parent"
  ./mvnw --batch-mode --no-transfer-progress -DskipTests \
    "org.apache.maven.plugins:maven-dependency-plugin:${MAVEN_DEPENDENCY_PLUGIN_VERSION}:tree" \
    -DoutputType=json \
    -DoutputFile=target/security/dependency-tree.json
)

rm -rf -- "${security_output}/osv-input"
python3 "${repository_root}/scripts/generate-dependency-inventory.py" \
  --repository "${repository_root}" \
  --output "${security_output}"

set +e
"${osv_binary}" scan source \
  --recursive \
  --no-ignore \
  --no-resolve \
  --data-source native \
  --format json \
  --verbosity error \
  --all-packages \
  --output-file "${security_output}/osv-results.json" \
  "${security_output}/osv-input"
osv_status=$?
set -e

if [[ "${osv_status}" -ne 0 && "${osv_status}" -ne 1 ]]; then
  echo "OSV-Scanner failed with status ${osv_status}; vulnerability gate is closed." >&2
  exit 1
fi

python3 "${repository_root}/scripts/triage-vulnerabilities.py" \
  --inventory "${security_output}/dependency-inventory.json" \
  --osv-results "${security_output}/osv-results.json" \
  --accepted-risks "${repository_root}/config/security/accepted-dependency-risks.json"

python3 "${repository_root}/scripts/check-dependabot-config.py"
python3 "${repository_root}/scripts/test-vulnerability-gate.py"
