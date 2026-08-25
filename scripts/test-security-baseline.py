#!/usr/bin/env python3
"""Focused negative fixtures for the final SEC8 baseline registry."""

from __future__ import annotations

import copy
import importlib.util
import sys
import unittest
from datetime import date, timedelta
from pathlib import Path


SCRIPT = Path(__file__).with_name("check-security-baseline.py")
SPEC = importlib.util.spec_from_file_location("security_baseline", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("cannot load check-security-baseline.py")
BASELINE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = BASELINE
SPEC.loader.exec_module(BASELINE)


class SecurityBaselineTests(unittest.TestCase):
    def setUp(self) -> None:
        self.policy = BASELINE.load(BASELINE.POLICY_PATH)

    def test_real_policy_passes(self) -> None:
        BASELINE.audit_policy(self.policy)
        BASELINE.audit_external_boundary(self.policy)
        BASELINE.audit_namespaces_and_publication()
        BASELINE.audit_git_privacy()

    def test_configured_channel_requires_complete_evidence(self) -> None:
        continuous = BASELINE.load(
            BASELINE.ROOT / "config/security/continuous-security-policy.json"
        )
        continuous["reporting_channel"].pop("recovery_configured")
        with self.assertRaisesRegex(ValueError, "evidence drift: recovery_configured"):
            BASELINE.audit_external_boundary(self.policy, continuous)

    def test_pending_channel_must_remain_fail_closed(self) -> None:
        policy = copy.deepcopy(self.policy)
        prerequisite = next(
            item for item in policy["external_prerequisites"] if item["id"] == "EP-01"
        )
        prerequisite.update({"status": "PENDING", "blocks": ["REL1"]})
        continuous = {
            "reporting_channel": {
                "status": "PENDING",
                "blocks_technical_security_work": False,
                "blocks_rel0": False,
                "blocks_rel1": False,
            }
        }
        with self.assertRaisesRegex(ValueError, "pending.*blocking semantics drift"):
            BASELINE.audit_external_boundary(policy, continuous)

    def test_tracked_binary_or_generated_evidence_fails(self) -> None:
        errors = BASELINE.tracked_path_errors({"evidence/candidate.tar.gz", "target/osv.json"})
        self.assertEqual(2, len(errors))

    def test_unclassified_threat_fails(self) -> None:
        policy = copy.deepcopy(self.policy)
        policy["threats"][0]["status"] = "UNKNOWN"
        with self.assertRaisesRegex(ValueError, "invalid status"):
            BASELINE.audit_policy(policy)

    def test_missing_threat_fails(self) -> None:
        policy = copy.deepcopy(self.policy)
        policy["threats"].pop()
        with self.assertRaisesRegex(ValueError, "threat inventory"):
            BASELINE.audit_policy(policy)

    def test_expired_residual_review_fails(self) -> None:
        policy = copy.deepcopy(self.policy)
        policy["residual_risks"][0]["review_by"] = (date.today() - timedelta(days=1)).isoformat()
        with self.assertRaisesRegex(ValueError, "review is expired"):
            BASELINE.audit_policy(policy)

    def test_missing_scanner_decision_fails(self) -> None:
        policy = copy.deepcopy(self.policy)
        policy["scanner_gap_review"].pop()
        with self.assertRaisesRegex(ValueError, "scanner gap review"):
            BASELINE.audit_policy(policy)


if __name__ == "__main__":
    unittest.main(verbosity=2)
