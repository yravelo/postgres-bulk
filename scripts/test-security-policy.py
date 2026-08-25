#!/usr/bin/env python3
"""Focused fixtures for SEC7 expiry and release-boundary behavior."""

from __future__ import annotations

import importlib.util
import tempfile
import unittest
from datetime import date
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


if __name__ == "__main__":
    unittest.main(verbosity=2)
