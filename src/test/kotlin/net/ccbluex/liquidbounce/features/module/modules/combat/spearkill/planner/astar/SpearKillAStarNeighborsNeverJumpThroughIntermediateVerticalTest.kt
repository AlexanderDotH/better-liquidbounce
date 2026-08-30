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

class SpearKillAStarNeighborsNeverJumpThroughIntermediateVerticalTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }


    @Test
    fun `SpearKill AStar neighbors never jump through intermediate vertical blocks`() {
        val neighbors = spearKillBidirectionalNeighbors(
            position = Vec3i.ZERO,
            allowDiagonal = false,
            isPassable = { true },
        )

        assertEquals(6, neighbors.size)
        assertTrue(neighbors.all { edge ->
            val offset = edge.node.subtract(Vec3i.ZERO)
            kotlin.math.abs(offset.x) + kotlin.math.abs(offset.y) + kotlin.math.abs(offset.z) == 1
        })
    }

    @Test
    fun `AStar approach bearings are twelve evenly spaced directions with preferred first`() {
        val preferred = Vec3(1.0, 0.0, 0.0)
        val bearings = spearKillAStarLungeDirections(preferred)

        assertEquals(SPEAR_KILL_A_STAR_APPROACH_BEARING_COUNT, bearings.size)
        assertEquals(12, bearings.size)
        assertVec3Equals(preferred, bearings.first(), 1e-9)
        assertEquals(bearings[1].x, bearings[2].x, 1e-9)
        assertEquals(bearings[1].z, -bearings[2].z, 1e-9)
        assertEquals(0.0, bearings.last().distanceTo(preferred.scale(-1.0)), 1e-9)
        assertTrue(bearings.all { kotlin.math.abs(it.length() - 1.0) < 1e-9 && it.y == 0.0 })
    }

    @Test
    fun `clear direct run-up bypasses the block search`() {
        var routeSearches = 0
        val route = resolveSpearKillAStarApproachRoute(
            origin = Vec3.ZERO,
            plannerGoal = Vec3(20.0, 0.0, 0.0),
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
            routeSearch = {
                routeSearches++
                null
            },
        )

        assertEquals(emptyList<Vec3>(), route)
        assertEquals(0, routeSearches)
    }

    @Test
    fun `swept segment validator follows the hitbox corridor instead of its full bounding rectangle`() {
        val playerBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3)
        val offCorridorObstacle = AABB(0.0, 0.0, 9.0, 1.0, 2.0, 10.0)
        val onCorridorObstacle = AABB(4.5, 0.0, 4.5, 5.5, 2.0, 5.5)
        val from = Vec3.ZERO
        val to = Vec3(10.0, 0.0, 10.0)

        val offCorridorValidator = createSpearKillAStarSegmentValidator(
            origin = from,
            playerBoundingBox = playerBox,
            hasHitboxRaycastCollision = { box, movement ->
                hasSpearKillHitboxRaycastCollision(box, movement, listOf(offCorridorObstacle))
            },
        )
        val onCorridorValidator = createSpearKillAStarSegmentValidator(
            origin = from,
            playerBoundingBox = playerBox,
            hasHitboxRaycastCollision = { box, movement ->
                hasSpearKillHitboxRaycastCollision(box, movement, listOf(onCorridorObstacle))
            },
        )

        assertTrue(offCorridorValidator.isClear(from, to))
        assertFalse(onCorridorValidator.isClear(from, to))
    }

    @Test
    fun `long diagonal validation performs one cached hitbox raycast`() {
        var raycasts = 0
        var castMovement: Vec3? = null
        val validator = createSpearKillAStarSegmentValidator(
            origin = Vec3.ZERO,
            playerBoundingBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3),
            hasHitboxRaycastCollision = { _, movement ->
                raycasts++
                castMovement = movement
                false
            },
        )

        assertTrue(validator.isClear(Vec3.ZERO, Vec3(100.0, 0.0, 100.0)))
        assertTrue(validator.isClear(Vec3.ZERO, Vec3(100.0, 0.0, 100.0)))
        assertEquals(1, raycasts)
        assertVec3Equals(Vec3(100.0, 0.0, 100.0), castMovement!!, 1e-9)
    }

    @Test
    fun `vanilla shape scope ignores client walk-through solidification`() {
        val previous = ShapeFlag.noShapeChange
        ShapeFlag.noShapeChange = false
        try {
            val seen = withVanillaSpearKillBlockShapes {
                assertTrue(ShapeFlag.noShapeChange)
                "ok"
            }
            assertEquals("ok", seen)
            assertFalse(ShapeFlag.noShapeChange)
        } finally {
            ShapeFlag.noShapeChange = previous
        }
    }

    @Test
    fun `direct Packet hitbox raycast rejects a terrain lip across the route`() {
        val origin = Vec3.ZERO
        val destination = Vec3(4.0, 0.0, 0.0)
        val terrainLip = AABB(1.0, 0.0, -0.3, 1.5, 0.4, 0.3)

        val validator = createSpearKillDirectPacketSegmentValidator(
            origin = origin,
            playerBoundingBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3),
            hasHitboxRaycastCollision = { box, movement ->
                hasSpearKillHitboxRaycastCollision(box, movement, listOf(terrainLip))
            },
        )

        assertFalse(validator.isClear(origin, destination))
    }

    @Test
    fun `server preflight rejects movement that would trigger moved wrongly`() {
        val requested = Vec3(17.32, 0.0, 0.0)

        assertTrue(
            isSpearKillServerPacketMovementAccepted(
                requestedMovement = requested,
                resolvedMovement = Vec3(17.1, 0.0, 0.0),
            ),
        )
        assertFalse(
            isSpearKillServerPacketMovementAccepted(
                requestedMovement = requested,
                resolvedMovement = Vec3(17.0, 0.0, 0.0),
            ),
        )
    }

    @Test
    fun `server packet validator rejects a terrain-clipped Elytra step`() {
        val origin = Vec3.ZERO
        val validator = createSpearKillServerPacketSegmentValidator(
            origin = origin,
            playerBoundingBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3),
            hasDestinationCollision = { false },
            resolveMovement = { _, movement -> movement.subtract(0.32, 0.0, 0.0) },
        )

        assertFalse(validator.isClear(origin, origin.add(17.32, 0.0, 0.0)))
    }
}
