#!/usr/bin/env python3
"""Apply the reviewable PostgreSQL Bulk risk policy to an OSV JSON result."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import sys
from pathlib import Path
from typing import Any


REQUIRED_RISK_FIELDS = {
    "advisory_id",
    "aliases",
    "dependency",
    "version",
    "scope",
    "reason",
    "applicability",
    "compensating_controls",
    "owner",
    "review_date",
    "expiry",
    "removal_condition",
    "disposition",
}


def args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--osv-results", type=Path, required=True)
    parser.add_argument("--accepted-risks", type=Path, required=True)
    parser.add_argument("--today", type=dt.date.fromisoformat, default=dt.date.today())
    return parser.parse_args()


def load_object(path: Path) -> dict[str, Any]:
    loaded = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(loaded, dict):
        raise ValueError(f"{path}: root must be an object")
    return loaded


def severity_value(group: dict[str, Any]) -> float:
    try:
        return float(group.get("max_severity", 0) or 0)
    except (TypeError, ValueError):
        return 0.0


def severity_label(value: float) -> str:
    if value >= 9.0:
        return "CRITICAL"
    if value >= 7.0:
        return "HIGH"
    if value >= 4.0:
        return "MODERATE"
    return "LOW"


def main() -> int:
    parsed = args()
    inventory = load_object(parsed.inventory)
    osv = load_object(parsed.osv_results)
    accepted = load_object(parsed.accepted_risks)

    external = inventory.get("external_packages")
    if not isinstance(external, list) or not external:
        raise ValueError("dependency inventory has no external packages")
    expected = {(str(item["name"]), str(item["version"])) for item in external}
    classifications = {
        (str(item["name"]), str(item["version"])): tuple(item["classifications"])
        for item in external
    }

    scanned: set[tuple[str, str]] = set()
    findings: list[dict[str, Any]] = []
    results = osv.get("results")
    if not isinstance(results, list):
        raise ValueError("OSV result lacks results array")
    for result in results:
        if not isinstance(result, dict):
            raise ValueError("OSV result entry must be an object")
        packages = result.get("packages", [])
        if not isinstance(packages, list):
            raise ValueError("OSV packages must be an array")
        for entry in packages:
            package = entry.get("package", {})
            key = (str(package.get("name", "")), str(package.get("version", "")))
            if not all(key):
                raise ValueError("OSV package lacks name or version")
            scanned.add(key)
            groups = entry.get("groups", [])
            if groups is None:
                groups = []
            if not isinstance(groups, list):
                raise ValueError(f"{key[0]}:{key[1]} groups must be an array")
            for group in groups:
                aliases = {str(value) for value in group.get("aliases", [])}
                ids = [str(value) for value in group.get("ids", [])]
                aliases.update(ids)
                if not ids:
                    raise ValueError(f"{key[0]}:{key[1]} vulnerability group lacks an ID")
                findings.append(
                    {
                        "advisory_id": ids[0],
                        "aliases": aliases,
                        "dependency": key[0],
                        "version": key[1],
                        "severity": severity_value(group),
                        "classifications": classifications.get(key, ()),
                    }
                )

    if scanned != expected:
        missing = sorted(expected - scanned)
        unexpected = sorted(scanned - expected)
        raise ValueError(
            f"incomplete OSV coverage; expected={len(expected)}, scanned={len(scanned)}, "
            f"missing={missing[:5]}, unexpected={unexpected[:5]}"
        )

    raw_risks = accepted.get("accepted_risks")
    if not isinstance(raw_risks, list):
        raise ValueError("accepted risk document lacks accepted_risks array")
    risks: list[dict[str, Any]] = []
    for index, risk in enumerate(raw_risks):
        if not isinstance(risk, dict):
            raise ValueError(f"accepted risk #{index + 1} must be an object")
        missing_fields = sorted(REQUIRED_RISK_FIELDS - set(risk))
        if missing_fields:
            raise ValueError(f"accepted risk #{index + 1} lacks {missing_fields}")
        if risk["disposition"] not in {"WARN", "INFORMATIONAL"}:
            raise ValueError(f"accepted risk #{index + 1} has invalid disposition")
        expiry = dt.date.fromisoformat(str(risk["expiry"]))
        if expiry < parsed.today:
            raise ValueError(
                f"accepted risk {risk['advisory_id']} for {risk['dependency']} expired {expiry}"
            )
        risks.append(risk)

    used_risks: set[int] = set()
    blocks = 0
    warnings = 0
    informationals = 0
    for finding in sorted(
        findings, key=lambda item: (item["dependency"], item["version"], item["advisory_id"])
    ):
        matched_index = None
        for index, risk in enumerate(risks):
            risk_aliases = {str(risk["advisory_id"]), *map(str, risk["aliases"])}
            if (
                finding["dependency"] == risk["dependency"]
                and finding["version"] == risk["version"]
                and finding["aliases"] & risk_aliases
            ):
                matched_index = index
                break

        if matched_index is not None:
            used_risks.add(matched_index)
            disposition = str(risks[matched_index]["disposition"])
        else:
            production = "production" in finding["classifications"]
            disposition = "BLOCK" if production or finding["severity"] >= 7.0 else "WARN"

        if disposition == "BLOCK":
            blocks += 1
        elif disposition == "WARN":
            warnings += 1
        else:
            informationals += 1
        categories = ",".join(finding["classifications"]) or "unknown"
        print(
            f"[{disposition}] {finding['advisory_id']} {finding['dependency']} "
            f"{finding['version']} {severity_label(finding['severity'])} scope={categories}"
        )

    unused = [risks[index] for index in range(len(risks)) if index not in used_risks]
    if unused:
        names = [f"{risk['advisory_id']}:{risk['dependency']}:{risk['version']}" for risk in unused]
        raise ValueError(f"stale accepted risks must be removed: {names}")

    print(
        f"OSV coverage: {len(scanned)}/{len(expected)} package versions; "
        f"findings={len(findings)}, BLOCK={blocks}, WARN={warnings}, INFORMATIONAL={informationals}"
    )
    return 1 if blocks else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"Vulnerability gate failed closed: {exc}", file=sys.stderr)
        sys.exit(1)
