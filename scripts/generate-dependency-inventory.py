#!/usr/bin/env python3
"""Normalize Maven dependency trees and create exact, non-resolving OSV inputs."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from collections import Counter
from pathlib import Path
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape


EXPECTED_MODULES = {
    "postgres-bulk-parent",
    "postgres-bulk-core",
    "postgres-bulk-pgjdbc",
    "postgres-bulk-hibernate",
    "postgres-bulk-spring-data",
    "postgres-bulk-spring-data-jdbc",
    "postgres-bulk-spring-boot-autoconfigure",
    "postgres-bulk-spring-boot-starter",
    "postgres-bulk-spring-boot-autoconfigure-jdbc",
    "postgres-bulk-spring-boot-starter-data-jdbc",
    "postgres-bulk-benchmarks",
    "spring-boot-basic",
    "spring-boot-data-jdbc",
}
INTERNAL_GROUPS = {"io.github.yravelo", "io.github.yravelo.examples"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def module_kind(root: dict[str, object]) -> str:
    artifact = str(root["artifactId"])
    group = str(root["groupId"])
    if artifact == "postgres-bulk-parent":
        return "parent"
    if artifact == "postgres-bulk-benchmarks":
        return "benchmark"
    if group == "io.github.yravelo.examples":
        return "example"
    return "product"


def occurrence_classification(kind: str, scopes: tuple[str, ...]) -> str:
    if kind == "benchmark":
        return "benchmark"
    if kind == "example":
        return "example"
    if "test" in scopes:
        return "test"
    if "provided" in scopes or "system" in scopes:
        return "build"
    return "production"


def coordinate(node: dict[str, object]) -> dict[str, str]:
    required = ("groupId", "artifactId", "version", "type")
    missing = [key for key in required if not str(node.get(key, "")).strip()]
    if missing:
        raise ValueError(f"dependency node lacks {', '.join(missing)}")
    return {
        "group_id": str(node["groupId"]),
        "artifact_id": str(node["artifactId"]),
        "version": str(node["version"]),
        "type": str(node["type"]),
        "classifier": str(node.get("classifier", "")),
    }


def package_key(item: dict[str, str]) -> tuple[str, str]:
    return (f"{item['group_id']}:{item['artifact_id']}", item["version"])


def pom_text(item: dict[str, str], suffix: str) -> str:
    type_element = "" if item["type"] == "jar" else f"\n      <type>{escape(item['type'])}</type>"
    classifier_element = (
        "" if not item["classifier"] else f"\n      <classifier>{escape(item['classifier'])}</classifier>"
    )
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>dev.postgresbulk.security</groupId>
  <artifactId>osv-input-{suffix}</artifactId>
  <version>1</version>
  <dependencies>
    <dependency>
      <groupId>{escape(item['group_id'])}</groupId>
      <artifactId>{escape(item['artifact_id'])}</artifactId>
      <version>{escape(item['version'])}</version>{type_element}{classifier_element}
    </dependency>
  </dependencies>
</project>
"""


