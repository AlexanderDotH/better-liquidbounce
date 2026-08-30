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
package net.ccbluex.liquidbounce.features.clicking.pattern

import net.ccbluex.liquidbounce.features.clicking.pattern.patterns.StabilizedPattern
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClickPatternBoundaryTest {

    @Test
    fun `four stabilized clicks remain evenly distributed across one cycle`() {
        val clicks = IntArray(20)

        StabilizedPattern.fill(clicks, 4..4, TEST_CONTEXT)

        val expected = IntArray(20) { index -> if (index % 5 == 0) 1 else 0 }
        assertContentEquals(expected, clicks)
    }

    @Test
    fun `pattern algorithms depend on their context instead of the click scheduler`() {
        val patternSources = Files.walk(PATTERN_SOURCE_ROOT).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "kt" }
                .map(Files::readString)
                .toList()
                .joinToString("\n")
        }
        val clickerSource = Files.readString(CLICKER_SOURCE)

        assertFalse("import net.ccbluex.liquidbounce.features.clicking.Clicker" in patternSources)
        assertTrue("context: ClickPatternContext" in patternSources)
        assertTrue("ClickPatternContext" in clickerSource)
    }

    private companion object {
        val TEST_CONTEXT = object : ClickPatternContext {
            override val random = Random(0L)
        }

        val PATTERN_SOURCE_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/clicking/pattern"
        )
        val CLICKER_SOURCE: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/clicking/Clicker.kt"
        )
    }
}
