#!/usr/bin/env python3
"""Audit the integrated SEC0-SEC8 threat, prerequisite, and publication baseline."""

from __future__ import annotations

import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from datetime import date
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
POLICY_PATH = ROOT / "config/security/security-baseline-policy.json"
EXPECTED_THREATS = {f"T-{number:02d}" for number in range(1, 16)}
EXPECTED_SCANNER_REVIEWS = {
    "CodeQL", "Semgrep", "Snyk", "OWASP Dependency-Check", "Sonar",
    "OpenSSF Scorecard", "Sigstore", "GitHub artifact attestations",
}
THREAT_STATES = {"MITIGATED", "ACCEPTED", "DEFERRED", "NOT APPLICABLE"}
DEBT_CLASSES = {
    "BLOCKER", "EXTERNAL PREREQUISITE", "ACCEPTED RISK",
    "DEFERRED ENHANCEMENT", "FUTURE GENERATION",
}
MAVEN = {"m": "http://maven.apache.org/POM/4.0.0"}


def load(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path.relative_to(ROOT)} must contain an object")
    return value


def nonempty(record: dict[str, Any], fields: tuple[str, ...], label: str) -> None:
    missing = [field for field in fields if not record.get(field)]
    if missing:
        raise ValueError(f"{label} lacks {missing}")


def audit_policy(policy: dict[str, Any]) -> None:
    if policy.get("schema_version") != 1 or policy.get("owner") != "yravelo":
        raise ValueError("invalid baseline policy identity/schema")
    threats = policy.get("threats", [])
    if {item.get("id") for item in threats} != EXPECTED_THREATS:
        raise ValueError("SEC0 threat inventory is incomplete or unexpected")
    for item in threats:
        nonempty(
            item,
            ("id", "threat", "status", "preventive", "detective", "response", "limitation"),
            f"threat {item.get('id')}",
        )
        if item["status"] not in THREAT_STATES:
            raise ValueError(f"threat {item['id']} has invalid status")

    for collection in ("external_prerequisites", "residual_risks"):
        records = policy.get(collection, [])
        if not records:
            raise ValueError(f"{collection} must not be empty")
        for item in records:
            nonempty(item, ("id", "description", "classification", "owner", "review_by"), item.get("id", collection))
            if item["classification"] not in DEBT_CLASSES:
                raise ValueError(f"{item['id']} has invalid debt classification")
            if date.fromisoformat(item["review_by"]) < date.today():
                raise ValueError(f"{item['id']} review is expired")

    reviewed = {item.get("tool") for item in policy.get("scanner_gap_review", [])}
    if reviewed != EXPECTED_SCANNER_REVIEWS:
        raise ValueError("scanner gap review is incomplete")


def audit_external_boundary(
    policy: dict[str, Any], continuous: dict[str, Any] | None = None
) -> None:
    if continuous is None:
        continuous = load(ROOT / "config/security/continuous-security-policy.json")
    channel = continuous["reporting_channel"]
    prerequisite = next(item for item in policy["external_prerequisites"] if item["id"] == "EP-01")
    if channel["status"] == "PENDING" and prerequisite["status"] == "PENDING":
        if (
            channel["blocks_technical_security_work"]
            or channel["blocks_rel0"]
            or not channel["blocks_rel1"]
            or "REL1" not in prerequisite["blocks"]
        ):
            raise ValueError("pending private reporting channel blocking semantics drift")
        return
    if channel["status"] != "CONFIGURED" or prerequisite["status"] != "PASS":
        raise ValueError("private reporting channel state drift")
    if (
        channel["blocks_technical_security_work"]
        or channel["blocks_rel0"]
        or channel["blocks_rel1"]
        or prerequisite["blocks"]
    ):
        raise ValueError("configured private reporting channel blocking semantics drift")
    expected_evidence = {
        "provider": "Proton Mail",
        "public_address": "postgresbulk-security@proton.me",
        "verified_on": "2026-08-25",
        "owner_control_verified": True,
        "mfa_enabled": True,
        "recovery_configured": True,
        "delivery_test": "PASS",
        "reply_round_trip": "PASS",
    }
    for field, expected in expected_evidence.items():
        if channel.get(field) != expected:
            raise ValueError(f"configured private reporting evidence drift: {field}")
    if prerequisite.get("resolved_on") != channel["verified_on"]:
        raise ValueError("private reporting resolution date drift")


