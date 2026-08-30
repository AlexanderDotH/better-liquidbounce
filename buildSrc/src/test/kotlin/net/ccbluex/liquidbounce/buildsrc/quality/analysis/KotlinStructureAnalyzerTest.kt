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

class KotlinStructureAnalyzerTest {

    private val analyzer = KotlinStructureAnalyzer(StructuralLimitPolicy.DEFAULT)

    @Test
    fun `production methods allow forty lines and reject forty one`() {
        val accepted = source("Accepted.kt", methodWithTotalLines(40))
        val rejected = source("Rejected.kt", methodWithTotalLines(41))

        assertTrue(analyzer.analyze(listOf(accepted)).isEmpty())
        val finding = analyzer.analyze(listOf(rejected)).single()

        assertEquals("method-lines:sample", finding.subject)
        assertEquals(41, finding.measuredValue)
        assertEquals(40, finding.limit)
    }

    @Test
    fun `test methods allow sixty lines and reject sixty one`() {
        val accepted = source("AcceptedTest.kt", methodWithTotalLines(60), SourceKind.TEST)
        val rejected = source("RejectedTest.kt", methodWithTotalLines(61), SourceKind.TEST)

        assertTrue(analyzer.analyze(listOf(accepted)).isEmpty())
        assertEquals(61, analyzer.analyze(listOf(rejected)).single().measuredValue)
    }

    @Test
    fun `production methods reject complexity above twelve and nesting above three`() {
        val complex = source("Complex.kt", method((1..13).joinToString("\n") { "if (flag$it) consume()" }))
        val nested = source("Nested.kt", method("if (a) {\nif (b) {\nif (c) {\nif (d) consume()\n}\n}\n}"))

        val complexFinding = analyzer.analyze(listOf(complex)).single()
        val nestedFinding = analyzer.analyze(listOf(nested)).single { it.subject.startsWith("nesting-depth:") }

        assertEquals(13, complexFinding.measuredValue)
        assertEquals(12, complexFinding.limit)
        assertEquals(4, nestedFinding.measuredValue)
        assertEquals(3, nestedFinding.limit)
    }

    @Test
    fun `comments and strings do not create false structural complexity`() {
        val content = method("// if (fake) {\nval text = \"while when catch && || {\"\nconsume(text)")

        assertTrue(analyzer.analyze(listOf(source("Clean.kt", content))).isEmpty())
    }

    @Test
    fun `escaped fun package segment is not parsed as a function declaration`() {
        val declarations = (1..41).joinToString("\n") { "val value$it = $it" }
        val content = "package sample.`fun`\n@Suppress(\"unused\")\nobject Sample {\n" +
            "$declarations\nfun small() = Unit\n}"

        assertTrue(analyzer.analyze(listOf(source("EscapedPackage.kt", content))).isEmpty())
    }

    @Test
    fun `bodyless signatures do not consume the following expression or block body`() {
        val padding = (1..41).joinToString("\n") { "// contract note $it" }
        val content = """
            interface Port {
                fun beginExecution()
                $padding
                fun close()
                $padding
                fun expression(): Int = 1
                fun block() { consume() }
            }
        """.trimIndent()

        val parsedNames = KotlinFunctionParser.parse(KotlinSourceMask.mask(content)).map { it.name }

        assertEquals(listOf("expression", "block"), parsedNames)
        assertTrue(analyzer.analyze(listOf(source("Port.kt", content))).isEmpty())
    }

    private fun methodWithTotalLines(total: Int): String {
        val statements = (1..total - 2).joinToString("\n") { "consume($it)" }
        return method(statements)
    }

    private fun method(body: String) = "fun sample() {\n$body\n}"

    private fun source(path: String, content: String, kind: SourceKind = SourceKind.PRODUCTION) =
        SourceFile("src/main/kotlin/sample/$path", content, kind)
}
