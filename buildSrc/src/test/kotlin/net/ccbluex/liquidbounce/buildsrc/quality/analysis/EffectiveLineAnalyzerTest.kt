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

class EffectiveLineAnalyzerTest {

    private val policy = FileLimitPolicy(productionLimit = 200, uiLimit = 200, testLimit = 300)
    private val analyzer = EffectiveLineAnalyzer(policy)

    @Test
    fun `standard license and blank lines do not count`() {
        val content = "$STANDARD_LICENSE\n\n${lines(200)}\n"

        assertEquals(200, EffectiveLineCounter.count(content))
        assertTrue(analyzer.analyze(listOf(source("ModuleClean.kt", content))).isEmpty())
    }

    @Test
    fun `imports and domain comments count toward production limit`() {
        val content = lines(199) + "\n// explains a domain invariant\n"

        assertTrue(analyzer.analyze(listOf(source("Clean.kt", content))).isEmpty())
        val violation = analyzer.analyze(listOf(source("Dirty.kt", "$content\nimport sample.Type"))).single()

        assertEquals("LB-HYG-001", violation.ruleId)
        assertEquals(201, violation.measuredValue)
        assertEquals(200, violation.limit)
    }

    @Test
    fun `test files allow exactly three hundred effective lines`() {
        val accepted = source("ExampleTest.kt", lines(300), SourceKind.TEST)
        val rejected = source("LargeTest.kt", lines(301), SourceKind.TEST)

        assertTrue(analyzer.analyze(listOf(accepted)).isEmpty())
        assertEquals(301, analyzer.analyze(listOf(rejected)).single().measuredValue)
    }

    private fun source(path: String, content: String, kind: SourceKind = SourceKind.PRODUCTION) =
        SourceFile(path, content, kind)

    private fun lines(count: Int) = (1..count).joinToString("\n") { "val line$it = $it" }

    private companion object {
        val STANDARD_LICENSE = """
            /*
             * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
             *
             * Copyright (c) 2015 - 2026 CCBlueX
             *
             * LiquidBounce is free software: you can redistribute it and/or modify
             * it under the terms of the GNU General Public License as published by
             * the Free Software Foundation, either version 3 of the License, or
             * (at your option) any later version.
             */
        """.trimIndent()
    }
}
