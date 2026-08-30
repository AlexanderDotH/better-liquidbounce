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

package net.ccbluex.liquidbounce.buildsrc.quality.architecture

import net.ccbluex.liquidbounce.buildsrc.quality.model.Finding
import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceFile
import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceKind
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.QualityGateResult
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetAssessment
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetStatus
import net.ccbluex.liquidbounce.buildsrc.quality.report.QualityReportRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArchitectureCutFeedbackTest {

    private val analyzer = ArchitectureAnalyzer(
        ArchitecturePolicy(
            internalPackagePrefix = "net.example",
            analyzedPathPrefixes = setOf("src/main/kotlin"),
            components = listOf(
                ArchitectureComponent(
                    id = "bootstrap",
                    packagePrefixes = setOf("net.example.injection"),
                    allowedDependencies = setOf("features", "foundation"),
                ),
                ArchitectureComponent(
                    id = "integration",
                    packagePrefixes = setOf("net.example.integration"),
                    allowedDependencies = setOf("features", "foundation"),
                ),
                ArchitectureComponent(
                    id = "features",
                    packagePrefixes = setOf("net.example.features"),
                    allowedDependencies = setOf("foundation"),
                ),
                ArchitectureComponent(
                    id = "foundation",
                    packagePrefixes = setOf("net.example.utils"),
                ),
            ),
            restrictedEdges = emptyList(),
        ),
    )

    @Test
    fun `planned cycle cuts keep stable actionable feedback in every report format`() {
        val forward = plannedFindings(plannedSources())
        val reversed = plannedFindings(plannedSources().reversed())

        assertEquals(forward, reversed)
        assertFeedback(forward, COMBAT_EDGE, "LB-ARCH-001", "net.example.features.combat.contract")
        assertFeedback(forward, BLOCK_PLACER_CYCLE, "LB-ARCH-002", "net.example.features.block.contract")
        assertFeedback(forward, WORLD_EDGE, "LB-ARCH-001", "net.example.utils.world.contract")

        val firstReports = QualityReportRenderer.render(blockingResult(forward))
        val secondReports = QualityReportRenderer.render(blockingResult(reversed.reversed()))

        assertEquals(firstReports, secondReports)
        CONTRACT_PACKAGES.forEach { targetPackage ->
            assertTrue(targetPackage in firstReports.json)
            assertTrue(targetPackage in firstReports.markdown)
            assertTrue(targetPackage in firstReports.sarif)
        }
    }

    private fun plannedFindings(files: List<SourceFile>): List<Finding> {
        val findings = analyzer.analyze(files).associateBy(Finding::fingerprint)
        return listOf(COMBAT_EDGE, BLOCK_PLACER_CYCLE, WORLD_EDGE).map(findings::getValue)
    }

    private fun assertFeedback(findings: List<Finding>, fingerprint: String, ruleId: String, targetPackage: String) {
        val finding = findings.single { it.fingerprint == fingerprint }
        assertEquals(ruleId, finding.ruleId)
        assertTrue(targetPackage in finding.recommendation)
        assertEquals(".github/CODING_STANDARDS.md#${ruleId.lowercase()}", finding.documentation)
    }

    private fun blockingResult(findings: List<Finding>) = QualityGateResult(
        assessments = findings.map { finding ->
            RatchetAssessment(finding, RatchetStatus.BLOCKING, "new debt", "LB-RATCHET-001")
        },
    )

    private fun plannedSources() = listOf(
        source(
            "features/combat/runtime/CombatManager.kt",
            "net.example.features.combat.runtime",
            "net.example.integration.rest.PlayerData",
        ),
        source(
            "integration/rest/PlayerData.kt",
            "net.example.integration.rest",
            "net.example.features.combat.runtime.CombatManager",
        ),
        source(
            "features/block/placer/BlockPlacer.kt",
            "net.example.features.block.placer",
            "net.example.features.render.ModuleDebug",
        ),
        source(
            "features/render/ModuleDebug.kt",
            "net.example.features.render",
            "net.example.features.block.placer.BlockPlacer",
        ),
        source(
            "utils/world/WorldExtensions.kt",
            "net.example.utils.world",
            "net.example.injection.MixinLevelInvoker",
        ),
        source(
            "injection/MixinLevelInvoker.kt",
            "net.example.injection",
            "net.example.utils.world.WorldExtensions",
        ),
    )

    private fun source(relativePath: String, packageName: String, importedName: String) = SourceFile(
        path = "src/main/kotlin/net/example/$relativePath",
        content = "package $packageName\nimport $importedName\nclass Sample",
        kind = SourceKind.PRODUCTION,
    )

    private companion object {
        const val COMBAT_EDGE =
            "LB-ARCH-001|net.example.features.combat.runtime|net.example.integration.rest.PlayerData"
        const val BLOCK_PLACER_CYCLE = "LB-ARCH-002|net.example.features.block.placer"
        const val WORLD_EDGE = "LB-ARCH-001|net.example.utils.world|net.example.injection.MixinLevelInvoker"

        val CONTRACT_PACKAGES = listOf(
            "net.example.features.combat.contract",
            "net.example.features.block.contract",
            "net.example.utils.world.contract",
        )
    }
}
