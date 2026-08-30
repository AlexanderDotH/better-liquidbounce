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

package net.ccbluex.liquidbounce.buildsrc.quality.analysis

import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceFile
import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceKind
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeStructureAnalyzerTest {
    private val analyzer = ThemeStructureAnalyzer(Path.of("..").toAbsolutePath().normalize(), StructuralLimitPolicy.DEFAULT)

    @Test
    fun `TypeScript functions enforce production and test line limits`() {
        val production = source("src-theme/src/large.ts", functionWithTotalLines(41))
        val test = source("src-theme/test/large.test.ts", functionWithTotalLines(61), SourceKind.TEST)

        assertEquals(41, analyzer.analyze(listOf(production)).single().measuredValue)
        assertEquals(61, analyzer.analyze(listOf(test)).single().measuredValue)
    }

    @Test
    fun `TypeScript AST ignores template strings and comments`() {
        val source = source(
            "src-theme/src/clean.ts",
            "function clean() { const text = `if (fake) { while (fake) {} }`; /* if (fake) */ return text; }",
        )

        assertTrue(analyzer.analyze(listOf(source)).isEmpty())
    }

    @Test
    fun `Svelte script complexity is measured without counting markup blocks`() {
        val decisions = (1..13).joinToString("\n") { "if (flag$it) consume();" }
        val source = source(
            "src-theme/src/Complex.svelte",
            """
                <script lang="ts">
                    function complex() {
                        $decisions
                    }
                </script>
                {#if visible}<div>{`while (fake) {}`}</div>{/if}
            """.trimIndent(),
        )

        val finding = analyzer.analyze(listOf(source)).single()

        assertEquals("cognitive-complexity:complex", finding.subject)
        assertEquals(13, finding.measuredValue)
    }

    @Test
    fun `Svelte script rejects four real nested decisions`() {
        val source = source(
            "src-theme/src/Nested.svelte",
            "<script>function nested() { if (a) { while (b) { for (;;) { if (c) consume(); } } } }</script>",
        )

        val finding = analyzer.analyze(listOf(source)).single { it.subject.startsWith("nesting-depth:") }

        assertEquals(4, finding.measuredValue)
    }

    @Test
    fun `TypeScript else if chain stays at one nesting level`() {
        val source = source(
            "src-theme/src/chained.ts",
            "function chained() { " +
                "if (a) first(); else if (b) second(); else if (c) third(); else if (d) fourth(); else fifth(); }",
        )

        assertTrue(analyzer.analyze(listOf(source)).isEmpty())
    }

    private fun functionWithTotalLines(total: Int): String {
        val body = (1..total - 2).joinToString("\n") { "consume($it);" }
        return "function sample() {\n$body\n}"
    }

    private fun source(path: String, content: String, kind: SourceKind = SourceKind.UI) = SourceFile(path, content, kind)
}
