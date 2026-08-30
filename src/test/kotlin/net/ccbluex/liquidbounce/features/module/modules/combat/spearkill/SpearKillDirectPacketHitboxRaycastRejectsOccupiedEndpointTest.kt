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

class SpearKillDirectPacketHitboxRaycastRejectsOccupiedEndpointTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }


    @Test
    fun `direct Packet hitbox raycast rejects an occupied endpoint and wall`() {
        val origin = Vec3.ZERO
        val destination = Vec3(4.0, 0.0, 0.0)
        val playerBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3)
        val occupiedDestination = createSpearKillDirectPacketSegmentValidator(
            origin = origin,
            playerBoundingBox = playerBox,
            hasHitboxRaycastCollision = { box, movement ->
                hasSpearKillHitboxRaycastCollision(
                    box,
                    movement,
                    listOf(AABB(3.8, 0.0, -0.3, 4.5, 1.8, 0.3)),
                )
            },
        )
        val clippedByWall = createSpearKillDirectPacketSegmentValidator(
            origin = origin,
            playerBoundingBox = playerBox,
            hasHitboxRaycastCollision = { box, movement ->
                hasSpearKillHitboxRaycastCollision(
                    box,
                    movement,
                    listOf(AABB(1.5, 0.0, -0.3, 2.0, 1.8, 0.3)),
                )
            },
        )

        assertFalse(occupiedDestination.isClear(origin, destination))
        assertFalse(clippedByWall.isClear(origin, destination))
    }

    @Test
    fun `AStar hitbox raycast rejects an elevated diagonal obstacle`() {
        val from = Vec3.ZERO
        val to = Vec3(2.0, 1.0, 0.0)
        val obstacle = AABB(0.9, 2.0, -0.2, 1.3, 2.5, 0.2)
        val validator = createSpearKillAStarSegmentValidator(
            origin = from,
            playerBoundingBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3),
            hasHitboxRaycastCollision = { box, movement ->
                hasSpearKillHitboxRaycastCollision(box, movement, listOf(obstacle))
            },
        )

        assertFalse(validator.isClear(from, to))
    }

    @Test
    fun `AStar neighbors always consult canTraverse for passable edges`() {
        val origin = BlockPos(0, 64, 0)
        val openCells = setOf(
            origin,
            origin.offset(1, 0, 0),
            origin.offset(0, 1, 0),
            origin.offset(0, 0, 1),
            origin.offset(1, 0, 1),
        )
        val blocked = origin.offset(1, 0, 0)
        val neighbors = spearKillBidirectionalNeighbors(
            position = origin,
            allowDiagonal = true,
            isPassable = { it in openCells },
            canTraverse = { _, to -> to != blocked },
        )

        assertFalse(neighbors.any { it.node == blocked })
        assertTrue(neighbors.any { it.node == origin.offset(0, 1, 0) })
        assertTrue(neighbors.any { it.node == origin.offset(1, 0, 1) })
    }
}
