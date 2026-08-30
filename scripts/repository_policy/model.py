"""Shared policy constants and feedback model."""

from __future__ import annotations

from pathlib import Path
from typing import NamedTuple


EXPECTED_NODE = "24.18.1"
EXPECTED_NPM = "12.0.2"
EXPECTED_JAVA = "25"
REQUIRED_CHECKS = {"quality-gate", "release-build"}
REQUIRED_RULES = {
    "deletion",
    "non_fast_forward",
    "pull_request",
    "required_linear_history",
    "required_status_checks",
}
REQUIRED_DOCUMENTED_RULES = (
    *(f"LB-HYG-{number:03d}" for number in range(1, 6)),
    "LB-ARCH-001",
    "LB-ARCH-002",
    "LB-RATCHET-001",
    "LB-RATCHET-002",
    *(f"LB-POLICY-{number:03d}" for number in range(1, 9)),
)
REQUIRED_GRADLE_SCRIPTS = (
    "gradle/repositories.gradle.kts",
    "gradle/game-dependencies.gradle.kts",
    "gradle/runtime-dependencies.gradle.kts",
    "gradle/theme.gradle.kts",
    "gradle/resource-packaging.gradle.kts",
    "gradle/testing.gradle.kts",
    "gradle/repository-policy.gradle.kts",
    "gradle/release-verification.gradle.kts",
    "gradle/artifacts.gradle.kts",
)


class Finding(NamedTuple):
    rule_id: str
    path: str
    message: str
    repair: str


def read_text(root: Path, relative: str) -> str | None:
    try:
        return (root / relative).read_text(encoding="utf-8")
    except (FileNotFoundError, OSError, UnicodeError):
        return None


def finding(rule_id: str, path: str, message: str, repair: str) -> Finding:
    return Finding(rule_id, path, message, repair)
