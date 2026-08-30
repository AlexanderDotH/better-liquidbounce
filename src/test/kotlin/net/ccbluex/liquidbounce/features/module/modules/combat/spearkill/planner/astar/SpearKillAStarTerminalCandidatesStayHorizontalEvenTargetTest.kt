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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar

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

class SpearKillAStarTerminalCandidatesStayHorizontalEvenTargetTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }


    @Test
    fun `AStar terminal candidates stay horizontal even for a target directly above`() {
        val approaches = createSpearKillAStarAttackApproachCandidates(
            targetBox = AABB(-0.5, 70.0, -0.5, 0.5, 72.0, 0.5),
            targetEyePosition = Vec3(0.0, 71.5, 0.0),
            playerEyeOffset = Vec3(0.0, 1.62, 0.0),
            preferredDirection = Vec3(0.0, 1.0, 0.0),
        )

        assertEquals(SPEAR_KILL_A_STAR_APPROACH_BEARING_COUNT, approaches.size)
        assertTrue(approaches.all { approach ->
            approach.terminalWaypoint.subtract(approach.plannerGoal).y == 0.0
        })
    }

    @Test
    fun `AStar approach candidates preserve MaxSpeed terminal length on every bearing`() {
        val maxSpeed = 10.0
        val approaches = createSpearKillAStarAttackApproachCandidates(
            targetBox = AABB(10.0, 64.0, 0.0, 11.0, 66.0, 1.0),
            targetEyePosition = Vec3(10.5, 65.5, 0.5),
            playerEyeOffset = Vec3(0.0, 1.62, 0.0),
            preferredDirection = Vec3(1.0, 0.0, 0.0),
            terminalLungeDistance = maxSpeed,
        )

        assertEquals(SPEAR_KILL_A_STAR_APPROACH_BEARING_COUNT, approaches.size)
        assertTrue(approaches.all { approach ->
            kotlin.math.abs(
                approach.terminalWaypoint.subtract(approach.plannerGoal).length() - maxSpeed,
            ) < 1e-9
        })
    }

    @Test
    fun `AStar skips a blocked primary lunge and keeps a lateral alternative`() {
        val approaches = createSpearKillAStarAttackApproachCandidates(
            targetBox = AABB(10.0, 64.0, 0.0, 11.0, 66.0, 1.0),
            targetEyePosition = Vec3(10.5, 65.5, 0.5),
            playerEyeOffset = Vec3(0.0, 1.62, 0.0),
            preferredDirection = Vec3(1.0, 0.0, 0.0),
        )

        val usable = filterSpearKillAStarApproachesByTerminalClearance(
            approaches = approaches,
            segmentValidator = SpearKillAStarSegmentValidator { from, to ->
                to.subtract(from).x == 0.0
            },
        )

        assertTrue(usable.isNotEmpty())
        assertTrue(usable.all { approach -> approach.terminalWaypoint.subtract(approach.plannerGoal).x == 0.0 })
    }

    @Test
    fun `Elytra flight only starts from a valid airborne state`() {
        assertTrue(canStartSpearKillElytraFlight(
            isFallFlying = false,
            hasFlyingAbility = false,
            isPassenger = false,
            isOnClimbable = false,
            isInWater = false,
            hasLevitation = false,
            isOnGround = false,
            hasUsableElytra = true,
        ))
        assertTrue(canStartSpearKillElytraFlight(
            isFallFlying = true,
            hasFlyingAbility = false,
            isPassenger = false,
            isOnClimbable = false,
            isInWater = false,
            hasLevitation = false,
            isOnGround = false,
            hasUsableElytra = true,
        ))
        assertFalse(canStartSpearKillElytraFlight(
            isFallFlying = false,
            hasFlyingAbility = false,
            isPassenger = false,
            isOnClimbable = false,
            isInWater = false,
            hasLevitation = false,
            isOnGround = true,
            hasUsableElytra = true,
        ))
        assertFalse(canStartSpearKillElytraFlight(
            isFallFlying = false,
            hasFlyingAbility = false,
            isPassenger = false,
            isOnClimbable = false,
            isInWater = false,
            hasLevitation = false,
            isOnGround = false,
            hasUsableElytra = false,
        ))
    }

    @Test
    fun `long full XYZ terminal straight is safely split and reverses exactly`() {
        val origin = Vec3(0.0, 64.0, 0.0)
        val terminalWaypoint = Vec3(12.0, 73.0, 6.0)
        val movements = buildSpearKillAStarPacketMovements(
            origin = origin,
            outboundWaypoints = listOf(terminalWaypoint),
            maxSpeed = 7.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!
        val outbound = movements.dropLast(1).take(movements.dropLast(1).size / 2)
        val inbound = movements.dropLast(1).drop(outbound.size)

        assertTrue(outbound.size > 1)
        assertTrue(outbound.all { it.length() <= 7.0 })
        assertTrue(outbound.all { it.x > 0.0 && it.y > 0.0 && it.z > 0.0 })
        assertVec3Equals(terminalWaypoint.subtract(origin), outbound.fold(Vec3.ZERO, Vec3::add), 1e-9)
        assertEquals(outbound.asReversed().map { it.scale(-1.0) }, inbound)
        assertVec3Equals(Vec3.ZERO, movements.fold(Vec3.ZERO, Vec3::add), 1e-9)
    }

    @Test
    fun `AStar long edge uses full fixed steps followed by its remainder`() {
        val route = buildSpearKillAStarPacketRoute(
            origin = Vec3.ZERO,
            outboundWaypoints = listOf(Vec3(2.0, 0.0, 0.0), Vec3(12.0, 0.0, 0.0)),
            maxSpeed = 3.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!
        val expectedOutbound = listOf(
            Vec3(2.0, 0.0, 0.0),
            Vec3(3.0, 0.0, 0.0),
            Vec3(3.0, 0.0, 0.0),
            Vec3(3.0, 0.0, 0.0),
            Vec3(1.0, 0.0, 0.0),
        )

        assertEquals(expectedOutbound, route.outboundMovements)
        assertEquals(
            expectedOutbound + expectedOutbound.asReversed().map { it.scale(-1.0) } + Vec3.ZERO,
            route.roundTripMovements,
        )
    }

    @Test
    fun `packet route validates outbound and exact-inverse return corridors`() {
        var validationCalls = 0
        val route = buildSpearKillAStarPacketRoute(
            origin = Vec3.ZERO,
            outboundWaypoints = listOf(Vec3(2.0, 0.0, 0.0), Vec3(5.0, 0.0, 0.0)),
            maxSpeed = 3.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ ->
                validationCalls++
                true
            },
        )

        assertNotNull(route)
        // Two outbound packet edges plus their exact inverse returns. Reverse is not free under
        // server-faithful collision (sand lips / stairs can clip one direction only).
        assertEquals(4, validationCalls)
    }

    @Test
    fun `Elytra AStar long edge uses two full safe steps followed by its remainder`() {
        val route = buildSpearKillAStarPacketRoute(
            origin = Vec3.ZERO,
            outboundWaypoints = listOf(Vec3(40.0, 0.0, 0.0)),
            maxSpeed = 17.32,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!
        val outbound = route.outboundMovements

        assertEquals(3, outbound.size)
        assertEquals(17.32, outbound[0].length(), 1e-9)
        assertEquals(17.32, outbound[1].length(), 1e-9)
        assertEquals(5.36, outbound[2].length(), 1e-9)
        assertTrue(outbound.all { it.length() <= 17.32 })
        assertEquals(
            outbound.asReversed().map { it.scale(-1.0) },
            route.roundTripMovements.drop(outbound.size).dropLast(1),
        )
    }
}
