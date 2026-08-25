#!/usr/bin/env python3
"""Focused fixtures for SEC7 expiry and release-boundary behavior."""

from __future__ import annotations

import importlib.util
import unittest
from datetime import date
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("check-security-policy.py")
SPEC = importlib.util.spec_from_file_location("security_policy", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Unable to load security-policy checker")
POLICY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(POLICY)


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


if __name__ == "__main__":
    unittest.main(verbosity=2)
