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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleSpearKill

import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.config.gson.fileGson
import net.ccbluex.liquidbounce.config.gson.interopGson
import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.list.ChoiceListValue
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.common.ShapeFlag
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.ccbluex.liquidbounce.test.assertVec3Equals
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.block.WeightedEdge
import net.ccbluex.liquidbounce.utils.block.aStarShortestPath
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.entity.Relative
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.abs

class SpearKillRaisesSpearPoseWhileItOwnsAttackTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }


    @Test
    fun `SpearKill raises the spear pose only while it owns the attack`() {
        assertFalse(shouldRaiseSpearKillAnimation(
            spearKillRunning = true,
            holdingSpear = true,
            attackPathActive = false,
            attackRequested = false,
            isUsingSpear = false,
        ))
        assertTrue(shouldRaiseSpearKillAnimation(
            spearKillRunning = true,
            holdingSpear = true,
            attackPathActive = true,
            attackRequested = false,
            isUsingSpear = false,
        ))
        assertTrue(shouldRaiseSpearKillAnimation(
            spearKillRunning = true,
            holdingSpear = true,
            attackPathActive = false,
            attackRequested = true,
            isUsingSpear = false,
        ))
        assertTrue(shouldRaiseSpearKillAnimation(
            spearKillRunning = true,
            holdingSpear = true,
            attackPathActive = false,
            attackRequested = false,
            isUsingSpear = true,
        ))
        assertFalse(shouldRaiseSpearKillAnimation(
            spearKillRunning = false,
            holdingSpear = true,
            attackPathActive = true,
            attackRequested = true,
            isUsingSpear = true,
        ))
    }

    @Test
    fun `manual hold and KillAura prehold reserve spear use from FastUse refresh`() {
        assertTrue(shouldControlSpearKillUse(
            spearKillRunning = true,
            attackPathActive = false,
            routePreparationActive = false,
            physicalUseRequested = true,
            automaticUseRequested = false,
        ))
        assertTrue(shouldControlSpearKillUse(
            spearKillRunning = true,
            attackPathActive = false,
            routePreparationActive = false,
            physicalUseRequested = false,
            automaticUseRequested = true,
        ))
        assertFalse(shouldControlSpearKillUse(
            spearKillRunning = false,
            attackPathActive = false,
            routePreparationActive = false,
            physicalUseRequested = true,
            automaticUseRequested = true,
        ))
    }

    @Test
    fun `SpearKill animation is a client-side charged pose snap`() {
        assertEquals(3f, spearKillAnimationTicks(shouldRaise = true, delayTicks = 3, originalTicks = 0f))
        assertEquals(3f, spearKillAnimationTicks(shouldRaise = true, delayTicks = 3, originalTicks = 8f))
        assertEquals(1f, spearKillAnimationTicks(shouldRaise = false, delayTicks = 3, originalTicks = 1f))
        assertTrue(shouldAnimateSpearKillUseItem(shouldRaise = true, isUsingItem = false))
        assertEquals(
            InteractionHand.MAIN_HAND,
            spearKillRaisedHand(
                shouldRaise = true,
                mainHandIsSpear = true,
                offHandIsSpear = false,
                isUsingItem = false,
                usedHand = InteractionHand.OFF_HAND,
            ),
        )
    }

    @Test
    fun `AStar route planner uses the configured diagonal option with bounded search defaults`() {
        val planner = SpearKillAStarRoutePlanner(allowDiagonal = false, maxCost = 250)

        assertFalse(planner.allowDiagonal)
        assertEquals(250, planner.maxCost)
        assertEquals(500, planner.maxIterations)
        assertEquals(1.0, planner.stopRange)
    }

    @Test
    fun `AStar planner accepts an empty route when already in the approach block`() {
        val planner = SpearKillAStarRoutePlanner(allowDiagonal = false, maxCost = 250)

        assertEquals(
            emptyList<Vec3>(),
            planner.plan(
                origin = Vec3(5.1, 64.0, 8.1),
                destination = Vec3(5.9, 64.0, 8.9),
            ),
        )
    }

    @Test
    fun `bidirectional AStar meets through a U-bend under a tight shared budget`() {
        // Cheap dead-end fan-out at S forces unidirectional search to exhaust the budget before the
        // corridor; bidirectional still meets from the goal side within the same shared budget.
        val deadEnds = (1..40).map { "X$it" }
        val corridor = listOf("S", "A", "B", "C", "D", "E", "F", "G")
        val edges = mutableMapOf<String, MutableList<WeightedEdge<String>>>()
        fun link(from: String, to: String, cost: Double) {
            edges.getOrPut(from) { mutableListOf() }.add(WeightedEdge(to, cost))
            edges.getOrPut(to) { mutableListOf() }.add(WeightedEdge(from, cost))
        }
        for (index in 0 until corridor.lastIndex) {
            link(corridor[index], corridor[index + 1], 1.0)
        }
        deadEnds.forEach { link("S", it, 0.5) }

        val zeroHeuristic = java.util.function.ToDoubleFunction<String> { 0.0 }
        val neighbors = { node: String -> edges[node].orEmpty() }
        val budget = 25

        assertNull(
            aStarShortestPath(
                start = "S",
                isGoal = { it == "G" },
                neighbors = neighbors,
                heuristic = zeroHeuristic,
                maxIterations = budget,
                maxCost = 100.0,
            ),
        )

        val path = bidirectionalAStarShortestPath(
            start = "S",
            end = "G",
            neighbors = neighbors,
            forwardHeuristic = zeroHeuristic,
            backwardHeuristic = zeroHeuristic,
            maxIterations = budget,
            maxCost = 100.0,
        )

        assertNotNull(path)
        assertEquals("S", path!!.nodes.first())
        assertEquals("G", path.nodes.last())
        assertTrue(path.nodes.none { it in deadEnds })
    }

    @Test
    fun `bidirectional AStar stops once the best meeting path is proven`() {
        var neighborCalls = 0
        val path = bidirectionalAStarShortestPath(
            start = 0,
            end = 10,
            neighbors = { node ->
                neighborCalls++
                buildList {
                    if (node > -1_000) add(WeightedEdge(node - 1, 1.0))
                    if (node < 1_000) add(WeightedEdge(node + 1, 1.0))
                }
            },
            forwardHeuristic = java.util.function.ToDoubleFunction { node -> kotlin.math.abs(10 - node).toDouble() },
            backwardHeuristic = java.util.function.ToDoubleFunction { node -> kotlin.math.abs(node).toDouble() },
            maxIterations = 500,
            maxCost = 100.0,
        )

        assertNotNull(path)
        assertEquals(10.0, path!!.totalCost, 1e-9)
        assertTrue(neighborCalls < 30, "Search kept expanding after proving the best path: $neighborCalls")
    }
}
