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

class PackagePathAnalyzerTest {

    private val analyzer = PackagePathAnalyzer(listOf(PackageRoot("src/main/kotlin"), PackageRoot("src/main/java")))

    @Test
    fun `accepts escaped Kotlin package segment at matching path`() {
        val source = source(
            "src/main/kotlin/net/example/modules/fun/Feature.kt",
            "package net.example.modules.`fun`\n\nclass Feature",
        )

        assertTrue(analyzer.analyze(listOf(source)).isEmpty())
    }

    @Test
    fun `reports mismatched Java package at declaration line`() {
        val source = source(
            "src/main/java/net/example/render/Renderer.java",
            "/* header */\npackage net.example.wrong;\nclass Renderer {}",
        )

        val finding = analyzer.analyze(listOf(source)).single()

        assertEquals("LB-HYG-003", finding.ruleId)
        assertEquals(2, finding.line)
        assertEquals("net.example.render", finding.expected)
        assertEquals("net.example.wrong", finding.actual)
    }

    private fun source(path: String, content: String) = SourceFile(path, content, SourceKind.PRODUCTION)
}
