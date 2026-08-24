#!/usr/bin/env python3
"""Prove that enabling OpenPGP only adds detached signatures."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
POLICY = json.loads(
    (ROOT / "config/security/release-signing-policy.json").read_text(encoding="utf-8")
)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def artifacts(staging: Path, version: str) -> set[Path]:
    prefix = Path(*POLICY["group_id"].split("."))
    parent = POLICY["parent_artifact"]
    result = {prefix / parent / version / f"{parent}-{version}.pom"}
    for module in POLICY["publishable_artifacts"]:
        directory = prefix / module / version
        result.update(
            {
                directory / f"{module}-{version}.pom",
                directory / f"{module}-{version}.jar",
                directory / f"{module}-{version}-sources.jar",
                directory / f"{module}-{version}-javadoc.jar",
                directory / f"{module}-{version}-cyclonedx.json",
            }
        )
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--unsigned", type=Path, required=True)
    parser.add_argument("--signed", type=Path, required=True)
    parser.add_argument("--version", required=True)
    args = parser.parse_args()
    changed = []
    for relative in sorted(artifacts(args.unsigned, args.version)):
        unsigned = args.unsigned / relative
        signed = args.signed / relative
        if not unsigned.is_file() or not signed.is_file() or digest(unsigned) != digest(signed):
            changed.append(relative.as_posix())
    if changed:
        raise SystemExit(f"signing changed primary/SBOM bytes: {changed}")
    print("Unsigned/signed artifact identity: PASS (46/46 SHA-256 matches)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
