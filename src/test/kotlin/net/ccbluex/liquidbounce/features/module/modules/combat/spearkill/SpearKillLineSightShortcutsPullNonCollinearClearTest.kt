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

class SpearKillLineSightShortcutsPullNonCollinearClearTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }


    @Test
    fun `line of sight shortcuts pull non-collinear clear corridors`() {
        val origin = Vec3(0.0, 64.0, 0.0)
        val waypoints = listOf(
            Vec3(1.0, 64.0, 0.0),
            Vec3(2.0, 64.0, 1.0),
            Vec3(3.0, 64.0, 1.0),
            Vec3(4.0, 64.0, 0.0),
        )
        val alwaysClear = SpearKillAStarSegmentValidator { _, _ -> true }
        val collinear = simplifySpearKillAStarWaypoints(origin, waypoints, maxSpeed = 2.0, alwaysClear)
        val los = simplifySpearKillAStarWaypointsWithLineOfSight(origin, waypoints, alwaysClear)

        assertTrue(los.size < collinear.size)
        // LOS may jump farther than StepLimit; packet expansion splits afterward.
        assertEquals(listOf(Vec3(4.0, 64.0, 0.0)), los)

        val blockedFar = SpearKillAStarSegmentValidator { from, to ->
            from.distanceTo(to) <= 1.5
        }
        val blockedLos = simplifySpearKillAStarWaypointsWithLineOfSight(origin, waypoints, blockedFar)
        assertEquals(waypoints, blockedLos)
    }

    @Test
    fun `AStar attack approach creates a long straight run-up with valid spear stand-off`() {
        val hitPoint = Vec3(10.0, 65.5, 0.0)
        val eyeOffset = Vec3(0.0, 1.5, 0.0)
        val approach = createSpearKillAStarAttackApproach(
            targetHitPoint = hitPoint,
            playerEyeOffset = eyeOffset,
            lookDirection = Vec3(1.0, 0.0, 0.0),
        )!!

        assertVec3Equals(Vec3(0.75, 64.0, 0.0), approach.plannerGoal, 1e-9)
        assertVec3Equals(Vec3(7.75, 64.0, 0.0), approach.terminalWaypoint, 1e-9)
        assertEquals(7.0, approach.plannerGoal.distanceTo(approach.terminalWaypoint), 1e-9)
        assertEquals(2.25, approach.terminalWaypoint.add(eyeOffset).distanceTo(hitPoint), 1e-9)
    }

    @Test
    fun `AStar attack approach projects its terminal straight onto the horizontal plane`() {
        val hitPoint = Vec3(10.0, 72.0, 5.0)
        val eyeOffset = Vec3(0.0, 1.62, 0.0)
        val direction = Vec3(6.0, 8.0, 3.0)
        val horizontal = Vec3(direction.x, 0.0, direction.z).normalize()

        val approach = createSpearKillAStarAttackApproach(hitPoint, eyeOffset, direction)!!

        assertVec3Equals(hitPoint.subtract(horizontal.scale(9.25)).subtract(eyeOffset), approach.plannerGoal, 1e-9)
        assertVec3Equals(
            hitPoint.subtract(horizontal.scale(2.25)).subtract(eyeOffset),
            approach.terminalWaypoint,
            1e-9,
        )
        val terminalStraight = approach.terminalWaypoint.subtract(approach.plannerGoal)
        assertEquals(7.0, terminalStraight.length(), 1e-9)
        assertTrue(terminalStraight.x > 0.0)
        assertEquals(0.0, terminalStraight.y, 1e-9)
        assertTrue(terminalStraight.z > 0.0)
    }

    @Test
    fun `AStar terminal lunge keeps a reliable strike gap and one full default-speed packet`() {
        val hitPoint = Vec3(10.0, 65.5, 0.0)
        val eyeOffset = Vec3(0.0, 1.5, 0.0)
        val approach = createSpearKillAStarAttackApproach(
            targetHitPoint = hitPoint,
            playerEyeOffset = eyeOffset,
            lookDirection = Vec3(1.0, 0.0, 0.0),
        )!!
        val outbound = buildSpearKillAStarOutboundMovements(
            origin = approach.plannerGoal,
            waypoints = listOf(approach.terminalWaypoint),
            maxSpeed = resolveSpearKillMovementTransport(7.0, 17.32, elytraActive = false).maxSpeed,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!

        assertEquals(2.25, approach.terminalWaypoint.add(eyeOffset).distanceTo(hitPoint), 1e-9)
        assertTrue(approach.terminalWaypoint.add(eyeOffset).distanceTo(hitPoint) > 2.0)
        assertTrue(approach.terminalWaypoint.add(eyeOffset).distanceTo(hitPoint) <= 4.5)
        assertEquals(1, outbound.size)
        assertVec3Equals(Vec3(7.0, 0.0, 0.0), outbound.single(), 1e-9)
    }

    @Test
    fun `AStar terminal lunge matches the configured step size while preserving the strike gap`() {
        val hitPoint = Vec3(10.0, 65.5, 0.0)
        val eyeOffset = Vec3(0.0, 1.5, 0.0)
        val approach = createSpearKillAStarAttackApproach(
            targetHitPoint = hitPoint,
            playerEyeOffset = eyeOffset,
            lookDirection = Vec3(1.0, 0.0, 0.0),
            terminalLungeDistance = 3.0,
        )!!
        val outbound = buildSpearKillAStarOutboundMovements(
            origin = approach.plannerGoal,
            waypoints = listOf(approach.terminalWaypoint),
            maxSpeed = 3.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!

        assertEquals(2.25, approach.terminalWaypoint.add(eyeOffset).distanceTo(hitPoint), 1e-9)
        assertEquals(1, outbound.size)
        assertEquals(3.0, outbound.single().length(), 1e-9)
        assertVec3Equals(Vec3(3.0, 0.0, 0.0), outbound.single(), 1e-9)
    }

    @Test
    fun `close AStar target routes backward before one full StepLimit terminal hit`() {
        val origin = Vec3(5.0, 64.0, 0.0)
        val hitPoint = Vec3(10.0, 65.5, 0.0)
        val eyeOffset = Vec3(0.0, 1.5, 0.0)
        val approach = createSpearKillAStarAttackApproach(
            targetHitPoint = hitPoint,
            playerEyeOffset = eyeOffset,
            lookDirection = Vec3(1.0, 0.0, 0.0),
            terminalLungeDistance = 7.0,
        )!!
        val route = buildSpearKillAStarPacketRoute(
            origin = origin,
            outboundWaypoints = listOf(approach.plannerGoal, approach.terminalWaypoint),
            maxSpeed = 7.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!

        assertVec3Equals(Vec3(0.75, 64.0, 0.0), approach.plannerGoal, 1e-9)
        assertVec3Equals(Vec3(7.75, 64.0, 0.0), approach.terminalWaypoint, 1e-9)
        assertTrue(route.outboundMovements.all { it.length() <= 7.0 })
        assertVec3Equals(Vec3(7.0, 0.0, 0.0), route.outboundMovements.last(), 1e-9)
        assertVec3Equals(
            approach.terminalWaypoint.subtract(origin),
            route.outboundMovements.fold(Vec3.ZERO, Vec3::add),
            1e-9,
        )
        assertVec3Equals(Vec3.ZERO, route.roundTripMovements.fold(Vec3.ZERO, Vec3::add), 1e-9)
    }

    @Test
    fun `AStar accepts a terminal lunge split into StepLimit packets plus remainder`() {
        val approach = SpearKillAStarAttackApproach(
            plannerGoal = Vec3(0.0, 64.0, 0.0),
            terminalWaypoint = Vec3(7.0, 64.0, 0.0),
        )

        assertTrue(isSpearKillAStarTerminalStepValid(
            outboundMovements = listOf(Vec3(-5.0, 0.0, 0.0), Vec3(7.0, 0.0, 0.0)),
            approach = approach,
            stepLimit = 7.0,
        ))
        assertTrue(isSpearKillAStarTerminalStepValid(
            outboundMovements = listOf(Vec3(-5.0, 0.0, 0.0), Vec3(4.0, 0.0, 0.0), Vec3(3.0, 0.0, 0.0)),
            approach = approach,
            stepLimit = 4.0,
        ))
        assertTrue(isSpearKillAStarTerminalStepValid(
            outboundMovements = listOf(Vec3(3.0, 0.0, 0.0), Vec3(3.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0)),
            approach = approach,
            stepLimit = 3.0,
        ))
        assertFalse(isSpearKillAStarTerminalStepValid(
            outboundMovements = listOf(Vec3(-5.0, 0.0, 0.0), Vec3(0.0, 0.0, 7.0)),
            approach = approach,
            stepLimit = 7.0,
        ))
        assertFalse(isSpearKillAStarTerminalStepValid(
            outboundMovements = listOf(Vec3(8.0, 0.0, 0.0)),
            approach = approach,
            stepLimit = 7.0,
        ))
    }
}
