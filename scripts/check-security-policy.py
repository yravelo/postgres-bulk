#!/usr/bin/env python3
"""Validate SEC7 inventory, expiry, tool freshness and release-boundary policy."""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from datetime import date
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
POLICY_PATH = ROOT / "config/security/continuous-security-policy.json"
MAVEN_NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}


def load_json(path: Path) -> dict[str, Any]:
    loaded = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(loaded, dict):
        raise ValueError(f"{path.relative_to(ROOT)} must contain an object")
    return loaded


def parse_iso(value: Any, field: str) -> date:
    if not isinstance(value, str):
        raise ValueError(f"{field} must be an ISO date")
    try:
        return date.fromisoformat(value)
    except ValueError as exc:
        raise ValueError(f"{field} is not a valid ISO date: {value}") from exc


def audit_expiring_records(
    records: list[dict[str, Any]],
    *,
    expiry_field: str,
    review_field: str,
    label: str,
    today: date,
    warning_days: int,
) -> list[str]:
    warnings: list[str] = []
    for index, record in enumerate(records):
        expiry = parse_iso(record.get(expiry_field), f"{label}[{index}].{expiry_field}")
        reviewed = parse_iso(record.get(review_field), f"{label}[{index}].{review_field}")
        if reviewed > today:
            raise ValueError(f"{label}[{index}] has a future review date")
        remaining = (expiry - today).days
        if remaining < 0:
            raise ValueError(f"{label}[{index}] expired on {expiry.isoformat()}")
        if remaining <= warning_days:
            warnings.append(f"{label}[{index}] expires in {remaining} days")
    return warnings


def pom_properties(path: Path) -> dict[str, str]:
    root = ET.parse(path).getroot()
    node = root.find("m:properties", MAVEN_NAMESPACE)
    if node is None:
        return {}
    return {child.tag.rsplit("}", 1)[-1]: (child.text or "").strip() for child in node}


def audit_inventory(policy: dict[str, Any]) -> list[str]:
    warnings: list[str] = []
    workflows = {
        path.name
        for pattern in ("*.yml", "*.yaml")
        for path in (ROOT / ".github/workflows").glob(pattern)
    }
    expected_workflows = set(policy["workflows"])
    if workflows != expected_workflows:
        raise ValueError(
            f"workflow drift: expected={sorted(expected_workflows)}, actual={sorted(workflows)}"
        )

    parent = ET.parse(ROOT / "code/postgres-bulk-parent/pom.xml").getroot()
    actual_modules = [
        (node.text or "").strip() for node in parent.findall("m:modules/m:module", MAVEN_NAMESPACE)
    ]
    if actual_modules != policy["modules"]["reactor"]:
        raise ValueError("reactor module inventory drift")

    actual_poms = sorted(
        str(path.relative_to(ROOT))
        for base in (ROOT / "code", ROOT / "examples", ROOT / "verification")
        for path in base.rglob("pom.xml")
    )
    expected_poms = sorted(policy["modules"]["all_poms"])
    if actual_poms != expected_poms:
        raise ValueError(
            f"POM inventory drift: missing={sorted(set(expected_poms) - set(actual_poms))}, "
            f"unexpected={sorted(set(actual_poms) - set(expected_poms))}"
        )

    sbom = load_json(ROOT / "config/security/sbom-policy.json")
    signing = load_json(ROOT / "config/security/release-signing-policy.json")
    publishable = policy["modules"]["publishable"]
    if publishable != sbom["publishable_artifacts"] or publishable != signing["publishable_artifacts"]:
        raise ValueError("publishable module inventories disagree")
    if policy["modules"]["non_publishable"] != sbom["non_publishable_artifacts"]:
        raise ValueError("non-publishable module inventories disagree")

    for gate in policy["gates"]:
        command = gate.get("command", "")
        if not gate.get("owner", policy.get("owner")):
            raise ValueError(f"gate {gate.get('id')} lacks an owner")
        if not gate.get("modes") or not gate.get("ci") or not gate.get("failure"):
            raise ValueError(f"gate {gate.get('id')} has incomplete operational metadata")
        first = command.split()[0]
        if first.startswith("./scripts/") and not (ROOT / first[2:]).is_file():
            raise ValueError(f"gate {gate.get('id')} references missing command {first}")
    return warnings


