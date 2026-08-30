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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

class SpearKillAStarPackageBoundaryTest {

    @Test
    fun `AStar planner owns its route primitives without combat facade dependencies`() {
        val sourceRoot = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/spearkill/planner/astar",
        )
        val combinedSource = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { it.isRegularFile() && it.extension == "kt" }
                .map { Files.readString(it) }
                .toList()
                .joinToString("\n")
        }

        val combatPackage = "import net.ccbluex.liquidbounce.features.module.modules.combat."
        val spearKillPackage = "${combatPackage}spearkill."
        val ownPackage = "${spearKillPackage}planner.astar."
        combinedSource.lineSequence()
            .filter { it.startsWith("import ") }
            .forEach { importLine ->
                val importsCombatFacade = importLine == "${combatPackage}*" ||
                    importLine.startsWith("${combatPackage}Module")
                val importsCombatSibling = importLine.startsWith("${combatPackage}fightbot.") ||
                    importLine.startsWith("${combatPackage}remotekill.")
                val importsSpearKillParentOrSibling = importLine.startsWith(spearKillPackage) &&
                    !importLine.startsWith(ownPackage)
                assertFalse(
                    importsCombatFacade || importsCombatSibling || importsSpearKillParentOrSibling,
                    importLine,
                )
            }

        listOf(
            "internal class BidirectionalAStarSearch",
            "internal fun <T> bidirectionalAStarShortestPath",
            "internal fun hasValidSpearKillPacketStepBounds",
            "internal fun buildSpearKillFixedStepMovements",
            "internal fun spearKillPacketTravelTicks",
        ).forEach { ownedDeclaration ->
            assertTrue(ownedDeclaration in combinedSource, ownedDeclaration)
        }
    }
}
