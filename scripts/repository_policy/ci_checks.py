"""Checks for pinned CI, review, and branch-protection contracts."""

from __future__ import annotations

import json
import re
import stat
from pathlib import Path

from .model import (
    EXPECTED_JAVA,
    EXPECTED_NODE,
    EXPECTED_NPM,
    REQUIRED_CHECKS,
    REQUIRED_DOCUMENTED_RULES,
    REQUIRED_RULES,
    Finding,
    finding,
    read_text,
)


def verify_toolchains(root: Path) -> Finding | None:
    node = read_text(root, ".node-version")
    npm = read_text(root, ".npm-version")
    catalog = read_text(root, "gradle/libs.versions.toml")
    java_match = catalog and re.search(r'^jdk\s*=\s*"([^"]+)"', catalog, re.MULTILINE)
    valid = node and node.strip() == EXPECTED_NODE and npm and npm.strip() == EXPECTED_NPM
    workflow_directory = root / ".github/workflows"
    workflow_text = "\n".join(
        path.read_text(encoding="utf-8")
        for pattern in ("*.yml", "*.yaml")
        for path in workflow_directory.glob(pattern)
    ) if workflow_directory.is_dir() else ""
    hardcoded = re.search(r'(?m)^\s*(?:java|node)-version:\s*["\']?\d', workflow_text)
    hardcoded = hardcoded or re.search(r'\bnpm@\d+(?:\.\d+){0,2}\b', workflow_text)
    if valid and java_match and java_match.group(1) == EXPECTED_JAVA and not hardcoded:
        return None
    return finding(
        "LB-POLICY-001",
        ".node-version",
        "Java, Node, or npm does not match the repository toolchain pins.",
        f"Pin Java {EXPECTED_JAVA}, Node {EXPECTED_NODE}, and npm {EXPECTED_NPM} in their canonical files.",
    )


def _rule_parameters(rules: dict[str, dict], rule_type: str) -> dict:
    return rules.get(rule_type, {}).get("parameters", {})


def verify_ruleset(root: Path) -> Finding | None:
    path = ".github/rulesets/nextgen.json"
    try:
        payload = json.loads(read_text(root, path) or "")
    except json.JSONDecodeError:
        payload = {}
    rules = {rule.get("type"): rule for rule in payload.get("rules", []) if isinstance(rule, dict)}
    pull_request = _rule_parameters(rules, "pull_request")
    status = _rule_parameters(rules, "required_status_checks")
    checks = {item.get("context") for item in status.get("required_status_checks", [])}
    valid = (
        payload.get("target") == "branch"
        and payload.get("enforcement") == "active"
        and payload.get("bypass_actors") == []
        and payload.get("conditions", {}).get("ref_name", {}).get("include") == ["refs/heads/nextgen"]
        and payload.get("conditions", {}).get("ref_name", {}).get("exclude") == []
        and REQUIRED_RULES <= rules.keys()
        and pull_request.get("allowed_merge_methods") == ["rebase"]
        and pull_request.get("dismiss_stale_reviews_on_push") is True
        and pull_request.get("require_code_owner_review") is False
        and pull_request.get("require_last_push_approval") is False
        and pull_request.get("required_approving_review_count") == 0
        and pull_request.get("required_review_thread_resolution") is True
        and status.get("strict_required_status_checks_policy") is True
        and status.get("do_not_enforce_on_create") is False
        and REQUIRED_CHECKS == checks
    )
    if valid:
        return None
    return finding(
        "LB-POLICY-003",
        path,
        "The versioned nextgen ruleset violates the solo-safe PR, force, deletion, or checked-update contract.",
        "Restore no-bypass, solo-safe, rebase-only PRs with resolved threads and both required checks.",
    )


def verify_vendor_entrypoint(root: Path) -> Finding | None:
    path = "scripts/verify-baritone-vendor.sh"
    entrypoint = root / path
    if (
        entrypoint.is_file()
        and not entrypoint.is_symlink()
        and stat.S_IMODE(entrypoint.stat().st_mode) == 0o755
    ):
        return None
    return finding(
        "LB-POLICY-004",
        path,
        "The CI vendor verifier is missing or is not tracked with executable mode 100755.",
        "Keep scripts/verify-baritone-vendor.sh tracked as mode 100755.",
    )


def verify_review_contract(root: Path) -> Finding | None:
    paths = ("CONTRIBUTING.md", ".github/CODEOWNERS", ".github/pull_request_template.md")
    contribution = (read_text(root, paths[0]) or "").lower()
    owners = read_text(root, paths[1]) or ""
    template = (read_text(root, paths[2]) or "").lower()
    standards = (read_text(root, ".github/CODING_STANDARDS.md") or "").lower()
    agents = (read_text(root, "AGENTS.md") or "").lower()
    required_agent_feedback = (
        "source-quality.md", "rule id", "characterization", "qualitygate",
        "baseline", "structural suppression", "200", "300",
    )
    valid = (
        all((root / path).is_file() for path in paths)
        and "qualitygate" in contribution
        and "characterization" in contribution
        and any(line.startswith("* @") for line in owners.splitlines())
        and "characterization" in template
        and "qualitygate" in template
        and "baseline" in template
        and all(f'<a id="{rule_id.lower()}"></a>' in standards for rule_id in REQUIRED_DOCUMENTED_RULES)
        and all(fragment in agents for fragment in required_agent_feedback)
    )
    if valid:
        return None
    return finding(
        "LB-POLICY-007",
        ".github/pull_request_template.md",
        "The contributor, ownership, or pull-request review contract is missing or incomplete.",
        "Synchronize CONTRIBUTING, CODEOWNERS, the PR checklist, AGENTS feedback, and every documented rule anchor.",
    )
