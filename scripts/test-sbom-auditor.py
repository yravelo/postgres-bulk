#!/usr/bin/env python3
"""Focused fail-closed tests for the SEC4 SBOM auditor."""

from __future__ import annotations

import copy
import importlib.util
import sys
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("check-sbom.py")
SPEC = importlib.util.spec_from_file_location("check_sbom", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("cannot load check-sbom.py")
CHECK = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = CHECK
SPEC.loader.exec_module(CHECK)


def component(group: str, name: str, version: str, license_id: str = "Apache-2.0") -> dict:
    purl = f"pkg:maven/{group}/{name}@{version}?type=jar"
    return {
        "type": "library",
        "bom-ref": purl,
        "group": group,
        "name": name,
        "version": version,
        "purl": purl,
        "licenses": [{"license": {"id": license_id}}],
    }


def valid_document() -> dict:
    root = component("io.github.yravelo", "fixture-root", "0.1.0")
    dependency = component("org.example", "safe-library", "1.0.0")
    return {
        "bomFormat": "CycloneDX",
        "specVersion": "1.6",
        "version": 1,
        "metadata": {
            "tools": {
                "components": [
                    {
                        "type": "library",
                        "group": "org.cyclonedx",
                        "name": "cyclonedx-maven-plugin",
                        "version": "2.9.3",
                    }
                ]
            },
            "component": root,
        },
        "components": [dependency],
        "dependencies": [
            {"ref": root["bom-ref"], "dependsOn": [dependency["bom-ref"]]},
            {"ref": dependency["bom-ref"], "dependsOn": []},
        ],
    }


class SbomAuditorTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.policy = CHECK.load_policy()

    def errors(self, document: dict) -> list[str]:
        errors, _, _ = CHECK.audit_document(
            document,
            expected_name="fixture-root",
            version="0.1.0",
            policy=self.policy,
            build_tools=frozenset(),
        )
        return errors

    def test_valid_sbom_passes(self) -> None:
        self.assertEqual([], self.errors(valid_document()))

    def test_missing_component_fails(self) -> None:
        document = valid_document()
        document["components"] = []
        self.assertTrue(any("missing component" in error for error in self.errors(document)))

    def test_test_dependency_fails(self) -> None:
        document = valid_document()
        document["components"][0] = component("org.junit.jupiter", "junit-jupiter", "5.12.2")
        document["dependencies"][0]["dependsOn"] = [document["components"][0]["bom-ref"]]
        document["dependencies"][1]["ref"] = document["components"][0]["bom-ref"]
        self.assertTrue(any("test dependency" in error for error in self.errors(document)))

    def test_snapshot_fails(self) -> None:
        document = valid_document()
        document["components"][0] = component("org.example", "safe-library", "1.0-SNAPSHOT")
        self.assertTrue(any("SNAPSHOT" in error for error in self.errors(document)))

    def test_unknown_production_license_fails(self) -> None:
        document = valid_document()
        document["components"][0]["licenses"] = []
        self.assertTrue(any("unknown production license" in error for error in self.errors(document)))

    def test_wrong_internal_group_fails(self) -> None:
        document = valid_document()
        document["components"][0] = component("com.example", "postgres-bulk-core", "0.1.0")
        self.assertTrue(any("wrong internal coordinate" in error for error in self.errors(document)))

    def test_absolute_path_fails(self) -> None:
        document = valid_document()
        document["metadata"]["component"]["externalReferences"] = [
            {"type": "distribution", "url": "/home/private/artifact.jar"}
        ]
        self.assertTrue(any("absolute/file path" in error for error in self.errors(document)))

    def test_stale_license_exception_fails(self) -> None:
        errors = CHECK.license_review_set_errors(
            set(),
            set(),
            set(),
            {"org.example:removed-library:1.0.0"},
        )
        self.assertTrue(any("exception set drifted" in error for error in errors))


if __name__ == "__main__":
    unittest.main(verbosity=2)
