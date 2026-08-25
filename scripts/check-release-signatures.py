#!/usr/bin/env python3
"""Fail-closed verifier for the SEC5 signed release candidate and evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
POLICY_PATH = ROOT / "config/security/release-signing-policy.json"
SAFE_HASH_IDS = {8: "SHA256", 9: "SHA384", 10: "SHA512", 11: "SHA224"}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify_signature(data: Path, signature: Path, homedir: Path, fingerprint: str) -> None:
    if not signature.is_file():
        raise ValueError(f"missing signature: {signature}")
    result = subprocess.run(
        [
            "gpg",
            "--batch",
            "--no-auto-key-retrieve",
            "--trust-model",
            "always",
            "--homedir",
            str(homedir),
            "--status-fd",
            "1",
            "--verify",
            str(signature),
            str(data),
        ],
        check=False,
        capture_output=True,
        text=True,
    )
    valid = [line.split() for line in result.stdout.splitlines() if " VALIDSIG " in line]
    if result.returncode != 0 or len(valid) != 1:
        raise ValueError(f"invalid signature: {signature.name}")
    fields = valid[0]
    signer = fields[2].upper()
    public_key_algorithm = int(fields[8])
    hash_algorithm = int(fields[9])
    if signer != fingerprint:
        raise ValueError(f"signature from unapproved signer: {signature.name}")
    if public_key_algorithm != 1:
        raise ValueError(f"non-RSA release signature: {signature.name}")
    if hash_algorithm not in SAFE_HASH_IDS:
        raise ValueError(f"forbidden or unknown signature hash: {signature.name}")


def parse_checksums(path: Path, root: Path) -> dict[str, str]:
    entries: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        digest, separator, relative = line.partition("  ")
        if not separator or len(digest) != 64 or any(c not in "0123456789abcdef" for c in digest):
            raise ValueError("malformed SHA256SUMS entry")
        candidate = (root / relative).resolve()
        if not candidate.is_relative_to(root.resolve()) or not candidate.is_file():
            raise ValueError(f"unsafe or missing checksum target: {relative}")
        if relative in entries:
            raise ValueError(f"duplicate checksum target: {relative}")
        entries[relative] = digest
        if sha256(candidate) != digest:
            raise ValueError(f"checksum mismatch: {relative}")
    return entries


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--policy", type=Path, default=POLICY_PATH)
    parser.add_argument("--staging", type=Path, required=True)
    parser.add_argument("--evidence-directory", type=Path, required=True)
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--checksums", type=Path, required=True)
    parser.add_argument("--gnupghome", type=Path, required=True)
    parser.add_argument("--expected-source-commit", required=True)
    args = parser.parse_args()

    policy = json.loads(args.policy.read_text(encoding="utf-8"))
    inventory = json.loads(args.inventory.read_text(encoding="utf-8"))
    fingerprint = policy["approved_signer_fingerprint"]
    release = inventory.get("release", {})
    if release.get("approved_signer_fingerprint") != fingerprint:
        raise SystemExit("release inventory signer fingerprint differs from policy")
    if release.get("group_id") != policy["group_id"]:
        raise SystemExit("release inventory group differs from policy")
    if release.get("planned_tag") != policy["planned_tag"]:
        raise SystemExit("release inventory planned tag differs from policy")
    if release.get("source_commit") != args.expected_source_commit:
        raise SystemExit("release inventory source commit differs from expected commit")
    if release.get("version") != policy["release_version"] or release.get("tag_created") is not False:
        raise SystemExit("release inventory version/tag state is not the reviewed candidate")
    if len(inventory.get("artifacts", [])) != policy["expected_central_artifacts"]:
        raise SystemExit("unexpected Central artifact count")
    evidence_roles = {item.get("role") for item in inventory.get("supplemental_evidence", [])}
    if evidence_roles != {"aggregate-sbom", "release-inventory", "checksum-manifest"}:
        raise SystemExit("unexpected supplemental evidence inventory")

    required_checksum_paths: set[str] = set()
    signatures = 0
    for item in inventory["artifacts"]:
        data = args.staging / item["filename"]
        signature_info = item.get("signature")
        if not data.is_file() or sha256(data) != item["sha256"]:
            raise SystemExit(f"artifact digest mismatch: {item['filename']}")
        if not isinstance(signature_info, dict):
            raise SystemExit(f"missing signature inventory: {item['filename']}")
        signature = args.staging / signature_info["filename"]
        if sha256(signature) != signature_info["sha256"]:
            raise SystemExit(f"signature digest mismatch: {signature_info['filename']}")
        try:
            verify_signature(data, signature, args.gnupghome, fingerprint)
        except ValueError as exc:
            raise SystemExit(str(exc)) from exc
        required_checksum_paths.add(f"staging/{item['filename']}")
        signatures += 1

    aggregate = args.evidence_directory / f"postgres-bulk-{policy['release_version']}-aggregate.cdx.json"
    supplemental = [aggregate, args.inventory, args.checksums]
    for data in supplemental:
        try:
            verify_signature(data, Path(f"{data}.asc"), args.gnupghome, fingerprint)
        except ValueError as exc:
            raise SystemExit(str(exc)) from exc
        signatures += 1
    required_checksum_paths.add(f"evidence/{aggregate.name}")
    required_checksum_paths.add(f"evidence/{args.inventory.name}")

    try:
        checksum_entries = parse_checksums(args.checksums, args.evidence_directory.parent)
    except ValueError as exc:
        raise SystemExit(str(exc)) from exc
    if set(checksum_entries) != required_checksum_paths:
        raise SystemExit(
            "checksum inventory mismatch; "
            f"missing={sorted(required_checksum_paths - set(checksum_entries))}, "
            f"unexpected={sorted(set(checksum_entries) - required_checksum_paths)}"
        )
    if signatures != policy["expected_total_signatures"]:
        raise SystemExit(f"unexpected verified signature count: {signatures}")
    print(
        "Release signature verification: PASS "
        f"({policy['expected_central_signatures']} Central + 3 evidence signatures)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
