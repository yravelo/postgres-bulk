#!/usr/bin/env python3
"""Fail-closed CycloneDX, Maven graph, license, and release identity audit."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, unquote, urlparse


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
ABSOLUTE_PATH = re.compile(r"^(?:/|[A-Za-z]:[\\/]|file:)")
SECRET_TEXT = re.compile(
    r"BEGIN (?:RSA |EC |OPENSSH |PGP )?PRIVATE KEY|"
    r"(?:password|passwd|token|secret)\s*[:=]\s*[^\s,]{8,}",
    re.IGNORECASE,
)
FORBIDDEN_TEST_GROUPS = ("org.junit", "org.testcontainers", "org.mockito")


@dataclass(frozen=True, order=True)
class Coordinate:
    group: str
    name: str
    version: str
    artifact_type: str = "jar"
    classifier: str = ""

    @property
    def gav(self) -> str:
        return f"{self.group}:{self.name}:{self.version}"


@dataclass
class Policy:
    root_group: str
    spec_version: str
    plugin_version: str
    project_type: str
    publishable: tuple[str, ...]
    non_publishable: frozenset[str]
    reviewed_cyclonedx_only: dict[str, frozenset[str]]
    forbidden_jdbc: frozenset[str]
    permissive: frozenset[str]
    review: frozenset[str]
    blocked: frozenset[str]
    reviewed_multiple: dict[str, dict[str, Any]]
    exceptions: dict[str, dict[str, Any]]


def fail(message: str) -> None:
    print(f"SBOM/license audit failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_object(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path}: root must be an object")
    return value


def load_policy() -> Policy:
    sbom = load_object(REPOSITORY_ROOT / "config/security/sbom-policy.json")
    license_policy = load_object(REPOSITORY_ROOT / "config/security/license-policy.json")
    if sbom.get("schema_version") != 1 or license_policy.get("schema_version") != 1:
        raise ValueError("unsupported security-policy schema")
    cdx = sbom.get("cyclonedx")
    if not isinstance(cdx, dict):
        raise ValueError("missing CycloneDX policy")
    reviews = license_policy.get("reviewed_multiple_licenses")
    exceptions = license_policy.get("exceptions")
    if not isinstance(reviews, list) or not isinstance(exceptions, list):
        raise ValueError("license reviews/exceptions must be arrays")
    review_map: dict[str, dict[str, Any]] = {}
    for record in reviews:
        if not isinstance(record, dict) or not isinstance(record.get("dependency"), str):
            raise ValueError("invalid multiple-license review")
        dependency = record["dependency"]
        if dependency in review_map:
            raise ValueError(f"duplicate license review: {dependency}")
        validate_review_record(record, dependency)
        review_map[dependency] = record
    exception_map: dict[str, dict[str, Any]] = {}
    for index, record in enumerate(exceptions):
        if not isinstance(record, dict) or not isinstance(record.get("dependency"), str):
            raise ValueError(f"license exception {index} must identify a dependency")
        dependency = record["dependency"]
        if dependency in exception_map or dependency in review_map:
            raise ValueError(f"duplicate license policy record: {dependency}")
        validate_review_record(record, dependency)
        exception_map[dependency] = record
    publishable = sbom.get("publishable_artifacts")
    if not isinstance(publishable, list) or len(publishable) != 9 or len(set(publishable)) != 9:
        raise ValueError("SBOM policy must contain exactly nine unique publishable artifacts")
    reviewed_cyclonedx_only = sbom.get("reviewed_cyclonedx_only_components")
    if not isinstance(reviewed_cyclonedx_only, dict) or not set(reviewed_cyclonedx_only) <= set(
        publishable
    ):
        raise ValueError("invalid reviewed CycloneDX-only component policy")
    if any(
        not isinstance(values, list) or len(values) != len(set(values))
        for values in reviewed_cyclonedx_only.values()
    ):
        raise ValueError("reviewed CycloneDX-only coordinates must be unique arrays")
    return Policy(
        root_group=str(cdx["root_group"]),
        spec_version=str(cdx["spec_version"]),
        plugin_version=str(cdx["plugin_version"]),
        project_type=str(cdx["project_type"]),
        publishable=tuple(str(value) for value in publishable),
        non_publishable=frozenset(str(value) for value in sbom["non_publishable_artifacts"]),
        reviewed_cyclonedx_only={
            str(artifact): frozenset(str(value) for value in coordinates)
            for artifact, coordinates in reviewed_cyclonedx_only.items()
        },
        forbidden_jdbc=frozenset(str(value) for value in sbom["forbidden_jdbc_starter_components"]),
        permissive=frozenset(str(value) for value in license_policy["permissive_license_ids"]),
        review=frozenset(str(value) for value in license_policy["review_license_ids"]),
        blocked=frozenset(str(value) for value in license_policy["blocked_strong_copyleft_ids"]),
        reviewed_multiple=review_map,
        exceptions=exception_map,
    )


def validate_review_record(record: dict[str, Any], label: str) -> None:
    required = ("dependency", "licenses", "scope", "reason", "owner", "reviewed_on", "expires")
    missing = [name for name in required if not record.get(name)]
    if missing:
        raise ValueError(f"{label}: missing {', '.join(missing)}")
    if record["scope"] != "compile/runtime":
        raise ValueError(f"{label}: scope must be compile/runtime")
    if record["owner"] != "yravelo":
        raise ValueError(f"{label}: unexpected owner")
    reviewed = date.fromisoformat(str(record["reviewed_on"]))
    expires = date.fromisoformat(str(record["expires"]))
    if reviewed > date.today() or expires < date.today() or expires <= reviewed:
        raise ValueError(f"{label}: invalid or expired review dates")
    licenses = record["licenses"]
    if not isinstance(licenses, list) or not licenses or len(set(licenses)) != len(licenses):
        raise ValueError(f"{label}: licenses must be a unique non-empty list")


def strings(value: Any) -> list[str]:
    found: list[str] = []
    if isinstance(value, str):
        found.append(value)
    elif isinstance(value, list):
        for item in value:
            found.extend(strings(item))
    elif isinstance(value, dict):
        for item in value.values():
            found.extend(strings(item))
    return found


def parse_purl(value: str) -> Coordinate:
    parsed = urlparse(value)
    if parsed.scheme != "pkg" or not parsed.path.startswith("maven/") or "@" not in parsed.path:
        raise ValueError(f"invalid Maven purl: {value}")
    package, version = parsed.path.removeprefix("maven/").rsplit("@", 1)
    if "/" not in package:
        raise ValueError(f"invalid Maven purl namespace: {value}")
    group, name = package.rsplit("/", 1)
    qualifiers = parse_qs(parsed.query)
    return Coordinate(
        unquote(group),
        unquote(name),
        unquote(version),
        qualifiers.get("type", ["jar"])[0],
        qualifiers.get("classifier", [""])[0],
    )


def component_coordinate(component: dict[str, Any]) -> Coordinate:
    purl = component.get("purl")
    if not isinstance(purl, str):
        raise ValueError("component has no Maven purl")
    coordinate = parse_purl(purl)
    if (
        component.get("group") != coordinate.group
        or component.get("name") != coordinate.name
        or component.get("version") != coordinate.version
    ):
        raise ValueError(f"component fields disagree with purl: {purl}")
    if component.get("bom-ref") != purl:
        raise ValueError(f"bom-ref must equal purl: {purl}")
    return coordinate


def license_ids(component: dict[str, Any]) -> tuple[str, ...]:
    result: list[str] = []
    for entry in component.get("licenses", []):
        if not isinstance(entry, dict):
            continue
        license_value = entry.get("license")
        if isinstance(license_value, dict):
            value = license_value.get("id") or license_value.get("name")
        else:
            value = entry.get("expression")
        if isinstance(value, str) and value.strip():
            result.append(value.strip())
    return tuple(sorted(set(result)))


def build_tool_coordinates() -> frozenset[str]:
    document = load_object(REPOSITORY_ROOT / "config/security/build-tools.json")
    tools = document.get("build_tools")
    if not isinstance(tools, list):
        raise ValueError("build tool inventory is missing")
    return frozenset(str(tool["name"]) for tool in tools if isinstance(tool, dict))


def audit_document(
    document: dict[str, Any],
    *,
    expected_name: str,
    version: str,
    policy: Policy,
    build_tools: frozenset[str],
    root_artifact_type: str = "jar",
) -> tuple[list[str], dict[str, Coordinate], dict[str, tuple[str, ...]]]:
    errors: list[str] = []
    if document.get("bomFormat") != "CycloneDX" or document.get("specVersion") != policy.spec_version:
        errors.append("wrong CycloneDX format/spec version")
    if document.get("version") != 1:
        errors.append("BOM document version must be 1")
    if "serialNumber" in document:
        errors.append("serialNumber must be disabled for reproducibility")
    metadata = document.get("metadata")
    if not isinstance(metadata, dict) or not isinstance(metadata.get("component"), dict):
        errors.append("missing metadata root component")
        return errors, {}, {}
    root = metadata["component"]
    try:
        root_coordinate = component_coordinate(root)
    except ValueError as exc:
        errors.append(str(exc))
        return errors, {}, {}
    if root_coordinate != Coordinate(
        policy.root_group, expected_name, version, root_artifact_type
    ):
        errors.append(f"wrong root identity: {root_coordinate.gav}")
    if root.get("type") != policy.project_type:
        errors.append("root component type must be library")
    tools = metadata.get("tools", {}).get("components", []) if isinstance(metadata.get("tools"), dict) else []
    if not any(
        isinstance(tool, dict)
        and tool.get("group") == "org.cyclonedx"
        and tool.get("name") == "cyclonedx-maven-plugin"
        and tool.get("version") == policy.plugin_version
        for tool in tools
    ):
        errors.append("pinned CycloneDX generator metadata is missing")

    all_strings = strings(document)
    if any("SNAPSHOT" in value for value in all_strings):
        errors.append("SNAPSHOT found")
    if any(ABSOLUTE_PATH.match(value) for value in all_strings):
        errors.append("absolute/file path found")
    serialized = json.dumps(document, sort_keys=True)
    if SECRET_TEXT.search(serialized):
        errors.append("secret-like metadata found")
    if "yusnier" in serialized.lower() or "postgress-copy" in serialized:
        errors.append("private workstation metadata found")

    ref_coordinates = {str(root["bom-ref"]): root_coordinate}
    licenses_by_gav: dict[str, tuple[str, ...]] = {}
    seen_coordinates = {root_coordinate}
    components = document.get("components", [])
    if not isinstance(components, list):
        errors.append("components must be an array")
        components = []
    for component in components:
        if not isinstance(component, dict):
            errors.append("component must be an object")
            continue
        try:
            coordinate = component_coordinate(component)
        except ValueError as exc:
            errors.append(str(exc))
            continue
        if coordinate in seen_coordinates:
            errors.append(f"duplicate component: {coordinate.gav}")
        seen_coordinates.add(coordinate)
        ref_coordinates[str(component["bom-ref"])] = coordinate
        ga = f"{coordinate.group}:{coordinate.name}"
        if coordinate.name.startswith("postgres-bulk-"):
            if coordinate.group != policy.root_group or coordinate.version != version:
                errors.append(f"wrong internal coordinate/version: {coordinate.gav}")
        if coordinate.group == "io.ybr.postgresbulk":
            errors.append(f"Java namespace used as Maven group: {coordinate.gav}")
        if coordinate.name in policy.non_publishable:
            errors.append(f"non-publishable component leaked: {coordinate.gav}")
        if coordinate.group.startswith(FORBIDDEN_TEST_GROUPS):
            errors.append(f"test dependency leaked: {coordinate.gav}")
        if ga in build_tools:
            errors.append(f"build plugin leaked into runtime components: {coordinate.gav}")
        ids = license_ids(component)
        licenses_by_gav[coordinate.gav] = ids
        if not ids:
            errors.append(f"unknown production license: {coordinate.gav}")
        if set(ids) & policy.blocked:
            errors.append(f"blocked strong-copyleft license for {coordinate.gav}: {ids}")
        unknown = set(ids) - policy.permissive - policy.review - policy.blocked
        if unknown:
            errors.append(f"unclassified production license for {coordinate.gav}: {sorted(unknown)}")
        if len(ids) == 1 and ids[0] in policy.review:
            exception = policy.exceptions.get(coordinate.gav)
            approved = tuple(sorted(str(value) for value in exception["licenses"])) if exception else ()
            if ids != approved:
                errors.append(
                    f"review license lacks exact approved exception: {coordinate.gav}: {ids[0]}"
                )

    dependencies = document.get("dependencies")
    if not isinstance(dependencies, list):
        errors.append("dependencies must be an array")
        dependencies = []
    dependency_refs: set[str] = set()
    for node in dependencies:
        if not isinstance(node, dict) or not isinstance(node.get("ref"), str):
            errors.append("invalid dependency node")
            continue
        ref = node["ref"]
        dependency_refs.add(ref)
        if ref not in ref_coordinates:
            errors.append(f"dependency node references missing component: {ref}")
        depends_on = node.get("dependsOn", [])
        if not isinstance(depends_on, list):
            errors.append(f"dependsOn must be an array: {ref}")
            continue
        for child in depends_on:
            if child not in ref_coordinates:
                errors.append(f"dependency edge references missing component: {child}")
    if dependency_refs != set(ref_coordinates):
        errors.append("dependency graph does not contain exactly one node per component/root")
    return errors, ref_coordinates, licenses_by_gav


def tree_graph(document: dict[str, Any]) -> tuple[set[Coordinate], set[tuple[Coordinate, Coordinate]]]:
    nodes: set[Coordinate] = set()
    edges: set[tuple[Coordinate, Coordinate]] = set()

    def walk(node: dict[str, Any]) -> Coordinate:
        coordinate = Coordinate(
            str(node["groupId"]),
            str(node["artifactId"]),
            str(node["version"]),
            str(node.get("type", "jar")),
            str(node.get("classifier", "")),
        )
        nodes.add(coordinate)
        children = node.get("children", [])
        if not isinstance(children, list):
            raise ValueError(f"invalid Maven children for {coordinate.gav}")
        for child in children:
            if not isinstance(child, dict):
                raise ValueError("invalid Maven dependency child")
            child_coordinate = walk(child)
            edges.add((coordinate, child_coordinate))
        return coordinate

    walk(document)
    return nodes, edges


def expanded_tree_graph(
    artifact: str,
    trees: dict[str, dict[str, Any]],
    policy: Policy,
) -> tuple[set[Coordinate], set[tuple[Coordinate, Coordinate]]]:
    """Expand reactor-module nodes using their independently resolved Maven trees."""
    nodes: set[Coordinate] = set()
    edges: set[tuple[Coordinate, Coordinate]] = set()
    pending = [artifact]
    expanded: set[str] = set()
    while pending:
        current = pending.pop()
        if current in expanded:
            continue
        expanded.add(current)
        current_nodes, current_edges = tree_graph(trees[current])
        nodes.update(current_nodes)
        edges.update(current_edges)
        pending.extend(
            coordinate.name
            for coordinate in current_nodes
            if coordinate.group == policy.root_group
            and coordinate.name in policy.publishable
            and coordinate.name not in expanded
        )
    return nodes, edges


def bom_graph(
    document: dict[str, Any], ref_coordinates: dict[str, Coordinate]
) -> tuple[set[Coordinate], set[tuple[Coordinate, Coordinate]]]:
    edges: set[tuple[Coordinate, Coordinate]] = set()
    for node in document["dependencies"]:
        parent = ref_coordinates[node["ref"]]
        for child_ref in node.get("dependsOn", []):
            edges.add((parent, ref_coordinates[child_ref]))
    return set(ref_coordinates.values()), edges


def semantic_projection(document: dict[str, Any]) -> dict[str, Any]:
    metadata = document["metadata"]
    components = [metadata["component"], *document.get("components", [])]
    projected_components = []
    for component in components:
        projected_components.append(
            {
                "bom-ref": component.get("bom-ref"),
                "group": component.get("group"),
                "name": component.get("name"),
                "version": component.get("version"),
                "type": component.get("type"),
                "purl": component.get("purl"),
                "scope": component.get("scope"),
                "licenses": license_ids(component),
                "hashes": sorted(
                    (value.get("alg"), value.get("content")) for value in component.get("hashes", [])
                ),
            }
        )
    return {
        "bomFormat": document.get("bomFormat"),
        "specVersion": document.get("specVersion"),
        "components": sorted(projected_components, key=lambda value: str(value["bom-ref"])),
        "dependencies": sorted(
            (node["ref"], tuple(sorted(node.get("dependsOn", []))))
            for node in document.get("dependencies", [])
        ),
    }


def audit_directory(
    directory: Path,
    version: str,
    policy: Policy,
    osv_inventory: Path | None,
) -> dict[str, Any]:
    if "SNAPSHOT" in version or not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version):
        raise ValueError(f"stable SemVer required, got {version}")
    build_tools = build_tool_coordinates()
    aggregate_path = directory / f"postgres-bulk-{version}-aggregate.cdx.json"
    expected_files = {
        aggregate_path,
        *(directory / f"{artifact}-{version}.cdx.json" for artifact in policy.publishable),
    }
    actual_files = set(directory.glob("*.cdx.json"))
    if actual_files != expected_files:
        missing = sorted(str(path.name) for path in expected_files - actual_files)
        unexpected = sorted(str(path.name) for path in actual_files - expected_files)
        raise ValueError(f"SBOM file inventory mismatch; missing={missing}, unexpected={unexpected}")

    all_errors: list[str] = []
    module_documents: dict[str, dict[str, Any]] = {}
    tree_documents: dict[str, dict[str, Any]] = {}
    module_components: dict[str, int] = {}
    module_edges: dict[str, int] = {}
    union_coordinates: set[Coordinate] = set()
    for artifact in policy.publishable:
        tree_path = directory / "dependency-trees" / f"{artifact}-{version}.json"
        if not tree_path.is_file():
            all_errors.append(f"missing Maven dependency tree: {tree_path.name}")
        else:
            tree_documents[artifact] = load_object(tree_path)
    for artifact in policy.publishable:
        path = directory / f"{artifact}-{version}.cdx.json"
        document = load_object(path)
        errors, refs, _ = audit_document(
            document,
            expected_name=artifact,
            version=version,
            policy=policy,
            build_tools=build_tools,
        )
        all_errors.extend(f"{path.name}: {error}" for error in errors)
        if artifact in tree_documents and not errors:
            tree_nodes, tree_edges = expanded_tree_graph(artifact, tree_documents, policy)
            bom_nodes, bom_edges = bom_graph(document, refs)
            missing_nodes = tree_nodes - bom_nodes
            unexpected_nodes = bom_nodes - tree_nodes
            reviewed_unexpected = policy.reviewed_cyclonedx_only.get(artifact, frozenset())
            actual_unexpected = {coordinate.gav for coordinate in unexpected_nodes}
            if missing_nodes or actual_unexpected != reviewed_unexpected:
                all_errors.append(
                    f"{artifact}: Maven/SBOM component mismatch "
                    f"missing={sorted(missing_nodes)} "
                    f"unexpected={sorted(actual_unexpected)} "
                    f"reviewed={sorted(reviewed_unexpected)}"
                )
            if not tree_edges <= bom_edges:
                all_errors.append(
                    f"{artifact}: Maven/SBOM dependency-edge mismatch "
                    f"missing={len(tree_edges - bom_edges)}"
                )
        module_documents[artifact] = document
        module_components[artifact] = len(document.get("components", []))
        module_edges[artifact] = sum(len(node.get("dependsOn", [])) for node in document.get("dependencies", []))
        union_coordinates.update(refs.values())

    aggregate = load_object(aggregate_path)
    errors, aggregate_refs, aggregate_licenses = audit_document(
        aggregate,
        expected_name="postgres-bulk-parent",
        version=version,
        policy=policy,
        build_tools=build_tools,
        root_artifact_type="pom",
    )
    all_errors.extend(f"{aggregate_path.name}: {error}" for error in errors)
    aggregate_coordinates = set(aggregate_refs.values())
    aggregate_product = {
        coordinate
        for coordinate in aggregate_coordinates
        if coordinate.group == policy.root_group and coordinate.name != "postgres-bulk-parent"
    }
    expected_product = {Coordinate(policy.root_group, name, version) for name in policy.publishable}
    if aggregate_product != expected_product:
        all_errors.append("aggregate does not contain exactly the nine internal release components")
    if not (
        union_coordinates
        - {Coordinate(policy.root_group, name, version) for name in policy.publishable}
        <= aggregate_coordinates
    ):
        all_errors.append("aggregate is missing a component present in a per-artifact SBOM")

    jdbc = module_documents["postgres-bulk-spring-boot-starter-data-jdbc"]
    jdbc_ga = {
        f"{component.get('group')}:{component.get('name')}" for component in jdbc.get("components", [])
    }
    leaked = sorted(jdbc_ga & policy.forbidden_jdbc)
    if leaked:
        all_errors.append(f"JDBC starter contains forbidden JPA/test components: {leaked}")

    external_licenses = {
        gav: ids for gav, ids in aggregate_licenses.items() if not gav.startswith(f"{policy.root_group}:")
    }
    actual_multiple = {gav: ids for gav, ids in external_licenses.items() if len(ids) > 1}
    if set(actual_multiple) != set(policy.reviewed_multiple):
        all_errors.append(
            "multiple-license review set drifted: "
            f"actual={sorted(actual_multiple)} policy={sorted(policy.reviewed_multiple)}"
        )
    for gav, ids in actual_multiple.items():
        expected_ids = tuple(sorted(str(value) for value in policy.reviewed_multiple[gav]["licenses"]))
        if ids != expected_ids:
            all_errors.append(f"multiple-license choice drifted for {gav}: {ids}")
    actual_exceptions = {
        gav: ids
        for gav, ids in external_licenses.items()
        if len(ids) == 1 and ids[0] in policy.review
    }
    if set(actual_exceptions) != set(policy.exceptions):
        all_errors.append(
            "single review-license exception set drifted: "
            f"actual={sorted(actual_exceptions)} policy={sorted(policy.exceptions)}"
        )

    osv_reconciled = False
    if osv_inventory is not None:
        inventory = load_object(osv_inventory)
        packages = inventory.get("external_packages")
        if not isinstance(packages, list):
            all_errors.append("OSV inventory has no external_packages array")
        else:
            osv_external = {
                (str(item["name"]), str(item["version"]))
                for item in packages
                if isinstance(item, dict) and item.get("consumer_reachable") is True
            }
            sbom_external = {
                (f"{coordinate.group}:{coordinate.name}", coordinate.version)
                for coordinate in aggregate_coordinates
                if coordinate.group != policy.root_group
            }
            if osv_external != sbom_external:
                all_errors.append(
                    "aggregate/OSV consumer inventory mismatch: "
                    f"missing={sorted(osv_external - sbom_external)} "
                    f"unexpected={sorted(sbom_external - osv_external)}"
                )
            else:
                osv_reconciled = True

    if all_errors:
        raise ValueError("\n".join(all_errors))
    aggregate_edges = sum(len(node.get("dependsOn", [])) for node in aggregate.get("dependencies", []))
    summary = {
        "schema_version": 1,
        "release_version": version,
        "cyclonedx_spec": policy.spec_version,
        "cyclonedx_plugin": policy.plugin_version,
        "per_artifact_sboms": len(policy.publishable),
        "aggregate_sbom": True,
        "module_component_counts": dict(sorted(module_components.items())),
        "module_dependency_edge_counts": dict(sorted(module_edges.items())),
        "aggregate_component_count": len(aggregate.get("components", [])),
        "aggregate_dependency_edge_count": aggregate_edges,
        "external_production_component_count": len(external_licenses),
        "licenses": sorted({license_id for ids in external_licenses.values() for license_id in ids}),
        "unknown_production_licenses": 0,
        "reviewed_multiple_license_components": len(actual_multiple),
        "unresolved_license_or_integrity_blocks": 0,
        "osv_inventory_reconciled": osv_reconciled,
    }
    (directory / "audit-summary.json").write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    license_lines = [
        f"{gav}\t{','.join(ids)}" for gav, ids in sorted(external_licenses.items())
    ]
    (directory / "production-licenses.txt").write_text(
        "\n".join(license_lines) + "\n", encoding="utf-8"
    )
    return summary


def compare_directories(first: Path, second: Path, version: str, policy: Policy) -> None:
    names = [
        *(f"{artifact}-{version}.cdx.json" for artifact in policy.publishable),
        f"postgres-bulk-{version}-aggregate.cdx.json",
    ]
    for name in names:
        left = semantic_projection(load_object(first / name))
        right = semantic_projection(load_object(second / name))
        if left != right:
            raise ValueError(f"semantic SBOM mismatch between clean generations: {name}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--version", default="0.1.0")
    parser.add_argument("--osv-inventory", type=Path)
    parser.add_argument("--compare", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    policy = load_policy()
    summary = audit_directory(args.directory, args.version, policy, args.osv_inventory)
    if args.compare is not None:
        audit_directory(args.compare, args.version, policy, args.osv_inventory)
        compare_directories(args.directory, args.compare, args.version, policy)
        print("Semantic SBOM comparison: PASS")
    print(
        "SBOM/license audit: PASS "
        f"({summary['per_artifact_sboms']} per-artifact + aggregate, "
        f"{summary['external_production_component_count']} external production components, "
        "0 unknown, 0 BLOCK)"
    )
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
        fail(str(exc))
