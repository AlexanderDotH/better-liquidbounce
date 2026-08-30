"""Negative contracts for repository-policy checks that span multiple files."""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

try:
    import test_verify_repository_policy as policy_test_support
except ModuleNotFoundError:
    from scripts.tests import test_verify_repository_policy as policy_test_support

POLICY = policy_test_support.POLICY


class RepositoryPolicyHardeningTest(unittest.TestCase):
    def test_each_acceptance_job_requires_its_own_pinned_node_setup(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self.valid_repository(directory)
            workflow = root / ".github/workflows/build.yml"
            quality, release = workflow.read_text(encoding="utf-8").split("  release-build:\n", 1)
            release = release.replace("node-version-file: .node-version", "node-version-file: .tool-versions")
            quality += "      - run: duplicate node-version-file: .node-version\n"
            workflow.write_text(quality + "  release-build:\n" + release, encoding="utf-8")

            rule_ids = self.rule_ids(root)

        self.assertIn("LB-POLICY-002", rule_ids)

    def test_release_command_cannot_be_satisfied_by_quality_job_text(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self.valid_repository(directory)
            workflow = root / ".github/workflows/build.yml"
            quality, release = workflow.read_text(encoding="utf-8").split("  release-build:\n", 1)
            release = release.replace("build verifyReleaseArtifact", "build")
            quality += "      - run: duplicate build verifyReleaseArtifact\n"
            workflow.write_text(quality + "  release-build:\n" + release, encoding="utf-8")

            rule_ids = self.rule_ids(root)

        self.assertIn("LB-POLICY-002", rule_ids)

    def test_root_quality_gate_must_own_build_logic_tests(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self.valid_repository(directory)
            release = root / "gradle/release-verification.gradle.kts"
            release.write_text(
                release.read_text(encoding="utf-8").replace(
                    "dependsOn(testBuildLogic,)",
                    'dependsOn("test")',
                ),
                encoding="utf-8",
            )

            rule_ids = self.rule_ids(root)

        self.assertIn("LB-POLICY-005", rule_ids)

    def test_build_logic_composite_requires_stable_settings_alias(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self.valid_repository(directory)
            settings = root / "settings.gradle.kts"
            settings.write_text(
                settings.read_text(encoding="utf-8").replace(
                    'name = "build-logic-tests"',
                    'name = "renamed-build-logic-tests"',
                ),
                encoding="utf-8",
            )

            rule_ids = self.rule_ids(root)

        self.assertIn("LB-POLICY-005", rule_ids)

    def test_quality_reports_cannot_be_optional(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self.valid_repository(directory)
            workflow = root / ".github/workflows/build.yml"
            quality, release = workflow.read_text(encoding="utf-8").split("  release-build:\n", 1)
            quality = quality.replace("if-no-files-found: error", "if-no-files-found: ignore")
            workflow.write_text(quality + "  release-build:\n" + release, encoding="utf-8")

            rule_ids = self.rule_ids(root)

        self.assertIn("LB-POLICY-002", rule_ids)

    def test_each_required_quality_report_must_be_checked(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self.valid_repository(directory)
            workflow = root / ".github/workflows/build.yml"
            content = workflow.read_text(encoding="utf-8")
            workflow.write_text(
                content.replace(
                    "            build/reports/detekt/detekt.sarif\n",
                    "",
                    1,
                ),
                encoding="utf-8",
            )

            rule_ids = self.rule_ids(root)

        self.assertIn("LB-POLICY-002", rule_ids)

    def test_safe_sarif_uploads_cannot_skip_missing_files(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self.valid_repository(directory)
            workflow = root / ".github/workflows/build.yml"
            content = workflow.read_text(encoding="utf-8")
            workflow.write_text(
                content.replace(
                    "if: ${{ always() && github.actor != 'dependabot[bot]'",
                    "if: ${{ always() && hashFiles('report.sarif') != '' && github.actor != 'dependabot[bot]'",
                    1,
                ),
                encoding="utf-8",
            )

            rule_ids = self.rule_ids(root)

        self.assertIn("LB-POLICY-002", rule_ids)

    def test_report_artifact_remains_available_after_gate_failure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self.valid_repository(directory)
            workflow = root / ".github/workflows/build.yml"
            content = workflow.read_text(encoding="utf-8")
            workflow.write_text(
                content.replace(
                    "      - name: Upload source-quality reports\n        if: ${{ always() }}",
                    "      - name: Upload source-quality reports\n        if: ${{ success() }}",
                ),
                encoding="utf-8",
            )

            rule_ids = self.rule_ids(root)

        self.assertIn("LB-POLICY-002", rule_ids)

    def test_gradle_exclude_task_equals_syntax_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self.valid_repository(directory)
            workflow = root / ".github/workflows/build.yml"
            workflow.write_text(
                workflow.read_text(encoding="utf-8") + "      - run: ./gradlew --exclude-task=detekt build\n",
                encoding="utf-8",
            )

            rule_ids = self.rule_ids(root)

        self.assertIn("LB-POLICY-002", rule_ids)

    def test_ruleset_cannot_exclude_the_protected_branch(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self.valid_repository(directory)
            path = root / ".github/rulesets/nextgen.json"
            ruleset = json.loads(path.read_text(encoding="utf-8"))
            ruleset["conditions"]["ref_name"]["exclude"] = ["refs/heads/nextgen"]
            path.write_text(json.dumps(ruleset), encoding="utf-8")

            rule_ids = self.rule_ids(root)

        self.assertIn("LB-POLICY-003", rule_ids)

    def test_vendor_entrypoint_requires_exact_0755_mode(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self.valid_repository(directory)
            (root / "scripts/verify-baritone-vendor.sh").chmod(0o775)

            rule_ids = self.rule_ids(root)

        self.assertIn("LB-POLICY-004", rule_ids)

    def test_coding_standard_must_document_every_stable_rule(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self.valid_repository(directory)
            standards = root / ".github/CODING_STANDARDS.md"
            standards.write_text(
                standards.read_text(encoding="utf-8").replace("lb-hyg-001", "missing-rule"),
                encoding="utf-8",
            )

            rule_ids = self.rule_ids(root)

        self.assertIn("LB-POLICY-007", rule_ids)

    def test_agents_contract_must_keep_actionable_feedback_loop(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self.valid_repository(directory)
            agents = root / "AGENTS.md"
            agents.write_text(agents.read_text(encoding="utf-8").replace("source-quality.md", "report.md"))

            rule_ids = self.rule_ids(root)

        self.assertIn("LB-POLICY-007", rule_ids)

    @staticmethod
    def valid_repository(directory: str) -> Path:
        root = Path(directory)
        policy_test_support.RepositoryPolicyTest.write_valid_fixture(root)
        return root

    @staticmethod
    def rule_ids(root: Path) -> set[str]:
        return {finding.rule_id for finding in POLICY.verify_repository(root)}


if __name__ == "__main__":
    unittest.main()
