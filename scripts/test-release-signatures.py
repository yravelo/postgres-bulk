#!/usr/bin/env python3
"""Regression fixtures for SEC5 inventory, checksum, and OpenPGP gates."""

from __future__ import annotations

import hashlib
import importlib.util
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GENERATOR = ROOT / "scripts/generate-release-inventory.py"
CHECKER_PATH = ROOT / "scripts/check-release-signatures.py"
SPEC = importlib.util.spec_from_file_location("release_signature_checker", CHECKER_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Unable to load release signature checker")
CHECKER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECKER)


def run(
    *args: str, ok: bool = True, input_text: str | None = None
) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        args, check=False, capture_output=True, text=True, input=input_text
    )
    if ok and result.returncode != 0:
        raise AssertionError(result.stderr or result.stdout)
    return result


def fingerprint(home: Path) -> str:
    output = run(
        "gpg", "--batch", "--homedir", str(home), "--with-colons", "--list-secret-keys"
    ).stdout
    return next(line.split(":")[9] for line in output.splitlines() if line.startswith("fpr:"))


def sign(home: Path, signer: str, data: Path, digest: str = "SHA512") -> Path:
    signature = Path(f"{data}.asc")
    run(
        "gpg", "--batch", "--yes", "--homedir", str(home), "--armor", "--detach-sign",
        "--local-user", signer, "--digest-algo", digest, "--output", str(signature), str(data),
    )
    return signature


class ReleaseSignatureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.temp = tempfile.TemporaryDirectory(prefix="postgres-bulk-sec5-")
        cls.root = Path(cls.temp.name)
        cls.home = cls.root / "gnupg"
        cls.wrong_home = cls.root / "wrong-gnupg"
        for home, uid in ((cls.home, "SEC5 valid fixture"), (cls.wrong_home, "SEC5 wrong fixture")):
            home.mkdir(mode=0o700)
            run(
                "gpg", "--batch", "--homedir", str(home), "--pinentry-mode", "loopback",
                "--passphrase", "", "--quick-gen-key", uid, "rsa2048", "sign", "1d",
            )
        cls.signer = fingerprint(cls.home)
        cls.wrong_signer = fingerprint(cls.wrong_home)
        base_policy = json.loads(
            (ROOT / "config/security/release-signing-policy.json").read_text(encoding="utf-8")
        )
        base_policy["approved_signer_fingerprint"] = cls.signer
        cls.policy = cls.root / "policy.json"
        cls.policy.write_text(json.dumps(base_policy), encoding="utf-8")
        cls.candidate = cls.root / "candidate"
        cls.staging = cls.candidate / "staging"
        cls.evidence = cls.candidate / "evidence"
        cls.evidence.mkdir(parents=True)
        prefix = Path("io/github/yravelo")
        parent = base_policy["parent_artifact"]
        files = [prefix / parent / "0.1.0" / f"{parent}-0.1.0.pom"]
        for module in base_policy["publishable_artifacts"]:
            directory = prefix / module / "0.1.0"
            files.extend(
                directory / name
                for name in (
                    f"{module}-0.1.0.pom", f"{module}-0.1.0.jar",
                    f"{module}-0.1.0-sources.jar", f"{module}-0.1.0-javadoc.jar",
                    f"{module}-0.1.0-cyclonedx.json",
                )
            )
        for relative in files:
            path = cls.staging / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(f"fixture:{relative.as_posix()}\n".encode())
            sign(cls.home, cls.signer, path)
        cls.aggregate = cls.evidence / "postgres-bulk-0.1.0-aggregate.cdx.json"
        cls.aggregate.write_text('{"fixture":true}\n', encoding="utf-8")
        cls.inventory = cls.evidence / "release-inventory.json"
        run(
            "python3", str(GENERATOR), "--policy", str(cls.policy), "--staging", str(cls.staging),
            "--version", "0.1.0", "--source-commit", "a" * 40, "--output", str(cls.inventory),
            "--require-signatures", "--aggregate-sbom", str(cls.aggregate),
        )
        cls.checksums = cls.evidence / "SHA256SUMS"
        checksum_files = [p for p in cls.staging.rglob("*") if p.is_file() and not p.name.endswith(".asc")]
        checksum_files.extend([cls.aggregate, cls.inventory])
        cls.checksums.write_text(
            "".join(
                f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.relative_to(cls.candidate).as_posix()}\n"
                for path in sorted(checksum_files)
            ),
            encoding="utf-8",
        )
        for path in (cls.aggregate, cls.inventory, cls.checksums):
            sign(cls.home, cls.signer, path)

    @classmethod
    def tearDownClass(cls) -> None:
        cls.temp.cleanup()

    def checker(self) -> subprocess.CompletedProcess[str]:
        return run(
            "python3", str(CHECKER_PATH), "--policy", str(self.policy),
            "--staging", str(self.staging), "--evidence-directory", str(self.evidence),
            "--inventory", str(self.inventory), "--checksums", str(self.checksums),
            "--gnupghome", str(self.home), ok=False,
        )

    def test_valid_fixture(self) -> None:
        self.assertEqual(0, self.checker().returncode)

    def test_missing_signature(self) -> None:
        with self.assertRaisesRegex(ValueError, "missing signature"):
            CHECKER.verify_signature(
                self.aggregate, self.evidence / "missing.asc", self.home, self.signer
            )

    def test_wrong_signer(self) -> None:
        target = self.root / "wrong-signer.txt"
        target.write_text("wrong signer\n", encoding="utf-8")
        signature = sign(self.wrong_home, self.wrong_signer, target)
        verify_home = self.root / "verify-wrong"
        verify_home.mkdir(mode=0o700)
        exported = run("gpg", "--batch", "--homedir", str(self.wrong_home), "--armor", "--export", self.wrong_signer).stdout
        run("gpg", "--batch", "--homedir", str(verify_home), "--import", input_text=exported)
        with self.assertRaisesRegex(ValueError, "unapproved signer"):
            CHECKER.verify_signature(target, signature, verify_home, self.signer)

    def test_tampered_artifact(self) -> None:
        target = self.root / "tampered.txt"
        target.write_text("original\n", encoding="utf-8")
        signature = sign(self.home, self.signer, target)
        target.write_text("tampered\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "invalid signature"):
            CHECKER.verify_signature(target, signature, self.home, self.signer)

    def test_wrong_checksum(self) -> None:
        bad = self.root / "bad-SHA256SUMS"
        bad.write_text(f"{'0' * 64}  evidence/{self.aggregate.name}\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            CHECKER.parse_checksums(bad, self.candidate)

    def test_unexpected_artifact(self) -> None:
        unexpected = self.staging / "io/github/yravelo/unexpected/0.1.0/unexpected-0.1.0.jar"
        unexpected.parent.mkdir(parents=True)
        unexpected.write_text("unexpected", encoding="utf-8")
        result = self._generator_failure()
        unexpected.unlink()
        self.assertIn("unexpected", result.stderr + result.stdout)

    def test_snapshot_candidate(self) -> None:
        result = run(
            "python3", str(GENERATOR), "--policy", str(self.policy), "--staging", str(self.staging),
            "--version", "0.1.0-SNAPSHOT", "--source-commit", "a" * 40,
            "--output", str(self.root / "snapshot.json"), ok=False,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("stable SemVer", result.stderr + result.stdout)

    def test_benchmark_artifact(self) -> None:
        benchmark = self.staging / "io/github/yravelo/postgres-bulk-benchmarks/0.1.0/postgres-bulk-benchmarks-0.1.0.jar"
        benchmark.parent.mkdir(parents=True)
        benchmark.write_text("benchmark", encoding="utf-8")
        result = self._generator_failure()
        shutil.rmtree(self.staging / "io/github/yravelo/postgres-bulk-benchmarks")
        self.assertIn("benchmark/example", result.stderr + result.stdout)

    def _generator_failure(self) -> subprocess.CompletedProcess[str]:
        return run(
            "python3", str(GENERATOR), "--policy", str(self.policy), "--staging", str(self.staging),
            "--version", "0.1.0", "--source-commit", "a" * 40,
            "--output", str(self.root / "invalid.json"), "--require-signatures", ok=False,
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
