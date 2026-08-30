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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillPacketSessionPortAdapter
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

class SpearKillAttackDirectionRoundTripFollowTargetElevationTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }


    @Test
    fun `attack direction and round trip follow target elevation`() {
        val playerEyePosition = Vec3(0.0, 1.62, 0.0)
        val targetEyeOffset = Vec3(0.0, 1.62, 0.0)
        val fallbackDirection = Vec3(0.0, 0.0, 1.0)

        val higherTargetDirection = calculateSpearKillAttackDirection(
            playerEyePosition = playerEyePosition,
            predictedTargetPosition = Vec3(5.0, 4.0, 0.0),
            targetEyeOffset = targetEyeOffset,
            fallbackDirection = fallbackDirection,
        )
        val lowerTargetDirection = calculateSpearKillAttackDirection(
            playerEyePosition = playerEyePosition,
            predictedTargetPosition = Vec3(5.0, -4.0, 0.0),
            targetEyeOffset = targetEyeOffset,
            fallbackDirection = fallbackDirection,
        )

        assertVec3Equals(Vec3(5.0, 4.0, 0.0).normalize(), higherTargetDirection, 1e-9)
        assertVec3Equals(Vec3(5.0, -4.0, 0.0).normalize(), lowerTargetDirection, 1e-9)

        val higherMovements = buildSpearKillAttackMovements(higherTargetDirection, 10.0, 7.0)
        val lowerMovements = buildSpearKillAttackMovements(lowerTargetDirection, 10.0, 7.0)
        assertTrue(higherMovements.first().y > 0.0)
        assertTrue(lowerMovements.first().y < 0.0)
        assertTrue(higherMovements.dropLast(1).all { it.length() <= 7.0 })
        assertTrue(lowerMovements.dropLast(1).all { it.length() <= 7.0 })
        assertVec3Equals(Vec3.ZERO, higherMovements.fold(Vec3.ZERO, Vec3::add), 1e-9)
        assertVec3Equals(Vec3.ZERO, lowerMovements.fold(Vec3.ZERO, Vec3::add), 1e-9)
    }

    @Test
    fun `attack direction supports a target directly above`() {
        val direction = calculateSpearKillAttackDirection(
            playerEyePosition = Vec3(4.0, 2.0, 7.0),
            predictedTargetPosition = Vec3(4.0, 12.0, 7.0),
            targetEyeOffset = Vec3.ZERO,
            fallbackDirection = Vec3(1.0, 0.0, 0.0),
        )

        assertVec3Equals(Vec3(0.0, 1.0, 0.0), direction, 1e-9)
    }

    @Test
    fun `fall protection starts only after a damaging fall begins`() {
        assertFalse(shouldProtectSpearKillFallDamage(
            fallDistance = 2.0,
            verticalVelocity = -1.0,
            safeFallDistance = 3.0,
            tickCount = 21,
        ))
        assertFalse(shouldProtectSpearKillFallDamage(
            fallDistance = 2.1,
            verticalVelocity = -1.0,
            safeFallDistance = 3.0,
            tickCount = 20,
        ))
        assertTrue(shouldProtectSpearKillFallDamage(
            fallDistance = 2.1,
            verticalVelocity = -1.0,
            safeFallDistance = 3.0,
            tickCount = 21,
        ))
    }

    @Test
    fun `fall protection confirms only its selected movement packet`() {
        val tracker = SpearKillFallDamagePacketTracker()
        val protectedPacket = ServerboundMovePlayerPacket.StatusOnly(false, false)
        val cancelledPacket = ServerboundMovePlayerPacket.StatusOnly(false, false)
        val retryPacket = ServerboundMovePlayerPacket.StatusOnly(false, false)
        val unrelatedPacket = ServerboundMovePlayerPacket.StatusOnly(false, false)

        tracker.protect(protectedPacket)
        tracker.protect(cancelledPacket)

        assertTrue(protectedPacket.onGround)
        protectedPacket.onGround = false
        assertFalse(tracker.reassertGround(unrelatedPacket))
        assertTrue(tracker.reassertGround(protectedPacket))
        assertTrue(protectedPacket.onGround)
        assertFalse(tracker.confirmFinalState(unrelatedPacket, cancelled = false))
        assertFalse(tracker.confirmFinalState(cancelledPacket, cancelled = true))
        assertTrue(tracker.confirmFinalState(protectedPacket, cancelled = false))

        tracker.protect(retryPacket)
        assertTrue(tracker.confirmFinalState(retryPacket, cancelled = false))
    }

    @Test
    fun `owned recovery confirmation carries its explicit position and ground bit`() {
        val position = Vec3(12.5, 72.25, -4.75)
        val packet = createSpearKillPositionPacket(
            position = position,
            yaw = 37.0f,
            pitch = -12.0f,
            onGround = true,
            horizontalCollision = false,
        )

        assertTrue(packet.hasPosition())
        assertTrue(packet.onGround)
        assertEquals(position.x, packet.getX(0.0))
        assertEquals(position.y, packet.getY(0.0))
        assertEquals(position.z, packet.getZ(0.0))
    }

    @Test
    fun `attack path uses bounded steps and returns to its origin`() {
        val movements = buildSpearKillAttackMovements(
            direction = Vec3(1.0, -1.0, 0.0).normalize(),
            distance = 16.0,
            maxSpeed = 7.0,
        )

        assertEquals(7, movements.size)
        assertTrue(movements.dropLast(1).all { it.length() <= 7.0 })
        assertTrue(movements.first().y < 0.0)
        assertVec3Equals(Vec3.ZERO, movements.fold(Vec3.ZERO, Vec3::add), 1e-9)
        assertVec3Equals(Vec3.ZERO, movements.last(), 1e-9)
    }

    @Test
    fun `direct attack long travel uses full fixed steps followed by its remainder`() {
        val movements = buildSpearKillAttackMovements(
            direction = Vec3(1.0, 0.0, 0.0),
            distance = 10.0,
            maxSpeed = 3.0,
        )
        val expectedOutbound = listOf(
            Vec3(3.0, 0.0, 0.0),
            Vec3(3.0, 0.0, 0.0),
            Vec3(3.0, 0.0, 0.0),
            Vec3(1.0, 0.0, 0.0),
        )

        assertEquals(expectedOutbound + expectedOutbound.asReversed().map { it.scale(-1.0) } + Vec3.ZERO, movements)

        val shortMovements = buildSpearKillAttackMovements(
            direction = Vec3(1.0, 0.0, 0.0),
            distance = 2.0,
            maxSpeed = 3.0,
        )
        assertEquals(3, shortMovements.size)
        assertVec3Equals(Vec3(2.0, 0.0, 0.0), shortMovements[0], 1e-9)
        assertVec3Equals(Vec3(-2.0, 0.0, 0.0), shortMovements[1], 1e-9)
        assertVec3Equals(Vec3.ZERO, shortMovements[2], 1e-9)
    }

    @Test
    fun `cancelled packet retries the same virtual step`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.start(listOf(Vec3(4.0, -2.0, 1.0), Vec3(-4.0, 2.0, -1.0), Vec3.ZERO))

        val firstAttempt = session.prepareNextStep()
        assertTrue(session.requiresDelivery)
        session.confirmStep(delivered = false)
        assertFalse(session.requiresDelivery)
        val retry = session.prepareNextStep()

        assertVec3Equals(Vec3(4.0, -2.0, 1.0), firstAttempt!!, 1e-9)
        assertVec3Equals(firstAttempt, retry!!, 1e-9)
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
    }

    @Test
    fun `packet session commits exactly one step per confirmation`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.start(listOf(Vec3(3.0, 0.0, 0.0), Vec3(3.0, 0.0, 0.0)))

        assertVec3Equals(Vec3(3.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)

        assertVec3Equals(Vec3(3.0, 0.0, 0.0), session.committedOffset, 1e-9)
        assertVec3Equals(Vec3(6.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
    }
}
