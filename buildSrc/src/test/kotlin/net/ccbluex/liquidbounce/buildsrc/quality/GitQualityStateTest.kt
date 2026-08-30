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

package net.ccbluex.liquidbounce.buildsrc.quality

import net.ccbluex.liquidbounce.buildsrc.quality.config.RatchetJson
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetBaseline
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetEntry
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitQualityStateTest {

    @Test
    fun `working tree paths and tracked ratchet reference are resolved deterministically`() {
        val root = Files.createTempDirectory("source-quality-git")
        val ratchet = root.resolve("config/source-ratchet.json")
        val source = root.resolve("src/Tracked.kt")
        source.parent.createDirectories()
        source.writeText("class Tracked")
        RatchetJson.write(ratchet, baseline(5))
        git(root, "init")
        git(root, "config", "user.email", "quality@example.invalid")
        git(root, "config", "user.name", "Source Quality")
        git(root, "config", "commit.gpgsign", "false")
        git(root, "add", ".")
        git(root, "commit", "-m", "baseline")

        source.writeText("class Changed")
        root.resolve("src/Untracked.kt").writeText("class Untracked")
        RatchetJson.write(ratchet, baseline(6))

        val state = GitQualityState.load(root, ratchet)

        assertEquals(setOf("config/source-ratchet.json", "src/Tracked.kt", "src/Untracked.kt"), state.touchedPaths)
        assertEquals(5, state.referenceBaseline?.entries?.single()?.maximum)
    }

    @Test
    fun `repository without ratchet has no reference baseline`() {
        val root = Files.createTempDirectory("source-quality-git-clean")
        root.resolve("src/Tracked.kt").also { it.parent.createDirectories() }.writeText("class Tracked")
        git(root, "init")
        git(root, "config", "user.email", "quality@example.invalid")
        git(root, "config", "user.name", "Source Quality")
        git(root, "config", "commit.gpgsign", "false")
        git(root, "add", ".")
        git(root, "commit", "-m", "baseline removed")

        val state = GitQualityState.load(root, root.resolve("config/source-ratchet.json"))

        assertEquals(null, state.referenceBaseline)
    }

    private fun baseline(maximum: Int) = RatchetBaseline(
        1,
        "revision",
        listOf(RatchetEntry("fingerprint", "LB-HYG-001", "src/Tracked.kt", "lines", maximum)),
    )

    private fun git(root: Path, vararg arguments: String) {
        val process = ProcessBuilder(listOf("git", "-C", root.toString()) + arguments).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { output }
    }
}
