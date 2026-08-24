#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(cd -- "${SCRIPT_DIR}/.." && pwd)
MODE=${1:-current}
GITLEAKS_VERSION=8.30.1
RELEASE_BASE=https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}

case "$(uname -s)-$(uname -m)" in
  Linux-x86_64)
    ASSET=gitleaks_${GITLEAKS_VERSION}_linux_x64.tar.gz
    ARCHIVE_SHA256=551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb
    BINARY_SHA256=88f91962aa2f93ac6ab281d553b9e125f5197bbbce38f9f2437f7299c32e5509
    ;;
  Linux-aarch64|Linux-arm64)
    ASSET=gitleaks_${GITLEAKS_VERSION}_linux_arm64.tar.gz
    ARCHIVE_SHA256=e4a487ee7ccd7d3a7f7ec08657610aa3606637dab924210b3aee62570fb4b080
    BINARY_SHA256=00e91bbe655bd7c47753e8cfe61cb76ea1a5d7e7702fe161ee40102b46b3823b
    ;;
  *)
    echo "Unsupported platform for the pinned Gitleaks binary: $(uname -s)-$(uname -m)" >&2
    exit 2
    ;;
esac

CACHE_BASE=${GITLEAKS_CACHE_DIR:-${RUNNER_TEMP:-${TMPDIR:-/tmp}}/postgres-bulk-security-tools}
TOOL_DIRECTORY=${CACHE_BASE}/gitleaks-${GITLEAKS_VERSION}-${ASSET%.tar.gz}
GITLEAKS=${TOOL_DIRECTORY}/gitleaks

verify_binary() {
  printf '%s  %s\n' "${BINARY_SHA256}" "${GITLEAKS}" | sha256sum --check --status
}

if [[ ! -x "${GITLEAKS}" ]] || ! verify_binary; then
  mkdir -p "${TOOL_DIRECTORY}"
  DOWNLOAD=$(mktemp "${TOOL_DIRECTORY}/download.XXXXXX")
  trap 'rm -f -- "${DOWNLOAD}"' EXIT
  curl --proto '=https' --tlsv1.2 --fail --location --silent --show-error \
    "${RELEASE_BASE}/${ASSET}" --output "${DOWNLOAD}"
  printf '%s  %s\n' "${ARCHIVE_SHA256}" "${DOWNLOAD}" | sha256sum --check --status
  tar -xzf "${DOWNLOAD}" -C "${TOOL_DIRECTORY}" gitleaks
  chmod 0755 "${GITLEAKS}"
  verify_binary
fi

if [[ "$("${GITLEAKS}" version)" != "${GITLEAKS_VERSION}" ]]; then
  echo "Unexpected Gitleaks version." >&2
  exit 1
fi

case "${MODE}" in
  current)
    "${GITLEAKS}" dir --redact=100 --no-banner --exit-code 1 "${REPOSITORY_ROOT}"
    ;;
  history)
    git -C "${REPOSITORY_ROOT}" rev-parse --is-inside-work-tree >/dev/null
    "${GITLEAKS}" git --redact=100 --no-banner --exit-code 1 "${REPOSITORY_ROOT}"
    ;;
  *)
    echo "Usage: $0 {current|history}" >&2
    exit 2
    ;;
esac

echo "Gitleaks ${GITLEAKS_VERSION} ${MODE} scan: PASS"
