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

class RepositoryStructureAnalyzerTest {

    private val combatRoot = "src/main/kotlin/net/example/features/module/modules/combat"
    private val analyzer = RepositoryStructureAnalyzer(
        categoryRoots = setOf(combatRoot),
        strategyDirectories = setOf("modes", "exploits", "triggers"),
        minimumClusterFiles = 5,
        minimumPrefixTokens = 2,
    )

    @Test
    fun `category root permits only module facades`() {
        val accepted = source("$combatRoot/ModuleMaceKill.kt")
        val rejected = source("$combatRoot/MaceKillRoutePlanner.kt")

        val finding = analyzer.analyze(listOf(accepted, rejected)).single()

        assertEquals("LB-HYG-004", finding.ruleId)
        assertEquals(rejected.path, finding.path)
    }

    @Test
    fun `MaceKill and SpearKill sibling clusters require feature packages`() {
        val roles = listOf("Planner", "Runtime", "Session", "Policy", "Renderer")
        val files = roles.map { source("$combatRoot/MaceKill$it.kt") } +
            roles.map { source("$combatRoot/SpearKill$it.kt") }

        val findings = analyzer.analyze(files).filter { it.ruleId == "LB-HYG-005" }

        assertEquals(listOf("macekill", "spearkill"), findings.map { it.subject })
        assertTrue(findings.all { it.measuredValue == 5 })
    }

    @Test
    fun `feature package and strategy collection remove false prefix clusters`() {
        val packaged = listOf("Planner", "Runtime", "Session", "Policy", "Renderer")
            .map { source("$combatRoot/macekill/MaceKill$it.kt") }
        val strategies = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo")
            .map { source("$combatRoot/fly/modes/FlyMode$it.kt") }

        assertTrue(analyzer.analyze(packaged + strategies).none { it.ruleId == "LB-HYG-005" })
    }

    @Test
    fun `technical mixin prefix plus the owning directory is not a semantic cluster`() {
        val files = listOf("Movement", "Collision", "Pose", "Tick", "Render")
            .map { source("src/main/java/net/example/injection/mixins/entity/MixinEntity$it.java") }

        assertTrue(analyzer.analyze(files).none { it.ruleId == "LB-HYG-005" })
    }

    private fun source(path: String) = SourceFile(path, "package sample", SourceKind.PRODUCTION)
}
