#!/usr/bin/env python3
"""Adversarial fixtures for network/tool/keyserver fail-closed behavior."""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class OperationalSecurityTests(unittest.TestCase):
    def test_scanner_checksum_mismatch_does_not_pass(self) -> None:
        with tempfile.TemporaryDirectory(prefix="postgres-bulk-checksum-fixture-") as temp:
            base = Path(temp)
            fake_bin = base / "bin"
            fake_bin.mkdir()
            fake_curl = fake_bin / "curl"
            fake_curl.write_text(
                "#!/usr/bin/env sh\n"
                "while [ \"$#\" -gt 0 ]; do\n"
                "  if [ \"$1\" = \"--output\" ]; then shift; printf corrupt > \"$1\"; exit 0; fi\n"
                "  shift\n"
                "done\n"
                "exit 2\n",
                encoding="utf-8",
            )
            fake_curl.chmod(0o755)
            environment = os.environ.copy()
            environment["PATH"] = f"{fake_bin}:{environment['PATH']}"
            environment["GITLEAKS_CACHE_DIR"] = str(base / "cache")
            result = subprocess.run(
                [str(ROOT / "scripts/check-secrets.sh"), "fixture"],
                cwd=ROOT,
                env=environment,
                check=False,
                capture_output=True,
                text=True,
            )
            self.assertNotEqual(0, result.returncode)

    def test_required_keyserver_failure_does_not_pass(self) -> None:
        with tempfile.TemporaryDirectory(prefix="postgres-bulk-keyserver-fixture-") as temp:
            fixture = Path(temp)
            scripts = fixture / "scripts"
            config = fixture / "config/security"
            keys = fixture / "docs/security/keys"
            scripts.mkdir(parents=True)
            config.mkdir(parents=True)
            keys.mkdir(parents=True)
            shutil.copy2(ROOT / "scripts/check-public-key.sh", scripts / "check-public-key.sh")
            policy = json.loads(
                (ROOT / "config/security/continuous-security-policy.json").read_text(encoding="utf-8")
            )
            source_key = ROOT / policy["signing"]["public_key"]
            target_key = keys / source_key.name
            shutil.copy2(source_key, target_key)
            policy["signing"]["public_key"] = str(target_key.relative_to(fixture))
            policy["signing"]["remote_url"] = "https://127.0.0.1:1/unavailable"
            (config / "continuous-security-policy.json").write_text(
                json.dumps(policy), encoding="utf-8"
            )
            result = subprocess.run(
                [str(scripts / "check-public-key.sh"), "remote"],
                cwd=fixture,
                check=False,
                capture_output=True,
                text=True,
                timeout=15,
            )
            self.assertNotEqual(0, result.returncode)


if __name__ == "__main__":
    unittest.main(verbosity=2)
