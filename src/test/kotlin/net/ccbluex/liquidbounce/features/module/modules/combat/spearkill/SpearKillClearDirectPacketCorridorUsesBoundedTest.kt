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

class SpearKillClearDirectPacketCorridorUsesBoundedTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `clear direct Packet corridor uses bounded outbound and exact inverse return`() {
        val route = buildSpearKillDirectPacketRoute(
            origin = Vec3(4.0, 64.0, -2.0),
            direction = Vec3(1.0, 0.0, 0.0),
            distance = 25.0,
            maxSpeed = 10.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!
        val expectedOutbound = listOf(
            Vec3(10.0, 0.0, 0.0),
            Vec3(10.0, 0.0, 0.0),
            Vec3(5.0, 0.0, 0.0),
        )

        assertEquals(expectedOutbound, route.outboundMovements)
        assertEquals(
            expectedOutbound + expectedOutbound.asReversed().map { it.scale(-1.0) } + Vec3.ZERO,
            route.roundTripMovements,
        )
    }

    @Test
    fun `direct Packet preflight rejects a later blocked outbound edge`() {
        val origin = Vec3(4.0, 64.0, -2.0)
        val validatedEdges = mutableListOf<Pair<Vec3, Vec3>>()

        val route = buildSpearKillDirectPacketRoute(
            origin = origin,
            direction = Vec3(1.0, 0.0, 0.0),
            distance = 25.0,
            maxSpeed = 10.0,
            segmentValidator = SpearKillAStarSegmentValidator { from, to ->
                validatedEdges += from to to
                from.x < origin.x + 10.0
            },
        )

        assertNull(route)
        assertEquals(
            listOf(
                origin to origin.add(10.0, 0.0, 0.0),
                origin.add(10.0, 0.0, 0.0) to origin.add(20.0, 0.0, 0.0),
            ),
            validatedEdges,
        )
    }

    @Test
    fun `direct Packet preflight rejects an inverse-only collision`() {
        val origin = Vec3(4.0, 64.0, -2.0)

        assertNull(
            buildSpearKillDirectPacketRoute(
                origin = origin,
                direction = Vec3(1.0, 0.0, 0.0),
                distance = 25.0,
                maxSpeed = 10.0,
                segmentValidator = SpearKillAStarSegmentValidator { from, to -> to.x >= from.x },
            ),
        )
    }

    @Test
    fun `blocked direct Packet route produces a blocked start result`() {
        val route = buildSpearKillDirectPacketRoute(
            origin = Vec3.ZERO,
            direction = Vec3(1.0, 0.0, 0.0),
            distance = 10.0,
            maxSpeed = 10.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> false },
        )
        val startResult = if (route == null) {
            SpearKillAttackStartResult.BLOCKED
        } else {
            SpearKillAttackStartResult.STARTED
        }

