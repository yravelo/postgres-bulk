#!/usr/bin/env python3
"""Focused fixtures for SEC7 expiry and release-boundary behavior."""

from __future__ import annotations

import importlib.util
import subprocess
import shutil
import tempfile
import unittest
from datetime import date, timedelta
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("check-security-policy.py")
SPEC = importlib.util.spec_from_file_location("security_policy", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Unable to load security-policy checker")
POLICY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(POLICY)

SAST_PATH = Path(__file__).with_name("check-static-analysis.py")
SAST_SPEC = importlib.util.spec_from_file_location("static_analysis", SAST_PATH)
if SAST_SPEC is None or SAST_SPEC.loader is None:
    raise RuntimeError("Unable to load static-analysis checker")
SAST = importlib.util.module_from_spec(SAST_SPEC)
SAST_SPEC.loader.exec_module(SAST)


class SecurityPolicyTests(unittest.TestCase):
    @staticmethod
    def release_preflight_fixture(base: Path) -> Path:
        repository = base / "repository"
        scripts = repository / "scripts"
        scripts.mkdir(parents=True)
        preflight = scripts / "check-release-security-preflight.sh"
        shutil.copy2(Path(__file__).with_name("check-release-security-preflight.sh"), preflight)
        for name in ("check-security-policy.py", "check-public-key.sh"):
            stub = scripts / name
            stub.write_text("#!/usr/bin/env sh\nexit 0\n", encoding="utf-8")
            stub.chmod(0o755)
        subprocess.run(["git", "init", "--initial-branch=main", str(repository)], check=True, capture_output=True)
        subprocess.run(["git", "-C", str(repository), "config", "user.name", "SEC8 Fixture"], check=True)
        subprocess.run(["git", "-C", str(repository), "config", "user.email", "fixture@example.invalid"], check=True)
        (repository / "tracked.txt").write_text("baseline\n", encoding="utf-8")
        subprocess.run(["git", "-C", str(repository), "add", "."], check=True)
        subprocess.run(["git", "-C", str(repository), "commit", "-m", "fixture"], check=True, capture_output=True)
        remote = base / "remote.git"
        subprocess.run(["git", "init", "--bare", str(remote)], check=True, capture_output=True)
        subprocess.run(["git", "-C", str(repository), "remote", "add", "origin", str(remote)], check=True)
        subprocess.run(["git", "-C", str(repository), "push", "--set-upstream", "origin", "main"], check=True, capture_output=True)
        return repository

    def test_valid_record_passes(self) -> None:
        warnings = POLICY.audit_expiring_records(
            [{"reviewed": "2026-01-01", "expires": "2026-12-01"}],
            expiry_field="expires",
            review_field="reviewed",
            label="fixture",
            today=date(2026, 8, 25),
            warning_days=30,
        )
        self.assertEqual([], warnings)

    def test_expired_accepted_risk_fails(self) -> None:
        with self.assertRaisesRegex(ValueError, "expired"):
            POLICY.audit_expiring_records(
                [{"review": "2026-01-01", "expiry": "2026-08-24"}],
                expiry_field="expiry",
                review_field="review",
                label="accepted-risk",
                today=date(2026, 8, 25),
                warning_days=30,
            )

    def test_expired_sast_exclusion_fails(self) -> None:
        with self.assertRaisesRegex(ValueError, "expired"):
            POLICY.audit_expiring_records(
                [{"reviewed": "2026-01-01", "review_by": "2026-08-24"}],
                expiry_field="review_by",
                review_field="reviewed",
                label="sast-exclusion",
                today=date(2026, 8, 25),
                warning_days=30,
            )

    def test_expired_license_exception_fails(self) -> None:
        with self.assertRaisesRegex(ValueError, "expired"):
            POLICY.audit_expiring_records(
                [{"reviewed_on": "2026-01-01", "expires": "2026-08-24"}],
                expiry_field="expires",
                review_field="reviewed_on",
                label="license-exception",
                today=date(2026, 8, 25),
                warning_days=30,
            )

    def test_near_expiry_warns(self) -> None:
        warnings = POLICY.audit_expiring_records(
            [{"reviewed": "2026-01-01", "expires": "2026-09-01"}],
            expiry_field="expires",
            review_field="reviewed",
            label="fixture",
            today=date(2026, 8, 25),
            warning_days=30,
        )
        self.assertEqual(["fixture[0] expires in 7 days"], warnings)

    def test_rel1_fails_while_reporting_channel_is_pending(self) -> None:
        policy = {
            "reporting_channel": {
                "status": "PENDING",
                "blocks_technical_security_work": False,
                "blocks_rel0": False,
                "blocks_rel1": True,
            }
        }
        POLICY.audit_preflight(policy, "technical")
        with self.assertRaisesRegex(ValueError, "REL1 preflight blocked"):
            POLICY.audit_preflight(policy, "rel1")

    def test_stale_sast_class_or_method_fails(self) -> None:
        with tempfile.TemporaryDirectory(prefix="postgres-bulk-sast-fixture-") as temp:
            parent = Path(temp)
            source = parent / "module/src/main/java/org/example/Reviewed.java"
            source.parent.mkdir(parents=True)
            source.write_text("package org.example; class Reviewed { void current() {} }", encoding="utf-8")
            self.assertTrue(SAST.exclusion_target_exists(parent, "org.example.Reviewed", "current"))
            self.assertFalse(SAST.exclusion_target_exists(parent, "org.example.Reviewed", "removed"))
            self.assertFalse(SAST.exclusion_target_exists(parent, "org.example.Removed", "current"))

    def test_new_module_requires_classification(self) -> None:
        drift = POLICY.inventory_drift({"parent", "module-a"}, {"parent", "module-a", "module-new"}, "POM")
        self.assertIsNotNone(drift)
        self.assertIn("module-new", drift or "")

    def test_tool_version_drift_fails(self) -> None:
        policy = POLICY.load_json(POLICY.POLICY_PATH)
        policy["tools"] = [dict(item) for item in policy["tools"]]
        next(item for item in policy["tools"] if item["id"] == "gitleaks")["version"] = "0.0.0"
        with self.assertRaisesRegex(ValueError, "gitleaks version drift"):
            POLICY.audit_tools(policy)

    def test_signing_key_below_minimum_validity_fails(self) -> None:
        policy = POLICY.load_json(POLICY.POLICY_PATH)
        policy["signing"] = dict(policy["signing"])
        today = date(2026, 8, 25)
        policy["signing"]["expires"] = (today + timedelta(days=10)).isoformat()
        with self.assertRaisesRegex(ValueError, "expires too soon"):
            POLICY.audit_signing_policy(policy, today)

    @staticmethod
    def write_sast_report(path: Path, *, bugs: int = 0, errors: int = 0, missing: int = 0) -> None:
        path.write_text(
            "<BugCollection>"
            "<Project><Plugin id='com.h3xstream.findsecbugs' enabled='true'/></Project>"
            f"<Errors errors='{errors}' missingClasses='{missing}'/>"
            f"<FindBugsSummary total_bugs='{bugs}'/>"
            "</BugCollection>",
            encoding="utf-8",
        )

    def test_new_medium_or_high_sast_finding_fails(self) -> None:
        with tempfile.TemporaryDirectory(prefix="postgres-bulk-sast-report-") as temp:
            report = Path(temp) / "spotbugs.xml"
            self.write_sast_report(report, bugs=1)
            self.assertIn("untriaged findings", SAST.report_error(report, "fixture") or "")

    def test_new_security_sast_finding_fails(self) -> None:
        with tempfile.TemporaryDirectory(prefix="postgres-bulk-findsecbugs-report-") as temp:
            report = Path(temp) / "spotbugs.xml"
            self.write_sast_report(report, bugs=1)
            self.assertIn("untriaged findings", SAST.report_error(report, "security-fixture") or "")

    def test_sast_analyzer_or_missing_class_error_fails(self) -> None:
        with tempfile.TemporaryDirectory(prefix="postgres-bulk-sast-error-") as temp:
            report = Path(temp) / "spotbugs.xml"
            self.write_sast_report(report, errors=1, missing=1)
            self.assertIn("analyzer errors", SAST.report_error(report, "fixture") or "")

    def test_configured_reporting_channel_clears_rel1_blocker(self) -> None:
        policy = {
            "reporting_channel": {
                "status": "CONFIGURED",
                "blocks_technical_security_work": False,
                "blocks_rel0": False,
                "blocks_rel1": False,
            }
        }
        POLICY.audit_preflight(policy, "rel1")

    def test_technical_preflight_rejects_dirty_tree(self) -> None:
        with tempfile.TemporaryDirectory(prefix="postgres-bulk-dirty-preflight-") as temp:
            repository = self.release_preflight_fixture(Path(temp))
            (repository / "dirty.txt").write_text("dirty\n", encoding="utf-8")
            result = subprocess.run(
                [str(repository / "scripts/check-release-security-preflight.sh"), "technical"],
                cwd=repository,
                check=False,
                capture_output=True,
                text=True,
            )
            self.assertNotEqual(0, result.returncode)
            self.assertIn("worktree is not clean", result.stderr)

    def test_technical_preflight_rejects_wrong_source_commit(self) -> None:
        with tempfile.TemporaryDirectory(prefix="postgres-bulk-wrong-source-") as temp:
            repository = self.release_preflight_fixture(Path(temp))
            (repository / "tracked.txt").write_text("local-only\n", encoding="utf-8")
            subprocess.run(["git", "-C", str(repository), "add", "tracked.txt"], check=True)
            subprocess.run(
                ["git", "-C", str(repository), "commit", "-m", "local only"],
                check=True,
                capture_output=True,
            )
            result = subprocess.run(
                [str(repository / "scripts/check-release-security-preflight.sh"), "technical"],
                cwd=repository,
                check=False,
                capture_output=True,
                text=True,
            )
            self.assertNotEqual(0, result.returncode)
            self.assertIn("HEAD differs from origin/main", result.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
