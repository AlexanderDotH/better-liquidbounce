#!/usr/bin/env python3
"""Validate versioned CI, toolchain, and nextgen protection contracts."""

from __future__ import annotations

import sys
from pathlib import Path


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

from repository_policy import Finding, REQUIRED_GRADLE_SCRIPTS, verify_repository  # noqa: E402

__all__ = ["Finding", "REQUIRED_GRADLE_SCRIPTS", "verify_repository"]


def main() -> int:
    findings = verify_repository(SCRIPT_DIRECTORY.parent)
    for finding in findings:
        print(f"{finding.rule_id} {finding.path}: {finding.message}", file=sys.stderr)
        print(f"  Repair: {finding.repair}", file=sys.stderr)
        anchor = finding.rule_id.lower()
        print(f"  Documentation: .github/CODING_STANDARDS.md#{anchor}", file=sys.stderr)
    if findings:
        return 1
    print("[repository-policy] toolchain, CI, review, and nextgen protection contracts are valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
