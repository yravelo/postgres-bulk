#!/usr/bin/env python3
"""Fail closed when a GitHub Actions security invariant is broken."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

try:
    import yaml
except ImportError as exc:  # pragma: no cover - exercised by CI prerequisite installation
    raise SystemExit("PyYAML is required: install the python3-yaml package") from exc


ROOT = Path(__file__).resolve().parents[1]
CONTINUOUS_POLICY = json.loads(
    (ROOT / "config/security/continuous-security-policy.json").read_text(encoding="utf-8")
)
WORKFLOWS = {
    name: set(triggers) for name, triggers in CONTINUOUS_POLICY["workflows"].items()
}
FORBIDDEN_TRIGGERS = {
    "pull_request_target",
    "workflow_run",
    "repository_dispatch",
}
APPROVED_ACTIONS = {
    name: (record["sha"], record["version"])
    for name, record in CONTINUOUS_POLICY["actions"].items()
}
BENCHMARK_PROFILES = {
    "smoke",
    "baseline",
    "large",
    "multi-schema-smoke",
    "multi-schema-baseline",
}
HOSTED_RUNNER = "ubuntu-latest"
PUBLIC_PR_WORKFLOWS = {"build.yml", "compatibility.yml", "dependency-review.yml"}
HARDENED_CHECKOUT_WORKFLOWS = {
    "build.yml",
    "compatibility.yml",
    "dependency-review.yml",
    "security.yml",
}
COMPATIBILITY_LANES = {
    "multi-schema-composition": None,
    "java": ("java", ["21", "25"]),
    "boot-minimum": None,
    "postgres": ("postgres", ["16.14-alpine", "17.10-alpine"]),
    "newest": None,
    "hibernate-adapter": ("hibernate", ["6.6.15.Final", "6.6.55.Final"]),
    "pgjdbc": ("pgjdbc", ["42.7.5", "42.7.13"]),
}
USES_LINE = re.compile(r"^\s*uses:\s*([^\s#]+)\s+#\s*(v\d+(?:\.\d+\.\d+)?)\s*$")
SECRET_REFERENCE = re.compile(r"\$\{\{\s*secrets\.([A-Za-z_][A-Za-z0-9_]*)\s*\}\}")
PINNED_AUDIT_INSTALL = "./scripts/install-ci-audit-prerequisites.sh"


def public_pr_jobs_use_hosted_runner(workflow: dict[str, Any]) -> bool:
    """Return true only when every public-PR job selects the reviewed ephemeral runner."""
    jobs = workflow.get("jobs", {})
    return isinstance(jobs, dict) and bool(jobs) and all(
        isinstance(job, dict) and job.get("runs-on") == HOSTED_RUNNER
        for job in jobs.values()
    )


def load_workflow(path: Path) -> tuple[dict[str, Any], str]:
    text = path.read_text(encoding="utf-8")
    try:
        loaded = yaml.load(text, Loader=yaml.BaseLoader)
    except yaml.YAMLError as exc:
        raise ValueError(f"invalid YAML: {exc}") from exc
    if not isinstance(loaded, dict):
        raise ValueError("workflow root must be a mapping")
    return loaded, text


def trigger_errors(name: str, workflow: dict[str, Any]) -> list[str]:
    configured = workflow.get("on")
    if not isinstance(configured, dict):
        return ["top-level on must be an explicit mapping"]
    actual = set(configured)
    errors: list[str] = []
    if actual != WORKFLOWS[name]:
        errors.append(f"unexpected triggers {sorted(actual)}; expected {sorted(WORKFLOWS[name])}")
    forbidden = actual & FORBIDDEN_TRIGGERS
    if forbidden:
        errors.append(f"forbidden triggers: {sorted(forbidden)}")
    if "push" in actual:
        push = configured["push"]
        if push != {"branches": ["main"]}:
            errors.append("push must target main explicitly and must not use tag triggers")
    return errors


def permission_errors(workflow: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    if workflow.get("permissions") != {"contents": "read"}:
        errors.append("top-level permissions must be exactly contents: read")
    jobs = workflow.get("jobs")
    if not isinstance(jobs, dict):
        return errors + ["jobs must be a mapping"]
    for job_name, job in jobs.items():
        if not isinstance(job, dict):
            errors.append(f"job {job_name} must be a mapping")
            continue
        permissions = job.get("permissions")
        if permissions is not None and permissions != {"contents": "read"}:
            errors.append(f"job {job_name} overrides least-privilege permissions")
        if "timeout-minutes" not in job:
            errors.append(f"job {job_name} must declare timeout-minutes")
    return errors


def action_errors(text: str) -> list[str]:
    errors: list[str] = []
    uses_lines = [line for line in text.splitlines() if re.match(r"^\s*uses:", line)]
    if not uses_lines:
        errors.append("workflow contains no auditable uses lines")
    for line in uses_lines:
        match = USES_LINE.match(line)
        if match is None:
            errors.append(f"Action must use a full SHA and version comment: {line.strip()}")
            continue
        reference, comment = match.groups()
        if "@" not in reference:
            errors.append(f"Action reference lacks @: {reference}")
            continue
        action, sha = reference.rsplit("@", 1)
        approved = APPROVED_ACTIONS.get(action)
        if approved is None:
            errors.append(f"Action is not allow-listed: {action}")
            continue
        expected_sha, expected_comment = approved
        if sha != expected_sha or comment != expected_comment:
            errors.append(f"Unexpected pin for {action}: {sha} # {comment}")
    return errors


def scalar_strings(value: Any) -> list[str]:
    if isinstance(value, dict):
        return [item for child in value.values() for item in scalar_strings(child)]
    if isinstance(value, list):
        return [item for child in value for item in scalar_strings(child)]
    return [value] if isinstance(value, str) else []


def secret_references(value: Any) -> set[str]:
    return {
        match.group(1)
        for scalar in scalar_strings(value)
        for match in SECRET_REFERENCE.finditer(scalar)
    }


def steps_for(workflow: dict[str, Any]) -> list[tuple[str, dict[str, Any]]]:
    steps: list[tuple[str, dict[str, Any]]] = []
    jobs = workflow.get("jobs", {})
    if not isinstance(jobs, dict):
        return steps
    for job_name, job in jobs.items():
        if not isinstance(job, dict) or not isinstance(job.get("steps"), list):
            continue
        for step in job["steps"]:
            if isinstance(step, dict):
                steps.append((job_name, step))
    return steps


def common_semantic_errors(name: str, workflow: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    jobs = workflow.get("jobs", {})
    for job_name, job in jobs.items() if isinstance(jobs, dict) else []:
        if not isinstance(job, dict):
            continue
        if job.get("continue-on-error") is not None:
            errors.append(f"job {job_name} must not use continue-on-error")

    for job_name, step in steps_for(workflow):
        if step.get("continue-on-error") is not None:
            errors.append(f"{job_name}: steps must not use continue-on-error")
        run = step.get("run")
        if isinstance(run, str):
            if "${{" in run:
                errors.append(f"{job_name}: expression interpolation is forbidden inside run blocks")
            if re.search(r"(^|[;&|]\s*)eval(?:\s|$)", run, re.MULTILINE):
                errors.append(f"{job_name}: eval is forbidden")
            if re.search(r"(^|\s)set\s+-[^\n]*x", run):
                errors.append(f"{job_name}: shell tracing is forbidden")
            if re.search(r"(^|\s)git\s+push(?:\s|$)", run):
                errors.append(f"{job_name}: repository writes are forbidden")

        uses = step.get("uses")
        inputs = step.get("with", {})
        if isinstance(uses, str) and uses.startswith("actions/checkout@"):
            if not isinstance(inputs, dict) or inputs.get("persist-credentials") != "false":
                errors.append(f"{job_name}: checkout must set persist-credentials: false")
            if isinstance(inputs, dict) and "token" in inputs:
                errors.append(f"{job_name}: checkout must not receive a custom token")
            expected_depth = "0" if name in {"release.yml", "security.yml"} else "1"
            if not isinstance(inputs, dict) or inputs.get("fetch-depth") != expected_depth:
                errors.append(f"{job_name}: checkout fetch-depth must be {expected_depth}")
            if name in HARDENED_CHECKOUT_WORKFLOWS and (
                not isinstance(inputs, dict) or inputs.get("clean") != "true"
            ):
                errors.append(f"{job_name}: CI checkout must set clean: true")

        if isinstance(uses, str) and uses.startswith("actions/setup-java@"):
            if not isinstance(inputs, dict) or inputs.get("distribution") != "temurin":
                errors.append(f"{job_name}: setup-java distribution must be temurin")
            forbidden = {
                "server-id",
                "server-username-env-var",
                "server-password-env-var",
                "gpg-private-key",
                "gpg-passphrase-env-var",
            }
            if not isinstance(inputs, dict) or inputs.get("overwrite-settings") != "false":
                errors.append(f"{job_name}: setup-java must not create publishing settings")
            if isinstance(inputs, dict) and forbidden & set(inputs):
                errors.append(f"{job_name}: setup-java contains publishing inputs")
            if name in HARDENED_CHECKOUT_WORKFLOWS and (
                not isinstance(inputs, dict)
                or inputs.get("settings-path") != "${{ runner.temp }}"
            ):
                errors.append(
                    f"{job_name}: CI setup-java settings must stay in runner.temp"
                )

        if isinstance(uses, str) and uses.startswith("actions/upload-artifact@"):
            if not isinstance(inputs, dict) or "retention-days" not in inputs:
                errors.append(f"{job_name}: uploaded artifacts require explicit retention-days")
            if isinstance(inputs, dict) and inputs.get("include-hidden-files") == "true":
                errors.append(f"{job_name}: hidden files must not be uploaded")
    return errors


def runner_boundary_errors(name: str, workflow: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    jobs = workflow.get("jobs", {})
    if not isinstance(jobs, dict):
        return ["jobs must be a mapping"]

    if secret_references(workflow):
        errors.append("workflows must not reference repository secrets")
    for job_name, job in jobs.items():
        if not isinstance(job, dict):
            continue
        if job.get("runs-on") != HOSTED_RUNNER:
            errors.append(
                f"job {job_name} must use {HOSTED_RUNNER}; persistent self-hosted runners are forbidden"
            )
        if name in PUBLIC_PR_WORKFLOWS and "if" in job:
            errors.append(
                f"job {job_name} must not gate public PR execution by actor or repository identity"
            )
    return errors


def compatibility_errors(workflow: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    jobs = workflow.get("jobs", {})
    if not isinstance(jobs, dict) or set(jobs) != set(COMPATIBILITY_LANES):
        return ["Compatibility job inventory must preserve the reviewed 11-lane matrix"]

    lane_count = 0
    for job_name, expected_matrix in COMPATIBILITY_LANES.items():
        job = jobs[job_name]
        strategy = job.get("strategy") if isinstance(job, dict) else None
        if expected_matrix is None:
            if strategy is not None:
                errors.append(f"{job_name}: unexpected matrix strategy")
            lane_count += 1
            continue
        axis, values = expected_matrix
        matrix = strategy.get("matrix") if isinstance(strategy, dict) else None
        if matrix != {axis: values}:
            errors.append(f"{job_name}: matrix must remain {axis}={values}")
        lane_count += len(values)
    if lane_count != 11:
        errors.append(f"Compatibility must expand to 11 lanes, found {lane_count}")
    return errors


def build_errors(workflow: dict[str, Any]) -> list[str]:
    steps = workflow["jobs"]["verify"]["steps"]
    names = [step.get("name", "") for step in steps]
    required = [
        "Install pinned security audit dependencies",
        "Verify security audit prerequisites",
        "Run fast security gates",
        "Set up Java",
    ]
    if not all(item in names for item in required):
        return ["Build must install and verify pinned audit dependencies before its canonical gates"]
    install = steps[names.index(required[0])].get("run", "")
    if " ".join(install.split()) != PINNED_AUDIT_INSTALL:
        return ["Build must install the reviewed hash-pinned audit dependencies"]
    if not all(names.index(left) < names.index(right) for left, right in zip(required, required[1:])):
        return ["Build fast security gates must run before Java/build execution"]
    return []


def security_errors(workflow: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    schedule = workflow["on"].get("schedule")
    if schedule != [{"cron": "17 4 * * 1"}]:
        errors.append("Security must run weekly at the reviewed UTC cron")
    concurrency = workflow.get("concurrency")
    if concurrency != {"group": "continuous-security", "cancel-in-progress": "false"}:
        errors.append("Security concurrency must serialize runs without cancellation")
    jobs = workflow.get("jobs", {})
    if set(jobs) != {"validate"}:
        return errors + ["Security workflow must contain exactly one validation job"]
    steps = jobs["validate"].get("steps", [])
    names = [step.get("name", "") for step in steps if isinstance(step, dict)]
    if "Install pinned security audit dependencies" not in names:
        errors.append("Security must install the pinned audit dependencies")
    else:
        install = steps[names.index("Install pinned security audit dependencies")].get("run", "")
        if " ".join(install.split()) != PINNED_AUDIT_INSTALL:
            errors.append("Security must install the reviewed hash-pinned audit dependencies")
    if "Run full continuous security validation" not in names:
        errors.append("Security workflow must invoke the canonical full validation")
    runs = "\n".join(step.get("run", "") for step in steps if isinstance(step, dict))
    if "./scripts/check-security.sh full" not in runs:
        errors.append("Security workflow lost the full orchestration command")
    return errors


def dependency_review_errors(workflow: dict[str, Any]) -> list[str]:
    jobs = workflow.get("jobs", {})
    if set(jobs) != {"dependency-review"}:
        return ["Dependency Review must contain exactly one reviewed job"]
    steps = jobs["dependency-review"].get("steps", [])
    review = next(
        (
            step
            for step in steps
            if isinstance(step, dict)
            and str(step.get("uses", "")).startswith("actions/dependency-review-action@")
        ),
        {},
    )
    expected = {
        "comment-summary-in-pr": "never",
        "fail-on-severity": "moderate",
        "license-check": "true",
        "show-openssf-scorecard": "true",
        "vulnerability-check": "true",
        "warn-only": "false",
    }
    return [] if review.get("with") == expected else [
        "Dependency Review inputs must remain fail-closed, read-only and comment-free"
    ]


def benchmark_errors(workflow: dict[str, Any]) -> list[str]:
    dispatch = workflow["on"]["workflow_dispatch"]
    options = dispatch["inputs"]["profile"].get("options", [])
    errors: list[str] = []
    if set(options) != BENCHMARK_PROFILES or len(options) != len(BENCHMARK_PROFILES):
        errors.append("benchmark profiles must match the reviewed allow-list")
    benchmark_steps = workflow["jobs"]["benchmark"]["steps"]
    run_step = next((step for step in benchmark_steps if step.get("name") == "Run benchmark profile"), {})
    run = run_step.get("run", "")
    env = run_step.get("env", {})
    if set(env) != {"BENCHMARK_PROFILE", "RESULT_LABEL", "POSTGRES_VERSION"}:
        errors.append("benchmark dynamic values must enter through the reviewed env boundary")
    if '"${BENCHMARK_PROFILE}"' not in run or '"${RESULT_LABEL}"' not in run:
        errors.append("benchmark variables must be shell-quoted")
    return errors


def release_errors(workflow: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    jobs = workflow["jobs"]
    if set(jobs) != {"candidate"}:
        errors.append("release workflow must remain candidate-only")
    candidate = jobs.get("candidate", {})
    if secret_references(candidate):
        errors.append("release candidate must not reference secrets")
    if secret_references(workflow):
        errors.append("release workflow must not reference repository secrets")

    dispatch_inputs = workflow["on"]["workflow_dispatch"].get("inputs", {})
    if set(dispatch_inputs) != {"version", "commit_sha", "confirmation"}:
        errors.append("release dispatch must expose candidate-only inputs")

    candidate_if = candidate.get("if", "")
    for required in ("github.repository", "github.actor", "github.event.repository.default_branch"):
        if required not in candidate_if:
            errors.append(f"release authorization guard lost {required}")

    candidate_runs = "\n".join(
        step.get("run", "") for step in candidate.get("steps", []) if isinstance(step, dict)
    )
    for required in (
        "^[0-9a-f]{40}$",
        "expected_confirmation",
        "git merge-base --is-ancestor",
        "./scripts/check-release-security-preflight.sh technical",
        "./scripts/check-workflow-security.py",
        "./scripts/check-secrets.sh history",
        "./scripts/test-release-signatures.py",
    ):
        if required not in candidate_runs:
            errors.append(f"release candidate validation lost: {required}")
    return errors


def audit_workflow(name: str, workflow: dict[str, Any], text: str) -> list[str]:
    errors = trigger_errors(name, workflow)
    errors.extend(permission_errors(workflow))
    errors.extend(action_errors(text))
    errors.extend(common_semantic_errors(name, workflow))
    errors.extend(runner_boundary_errors(name, workflow))
    if name == "build.yml":
        errors.extend(build_errors(workflow))
    elif name == "compatibility.yml":
        errors.extend(compatibility_errors(workflow))
    elif name == "dependency-review.yml":
        errors.extend(dependency_review_errors(workflow))
    elif name == "benchmarks.yml":
        errors.extend(benchmark_errors(workflow))
    elif name == "release.yml":
        errors.extend(release_errors(workflow))
    elif name == "security.yml":
        errors.extend(security_errors(workflow))
    return errors


def workflow_inventory_errors(actual: set[str]) -> list[str]:
    return [] if actual == set(WORKFLOWS) else [f"workflow inventory changed: {sorted(actual)}"]


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]).resolve()
    workflow_directory = root / ".github" / "workflows"
    actual = {
        path.name
        for pattern in ("*.yml", "*.yaml")
        for path in workflow_directory.glob(pattern)
    }
    errors: list[str] = []
    errors.extend(workflow_inventory_errors(actual))

    for name in sorted(WORKFLOWS):
        path = workflow_directory / name
        try:
            workflow, text = load_workflow(path)
        except (OSError, ValueError) as exc:
            errors.append(f"{name}: {exc}")
            continue
        errors.extend(f"{name}: {error}" for error in audit_workflow(name, workflow, text))

    if errors:
        print("Workflow security audit failed:", file=sys.stderr)
        print("\n".join(f"- {error}" for error in errors), file=sys.stderr)
        return 1
    print(f"Workflow security audit: PASS ({len(WORKFLOWS)} workflows, SHA-pinned allow-list)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
