"""Checks that span repository build and tool packaging."""

from __future__ import annotations

import re
from pathlib import Path

from .ci_checks import (
    verify_review_contract,
    verify_ruleset,
    verify_toolchains,
    verify_vendor_entrypoint,
)
from .model import REQUIRED_GRADLE_SCRIPTS, Finding, finding, read_text
from .workflow_checks import verify_workflow


def verify_gradle_wiring(root: Path) -> Finding | None:
    build = read_text(root, "build.gradle.kts") or ""
    applied = re.findall(r'apply\(from\s*=\s*"([^"]+\.gradle\.kts)"\)', build)
    gradle_directory = root / "gradle"
    actual = {
        path.relative_to(root).as_posix()
        for path in gradle_directory.glob("*.gradle.kts")
    } if gradle_directory.is_dir() else set()
    expected = set(REQUIRED_GRADLE_SCRIPTS)
    release_verification = read_text(root, "gradle/release-verification.gradle.kts") or ""
    settings = read_text(root, "settings.gradle.kts") or ""
    build_logic_contract = (
        'tasks.register("testBuildLogic")',
        'gradle.includedBuild("build-logic-tests").task(":test")',
        "testBuildLogic,",
    )
    if (
        set(applied) == expected == actual
        and len(applied) == len(expected)
        and 'includeBuild("buildSrc")' in settings
        and 'name = "build-logic-tests"' in settings
        and all(fragment in release_verification for fragment in build_logic_contract)
    ):
        return None
    return finding(
        "LB-POLICY-005",
        "build.gradle.kts",
        "Gradle responsibility scripts or build-logic tests are missing, duplicated, or left unwired.",
        "Apply every canonical Gradle slice exactly once and make testBuildLogic a qualityGate dependency.",
    )


def verify_typegen_packaging(root: Path) -> Finding | None:
    path = ".github/workflows/generate-definitions.yml"
    workflow = read_text(root, path) or ""
    helpers = list((root / "ts-defgen").glob("*.js")) if (root / "ts-defgen").is_dir() else []
    valid = (
        (root / "ts-defgen.js").is_file()
        and bool(helpers)
        and "cp ts-defgen.js run/LiquidBounce/scripts/" in workflow
        and "cp -R ts-defgen run/LiquidBounce/scripts/" in workflow
    )
    if valid:
        return None
    return finding(
        "LB-POLICY-006",
        path,
        "The TypeScript-definition entrypoint is copied without its focused CommonJS helper modules.",
        "Keep ts-defgen.js and ts-defgen/*.js together in the generated-definition runtime directory.",
    )


def verify_release_artifact(root: Path) -> Finding | None:
    path = "gradle/release-verification.gradle.kts"
    build_script = read_text(root, path) or ""
    plugin_catalog = read_text(root, "gradle/libs.versions.toml") or ""
    workflow = read_text(root, ".github/workflows/build.yml") or ""
    required_build_fragments = (
        'val releaseJar = tasks.named<Jar>("jar")',
        'val releaseArchive = releaseJar.flatMap { it.archiveFile }',
        "dependsOn(releaseJar)",
        'endsWith("-dev.jar")',
        'endsWith("-sources.jar")',
    )
    non_obfuscated_loom = 'id = "net.fabricmc.fabric-loom"' in plugin_catalog
    workflow_contract = (
        "build verifyReleaseArtifact" in workflow
        and "! -name '*-dev.jar'" in workflow
        and "! -name '*-sources.jar'" in workflow
    )
    valid = (
        non_obfuscated_loom
        and all(fragment in build_script for fragment in required_build_fragments)
        and "remapJar" not in build_script
        and workflow_contract
    )
    if valid:
        return None
    return finding(
        "LB-POLICY-008",
        path,
        "Release verification is not bound to non-obfuscated Loom's production JAR.",
        "Verify jar.archiveFile for Minecraft 26.x, reject dev/sources classifiers, and run verifyReleaseArtifact.",
    )


def verify_repository(root: Path) -> list[Finding]:
    checks = (
        verify_toolchains,
        verify_workflow,
        verify_ruleset,
        verify_vendor_entrypoint,
        verify_gradle_wiring,
        verify_typegen_packaging,
        verify_review_contract,
        verify_release_artifact,
    )
    return [result for check in checks if (result := check(root)) is not None]
