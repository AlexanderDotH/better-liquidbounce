/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

import org.gradle.api.tasks.Exec
import org.gradle.jvm.tasks.Jar
import java.io.OutputStream
import java.util.zip.ZipFile

val requiredReleaseEntries = listOf(
    "META-INF/MANIFEST.MF",
    "fabric.mod.json",
    "liquidbounce.mixins.json",
    "resources/liquidbounce/themes/liquidbounce/index.html",
)
val releaseJar = tasks.named<Jar>("jar")
val releaseArchive = releaseJar.flatMap { it.archiveFile }

val verifyBaritoneVendor = tasks.register<Exec>("verifyBaritoneVendor") {
    group = "verification"
    description = "Verifies the pinned Baritone vendor bundle"
    commandLine(file("scripts/verify-baritone-vendor.sh"))
}

val verifyReleaseArtifact = tasks.register("verifyReleaseArtifact") {
    group = "verification"
    description = "Verifies the release mod JAR and every required runtime entry"
    dependsOn(releaseJar)
    doLast {
        val archive = releaseArchive.get().asFile
        check(archive.isFile && archive.length() > 0L) { "Release JAR is missing or empty: $archive" }
        check(!archive.name.endsWith("-dev.jar")) { "Release verification selected a development JAR: $archive" }
        check(!archive.name.endsWith("-sources.jar")) { "Release verification selected a sources JAR: $archive" }
        ZipFile(archive).use { zip ->
            requiredReleaseEntries.forEach { entry ->
                check(zip.getEntry(entry) != null) { "Release JAR is missing '$entry': $archive" }
            }
            zip.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                zip.getInputStream(entry).use { stream -> stream.transferTo(OutputStream.nullOutputStream()) }
            }
        }
    }
}

val verifyReleaseVendorBundle = tasks.register<Exec>("verifyReleaseVendorBundle") {
    group = "verification"
    description = "Verifies the nested Baritone artifact inside the release JAR"
    dependsOn(verifyReleaseArtifact)
    doFirst {
        commandLine(
            file("scripts/verify-baritone-vendor.sh"),
            releaseArchive.get().asFile,
        )
    }
}

val testBuildLogic = tasks.register("testBuildLogic") {
    group = "verification"
    description = "Runs the buildSrc contract tests"
    dependsOn(gradle.includedBuild("build-logic-tests").task(":test"))
}

tasks.register("qualityGate") {
    group = "verification"
    description = "Runs repository, backend, theme, integration, and release acceptance checks"
    dependsOn(
        "verifyBuildEnvironment",
        "sourceQualityGate",
        "verifyRepositoryPolicy",
        "detekt",
        "test",
        "litematicaIntegrationTest",
        "verifyI18nJsonKeys",
        "checkTheme",
        "testTheme",
        "buildTheme",
        "build",
        testBuildLogic,
        verifyBaritoneVendor,
        verifyReleaseVendorBundle,
    )
}
