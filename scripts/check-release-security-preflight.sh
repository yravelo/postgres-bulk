#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(cd -- "${SCRIPT_DIR}/.." && pwd)
MODE=${1:-technical}

case "${MODE}" in
  technical)
    "${SCRIPT_DIR}/check-security-policy.py" --preflight technical
    "${SCRIPT_DIR}/check-public-key.sh" local
    if [[ "${CHECK_PUBLIC_KEY_REMOTE:-false}" == "true" ]]; then
      "${SCRIPT_DIR}/check-public-key.sh" remote
    fi
    git -C "${REPOSITORY_ROOT}" diff --quiet
    git -C "${REPOSITORY_ROOT}" diff --cached --quiet
    if [[ -n "$(git -C "${REPOSITORY_ROOT}" status --short --untracked-files=normal)" ]]; then
      echo "Technical release preflight failed: worktree is not clean." >&2
      exit 1
    fi
    if [[ "${GITHUB_ACTIONS:-false}" != "true" ]]; then
      git -C "${REPOSITORY_ROOT}" fetch --quiet origin main
    fi
    if [[ "$(git -C "${REPOSITORY_ROOT}" rev-parse HEAD)" != "$(git -C "${REPOSITORY_ROOT}" rev-parse origin/main)" ]]; then
      echo "Technical release preflight failed: HEAD differs from origin/main." >&2
      exit 1
    fi
    echo "Technical release security preflight: PASS"
    ;;
  rel1)
    "${SCRIPT_DIR}/check-security-policy.py" --preflight rel1
    ;;
  *)
    echo "Usage: $0 {technical|rel1}" >&2
    exit 2
    ;;
esac
