#!/usr/bin/env python3
"""Regression tests for the deterministic workflow-security rules."""

from __future__ import annotations

import copy
import importlib.util
import subprocess
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("check-workflow-security.py")
SPEC = importlib.util.spec_from_file_location("workflow_security", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Unable to load workflow security checker")
SECURITY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SECURITY)


class WorkflowSecurityTests(unittest.TestCase):
    def test_full_sha_with_version_comment_passes(self) -> None:
        text = (
            "      uses: actions/checkout@"
            "d23441a48e516b6c34aea4fa41551a30e30af803 # v6\n"
        )
        self.assertEqual([], SECURITY.action_errors(text))

    def test_mutable_action_tag_fails(self) -> None:
        errors = SECURITY.action_errors("      uses: actions/checkout@v6\n")
        self.assertTrue(any("full SHA" in error for error in errors))

    def test_pull_request_target_fails(self) -> None:
        workflow = {"on": {"pull_request_target": ""}}
        errors = SECURITY.trigger_errors("build.yml", workflow)
        self.assertTrue(any("forbidden triggers" in error for error in errors))

    def test_release_push_trigger_fails(self) -> None:
        workflow = {"on": {"push": {"branches": ["main"]}}}
        errors = SECURITY.trigger_errors("release.yml", workflow)
        self.assertTrue(any("unexpected triggers" in error for error in errors))

    def test_contents_write_fails(self) -> None:
        workflow = {
            "permissions": {"contents": "write"},
            "jobs": {"verify": {"timeout-minutes": "10"}},
        }
        errors = SECURITY.permission_errors(workflow)
        self.assertTrue(any("contents: read" in error for error in errors))

    def test_secret_in_candidate_is_detected(self) -> None:
        workflow, _ = SECURITY.load_workflow(
            Path(__file__).resolve().parents[1] / ".github" / "workflows" / "release.yml"
        )
        modified = copy.deepcopy(workflow)
        modified["jobs"]["candidate"]["steps"].append(
            {"run": "true", "env": {"TOKEN": "${{ secrets.CENTRAL_PASSWORD }}"}}
        )
        errors = SECURITY.release_errors(modified)
        self.assertTrue(any("candidate must not reference secrets" in error for error in errors))

    def test_malicious_benchmark_profile_fails_before_execution(self) -> None:
        runner = Path(__file__).with_name("run-benchmarks.sh")
        result = subprocess.run(
            [str(runner), "smoke;touch-not-allowed"],
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(2, result.returncode)
        self.assertIn("usage:", result.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
