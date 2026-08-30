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

import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.PathSensitivity

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val requiredJava = catalog.findVersion("jdk").get().requiredVersion
val requiredNode = providers.fileContents(layout.projectDirectory.file(".node-version")).asText.map(String::trim)
val requiredNpm = providers.fileContents(layout.projectDirectory.file(".npm-version")).asText.map(String::trim)
val nodeVersion = providers.exec {
    commandLine("node", "--version")
}.standardOutput.asText.map(String::trim)
val npmExecutable = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"
val npmVersion = providers.exec {
    commandLine(npmExecutable, "--version")
}.standardOutput.asText.map(String::trim)

val verifyBuildEnvironment = tasks.register("verifyBuildEnvironment") {
    group = "verification"
    description = "Verifies the pinned Java, Node, and npm versions"
    inputs.files(".node-version", ".npm-version", "gradle/libs.versions.toml")
    inputs.property("actualNode", nodeVersion)
    inputs.property("actualNpm", npmVersion)
    doLast {
        check(JavaVersion.current().majorVersion == requiredJava) {
            "Java $requiredJava is required, found ${JavaVersion.current().majorVersion}. Set JAVA_HOME to Java 25."
        }
        check(nodeVersion.get().removePrefix("v") == requiredNode.get()) {
            "Node ${requiredNode.get()} is required, found ${nodeVersion.get()}. Run fnm use ${requiredNode.get()}."
        }
        check(npmVersion.get() == requiredNpm.get()) {
            "npm ${requiredNpm.get()} is required, found ${npmVersion.get()}. Install npm@${requiredNpm.get()}."
        }
    }
}

val npmInstallTheme = tasks.register<Exec>("npmInstallTheme") {
    description = "Installs the locked dependencies for the web theme"
    dependsOn(verifyBuildEnvironment)
    workingDir = file("src-theme")
    commandLine(npmExecutable, "ci")
    inputs.files("src-theme/package.json", "src-theme/package-lock.json")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir("src-theme/node_modules")
}

tasks.register<Exec>("buildTheme") {
    description = "Builds the distributable web theme assets"
    dependsOn(npmInstallTheme)
    workingDir = file("src-theme")
    commandLine(npmExecutable, "run", "build")
    inputs.property("nodeVersion", nodeVersion)
    inputs.property("npmVersion", npmVersion)
    inputs.files(
        "src-theme/package.json",
        "src-theme/package-lock.json",
        "src-theme/index.html",
        "src-theme/svelte.config.js",
        "src-theme/tsconfig.json",
        "src-theme/tsconfig.node.json",
        "src-theme/vite.config.ts",
    ).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src-theme/src").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src-theme/public").withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir("src-theme/dist")
    outputs.cacheIf("Theme output is reproducible for locked dependencies and tool versions") { true }
}

tasks.register<Exec>("checkTheme") {
    group = "verification"
    description = "Type-checks the web theme"
    dependsOn(npmInstallTheme)
    workingDir = file("src-theme")
    commandLine(npmExecutable, "run", "check")
    inputs.property("nodeVersion", nodeVersion)
    inputs.property("npmVersion", npmVersion)
    inputs.files("src-theme/package.json", "src-theme/package-lock.json", "src-theme/tsconfig.json")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src-theme/src").withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.register<Exec>("testTheme") {
    group = "verification"
    description = "Runs the web theme contract tests"
    dependsOn(npmInstallTheme)
    workingDir = file("src-theme")
    commandLine(npmExecutable, "test")
    inputs.property("nodeVersion", nodeVersion)
    inputs.property("npmVersion", npmVersion)
    inputs.files("src-theme/package.json", "src-theme/package-lock.json", "src-theme/tsconfig.json")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src-theme/src").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src-theme/test").withPathSensitivity(PathSensitivity.RELATIVE)
}
