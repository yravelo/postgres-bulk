#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(cd -- "${SCRIPT_DIR}/.." && pwd)
RIPGREP_VERSION="15.2.0"
RIPGREP_SHA256="33e15bcf1624b25cdd2a55813a47a2f95dbe126268203e76aa6a585d1e7b149c"
RIPGREP_ARCHIVE="ripgrep-${RIPGREP_VERSION}-x86_64-unknown-linux-musl.tar.gz"
RIPGREP_URL="https://github.com/BurntSushi/ripgrep/releases/download/${RIPGREP_VERSION}/${RIPGREP_ARCHIVE}"

: "${RUNNER_TEMP:?RUNNER_TEMP must identify the ephemeral hosted-runner directory}"
: "${GITHUB_PATH:?GITHUB_PATH must be available in GitHub Actions}"

python3 -m pip install \
  --disable-pip-version-check \
  --no-deps \
  --only-binary=:all: \
  --require-hashes \
  --requirement "${REPOSITORY_ROOT}/config/security/python-requirements.txt"

archive_path="${RUNNER_TEMP}/${RIPGREP_ARCHIVE}"
install_path="${RUNNER_TEMP}/ripgrep-${RIPGREP_VERSION}"
mkdir -p "${install_path}"
curl --fail --location --proto '=https' --tlsv1.2 \
  --retry 3 --output "${archive_path}" "${RIPGREP_URL}"
printf '%s  %s\n' "${RIPGREP_SHA256}" "${archive_path}" | sha256sum --check --strict
tar --extract --gzip --file "${archive_path}" --directory "${install_path}" \
  --strip-components=1 "ripgrep-${RIPGREP_VERSION}-x86_64-unknown-linux-musl/rg"
"${install_path}/rg" --version
printf '%s\n' "${install_path}" >> "${GITHUB_PATH}"
