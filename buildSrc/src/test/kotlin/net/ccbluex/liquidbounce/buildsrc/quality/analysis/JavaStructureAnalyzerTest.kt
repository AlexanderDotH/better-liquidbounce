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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JavaStructureAnalyzerTest {
    private val analyzer = JavaStructureAnalyzer(StructuralLimitPolicy.DEFAULT)

    @Test
    fun `Java production methods allow forty lines and reject forty one`() {
        val accepted = source("Accepted.java", methodWithTotalLines(40))
        val rejected = source("Rejected.java", methodWithTotalLines(41))

        assertTrue(analyzer.analyze(listOf(accepted)).isEmpty())
        val finding = analyzer.analyze(listOf(rejected)).single()

        assertEquals("method-lines:sample", finding.subject)
        assertEquals(41, finding.measuredValue)
        assertEquals(40, finding.limit)
    }

    @Test
    fun `Java tests allow sixty lines and reject sixty one`() {
        val accepted = source("AcceptedTest.java", methodWithTotalLines(60), SourceKind.TEST)
        val rejected = source("RejectedTest.java", methodWithTotalLines(61), SourceKind.TEST)

        assertTrue(analyzer.analyze(listOf(accepted)).isEmpty())
        assertEquals(61, analyzer.analyze(listOf(rejected)).single().measuredValue)
    }

    @Test
    fun `Java AST ignores strings and comments but rejects real complexity and nesting`() {
        val fake = source(
            "Fake.java",
            "void sample() { String value = \"if while && {\"; /* if (fake) {} */ consume(value); }",
        )
        val complex = source(
            "Complex.java",
            "void sample() { " + (1..13).joinToString(" ") { "if (flag$it) consume();" } + " }",
        )
        val nested = source(
            "Nested.java",
            "void sample() { if (a) { while (b) { for (;;) { if (c) consume(); } } } }",
        )

        assertTrue(analyzer.analyze(listOf(fake)).isEmpty())
        assertEquals(13, analyzer.analyze(listOf(complex)).single().measuredValue)
        assertEquals(4, analyzer.analyze(listOf(nested)).single { it.subject.startsWith("nesting-depth:") }.measuredValue)
    }

    @Test
    fun `Java else if chain stays at one nesting level`() {
        val chained = source(
            "Chained.java",
            "void sample() { " +
                "if (a) first(); else if (b) second(); else if (c) third(); else if (d) fourth(); else fifth(); }",
        )

        assertTrue(analyzer.analyze(listOf(chained)).isEmpty())
    }

    @Test
    fun `Java lambda complexity is measured as its own function`() {
        val decisions = (1..13).joinToString(" ") { "if (flag$it) consume();" }
        val source = source(
            "Lambda.java",
            "void sample() { Runnable action = () -> { $decisions }; register(action); }",
        )

        val finding = analyzer.analyze(listOf(source)).single()

        assertTrue(finding.subject.startsWith("cognitive-complexity:lambda@"))
        assertEquals(13, finding.measuredValue)
    }

    private fun methodWithTotalLines(total: Int): String {
        val body = (1..total - 2).joinToString("\n") { "consume($it);" }
        return "void sample() {\n$body\n}"
    }

    private fun source(name: String, method: String, kind: SourceKind = SourceKind.PRODUCTION) = SourceFile(
        "src/main/java/sample/$name",
        "package sample;\nclass Sample {\n$method\n}",
        kind,
    )
}
