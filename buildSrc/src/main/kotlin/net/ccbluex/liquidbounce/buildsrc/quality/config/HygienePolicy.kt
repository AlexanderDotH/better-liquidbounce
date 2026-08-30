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

package net.ccbluex.liquidbounce.buildsrc.quality.config

import net.ccbluex.liquidbounce.buildsrc.quality.analysis.FileLimitPolicy
import net.ccbluex.liquidbounce.buildsrc.quality.analysis.StructuralLimitPolicy
import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceKind

data class HygienePolicy(
    val includedExtensions: Set<String>,
    val excludedDirectoryNames: Set<String>,
    val excludedPathPrefixes: Set<String>,
    val testPathPrefixes: Set<String>,
    val uiPathPrefixes: Set<String>,
    val testFileNamePatterns: List<Regex>,
    val fileLimits: FileLimitPolicy,
    val forbiddenSuppressions: Set<String>,
    val packageRoots: List<String>,
    val categoryRoots: Set<String>,
    val strategyDirectories: Set<String>,
    val minimumClusterFiles: Int,
    val minimumPrefixTokens: Int,
    val structuralLimits: StructuralLimitPolicy = StructuralLimitPolicy.DEFAULT,
) {
    fun validateContract(): HygienePolicy = apply {
        require(includedExtensions == REQUIRED_SOURCE_EXTENSIONS) {
            "Source discovery extensions must remain ${REQUIRED_SOURCE_EXTENSIONS.sorted()}"
        }
        require(excludedDirectoryNames == GENERATED_DIRECTORY_NAMES) {
            "Only generated, third-party, and tool-output directories may be excluded"
        }
        require(excludedPathPrefixes == GENERATED_PATH_PREFIXES) {
            "Only generated resource paths may be excluded"
        }
        require(testPathPrefixes == REQUIRED_TEST_PATH_PREFIXES) { "Test roots may not be widened or omitted" }
        require(uiPathPrefixes == setOf("src-theme")) { "The UI source root must remain src-theme" }
        require(testFileNamePatterns.mapTo(mutableSetOf(), Regex::pattern) == REQUIRED_TEST_PATTERNS) {
            "Test filename patterns may not be widened or omitted"
        }
        require(fileLimits == FileLimitPolicy(200, 200, 300)) { "Source file limits must remain 200/200/300" }
        require(structuralLimits == StructuralLimitPolicy.DEFAULT) { "Kotlin structural limits must remain 40/60/12/3" }
        require(minimumClusterFiles == 5 && minimumPrefixTokens == 2) { "Prefix cluster limits must remain 5/2" }
        require(forbiddenSuppressions == REQUIRED_STRUCTURAL_RULES) {
            "All structural suppressions must remain forbidden"
        }
        require(packageRoots.toSet() == REQUIRED_PACKAGE_ROOTS) { "Package/path analysis roots may not be omitted" }
        require(categoryRoots == REQUIRED_CATEGORY_ROOTS) { "Every module category root must remain protected" }
        require(strategyDirectories == setOf("modes", "exploits", "triggers")) {
            "Only modes, exploits, and triggers are homogeneous strategy collections"
        }
    }

    fun classify(path: String): SourceKind {
        val normalized = path.replace('\\', '/')
        if (testPathPrefixes.any { normalized.isWithin(it) } || testFileNamePatterns.any { it.matches(normalized) }) {
            return SourceKind.TEST
        }
        return if (uiPathPrefixes.any { normalized.isWithin(it) }) SourceKind.UI else SourceKind.PRODUCTION
    }

    private fun String.isWithin(prefix: String): Boolean {
        val normalizedPrefix = prefix.replace('\\', '/').trimEnd('/')
        return this == normalizedPrefix || startsWith("$normalizedPrefix/")
    }

    private companion object {
        val REQUIRED_SOURCE_EXTENSIONS = setOf("cjs", "java", "js", "kt", "kts", "mjs", "py", "sh", "svelte", "ts")
        val GENERATED_DIRECTORY_NAMES = setOf(
            ".git", ".gradle", ".idea", ".kotlin", ".svelte-kit", "build", "dist", "generated",
            "node_modules", "out", "third_party", "vendor",
        )
        val GENERATED_PATH_PREFIXES = setOf("src/main/resources", "src-theme/public")
        val REQUIRED_TEST_PATH_PREFIXES = setOf(
            "buildSrc/src/test", "scripts/tests", "src-theme/test", "src/test",
            "tools/macekill-lab/observer-plugin/src/test",
        )
        val REQUIRED_TEST_PATTERNS = setOf(
            ".*(?:Test|Tests|Spec)\\.(?:java|kt)$",
            ".*\\.(?:spec|test)\\.(?:cjs|js|mjs|ts)$",
        )
        val REQUIRED_PACKAGE_ROOTS = setOf(
            "buildSrc/src/main/kotlin",
            "buildSrc/src/test/kotlin",
            "src/main/java",
            "src/main/kotlin",
            "src/test/kotlin",
            "tools/macekill-lab/observer-plugin/src/main/java",
            "tools/macekill-lab/observer-plugin/src/test/java",
        )
        val REQUIRED_CATEGORY_ROOTS = setOf(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat",
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/exploit",
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/fun",
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/misc",
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement",
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player",
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render",
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world",
        )
        val REQUIRED_STRUCTURAL_RULES = setOf(
            "LargeClass",
            "TooManyFunctions",
            "LongMethod",
            "CognitiveComplexMethod",
            "NestedBlockDepth",
        )
    }
}