def main() -> int:
    args = parse_args()
    repository = args.repository.resolve()
    output = args.output.resolve()
    tree_paths = sorted(repository.glob("**/target/security/dependency-tree.json"))
    roots: list[tuple[Path, dict[str, object]]] = []
    for path in tree_paths:
        # Do not consume an inventory copied beneath the repository-level target directory.
        if path.is_relative_to(repository / "target"):
            continue
        root = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(root, dict):
            raise ValueError(f"{path}: dependency tree root is not an object")
        roots.append((path, root))

    actual_modules = {str(root.get("artifactId", "")) for _, root in roots}
    if actual_modules != EXPECTED_MODULES:
        missing = sorted(EXPECTED_MODULES - actual_modules)
        unexpected = sorted(actual_modules - EXPECTED_MODULES)
        raise ValueError(f"incomplete reactor inventory; missing={missing}, unexpected={unexpected}")
    if len(roots) != len(EXPECTED_MODULES):
        raise ValueError("duplicate dependency-tree output detected")

    occurrences: list[dict[str, object]] = []
    modules: list[dict[str, str]] = []

    def walk(
        node: dict[str, object],
        *,
        module: str,
        kind: str,
        depth: int,
        scopes: tuple[str, ...],
        path: tuple[str, ...],
    ) -> None:
        item = coordinate(node)
        scope = str(node.get("scope", ""))
        current_scopes = scopes + ((scope,) if scope else ())
        name, version = package_key(item)
        current_path = path + (f"{name}:{version}",)
        occurrences.append(
            {
                **item,
                "name": name,
                "module": module,
                "module_kind": kind,
                "classification": occurrence_classification(kind, current_scopes),
                "scope": scope or "root",
                "direct": depth == 1,
                "depth": depth,
                "path": list(current_path),
                "internal": item["group_id"] in INTERNAL_GROUPS,
            }
        )
        children = node.get("children", [])
        if not isinstance(children, list):
            raise ValueError(f"{name}: children must be a list")
        for child in children:
            if not isinstance(child, dict):
                raise ValueError(f"{name}: child must be an object")
            walk(
                child,
                module=module,
                kind=kind,
                depth=depth + 1,
                scopes=current_scopes,
                path=current_path,
            )

    for tree_path, root in sorted(roots, key=lambda pair: str(pair[1]["artifactId"])):
        module = str(root["artifactId"])
        kind = module_kind(root)
        modules.append(
            {
                "artifact_id": module,
                "group_id": str(root["groupId"]),
                "version": str(root["version"]),
                "kind": kind,
                "tree": str(tree_path.relative_to(repository)),
            }
        )
        children = root.get("children", [])
        if not isinstance(children, list):
            raise ValueError(f"{module}: children must be a list")
        for child in children:
            if not isinstance(child, dict):
                raise ValueError(f"{module}: child must be an object")
            walk(child, module=module, kind=kind, depth=1, scopes=(), path=(module,))

    build_tool_path = repository / "config" / "security" / "build-tools.json"
    build_tool_document = json.loads(build_tool_path.read_text(encoding="utf-8"))
    build_tools = build_tool_document.get("build_tools")
    if not isinstance(build_tools, list) or not build_tools:
        raise ValueError("build-tool inventory is missing or empty")
    parent_pom = ET.parse(repository / "code" / "postgres-bulk-parent" / "pom.xml").getroot()
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    properties_node = parent_pom.find("m:properties", namespace)
    if properties_node is None:
        raise ValueError("parent POM properties are missing")
    pom_properties = {child.tag.rsplit("}", 1)[-1]: (child.text or "").strip() for child in properties_node}
    for tool in build_tools:
        if not isinstance(tool, dict):
            raise ValueError("build-tool entry must be an object")
        name = str(tool.get("name", ""))
        version = str(tool.get("version", ""))
        activation = str(tool.get("activation", ""))
        parts = name.split(":")
        if len(parts) != 2 or not version or not activation:
            raise ValueError(f"invalid build-tool entry: {tool}")
        property_name = tool.get("property")
        if property_name is not None and pom_properties.get(str(property_name)) != version:
            raise ValueError(
                f"build-tool {name} drifted from parent property {property_name}={pom_properties.get(str(property_name))}"
            )
        occurrences.append(
            {
                "group_id": parts[0],
                "artifact_id": parts[1],
                "version": version,
                "type": "jar",
                "classifier": "",
                "name": name,
                "module": "build-tooling",
                "module_kind": "build",
                "classification": "build",
                "scope": activation,
                "direct": True,
                "depth": 1,
                "path": ["build-tooling", f"{name}:{version}"],
                "internal": False,
            }
        )

    occurrences.sort(
        key=lambda item: (
            str(item["module"]),
            str(item["name"]),
            str(item["version"]),
            int(item["depth"]),
            ":".join(item["path"]),
        )
    )
    external_by_package: dict[tuple[str, str], dict[str, str]] = {}
    for item in occurrences:
        if item["internal"]:
            continue
        external_by_package.setdefault(
            (str(item["name"]), str(item["version"])),
            {
                "group_id": str(item["group_id"]),
                "artifact_id": str(item["artifact_id"]),
                "version": str(item["version"]),
                "type": str(item["type"]),
                "classifier": str(item["classifier"]),
            },
        )

    external_packages: list[dict[str, object]] = []
    osv_input = output / "osv-input"
    osv_input.mkdir(parents=True, exist_ok=True)
    for package in sorted(external_by_package.values(), key=package_key):
        name, version = package_key(package)
        related = [item for item in occurrences if item["name"] == name and item["version"] == version]
        classifications = sorted({str(item["classification"]) for item in related})
        consumer_reachable = any(
            item["classification"] == "production" and item["module_kind"] == "product"
            for item in related
        )
        digest = hashlib.sha256(f"{name}@{version}".encode()).hexdigest()[:16]
        package_dir = osv_input / digest
        package_dir.mkdir(exist_ok=True)
        (package_dir / "pom.xml").write_text(pom_text(package, digest), encoding="utf-8")
        external_packages.append(
            {
                "name": name,
                "version": version,
                "classifications": classifications,
                "consumer_reachable": consumer_reachable,
                "osv_input": str((package_dir / "pom.xml").relative_to(output)),
            }
        )

    counts = Counter(str(item["classification"]) for item in occurrences)
    inventory = {
        "schema_version": 1,
        "generator": "scripts/generate-dependency-inventory.py",
        "module_count": len(modules),
        "external_package_count": len(external_packages),
        "modules": modules,
        "summary": {
            "occurrence_count": len(occurrences),
            "direct_occurrence_count": sum(bool(item["direct"]) for item in occurrences),
            "consumer_reachable_external_count": sum(
                bool(item["consumer_reachable"]) for item in external_packages
            ),
            "classifications": dict(sorted(counts.items())),
        },
        "external_packages": external_packages,
        "occurrences": occurrences,
    }
    output.mkdir(parents=True, exist_ok=True)
    (output / "dependency-inventory.json").write_text(
        json.dumps(inventory, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print(
        f"Dependency inventory: {len(modules)} modules, "
        f"{len(occurrences)} occurrences, {len(external_packages)} external package versions"
    )
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"Dependency inventory failed: {exc}", file=sys.stderr)
        sys.exit(1)
