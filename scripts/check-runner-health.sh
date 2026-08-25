#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(cd -- "${SCRIPT_DIR}/.." && pwd)
POLICY=${REPOSITORY_ROOT}/config/security/continuous-security-policy.json
MODE=${1:-check}
STATE_FILE=${2:-${REPOSITORY_ROOT}/target/security/runner-docker-baseline.txt}

readarray -t RUNNER_POLICY < <(python3 - "${POLICY}" <<'PY'
import json
import sys
with open(sys.argv[1], encoding="utf-8") as stream:
    runner = json.load(stream)["runner"]
print(runner["minimum_free_disk_gib"])
print(runner["minimum_actions_runner_version"])
print(runner["postgres_image"])
PY
)
MINIMUM_FREE_GIB=${RUNNER_POLICY[0]}
MINIMUM_RUNNER_VERSION=${RUNNER_POLICY[1]}
POSTGRES_IMAGE=${RUNNER_POLICY[2]}

for command in docker git gpg java python3 rg sha256sum; do
  if ! command -v "${command}" >/dev/null; then
    echo "Runner health failed: missing required command ${command}" >&2
    exit 1
  fi
done
if [[ ! -x "${REPOSITORY_ROOT}/code/postgres-bulk-parent/mvnw" ]]; then
  echo "Runner health failed: Maven Wrapper is not executable." >&2
  exit 1
fi

FREE_KIB=$(df -Pk "${REPOSITORY_ROOT}" | awk 'NR == 2 {print $4}')
MINIMUM_KIB=$((MINIMUM_FREE_GIB * 1024 * 1024))
if [[ ! "${FREE_KIB}" =~ ^[0-9]+$ ]] || (( FREE_KIB < MINIMUM_KIB )); then
  echo "Runner health failed: less than ${MINIMUM_FREE_GIB} GiB is free." >&2
  exit 1
fi
docker info >/dev/null

if [[ "${GITHUB_ACTIONS:-false}" == "true" ]]; then
  RUNNER_HOME=$(getent passwd "$(id -un)" | cut -d: -f6)
  for forbidden in \
    "${RUNNER_HOME}/.git-credentials" \
    "${RUNNER_HOME}/.config/gh/hosts.yml" \
    "${RUNNER_HOME}/.m2/settings.xml"; do
    if [[ -e "${forbidden}" ]]; then
      echo "Runner health failed: unexpected persistent credential/settings file exists." >&2
      exit 1
    fi
  done
  if [[ -d "${RUNNER_HOME}/.gnupg/private-keys-v1.d" ]] \
      && find "${RUNNER_HOME}/.gnupg/private-keys-v1.d" -type f -print -quit | rg -q .; then
    echo "Runner health failed: OpenPGP private-key material exists on the CI account." >&2
    exit 1
  fi

  if [[ -n "${RUNNER_TEMP:-}" ]]; then
    RUNNER_ROOT=$(realpath "${RUNNER_TEMP}/../..")
    LISTENER=${RUNNER_ROOT}/bin/Runner.Listener
    if [[ -x "${LISTENER}" ]]; then
      INSTALLED_VERSION=$("${LISTENER}" --version)
      if [[ "$(printf '%s\n%s\n' "${MINIMUM_RUNNER_VERSION}" "${INSTALLED_VERSION}" | sort -V | head -n1)" != "${MINIMUM_RUNNER_VERSION}" ]]; then
        echo "Runner health failed: Actions runner ${INSTALLED_VERSION} is below ${MINIMUM_RUNNER_VERSION}." >&2
        exit 1
      fi
    fi
  fi
fi

snapshot_testcontainers() {
  {
    docker ps -aq --filter label=org.testcontainers=true | sed 's/^/container /'
    docker network ls -q --filter label=org.testcontainers=true | sed 's/^/network /'
    docker volume ls -q --filter label=org.testcontainers=true | sed 's/^/volume /'
  } | sort -u
}

testcontainers_cleanup_complete() {
  local state_file=$1
  local current=$2
  snapshot_testcontainers > "${current}"
  ! comm -13 "${state_file}" "${current}" | rg -q .
}

case "${MODE}" in
  check)
    docker run --rm "${POSTGRES_IMAGE}" postgres --version >/dev/null
    ;;
  pre)
    mkdir -p "$(dirname -- "${STATE_FILE}")"
    snapshot_testcontainers > "${STATE_FILE}"
    docker run --rm "${POSTGRES_IMAGE}" postgres --version >/dev/null
    ;;
  post)
    if [[ ! -f "${STATE_FILE}" ]]; then
      echo "Runner health failed: missing pre-run Docker baseline." >&2
      exit 1
    fi
    CURRENT=$(mktemp)
    trap 'rm -f -- "${CURRENT}"' EXIT
    CLEANUP_COMPLETE=false
    for attempt in 1 2 3 4 5 6; do
      if testcontainers_cleanup_complete "${STATE_FILE}" "${CURRENT}"; then
        CLEANUP_COMPLETE=true
        break
      fi
      if [[ "${attempt}" -lt 6 ]]; then
        sleep 2
      fi
    done
    if [[ "${CLEANUP_COMPLETE}" != "true" ]]; then
      echo "Runner health failed: the validation left new Testcontainers/Ryuk Docker objects." >&2
      exit 1
    fi
    ;;
  *)
    echo "Usage: $0 {check|pre|post} [state-file]" >&2
    exit 2
    ;;
esac

echo "Runner health ${MODE}: PASS (Docker, disk, prerequisites and credential boundary)"
