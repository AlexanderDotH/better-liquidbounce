"""Checks for complete and event-parity CI acceptance workflows."""

from __future__ import annotations

import re
from pathlib import Path

from .model import Finding, finding, read_text


def _job_body(workflow: str, job_name: str) -> str | None:
    match = re.search(
        rf"(?ms)^  {re.escape(job_name)}:\n(?P<body>.*?)(?=^  [a-zA-Z0-9_-]+:\n|\Z)",
        workflow,
    )
    return match.group("body") if match else None


def _job_satisfies(body: str | None, required: tuple[str, ...]) -> bool:
    return bool(body and not re.search(r"(?m)^    if:", body) and all(item in body for item in required))


def _step_body(job_body: str | None, step_name: str) -> str | None:
    if not job_body:
        return None
    match = re.search(
        rf"(?ms)^      - name: {re.escape(step_name)}\n(?P<body>.*?)(?=^      - name: |\Z)",
        job_body,
    )
    return match.group("body") if match else None


def _required_report_contract(job_body: str | None) -> bool:
    step = _step_body(job_body, "Verify required quality reports")
    required_reports = (
        "build/reports/source-hygiene/source-quality.md",
        "build/reports/source-hygiene/source-quality.json",
        "build/reports/source-hygiene/source-quality.sarif",
        "build/reports/detekt/detekt.sarif",
    )
    required_logic = (
        "if: ${{ always() }}",
        "required_reports=(",
        'for report in "${required_reports[@]}"; do',
        'if [[ ! -f "$report" ]]; then',
        "missing=1",
        'exit "$missing"',
    )
    return bool(step and all(fragment in step for fragment in required_reports + required_logic))


def _report_artifact_contract(job_body: str | None) -> bool:
    step = _step_body(job_body, "Upload source-quality reports")
    required = (
        "if: ${{ always() }}",
        "actions/upload-artifact@",
        "if-no-files-found: error",
        "build/reports/source-hygiene/source-quality.md",
        "build/reports/source-hygiene/source-quality.json",
        "build/reports/source-hygiene/source-quality.sarif",
        "build/reports/detekt/detekt.sarif",
    )
    return bool(step and all(fragment in step for fragment in required))


def _safe_sarif_upload(job_body: str | None, step_name: str) -> bool:
    step = _step_body(job_body, step_name)
    required_condition = (
        "if: ${{ always()",
        "github.actor != 'dependabot[bot]'",
        "github.event_name == 'push'",
        "github.event.pull_request.head.repo.full_name == github.repository",
    )
    return bool(
        step
        and all(fragment in step for fragment in required_condition)
        and "hashFiles(" not in step
    )


def verify_workflow(root: Path) -> Finding | None:
    path = ".github/workflows/build.yml"
    workflow = read_text(root, path) or ""
    common_setup = (
        "node-version-file: .node-version",
        "needs.extract-versions.outputs.jdk_version",
        "npm install --global",
        ".npm-version",
    )
    quality_contract = common_setup + (
        "name: quality-gate", "./gradlew --no-daemon qualityGate",
        "source-quality.md", "source-quality.json", "source-quality.sarif",
        "detekt.sarif", "actions/upload-artifact@", "if-no-files-found: error",
        "codeql-action/upload-sarif@",
    )
    release_contract = common_setup + (
        "name: release-build", "build verifyReleaseArtifact",
        "! -name '*-dev.jar'", "! -name '*-sources.jar'",
    )
    forbidden = re.compile(r"(?:-x|--exclude-task)(?:\s+|=)(?:test|detekt)\b")
    quality_body = _job_body(workflow, "quality-gate")
    valid = (
        "push:" in workflow
        and "pull_request:" in workflow
        and _job_satisfies(quality_body, quality_contract)
        and _required_report_contract(quality_body)
        and _report_artifact_contract(quality_body)
        and _safe_sarif_upload(quality_body, "Upload source-quality SARIF")
        and _safe_sarif_upload(quality_body, "Upload Detekt SARIF")
        and _job_satisfies(_job_body(workflow, "release-build"), release_contract)
        and not forbidden.search(workflow)
    )
    if valid:
        return None
    return finding(
        "LB-POLICY-002",
        path,
        "Push and pull-request CI do not expose the same complete acceptance checks and reports.",
        "Run the complete root qualityGate, require every Markdown/JSON/SARIF report, keep safe SARIF uploads "
        "mandatory, retain release-build, and never skip tests or Detekt.",
    )
