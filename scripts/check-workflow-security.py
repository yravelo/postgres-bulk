#!/usr/bin/env python3
"""Fail closed when a GitHub Actions security invariant is broken."""

from __future__ import annotations

import re
import sys
from pathlib import Path
from typing import Any

try:
    import yaml
except ImportError as exc:  # pragma: no cover - exercised by CI prerequisite installation
    raise SystemExit("PyYAML is required: install the python3-yaml package") from exc


WORKFLOWS = {
    "build.yml": {"push", "pull_request"},
    "compatibility.yml": {"push", "pull_request"},
    "benchmarks.yml": {"workflow_dispatch"},
    "release.yml": {"workflow_dispatch"},
}
FORBIDDEN_TRIGGERS = {
    "pull_request_target",
    "workflow_run",
    "repository_dispatch",
    "schedule",
}
APPROVED_ACTIONS = {
    "actions/checkout": (
        "d23441a48e516b6c34aea4fa41551a30e30af803",
        "v6",
    ),
    "actions/setup-java": (
        "b6effb05e454b25005698d916606bdc6ffcbf961",
        "v5",
    ),
    "actions/upload-artifact": (
        "ea165f8d65b6e75b540449e92b4886f43607fa02",
        "v4",
    ),
}
RELEASE_SECRETS = {
    "CENTRAL_USERNAME",
    "CENTRAL_PASSWORD",
    "GPG_PRIVATE_KEY",
    "GPG_PASSPHRASE",
}
BENCHMARK_PROFILES = {
    "smoke",
    "baseline",
    "large",
    "multi-schema-smoke",
    "multi-schema-baseline",
}
SELF_HOSTED_WORKFLOWS = {"build.yml", "compatibility.yml"}
SELF_HOSTED_LABELS = ["self-hosted", "linux", "x64", "postgres-bulk-ci"]
TRUSTED_PULL_REQUEST_GUARD = (
    "github.event_name != 'pull_request' || "
    "(github.actor == 'yravelo' && "
    "github.event.pull_request.head.repo.full_name == github.repository)"
)
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
            expected_depth = "0" if name == "release.yml" else "1"
            if not isinstance(inputs, dict) or inputs.get("fetch-depth") != expected_depth:
                errors.append(f"{job_name}: checkout fetch-depth must be {expected_depth}")
            if name in SELF_HOSTED_WORKFLOWS and (
                not isinstance(inputs, dict) or inputs.get("clean") != "true"
            ):
                errors.append(f"{job_name}: self-hosted checkout must set clean: true")

        if isinstance(uses, str) and uses.startswith("actions/setup-java@"):
            if not isinstance(inputs, dict) or inputs.get("distribution") != "temurin":
                errors.append(f"{job_name}: setup-java distribution must be temurin")
            if job_name != "central-upload":
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
                if name in SELF_HOSTED_WORKFLOWS and (
                    not isinstance(inputs, dict)
                    or inputs.get("settings-path") != "${{ runner.temp }}"
                ):
                    errors.append(
                        f"{job_name}: self-hosted setup-java settings must stay in runner.temp"
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

    if name in SELF_HOSTED_WORKFLOWS:
        if secret_references(workflow):
            errors.append("self-hosted Build/Compatibility must not reference repository secrets")
        for job_name, job in jobs.items():
            if not isinstance(job, dict):
                continue
            if job.get("runs-on") != SELF_HOSTED_LABELS:
                errors.append(
                    f"job {job_name} must use the exact dedicated self-hosted label set"
                )
            if job.get("if") != TRUSTED_PULL_REQUEST_GUARD:
                errors.append(f"job {job_name} must use the exact trusted pull-request guard")
    else:
        for job_name, job in jobs.items():
            if isinstance(job, dict) and job.get("runs-on") != "ubuntu-latest":
                errors.append(f"job {job_name} must remain on the reviewed GitHub-hosted runner")
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
    required = ["Audit workflow security", "Scan current tree for secrets", "Set up Java"]
    if not all(item in names for item in required):
        return ["Build must contain workflow audit, current secret scan and Java setup"]
    if not names.index(required[0]) < names.index(required[1]) < names.index(required[2]):
        return ["Build security gates must run before Java/build execution"]
    return []


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
    candidate = jobs.get("candidate", {})
    upload = jobs.get("central-upload", {})
    if secret_references(candidate):
        errors.append("release candidate must not reference secrets")
    if secret_references(upload) != RELEASE_SECRETS:
        errors.append("central-upload must be the exact consumer of the four release secrets")
    for job_name, job in jobs.items():
        if job_name != "central-upload" and secret_references(job):
            errors.append(f"release secrets escaped into {job_name}")

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
        "./scripts/check-workflow-security.py",
        "./scripts/check-secrets.sh history",
    ):
        if required not in candidate_runs:
            errors.append(f"release candidate validation lost: {required}")

    upload_runs = "\n".join(
        step.get("run", "") for step in upload.get("steps", []) if isinstance(step, dict)
    )
    for required in ("refs/tags/${RELEASE_TAG}^{commit}", "git describe --exact-match --tags HEAD"):
        if required not in upload_runs:
            errors.append(f"release tag validation lost: {required}")
    cleanup = next(
        (step for step in upload.get("steps", []) if step.get("name") == "Remove temporary publishing credentials"),
        {},
    )
    if cleanup.get("if") != "${{ always() }}" or "GNUPGHOME" not in cleanup.get("run", ""):
        errors.append("temporary Maven/GPG material must be cleaned with always()")

    upload_setup = next(
        (
            step
            for step in upload.get("steps", [])
            if isinstance(step.get("uses"), str) and step["uses"].startswith("actions/setup-java@")
        ),
        {},
    )
    setup_inputs = upload_setup.get("with", {})
    expected_inputs = {
        "server-id": "central",
        "server-username-env-var": "CENTRAL_USERNAME",
        "server-password-env-var": "CENTRAL_PASSWORD",
        "gpg-passphrase-env-var": "GPG_PASSPHRASE",
    }
    for key, value in expected_inputs.items():
        if setup_inputs.get(key) != value:
            errors.append(f"central-upload setup-java lost {key}")
    if setup_inputs.get("settings-path") != "${{ runner.temp }}":
        errors.append("central-upload settings must remain under runner.temp")
    if setup_inputs.get("gpg-private-key") != "${{ secrets.GPG_PRIVATE_KEY }}":
        errors.append("central-upload setup-java lost its isolated GPG input")
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
    elif name == "benchmarks.yml":
        errors.extend(benchmark_errors(workflow))
    elif name == "release.yml":
        errors.extend(release_errors(workflow))
    return errors


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]).resolve()
    workflow_directory = root / ".github" / "workflows"
    actual = {
        path.name
        for pattern in ("*.yml", "*.yaml")
        for path in workflow_directory.glob(pattern)
    }
    errors: list[str] = []
    if actual != set(WORKFLOWS):
        errors.append(f"workflow inventory changed: {sorted(actual)}")

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
