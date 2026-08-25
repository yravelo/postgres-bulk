#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(cd -- "${SCRIPT_DIR}/.." && pwd)
MAVEN_PROJECT=${REPOSITORY_ROOT}/code/postgres-bulk-parent
MODE=${1:-fast}

run_gate() {
  local name=$1
  local classification=$2
  shift 2
  echo "SECURITY GATE START: ${name} [failure=${classification}]"
  if ! "$@"; then
    echo "SECURITY GATE FAIL: ${name} [classification=${classification}]" >&2
    return 1
  fi
  echo "SECURITY GATE PASS: ${name}"
}

fast_gates() {
  run_gate policy-drift CONTROL "${SCRIPT_DIR}/check-security-policy.py"
  run_gate policy-fixtures CONTROL "${SCRIPT_DIR}/test-security-policy.py"
  run_gate operational-fixtures CONTROL "${SCRIPT_DIR}/test-operational-security.py"
  run_gate baseline-closure CONTROL "${SCRIPT_DIR}/check-security-baseline.py"
  run_gate baseline-fixtures CONTROL "${SCRIPT_DIR}/test-security-baseline.py"
  run_gate workflow-hardening CONTROL "${SCRIPT_DIR}/check-workflow-security.py"
  run_gate workflow-fixtures CONTROL "${SCRIPT_DIR}/test-workflow-security.py"
  run_gate dependabot-policy CONTROL "${SCRIPT_DIR}/check-dependabot-config.py"
  run_gate secret-fixture SECURITY "${SCRIPT_DIR}/check-secrets.sh" fixture
  run_gate current-secrets SECURITY "${SCRIPT_DIR}/check-secrets.sh" current
}

full_gates() {
  fast_gates
  local runner_state=${REPOSITORY_ROOT}/target/security/runner-docker-baseline.txt
  run_gate runner-health-pre INFRASTRUCTURE "${SCRIPT_DIR}/check-runner-health.sh" pre "${runner_state}"
  run_gate history-secrets SECURITY "${SCRIPT_DIR}/check-secrets.sh" history
  run_gate dependency-vulnerabilities SECURITY_OR_EXTERNAL "${SCRIPT_DIR}/check-vulnerabilities.sh"
  run_gate reactor-and-testcontainers CODE_OR_INFRASTRUCTURE \
    bash -c 'cd "$1" && ./mvnw --batch-mode --no-transfer-progress clean verify' _ "${MAVEN_PROJECT}"
  run_gate static-analysis SECURITY "${SCRIPT_DIR}/check-static-analysis.py"
  local sbom_directory
  mkdir -p "${REPOSITORY_ROOT}/target"
  sbom_directory=$(mktemp -d "${REPOSITORY_ROOT}/target/sec7-sbom.XXXXXX")
  rmdir "${sbom_directory}"
  run_gate sbom-license SECURITY_OR_EXTERNAL \
    env REQUIRE_OSV_INVENTORY=true "${SCRIPT_DIR}/generate-sbom.sh" 0.1.0 "${sbom_directory}"
  run_gate documentation-api CODE "${SCRIPT_DIR}/check-documentation.sh"
  if [[ "${CHECK_PUBLIC_KEY_REMOTE:-false}" == "true" ]]; then
    run_gate openpgp-public-key EXTERNAL_OR_RELEASE_CONTROL "${SCRIPT_DIR}/check-public-key.sh" remote
  else
    run_gate openpgp-public-key RELEASE_CONTROL "${SCRIPT_DIR}/check-public-key.sh" local
  fi
  run_gate runner-health-post INFRASTRUCTURE "${SCRIPT_DIR}/check-runner-health.sh" post "${runner_state}"
}

case "${MODE}" in
  fast)
    fast_gates
    ;;
  full)
    full_gates
    ;;
  release)
    full_gates
    run_gate release-signing-inventory-fixtures SECURITY \
      "${SCRIPT_DIR}/test-release-signatures.py"
    run_gate technical-release-preflight RELEASE_CONTROL \
      "${SCRIPT_DIR}/check-release-security-preflight.sh" technical
    ;;
  *)
    echo "Usage: $0 {fast|full|release}" >&2
    exit 2
    ;;
esac

echo "Continuous security validation ${MODE}: PASS"
