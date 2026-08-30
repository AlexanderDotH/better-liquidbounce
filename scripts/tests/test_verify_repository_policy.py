"""Contract tests for the versioned repository policy."""

from __future__ import annotations

import importlib.util
import json
import stat
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).parents[1] / "verify_repository_policy.py"
SPEC = importlib.util.spec_from_file_location("verify_repository_policy", SCRIPT)
assert SPEC and SPEC.loader
POLICY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(POLICY)
DOCUMENTED_RULES = (
    *(f"LB-HYG-{number:03d}" for number in range(1, 6)),
    "LB-ARCH-001",
    "LB-ARCH-002",
    "LB-RATCHET-001",
    "LB-RATCHET-002",
    *(f"LB-POLICY-{number:03d}" for number in range(1, 9)),
)


class RepositoryPolicyTest(unittest.TestCase):
    def test_empty_repository_reports_each_missing_contract(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            findings = POLICY.verify_repository(Path(directory))

        rule_ids = {finding.rule_id for finding in findings}
        self.assertEqual(
            {
                "LB-POLICY-001", "LB-POLICY-002", "LB-POLICY-003",
                "LB-POLICY-004", "LB-POLICY-005", "LB-POLICY-006",
                "LB-POLICY-007", "LB-POLICY-008",
            },
            rule_ids,
        )

    def test_complete_fixture_satisfies_policy(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_valid_fixture(root)

            findings = POLICY.verify_repository(root)

        self.assertEqual([], findings)

    def test_workflow_cannot_skip_tests_or_detekt(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_valid_fixture(root)
            workflow = root / ".github/workflows/build.yml"
            workflow.write_text(
                workflow.read_text(encoding="utf-8") + "\n# ./gradlew build -x test\n",
                encoding="utf-8",
            )

            findings = POLICY.verify_repository(root)

        self.assertIn("LB-POLICY-002", {finding.rule_id for finding in findings})

    def test_acceptance_jobs_cannot_be_limited_to_one_event(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_valid_fixture(root)
            workflow = root / ".github/workflows/build.yml"
            content = workflow.read_text(encoding="utf-8")
            workflow.write_text(
                content.replace("  quality-gate:\n", "  quality-gate:\n    if: github.event_name == 'push'\n"),
                encoding="utf-8",
            )

            findings = POLICY.verify_repository(root)

        self.assertIn("LB-POLICY-002", {finding.rule_id for finding in findings})

    def test_toolchain_versions_cannot_be_duplicated_in_workflows(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_valid_fixture(root)
            (root / ".github/workflows/manual.yml").write_text(
                "steps:\n  - uses: actions/setup-java@v6\n    with:\n      java-version: '25'\n",
                encoding="utf-8",
            )

            findings = POLICY.verify_repository(root)

        self.assertIn("LB-POLICY-001", {finding.rule_id for finding in findings})

    def test_ruleset_rejects_single_owner_approval_deadlock(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_valid_fixture(root)
            ruleset_path = root / ".github/rulesets/nextgen.json"
            ruleset = json.loads(ruleset_path.read_text(encoding="utf-8"))
            pull_request = next(rule for rule in ruleset["rules"] if rule["type"] == "pull_request")
            pull_request["parameters"]["require_code_owner_review"] = True
            ruleset_path.write_text(json.dumps(ruleset), encoding="utf-8")

            findings = POLICY.verify_repository(root)

        self.assertIn("LB-POLICY-003", {finding.rule_id for finding in findings})

    def test_ruleset_rejects_solo_human_approval_deadlock(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_valid_fixture(root)
            ruleset_path = root / ".github/rulesets/nextgen.json"
            ruleset = json.loads(ruleset_path.read_text(encoding="utf-8"))
            pull_request = next(rule for rule in ruleset["rules"] if rule["type"] == "pull_request")
            pull_request["parameters"]["require_last_push_approval"] = True
            pull_request["parameters"]["required_approving_review_count"] = 1
            ruleset_path.write_text(json.dumps(ruleset), encoding="utf-8")

            findings = POLICY.verify_repository(root)

        self.assertIn("LB-POLICY-003", {finding.rule_id for finding in findings})

    def test_vendor_entrypoint_requires_git_executable_shape(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_valid_fixture(root)
            vendor = root / "scripts/verify-baritone-vendor.sh"
            vendor.chmod(0o744)

            findings = POLICY.verify_repository(root)

        self.assertIn("LB-POLICY-004", {finding.rule_id for finding in findings})

    def test_review_contract_requires_all_governance_files(self) -> None:
        required = (
            "CONTRIBUTING.md",
            ".github/CODEOWNERS",
            ".github/pull_request_template.md",
            ".github/CODING_STANDARDS.md",
            "AGENTS.md",
        )
        for missing in required:
            with self.subTest(missing=missing), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.write_valid_fixture(root)
                (root / missing).unlink()

                findings = POLICY.verify_repository(root)

                self.assertIn("LB-POLICY-007", {finding.rule_id for finding in findings})

    def test_release_verifier_accepts_plain_jar_task_for_non_obfuscated_loom(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_valid_fixture(root)
            (root / "gradle/release-verification.gradle.kts").write_text(
                'val releaseJar = tasks.named<Jar>("jar")\n'
                'val releaseArchive = releaseJar.flatMap { it.archiveFile }\n'
                'val verifyReleaseArtifact = tasks.register("verifyReleaseArtifact") { dependsOn(releaseJar) }\n'
                'check(!archive.name.endsWith("-dev.jar"))\n'
                'check(!archive.name.endsWith("-sources.jar"))\n',
                encoding="utf-8",
            )

            findings = POLICY.verify_repository(root)

        self.assertNotIn("LB-POLICY-008", {finding.rule_id for finding in findings})

    def test_release_verifier_rejects_remap_task_for_non_obfuscated_loom(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_valid_fixture(root)
            verifier = root / "gradle/release-verification.gradle.kts"
            verifier.write_text(
                verifier.read_text(encoding="utf-8")
                .replace('tasks.named<Jar>("jar")', 'tasks.named<Jar>("remapJar")')
                .replace("releaseJar", "remappedReleaseJar"),
                encoding="utf-8",
            )

            findings = POLICY.verify_repository(root)

        self.assertIn("LB-POLICY-008", {finding.rule_id for finding in findings})

    @staticmethod
    def write_valid_fixture(root: Path) -> None:
        (root / ".github/workflows").mkdir(parents=True)
        (root / ".github/rulesets").mkdir(parents=True)
        (root / "gradle").mkdir()
        (root / "scripts").mkdir()
        (root / ".node-version").write_text("24.18.1\n", encoding="utf-8")
        (root / ".npm-version").write_text("12.0.2\n", encoding="utf-8")
        (root / "gradle/libs.versions.toml").write_text(
            'jdk = "25"\n'
            'fabric-loom = { id = "net.fabricmc.fabric-loom", version = "1.17.20" }\n',
            encoding="utf-8",
        )
        (root / "settings.gradle.kts").write_text(
            'includeBuild("buildSrc") { name = "build-logic-tests" }\n',
            encoding="utf-8",
        )
        scripts = POLICY.REQUIRED_GRADLE_SCRIPTS
        (root / "build.gradle.kts").write_text(
            "\n".join(f'apply(from = "{path}")' for path in scripts) + "\n",
            encoding="utf-8",
        )
        for script in scripts:
            (root / script).write_text("// focused build responsibility\n", encoding="utf-8")
        (root / "gradle/release-verification.gradle.kts").write_text(
            'val releaseJar = tasks.named<Jar>("jar")\n'
            'val releaseArchive = releaseJar.flatMap { it.archiveFile }\n'
            'val verifyReleaseArtifact = tasks.register("verifyReleaseArtifact") { dependsOn(releaseJar) }\n'
            'check(!archive.name.endsWith("-dev.jar"))\n'
            'check(!archive.name.endsWith("-sources.jar"))\n'
            'val testBuildLogic = tasks.register("testBuildLogic") {\n'
            '    dependsOn(gradle.includedBuild("build-logic-tests").task(":test"))\n'
            '}\n'
            'tasks.register("qualityGate") { dependsOn(testBuildLogic,) }\n',
            encoding="utf-8",
        )
        workflow = """
on: {push: {}, pull_request: {}}
jobs:
  quality-gate:
    name: quality-gate
    steps:
      - run: node-version-file: .node-version needs.extract-versions.outputs.jdk_version npm install --global
      - run: npm install --global npm@\"$(cat .npm-version)\"
      - run: ./gradlew --no-daemon qualityGate
      - name: Verify required quality reports
        if: ${{ always() }}
        run: |
          required_reports=(
            build/reports/source-hygiene/source-quality.md
            build/reports/source-hygiene/source-quality.json
            build/reports/source-hygiene/source-quality.sarif
            build/reports/detekt/detekt.sarif
          )
          missing=0
          for report in "${required_reports[@]}"; do
            if [[ ! -f "$report" ]]; then
              missing=1
            fi
          done
          exit "$missing"
      - name: Upload source-quality reports
        if: ${{ always() }}
        uses: actions/upload-artifact@v7
        with:
          if-no-files-found: error
          path: |
            build/reports/source-hygiene/source-quality.md
            build/reports/source-hygiene/source-quality.json
            build/reports/source-hygiene/source-quality.sarif
            build/reports/detekt/detekt.sarif
      - name: Upload source-quality SARIF
        if: ${{ always() && github.actor != 'dependabot[bot]' && (github.event_name == 'push' || github.event.pull_request.head.repo.full_name == github.repository) }}
        run: codeql-action/upload-sarif@ source-quality.sarif
      - name: Upload Detekt SARIF
        if: ${{ always() && github.actor != 'dependabot[bot]' && (github.event_name == 'push' || github.event.pull_request.head.repo.full_name == github.repository) }}
        run: codeql-action/upload-sarif@ detekt.sarif
  release-build:
    name: release-build
    steps:
      - run: node-version-file: .node-version needs.extract-versions.outputs.jdk_version npm install --global .npm-version
      - run: ./gradlew --no-daemon build verifyReleaseArtifact
      - run: find release ! -name '*-dev.jar' ! -name '*-sources.jar'
"""
        (root / ".github/workflows/build.yml").write_text(workflow, encoding="utf-8")
        (root / ".github/workflows/generate-definitions.yml").write_text(
            "cp ts-defgen.js run/LiquidBounce/scripts/\ncp -R ts-defgen run/LiquidBounce/scripts/\n",
            encoding="utf-8",
        )
        (root / "ts-defgen").mkdir()
        (root / "ts-defgen.js").write_text("// entrypoint\n", encoding="utf-8")
        (root / "ts-defgen/helper.js").write_text("// helper\n", encoding="utf-8")
        ruleset = {
            "target": "branch",
            "enforcement": "active",
            "bypass_actors": [],
            "conditions": {"ref_name": {"include": ["refs/heads/nextgen"], "exclude": []}},
            "rules": [
                {"type": "deletion"},
                {"type": "non_fast_forward"},
                {"type": "required_linear_history"},
                {
                    "type": "pull_request",
                    "parameters": {
                        "allowed_merge_methods": ["rebase"],
                        "dismiss_stale_reviews_on_push": True,
                        "require_code_owner_review": False,
                        "require_last_push_approval": False,
                        "required_approving_review_count": 0,
                        "required_review_thread_resolution": True,
                    },
                },
                {
                    "type": "required_status_checks",
                    "parameters": {
                        "strict_required_status_checks_policy": True,
                        "do_not_enforce_on_create": False,
                        "required_status_checks": [
                            {"context": "quality-gate"},
                            {"context": "release-build"},
                        ],
                    },
                },
            ],
        }
        (root / ".github/rulesets/nextgen.json").write_text(json.dumps(ruleset), encoding="utf-8")
        vendor = root / "scripts/verify-baritone-vendor.sh"
        vendor.write_text("#!/usr/bin/env bash\n", encoding="utf-8")
        vendor.chmod(vendor.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
        (root / "CONTRIBUTING.md").write_text(
            "Add characterization tests, then run `qualityGate`.\n",
            encoding="utf-8",
        )
        (root / ".github/CODEOWNERS").write_text("* @maintainer\n", encoding="utf-8")
        (root / ".github/pull_request_template.md").write_text(
            "- [ ] Characterization tests\n- [ ] `qualityGate`\n- [ ] No baseline increase\n",
            encoding="utf-8",
        )
        (root / ".github/CODING_STANDARDS.md").write_text(
            "\n".join(
                f'<a id="{rule_id.lower()}"></a>'
                for rule_id in DOCUMENTED_RULES
            ) + "\n",
            encoding="utf-8",
        )
        (root / "AGENTS.md").write_text(
            "Read source-quality.md and its stable rule ID before editing.\n"
            "Add characterization tests before moves.\n"
            "Run qualityGate after each responsibility.\n"
            "Never raise the baseline or add structural suppressions.\n"
            "Production files stay within 200 effective lines; tests stay within 300.\n",
            encoding="utf-8",
        )


if __name__ == "__main__":
    unittest.main()
