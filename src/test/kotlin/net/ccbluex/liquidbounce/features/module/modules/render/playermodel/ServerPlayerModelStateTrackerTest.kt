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

package net.ccbluex.liquidbounce.features.module.modules.render.playermodel

import net.ccbluex.liquidbounce.test.assertVec3Equals
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerPlayerModelStateTrackerTest {

    @AfterEach
    fun resetTracker() {
        ServerPlayerModelStateTracker.reset()
    }

    @Test
    fun `full movement packet initializes and advances transmitted state`() {
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.PosRot(1.0, 2.0, 3.0, 90f, 12f, true, false),
            nowNanos = 1L,
        )
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.PosRot(2.0, 2.0, 5.0, 120f, 20f, false, true),
            nowNanos = 50_000_001L,
        )

        val snapshot = ServerPlayerModelStateTracker.snapshot
        assertTrue(snapshot.isInitialized)
        assertVec3Equals(Vec3(1.0, 2.0, 3.0), snapshot.previousPosition!!, 0.0)
        assertVec3Equals(Vec3(2.0, 2.0, 5.0), snapshot.position!!, 0.0)
        assertEquals(90f, snapshot.previousRotation!!.yRot)
        assertEquals(120f, snapshot.rotation!!.yRot)
        assertFalse(snapshot.onGround)
        assertTrue(snapshot.horizontalCollision)
        assertTrue(snapshot.walkAnimationPosition > 0f)
        assertTrue(snapshot.walkAnimationSpeed > 0f)
    }

    @Test
    fun `partial movement packets preserve fields they do not contain`() {
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.PosRot(4.0, 5.0, 6.0, 35f, 10f, true, false),
            nowNanos = 1L,
        )
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.Rot(80f, -15f, false, true),
            nowNanos = 2L,
        )

        val afterRotation = ServerPlayerModelStateTracker.snapshot
        assertVec3Equals(Vec3(4.0, 5.0, 6.0), afterRotation.position!!, 0.0)
        assertEquals(80f, afterRotation.rotation!!.yRot)

        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.Pos(8.0, 9.0, 10.0, true, false),
            nowNanos = 3L,
        )

        val afterPosition = ServerPlayerModelStateTracker.snapshot
        assertVec3Equals(Vec3(8.0, 9.0, 10.0), afterPosition.position!!, 0.0)
        assertEquals(80f, afterPosition.rotation!!.yRot)
    }

    @Test
    fun `actual send bridge records effective player input`() {
        val input = Input(true, false, true, false, true, true, true)

        ServerPlayerModelStateTracker.onPacketSent(ServerboundPlayerInputPacket(input), nowNanos = 1L)

        val snapshot = ServerPlayerModelStateTracker.snapshot
        assertEquals(input, snapshot.input)
        assertTrue(snapshot.input.shift())
        assertTrue(snapshot.input.sprint())
    }

    @Test
    fun `correction replaces position and rotation without interpolation`() {
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.PosRot(1.0, 2.0, 3.0, 40f, 10f, true, false),
            nowNanos = 1L,
        )

        ServerPlayerModelStateTracker.correct(Vec3(20.0, 30.0, 40.0), 170f, -25f, nowNanos = 2L)

        val snapshot = ServerPlayerModelStateTracker.snapshot
        assertEquals(snapshot.position, snapshot.previousPosition)
        assertEquals(snapshot.rotation, snapshot.previousRotation)
        assertVec3Equals(Vec3(20.0, 30.0, 40.0), snapshot.position!!, 0.0)
        assertEquals(170f, snapshot.rotation!!.yRot)
        assertEquals(0f, snapshot.walkAnimationSpeed)
    }

    @Test
    fun `slot use swing release and reset follow transmitted packets`() {
        ServerPlayerModelStateTracker.onPacketSent(ServerboundSetCarriedItemPacket(4), nowNanos = 1L)
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 1, 90f, 15f),
            nowNanos = 2L,
        )
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundSwingPacket(InteractionHand.OFF_HAND),
            nowNanos = 3L,
        )

        val active = ServerPlayerModelStateTracker.snapshot
        assertEquals(4, active.selectedHotbarSlot)
        assertEquals(InteractionHand.MAIN_HAND, active.activeUseHand)
        assertEquals(2L, active.useStartedAtNanos)
        assertEquals(InteractionHand.OFF_HAND, active.swingHand)
        assertEquals(3L, active.swingStartedAtNanos)

        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                BlockPos.ZERO,
                Direction.DOWN,
            ),
            nowNanos = 4L,
        )

        assertNull(ServerPlayerModelStateTracker.snapshot.activeUseHand)
        ServerPlayerModelStateTracker.reset()
        assertEquals(ServerPlayerModelSnapshot.EMPTY, ServerPlayerModelStateTracker.snapshot)
    }

    @Test
    fun `slot change clears main hand item use`() {
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 1, 0f, 0f),
            nowNanos = 1L,
        )

        ServerPlayerModelStateTracker.onPacketSent(ServerboundSetCarriedItemPacket(7), nowNanos = 2L)

        assertNull(ServerPlayerModelStateTracker.snapshot.activeUseHand)
    }

    @Test
    fun `render snapshot expires swing after vanilla duration`() {
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundSwingPacket(InteractionHand.MAIN_HAND),
            nowNanos = 1L,
        )

        val expired = ServerPlayerModelStateTracker.snapshotForRender(
            nowNanos = 300_000_001L,
            swingDurationTicks = 6,
        )

        assertNull(expired.swingHand)
        assertNull(expired.swingStartedAtNanos)
    }

    @Test
    fun `item use expires when no release packet arrives`() {
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 1, 0f, 0f),
            nowNanos = 1L,
        )

        ServerPlayerModelStateTracker.snapshotForRender(
            nowNanos = 60_000_000_001L,
            swingDurationTicks = 6,
        )

        assertNull(ServerPlayerModelStateTracker.snapshot.activeUseHand)
    }
}
