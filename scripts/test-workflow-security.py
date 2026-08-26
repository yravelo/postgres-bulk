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
    @staticmethod
    def load(name: str) -> dict:
        workflow, _ = SECURITY.load_workflow(
            Path(__file__).resolve().parents[1] / ".github" / "workflows" / name
        )
        return workflow

    def test_full_sha_with_version_comment_passes(self) -> None:
        text = (
            "      uses: actions/checkout@"
            "d23441a48e516b6c34aea4fa41551a30e30af803 # v6\n"
        )
        self.assertEqual([], SECURITY.action_errors(text))

    def test_mutable_action_tag_fails(self) -> None:
        errors = SECURITY.action_errors("      uses: actions/checkout@v6\n")
        self.assertTrue(any("full SHA" in error for error in errors))

    def test_unclassified_action_fails(self) -> None:
        sha = "a" * 40
        errors = SECURITY.action_errors(f"      uses: example/unknown@{sha} # v1\n")
        self.assertTrue(any("not allow-listed" in error for error in errors))

    def test_new_workflow_requires_classification(self) -> None:
        actual = set(SECURITY.WORKFLOWS) | {"unclassified.yml"}
        errors = SECURITY.workflow_inventory_errors(actual)
        self.assertTrue(any("inventory changed" in error for error in errors))

    def test_pull_request_target_fails(self) -> None:
        workflow = {"on": {"pull_request_target": ""}}
        errors = SECURITY.trigger_errors("build.yml", workflow)
        self.assertTrue(any("forbidden triggers" in error for error in errors))

    def test_workflow_run_fails(self) -> None:
        workflow = {"on": {"workflow_run": {"workflows": ["Build"]}}}
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

    def test_build_using_self_hosted_runner_fails(self) -> None:
        workflow = self.load("build.yml")
        workflow["jobs"]["verify"]["runs-on"] = [
            "self-hosted", "linux", "x64", "postgres-bulk-ci"
        ]
        errors = SECURITY.runner_boundary_errors("build.yml", workflow)
        self.assertTrue(any("persistent self-hosted runners are forbidden" in error for error in errors))

    def test_compatibility_actor_guard_fails(self) -> None:
        workflow = self.load("compatibility.yml")
        workflow["jobs"]["java"]["if"] = "github.actor == 'yravelo'"
        errors = SECURITY.runner_boundary_errors("compatibility.yml", workflow)
        self.assertTrue(any("must not gate public PR" in error for error in errors))

    def test_all_workflows_use_reviewed_hosted_runner(self) -> None:
        for name in SECURITY.WORKFLOWS:
            workflow = self.load(name)
            self.assertEqual([], SECURITY.runner_boundary_errors(name, workflow))

    def test_security_schedule_drift_fails(self) -> None:
        workflow = self.load("security.yml")
        workflow["on"]["schedule"] = [{"cron": "0 0 * * *"}]
        errors = SECURITY.security_errors(workflow)
        self.assertTrue(any("reviewed UTC cron" in error for error in errors))

    def test_security_secret_reference_fails(self) -> None:
        workflow = self.load("security.yml")
        workflow["jobs"]["validate"]["env"] = {"TOKEN": "${{ secrets.NOT_ALLOWED }}"}
        errors = SECURITY.runner_boundary_errors("security.yml", workflow)
        self.assertTrue(any("must not reference" in error for error in errors))

    def test_ci_persistent_maven_settings_fail(self) -> None:
        workflow = self.load("build.yml")
        setup = next(
            step
            for step in workflow["jobs"]["verify"]["steps"]
            if step.get("name") == "Set up Java"
        )
        setup["with"].pop("settings-path")
        errors = SECURITY.common_semantic_errors("build.yml", workflow)
        self.assertTrue(any("settings must stay in runner.temp" in error for error in errors))

    def test_build_unpinned_python_audit_dependency_fails(self) -> None:
        workflow = self.load("build.yml")
        install = next(
            step
            for step in workflow["jobs"]["verify"]["steps"]
            if step.get("name") == "Install pinned security audit dependencies"
        )
        install["run"] = "python3 -m pip install PyYAML"
        errors = SECURITY.build_errors(workflow)
        self.assertTrue(any("hash-pinned" in error for error in errors))

    def test_checkout_with_persistent_credentials_fails(self) -> None:
        workflow = self.load("build.yml")
        checkout = next(
            step
            for step in workflow["jobs"]["verify"]["steps"]
            if step.get("name") == "Check out repository"
        )
        checkout["with"]["persist-credentials"] = "true"
        errors = SECURITY.common_semantic_errors("build.yml", workflow)
        self.assertTrue(any("persist-credentials" in error for error in errors))

    def assert_public_pr_path_is_hosted_and_secret_free(self, name: str) -> None:
        workflow = self.load(name)
        self.assertTrue(SECURITY.public_pr_jobs_use_hosted_runner(workflow))
        self.assertEqual(set(), SECURITY.secret_references(workflow))

    def test_fork_pr_selects_hosted_without_secrets(self) -> None:
        for name in SECURITY.PUBLIC_PR_WORKFLOWS:
            self.assert_public_pr_path_is_hosted_and_secret_free(name)

    def test_dependabot_pr_cannot_select_self_hosted(self) -> None:
        for name in SECURITY.PUBLIC_PR_WORKFLOWS:
            self.assert_public_pr_path_is_hosted_and_secret_free(name)

    def test_external_actor_cannot_select_self_hosted(self) -> None:
        for name in SECURITY.PUBLIC_PR_WORKFLOWS:
            self.assert_public_pr_path_is_hosted_and_secret_free(name)

    def test_owner_same_repository_pr_selects_hosted(self) -> None:
        for name in SECURITY.PUBLIC_PR_WORKFLOWS:
            self.assert_public_pr_path_is_hosted_and_secret_free(name)

    def test_trusted_main_push_selects_hosted(self) -> None:
        for name in SECURITY.PUBLIC_PR_WORKFLOWS:
            workflow = self.load(name)
            self.assertTrue(SECURITY.public_pr_jobs_use_hosted_runner(workflow))

    def test_security_dispatch_selects_hosted(self) -> None:
        workflow = self.load("security.yml")
        self.assertTrue(SECURITY.public_pr_jobs_use_hosted_runner(workflow))

    def test_release_remains_on_github_hosted_runner(self) -> None:
        workflow = self.load("release.yml")
        self.assertEqual([], SECURITY.runner_boundary_errors("release.yml", workflow))
        self.assertEqual("ubuntu-latest", workflow["jobs"]["candidate"]["runs-on"])

    def test_release_upload_job_fails(self) -> None:
        workflow = self.load("release.yml")
        workflow["jobs"]["central-upload"] = {
            "runs-on": "ubuntu-latest",
            "timeout-minutes": "30",
            "steps": [],
        }
        errors = SECURITY.release_errors(workflow)
        self.assertTrue(any("candidate-only" in error for error in errors))


if __name__ == "__main__":
    unittest.main(verbosity=2)
