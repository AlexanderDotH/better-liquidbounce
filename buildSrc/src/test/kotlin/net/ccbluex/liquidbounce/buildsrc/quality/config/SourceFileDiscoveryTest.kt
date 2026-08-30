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
import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceKind
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class SourceFileDiscoveryTest {

    @Test
    fun `discovers every first party source extension and excludes generated resources and scss`() {
        val root = Files.createTempDirectory("source-discovery")
        write(root, "src/main/kotlin/sample/App.kt")
        write(root, "build.gradle.kts")
        write(root, "src/main/java/sample/App.java")
        write(root, "src/test/kotlin/sample/AppTest.kt")
        write(root, "src-theme/src/View.svelte")
        write(root, "src-theme/src/View.styles.scss")
        write(root, "src-theme/src/View.test.ts")
        write(root, "src-theme/src/runtime.js")
        write(root, "src-theme/src/runtime.mjs")
        write(root, "src-theme/src/runtime.cjs")
        write(root, "scripts/verify.sh")
        write(root, "scripts/baritone_vendor/verify_metadata.py")
        write(root, "src/main/resources/generated.js")
        write(root, "third_party/vendor.js")
        val policy = policy()

        val files = SourceFileDiscovery.load(root, policy)

        assertEquals(
            listOf(
                "build.gradle.kts" to SourceKind.PRODUCTION,
                "scripts/baritone_vendor/verify_metadata.py" to SourceKind.PRODUCTION,
                "scripts/verify.sh" to SourceKind.PRODUCTION,
                "src-theme/src/View.svelte" to SourceKind.UI,
                "src-theme/src/View.test.ts" to SourceKind.TEST,
                "src-theme/src/runtime.cjs" to SourceKind.UI,
                "src-theme/src/runtime.js" to SourceKind.UI,
                "src-theme/src/runtime.mjs" to SourceKind.UI,
                "src/main/java/sample/App.java" to SourceKind.PRODUCTION,
                "src/main/kotlin/sample/App.kt" to SourceKind.PRODUCTION,
                "src/test/kotlin/sample/AppTest.kt" to SourceKind.TEST,
            ),
            files.map { it.path to it.kind },
        )
    }

    private fun write(root: Path, relative: String) {
        root.resolve(relative).also { it.parent.createDirectories() }.writeText("content")
    }

    private fun policy() = HygienePolicy(
        includedExtensions = setOf("kt", "kts", "java", "svelte", "ts", "js", "mjs", "cjs", "py", "sh"),
        excludedDirectoryNames = setOf("build", "third_party", "generated"),
        excludedPathPrefixes = setOf("src/main/resources"),
        testPathPrefixes = setOf("src/test"),
        uiPathPrefixes = setOf("src-theme"),
        testFileNamePatterns = listOf(Regex(".*\\.test\\.ts")),
        fileLimits = FileLimitPolicy(200, 200, 300),
        forbiddenSuppressions = emptySet(),
        packageRoots = emptyList(),
        categoryRoots = emptySet(),
        strategyDirectories = emptySet(),
        minimumClusterFiles = 5,
        minimumPrefixTokens = 2,
    )
}
