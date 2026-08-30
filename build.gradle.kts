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

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask

plugins {
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.gradleGitProperties)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
}

base {
    archivesName = project.property("archives_base_name") as String
    version = project.property("mod_version") as String
    group = project.property("maven_group") as String
}

loom {
    accessWidenerPath = file("src/main/resources/liquidbounce.accesswidener")
}

detekt {
    config.setFrom(file("${rootProject.projectDir}/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    baseline = file("${rootProject.projectDir}/config/detekt/baseline.xml")
}

tasks.withType<Detekt>().configureEach {
    reports {
        sarif.required.set(true)
    }
}

tasks.register<DetektCreateBaselineTask>("detektProjectBaseline") {
    description = "Overrides the current Detekt baseline"
    ignoreFailures.set(true)
    parallel.set(true)
    buildUponDefaultConfig.set(true)
    setSource(files(rootDir))
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline.set(file("$rootDir/config/detekt/baseline.xml"))
    include("**/*.kt", "**/*.kts")
    exclude("**/resources/**", "**/build/**")
}

java {
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get().toInt()))
    }
}

kotlin {
    compilerOptions {
        suppressWarnings = true
        jvmToolchain(libs.versions.jdk.get().toInt())
    }
}

tasks.runClient {
    jvmArgs("-XX:+UseZGC")
}

apply(from = "gradle/repositories.gradle.kts")
apply(from = "gradle/game-dependencies.gradle.kts")
apply(from = "gradle/runtime-dependencies.gradle.kts")
apply(from = "gradle/theme.gradle.kts")
apply(from = "gradle/resource-packaging.gradle.kts")
apply(from = "gradle/testing.gradle.kts")
apply(from = "gradle/repository-policy.gradle.kts")
apply(from = "gradle/release-verification.gradle.kts")
apply(from = "gradle/artifacts.gradle.kts")
