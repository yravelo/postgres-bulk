#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(cd -- "${SCRIPT_DIR}/.." && pwd)
POLICY=${REPOSITORY_ROOT}/config/security/continuous-security-policy.json
MODE=${1:-local}

readarray -t SIGNING_POLICY < <(python3 - "${POLICY}" <<'PY'
import json
import sys
with open(sys.argv[1], encoding="utf-8") as stream:
    signing = json.load(stream)["signing"]
print(signing["fingerprint"])
print(signing["public_key"])
print(signing["remote_url"])
PY
)
FINGERPRINT=${SIGNING_POLICY[0]}
PUBLIC_KEY=${REPOSITORY_ROOT}/${SIGNING_POLICY[1]}
REMOTE_URL=${SIGNING_POLICY[2]}

verify_key() {
  local key_file=$1
  local require_expiry=$2
  local metadata
  metadata=$(gpg --batch --with-colons --import-options show-only --import "${key_file}" 2>/dev/null)
  local actual_fingerprint
  actual_fingerprint=$(awk -F: '$1 == "fpr" {print toupper($10); exit}' <<<"${metadata}")
  if [[ "${actual_fingerprint}" != "${FINGERPRINT}" ]]; then
    echo "OpenPGP preflight failed: public-key fingerprint mismatch." >&2
    exit 1
  fi
  if [[ "${require_expiry}" == "true" ]]; then
    local expiry_epoch
    expiry_epoch=$(awk -F: '$1 == "pub" {print $7; exit}' <<<"${metadata}")
    if [[ ! "${expiry_epoch}" =~ ^[0-9]+$ ]] || (( expiry_epoch <= $(date +%s) )); then
      echo "OpenPGP preflight failed: tracked public key is expired or lacks a valid expiry." >&2
      exit 1
    fi
  fi
}

verify_key "${PUBLIC_KEY}" true
case "${MODE}" in
  local) ;;
  remote)
    DOWNLOADED=$(mktemp)
    trap 'rm -f -- "${DOWNLOADED}"' EXIT
    curl --proto '=https' --tlsv1.2 --fail --location --silent --show-error \
      "${REMOTE_URL}" --output "${DOWNLOADED}"
    # keys.openpgp.org strips an unverified UID and therefore its expiry-bearing
    # self-signature. Remote retrieval proves availability and exact identity;
    # the complete tracked export above remains the expiry source of truth.
    verify_key "${DOWNLOADED}" false
    ;;
  *)
    echo "Usage: $0 {local|remote}" >&2
    exit 2
    ;;
esac
echo "OpenPGP public-key ${MODE} preflight: PASS (${FINGERPRINT})"
