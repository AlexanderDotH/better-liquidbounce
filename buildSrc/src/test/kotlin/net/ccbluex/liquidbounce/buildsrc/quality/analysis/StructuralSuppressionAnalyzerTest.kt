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

class StructuralSuppressionAnalyzerTest {

    private val analyzer = StructuralSuppressionAnalyzer(
        setOf("LargeClass", "TooManyFunctions", "LongMethod", "CognitiveComplexMethod", "NestedBlockDepth"),
    )

    @Test
    fun `detects qualified file and declaration suppressions`() {
        val source = SourceFile(
            "src/main/kotlin/sample/Dirty.kt",
            """
                @file:Suppress("detekt:TooManyFunctions")
                package sample

                @Suppress(
                    "unused",
                    "detekt.CognitiveComplexMethod",
                )
                class Dirty
            """.trimIndent(),
            SourceKind.PRODUCTION,
        )

        val findings = analyzer.analyze(listOf(source))

        assertEquals(listOf("CognitiveComplexMethod", "TooManyFunctions"), findings.map { it.subject }.sorted())
        assertTrue(findings.all { it.ruleId == "LB-HYG-002" })
    }

    @Test
    fun `allows unrelated compiler suppressions`() {
        val source = SourceFile(
            "Clean.kt",
            "@Suppress(\"UNCHECKED_CAST\", \"NotLongMethodAtAll\")\nclass Clean",
            SourceKind.PRODUCTION,
        )

        assertTrue(analyzer.analyze(listOf(source)).isEmpty())
    }

    @Test
    fun `ignores suppression examples inside comments and string fixtures`() {
        val source = SourceFile(
            "Fixture.kt",
            "// @Suppress(\"LongMethod\")\n" +
                "val fixture = \"\"\"@file:Suppress(\"TooManyFunctions\")\"\"\"",
            SourceKind.TEST,
        )

        assertTrue(analyzer.analyze(listOf(source)).isEmpty())
    }
}
