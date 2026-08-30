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

val testRepositoryPolicy = tasks.register<Exec>("testRepositoryPolicy") {
    group = "verification"
    description = "Runs the repository-policy contract tests"
    commandLine(
        "python3",
        "-B",
        "-m",
        "unittest",
        "discover",
        "-s",
        "scripts/tests",
        "-p",
        "test_*.py",
    )
}

val testTypeDefinitionTooling = tasks.register<Exec>("testTypeDefinitionTooling") {
    group = "verification"
    description = "Runs the TypeScript-definition template contract tests"
    dependsOn("verifyBuildEnvironment")
    val tests = fileTree("scripts/tests") { include("*.test.mjs") }
    inputs.files(tests)
    inputs.dir("ts-defgen")
    doFirst {
        commandLine(listOf("node", "--test") + tests.files.sorted().map(File::getPath))
    }
}

tasks.register<Exec>("verifyRepositoryPolicy") {
    group = "verification"
    description = "Validates toolchain pins, CI parity, and the nextgen ruleset payload"
    dependsOn(testRepositoryPolicy, testTypeDefinitionTooling)
    commandLine("python3", "-B", "scripts/verify_repository_policy.py")
}