def audit_namespaces_and_publication() -> None:
    parent_path = ROOT / "code/postgres-bulk-parent/pom.xml"
    parent_text = parent_path.read_text(encoding="utf-8")
    if "<autoPublish>false</autoPublish>" not in parent_text or "<autoPublish>true</autoPublish>" in parent_text:
        raise ValueError("Central autoPublish guard drift")
    poms = [
        path
        for base in (ROOT / "code", ROOT / "examples", ROOT / "verification")
        for path in base.rglob("pom.xml")
    ]
    if any("io.github.postgresbulk" in path.read_text(encoding="utf-8") for path in poms):
        raise ValueError("provisional Maven coordinate remains active")
    parent = ET.parse(parent_path).getroot()
    group = parent.find("m:groupId", MAVEN)
    if group is None or (group.text or "").strip() != "io.github.yravelo":
        raise ValueError("Maven namespace drift")
    java_files = list((ROOT / "code/postgres-bulk-parent").rglob("src/main/java/*.java"))
    for path in java_files:
        match = re.search(r"^package\s+([\w.]+);", path.read_text(encoding="utf-8"), re.MULTILINE)
        if match and not match.group(1).startswith("io.ybr.postgresbulk"):
            raise ValueError(f"Java namespace drift: {path.relative_to(ROOT)}")

    release_text = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
    forbidden = ("git tag", "git push", "autoPublish=true", "gh release", "central-publish")
    if any(value in release_text for value in forbidden):
        raise ValueError("release workflow gained publication capability")
    workflow_text = "\n".join(
        (ROOT / ".github/workflows" / name).read_text(encoding="utf-8")
        for name in ("build.yml", "compatibility.yml", "security.yml", "release.yml")
    )
    if re.search(r"\$\{\{\s*secrets\.", workflow_text):
        raise ValueError("Build/Compatibility/Security/Release must remain secret-free")


def tracked_path_errors(paths: set[str]) -> list[str]:
    forbidden_suffixes = (".zip", ".tar", ".tar.gz", ".tgz", ".7z", ".p12", ".pfx", ".jks", ".keystore")
    errors = [f"unexpected tracked binary/archive: {path}" for path in sorted(paths) if path.endswith(forbidden_suffixes)]
    errors.extend(
        f"generated security/release evidence is tracked: {path}"
        for path in sorted(paths)
        if path.startswith("target/") or "/target/" in path
    )
    return errors


def audit_git_privacy() -> None:
    result = subprocess.run(
        ["git", "-C", str(ROOT), "ls-files", "-z"],
        check=True,
        capture_output=True,
    )
    paths = {value.decode("utf-8") for value in result.stdout.split(b"\0") if value}
    errors = tracked_path_errors(paths)
    local_path = re.compile(r"/(?:home|Users)/[^/\s]+/")
    for relative in paths:
        path = ROOT / relative
        if not path.is_file() or path.stat().st_size > 2_000_000:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        reviewed = text.replace("/home/postgres-bulk-runner/", "").replace("/home/private/", "")
        if local_path.search(reviewed):
            errors.append(f"tracked local absolute path: {relative}")
    if errors:
        raise ValueError("; ".join(errors))


def main() -> int:
    try:
        policy = load(POLICY_PATH)
        audit_policy(policy)
        audit_external_boundary(policy)
        audit_namespaces_and_publication()
        audit_git_privacy()
        if not (ROOT / "docs/security/security-baseline-closure.md").is_file():
            raise ValueError("security baseline closure document is missing")
    except (KeyError, OSError, ValueError, ET.ParseError, json.JSONDecodeError) as exc:
        print(f"Security baseline closure audit failed: {exc}", file=sys.stderr)
        return 1
    print(
        "Security baseline closure audit: PASS "
        f"({len(policy['threats'])} threats, {len(policy['residual_risks'])} residual risks, "
        f"{len(policy['external_prerequisites'])} external prerequisites)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