        assertEquals(SpearKillAttackStartResult.BLOCKED, startResult)
    }

    @Test
    fun `AStar compacts a clear straight route to MaxSpeed while preserving the final lunge`() {
        val origin = Vec3(0.5, 64.0, 0.5)
        val route = (1..7).map { Vec3(it + 0.5, 64.0, 0.5) }
        val compacted = simplifySpearKillAStarWaypoints(
            origin = origin,
            waypoints = route,
            maxSpeed = 7.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )
        val terminalLungeEndpoint = Vec3(8.5, 64.0, 0.5)
        val movements = buildSpearKillAStarPacketMovements(
            origin = origin,
            outboundWaypoints = compacted + terminalLungeEndpoint,
            maxSpeed = 7.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!

        assertEquals(1, compacted.size)
        assertVec3Equals(Vec3(7.5, 64.0, 0.5), compacted.single(), 1e-9)
        assertVec3Equals(Vec3(7.0, 0.0, 0.0), movements[0], 1e-9)
        assertVec3Equals(Vec3(1.0, 0.0, 0.0), movements[1], 1e-9)
        assertVec3Equals(Vec3.ZERO, movements.last(), 1e-9)
    }

    @Test
    fun `AStar retains intermediate nodes when a long shortcut is blocked`() {
        val origin = Vec3(0.5, 64.0, 0.5)
        val route = (1..4).map { Vec3(it + 0.5, 64.0, 0.5) }
        val compacted = simplifySpearKillAStarWaypoints(
            origin = origin,
            waypoints = route,
            maxSpeed = 7.0,
            segmentValidator = SpearKillAStarSegmentValidator { from, to -> from.distanceTo(to) <= 1.0 },
        )

        assertEquals(route, compacted)
    }

    @Test
    fun `AStar packet route preserves outward order expands vertical travel and reverses exactly`() {
        val origin = Vec3(0.25, 64.0, 0.75)
        val firstWaypoint = Vec3(1.5, 64.0, 0.5)
        val secondWaypoint = Vec3(1.5, 69.0, 0.5)
        val movements = buildSpearKillAStarPacketMovements(
            origin = origin,
            outboundWaypoints = listOf(firstWaypoint, secondWaypoint),
            maxSpeed = 2.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!
        val deltas = movements.dropLast(1)
        val half = deltas.size / 2
        var virtualPosition = origin
        val outboundPositions = buildList {
            for (movement in deltas.take(half)) {
                virtualPosition = virtualPosition.add(movement)
                add(virtualPosition)
            }
        }

        assertVec3Equals(Vec3.ZERO, movements.last(), 1e-9)
        assertTrue(deltas.all { it.length() <= 2.0 })
        assertVec3Equals(firstWaypoint, outboundPositions.first(), 1e-9)
        assertVec3Equals(secondWaypoint, outboundPositions.last(), 1e-9)
        assertTrue(deltas.take(half).count { it.y != 0.0 } > 1)
        for (index in 0 until half) {
            assertVec3Equals(deltas[index].scale(-1.0), deltas[deltas.lastIndex - index], 1e-9)
        }
        assertVec3Equals(Vec3.ZERO, deltas.fold(Vec3.ZERO, Vec3::add), 1e-9)
    }

    @Test
    fun `AStar packet route bounds every vertical delta below fall damage distance`() {
        val origin = Vec3(0.5, 72.0, 0.5)
        val destination = Vec3(0.5, 62.0, 0.5)
        val route = buildSpearKillAStarPacketRoute(
            origin = origin,
            outboundWaypoints = listOf(destination),
            maxSpeed = 7.4,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
            maxVerticalStep = 2.95,
        )!!

        assertTrue(route.roundTripMovements.dropLast(1).all { abs(it.y) <= 2.95 })
        assertVec3Equals(destination, route.outboundMovements.fold(origin, Vec3::add), 1e-9)
        assertVec3Equals(Vec3.ZERO, route.roundTripMovements.fold(Vec3.ZERO, Vec3::add), 1e-9)
    }

    @Test
    fun `AStar packet route rejects a server-blocked inverse return edge`() {
        val origin = Vec3(0.25, 64.0, 0.75)
        val waypoint = Vec3(1.5, 64.0, 0.75)

        assertNull(buildSpearKillAStarPacketRoute(
            origin = origin,
            outboundWaypoints = listOf(waypoint),
            maxSpeed = 2.0,
            segmentValidator = SpearKillAStarSegmentValidator { from, to -> to.x >= from.x },
        ))
    }

    @Test
    fun `AStar packet route rejects empty and collision-blocked routes`() {
        val origin = Vec3(0.25, 64.0, 0.75)
        val waypoint = Vec3(1.5, 64.0, 0.5)

        assertEquals(
            null,
            buildSpearKillAStarPacketMovements(
                origin = origin,
                outboundWaypoints = emptyList(),
                maxSpeed = 2.0,
                segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
            ),
        )
        assertEquals(
            null,
            buildSpearKillAStarPacketMovements(
                origin = origin,
                outboundWaypoints = listOf(waypoint),
                maxSpeed = 2.0,
                segmentValidator = SpearKillAStarSegmentValidator { _, _ -> false },
            ),
        )
    }
}