def audit_tools(policy: dict[str, Any]) -> None:
    tools = {item["id"]: item for item in policy["tools"]}
    if len(tools) != len(policy["tools"]):
        raise ValueError("duplicate tool IDs")
    for tool in tools.values():
        if not all(tool.get(field) for field in ("version", "source", "update", "owner")):
            raise ValueError(f"tool {tool.get('id')} lacks freshness metadata")

    script_expectations = {
        "gitleaks": (ROOT / "scripts/check-secrets.sh", r"GITLEAKS_VERSION=([0-9.]+)"),
        "osv-scanner": (ROOT / "scripts/check-vulnerabilities.sh", r'OSV_VERSION="([0-9.]+)"'),
        "maven-dependency-plugin": (
            ROOT / "scripts/check-vulnerabilities.sh",
            r'MAVEN_DEPENDENCY_PLUGIN_VERSION="([0-9.]+)"',
        ),
    }
    for tool_id, (path, pattern) in script_expectations.items():
        match = re.search(pattern, path.read_text(encoding="utf-8"))
        if match is None or match.group(1) != tools[tool_id]["version"]:
            raise ValueError(f"{tool_id} version drift in {path.relative_to(ROOT)}")

    properties = pom_properties(ROOT / "code/postgres-bulk-parent/pom.xml")
    property_tools = {
        "spotbugs-maven-plugin": "spotbugs-maven-plugin.version",
        "spotbugs": "spotbugs.version",
        "findsecbugs": "findsecbugs.version",
        "cyclonedx-maven-plugin": "cyclonedx-maven-plugin.version",
    }
    for tool_id, property_name in property_tools.items():
        if properties.get(property_name) != tools[tool_id]["version"]:
            raise ValueError(f"{tool_id} version drift in parent POM")

    sbom = load_json(ROOT / "config/security/sbom-policy.json")
    if sbom["cyclonedx"]["plugin_version"] != tools["cyclonedx-maven-plugin"]["version"]:
        raise ValueError("CycloneDX policy/tool version drift")

    build_tools = load_json(ROOT / "config/security/build-tools.json")["build_tools"]
    versions = {(item["name"], item["version"]) for item in build_tools}
    expected_coordinates = {
        "maven-dependency-plugin": "org.apache.maven.plugins:maven-dependency-plugin",
        "spotbugs-maven-plugin": "com.github.spotbugs:spotbugs-maven-plugin",
        "spotbugs": "com.github.spotbugs:spotbugs",
        "findsecbugs": "com.h3xstream.findsecbugs:findsecbugs-plugin",
        "cyclonedx-maven-plugin": "org.cyclonedx:cyclonedx-maven-plugin",
    }
    for tool_id, coordinate in expected_coordinates.items():
        if (coordinate, tools[tool_id]["version"]) not in versions:
            raise ValueError(f"{tool_id} version drift in build-tools inventory")


def audit_expiries(policy: dict[str, Any], today: date) -> list[str]:
    warning_days = int(policy["review_warning_days"])
    risks = load_json(ROOT / "config/security/accepted-dependency-risks.json")["accepted_risks"]
    licenses = load_json(ROOT / "config/security/license-policy.json")
    warnings = audit_expiring_records(
        risks,
        expiry_field="expiry",
        review_field="review_date",
        label="accepted-risk",
        today=today,
        warning_days=warning_days,
    )
    for key in ("reviewed_multiple_licenses", "exceptions"):
        warnings.extend(
            audit_expiring_records(
                licenses[key],
                expiry_field="expires",
                review_field="reviewed_on",
                label=f"license-{key}",
                today=today,
                warning_days=warning_days,
            )
        )
    return warnings


def audit_signing_policy(policy: dict[str, Any], today: date) -> list[str]:
    signing = policy["signing"]
    release = load_json(ROOT / "config/security/release-signing-policy.json")
    if signing["fingerprint"] != release["approved_signer_fingerprint"]:
        raise ValueError("OpenPGP fingerprint drift")
    expected_name = Path(signing["public_key"]).name
    if expected_name != release["public_key_file"] or not (ROOT / signing["public_key"]).is_file():
        raise ValueError("tracked OpenPGP public-key path drift")
    expiry = parse_iso(signing["expires"], "signing.expires")
    remaining = (expiry - today).days
    if remaining < int(signing["blocking_days"]):
        raise ValueError(f"release signing key expires too soon ({remaining} days)")
    return [f"release signing key expires in {remaining} days"] if remaining <= int(signing["warning_days"]) else []


def audit_preflight(policy: dict[str, Any], preflight: str | None) -> None:
    if preflight is None:
        return
    channel = policy["reporting_channel"]
    if preflight == "technical":
        if channel["blocks_technical_security_work"] or channel["blocks_rel0"]:
            raise ValueError("reporting-channel policy unexpectedly blocks technical/REL0 work")
        print("Technical security preflight: PASS (private reporting channel remains PENDING)")
        return
    if channel["status"] != "CONFIGURED" or channel["blocks_rel1"]:
        raise ValueError("REL1 preflight blocked: private vulnerability reporting channel is PENDING")
    print("REL1 security preflight: PASS")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--policy", type=Path, default=POLICY_PATH)
    parser.add_argument("--today", type=date.fromisoformat, default=date.today())
    parser.add_argument("--preflight", choices=("technical", "rel1"))
    args = parser.parse_args()
    try:
        policy = load_json(args.policy)
        if policy.get("schema_version") != 1:
            raise ValueError("continuous-security policy schema_version must be 1")
        warnings = audit_inventory(policy)
        audit_tools(policy)
        warnings.extend(audit_expiries(policy, args.today))
        warnings.extend(audit_signing_policy(policy, args.today))
        audit_preflight(policy, args.preflight)
    except (KeyError, OSError, ValueError, ET.ParseError, json.JSONDecodeError) as exc:
        print(f"Continuous security policy failed: {exc}", file=sys.stderr)
        return 1
    for warning in warnings:
        print(f"SECURITY POLICY WARNING: {warning}")
    print(
        "Continuous security policy: PASS "
        f"({len(policy['gates'])} gates, {len(policy['tools'])} tools, "
        f"{len(policy['workflows'])} workflows, {len(policy['modules']['all_poms'])} POMs)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
