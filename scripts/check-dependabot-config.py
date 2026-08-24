#!/usr/bin/env python3
"""Validate the repository's low-noise, no-auto-merge Dependabot policy."""

from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

try:
    import yaml
except ImportError as exc:  # pragma: no cover - CI installs python3-yaml
    raise SystemExit("PyYAML is required: install the python3-yaml package") from exc


ROOT = Path(__file__).resolve().parent.parent
CONFIG = ROOT / ".github" / "dependabot.yml"
EXPECTED = {
    ("maven", "/code/postgres-bulk-parent"),
    ("maven", "/examples/spring-boot-basic"),
    ("maven", "/examples/spring-boot-data-jdbc"),
    ("maven", "/verification/spring-boot-jdbc-consumer"),
    ("github-actions", "/"),
}


def contains_key(value: Any, forbidden: set[str]) -> bool:
    if isinstance(value, dict):
        return bool(set(map(str, value)) & forbidden) or any(
            contains_key(child, forbidden) for child in value.values()
        )
    if isinstance(value, list):
        return any(contains_key(child, forbidden) for child in value)
    return False


def main() -> int:
    loaded = yaml.safe_load(CONFIG.read_text(encoding="utf-8"))
    if not isinstance(loaded, dict) or loaded.get("version") != 2:
        raise ValueError("Dependabot config must use schema version 2")
    updates = loaded.get("updates")
    if not isinstance(updates, list):
        raise ValueError("Dependabot updates must be a list")
    actual = {(entry.get("package-ecosystem"), entry.get("directory")) for entry in updates}
    if actual != EXPECTED or len(updates) != len(EXPECTED):
        raise ValueError(f"unexpected Dependabot ecosystems/directories: {sorted(actual)}")
    if contains_key(loaded, {"auto-merge", "automerge", "merge-method"}):
        raise ValueError("Dependabot auto-merge configuration is forbidden")

    for entry in updates:
        schedule = entry.get("schedule", {})
        if schedule.get("interval") != "weekly":
            raise ValueError(f"{entry.get('package-ecosystem')} {entry.get('directory')} is not weekly")
        limit = entry.get("open-pull-requests-limit")
        if not isinstance(limit, int) or not 1 <= limit <= 5:
            raise ValueError("open-pull-requests-limit must remain between 1 and 5")
        ignore = entry.get("ignore", [])
        if not isinstance(ignore, list) or not any(
            "version-update:semver-major" in rule.get("update-types", []) for rule in ignore
        ):
            raise ValueError("every update lane must defer major upgrades to manual review")

    compose_files = (
        ROOT / "examples" / "spring-boot-basic" / "compose.yaml",
        ROOT / "examples" / "spring-boot-data-jdbc" / "compose.yaml",
    )
    for path in compose_files:
        compose = yaml.safe_load(path.read_text(encoding="utf-8"))
        images = [service.get("image") for service in compose.get("services", {}).values()]
        if not images or any(not isinstance(image, str) or ":" not in image or image.endswith(":latest") for image in images):
            raise ValueError(f"{path.relative_to(ROOT)} must use explicit non-latest image tags")

    print("Dependabot configuration passed: 5 weekly lanes, bounded PRs, majors manual, no auto-merge")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, yaml.YAMLError) as exc:
        print(f"Dependabot configuration failed: {exc}", file=sys.stderr)
        sys.exit(1)
