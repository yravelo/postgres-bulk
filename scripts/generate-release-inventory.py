#!/usr/bin/env python3
"""Generate the exact, source-bound release inventory for a staged Maven candidate."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
POLICY_PATH = ROOT / "config/security/release-signing-policy.json"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def expected(policy: dict, staging: Path, version: str) -> list[dict]:
    group_path = Path(*policy["group_id"].split("."))
    result = []
    parent = policy["parent_artifact"]
    parent_relative = group_path / parent / version / f"{parent}-{version}.pom"
    result.append(
        {
            "artifact_id": parent,
            "relative": parent_relative,
            "type": "pom",
            "classifier": None,
            "role": "parent-support",
            "sbom_relation": None,
        }
    )
    for artifact_id in policy["publishable_artifacts"]:
        directory = group_path / artifact_id / version
        sbom = directory / f"{artifact_id}-{version}-cyclonedx.json"
        variants = [
            (f"{artifact_id}-{version}.pom", "pom", None),
            (f"{artifact_id}-{version}.jar", "jar", None),
            (f"{artifact_id}-{version}-sources.jar", "jar", "sources"),
            (f"{artifact_id}-{version}-javadoc.jar", "jar", "javadoc"),
            (f"{artifact_id}-{version}-cyclonedx.json", "json", "cyclonedx"),
        ]
        for filename, artifact_type, classifier in variants:
            result.append(
                {
                    "artifact_id": artifact_id,
                    "relative": directory / filename,
                    "type": artifact_type,
                    "classifier": classifier,
                    "role": "published-sbom" if classifier == "cyclonedx" else "published-primary",
                    "sbom_relation": (
                        {"kind": "describes", "target": artifact_id}
                        if classifier == "cyclonedx"
                        else {"kind": "described-by", "path": sbom.as_posix()}
                    ),
                }
            )
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--staging", type=Path, required=True)
    parser.add_argument("--policy", type=Path, default=POLICY_PATH)
    parser.add_argument("--version", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--require-signatures", action="store_true")
    parser.add_argument("--aggregate-sbom", type=Path)
    parser.add_argument("--checksum-manifest", type=Path)
    args = parser.parse_args()

    if not re.fullmatch(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)", args.version):
        raise SystemExit("release inventory requires a stable SemVer version")
    if not re.fullmatch(r"[0-9a-f]{40}", args.source_commit):
        raise SystemExit("source commit must be a full lowercase 40-character SHA")
    policy = json.loads(args.policy.read_text(encoding="utf-8"))
    if args.version != policy["release_version"]:
        raise SystemExit("version does not match the reviewed release-signing policy")
    staging = args.staging.resolve()
    entries = expected(policy, staging, args.version)
    expected_paths = {item["relative"] for item in entries}
    expected_signatures = {Path(f"{path}.asc") for path in expected_paths}

    actual = {
        path.relative_to(staging)
        for path in staging.rglob("*")
        if path.is_file()
        and (
            path.suffix in {".pom", ".jar", ".asc"}
            or path.name.endswith("-cyclonedx.json")
        )
    }
    if any("SNAPSHOT" in path.as_posix() for path in actual):
        raise SystemExit("SNAPSHOT content is forbidden")
    if any(
        token in path.as_posix()
        for path in actual
        for token in ("postgres-bulk-benchmarks", "spring-boot-basic", "spring-boot-data-jdbc")
    ):
        raise SystemExit("benchmark/example content is forbidden")
    allowed = expected_paths | (expected_signatures if args.require_signatures else set())
    if actual != allowed:
        raise SystemExit(
            "release staging inventory mismatch; "
            f"missing={sorted(map(str, allowed - actual))}, "
            f"unexpected={sorted(map(str, actual - allowed))}"
        )
    artifacts = []
    for item in entries:
        path = staging / item["relative"]
        if not path.is_file():
            raise SystemExit(f"missing release artifact: {item['relative']}")
        signature = Path(f"{path}.asc")
        artifacts.append(
            {
                "coordinates": {
                    "group_id": policy["group_id"],
                    "artifact_id": item["artifact_id"],
                    "version": args.version,
                },
                "filename": item["relative"].as_posix(),
                "type": item["type"],
                "classifier": item["classifier"],
                "sha256": sha256(path),
                "signature": (
                    {
                        "filename": signature.relative_to(staging).as_posix(),
                        "sha256": sha256(signature),
                    }
                    if signature.is_file()
                    else None
                ),
                "sbom_relation": item["sbom_relation"],
                "publishable": True,
                "role": item["role"],
            }
        )

    evidence = []
    if args.aggregate_sbom is not None:
        if not args.aggregate_sbom.is_file():
            raise SystemExit(f"missing supplemental evidence: {args.aggregate_sbom}")
        evidence.append(
            {
                "role": "aggregate-sbom",
                "filename": args.aggregate_sbom.name,
                "sha256": sha256(args.aggregate_sbom),
                "signature": f"{args.aggregate_sbom.name}.asc",
                "publishable": False,
            }
        )
    evidence.extend(
        [
            {
                "role": "release-inventory",
                "filename": args.output.name,
                "sha256": None,
                "signature": f"{args.output.name}.asc",
                "publishable": False,
            },
            {
                "role": "checksum-manifest",
                "filename": (args.checksum_manifest or Path("SHA256SUMS")).name,
                "sha256": None,
                "signature": f"{(args.checksum_manifest or Path('SHA256SUMS')).name}.asc",
                "publishable": False,
            },
        ]
    )

    manifest = {
        "schema_version": 1,
        "release": {
            "group_id": policy["group_id"],
            "version": args.version,
            "planned_tag": policy["planned_tag"],
            "tag_created": False,
            "source_commit": args.source_commit,
            "approved_signer_fingerprint": policy["approved_signer_fingerprint"],
        },
        "counts": {
            "central_artifacts": len(artifacts),
            "central_signatures": sum(item["signature"] is not None for item in artifacts),
            "primary_artifacts": sum(item["role"] != "published-sbom" for item in artifacts),
            "attached_sboms": sum(item["role"] == "published-sbom" for item in artifacts),
        },
        "artifacts": artifacts,
        "supplemental_evidence": evidence,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Release inventory: PASS ({len(artifacts)} Central artifacts)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
