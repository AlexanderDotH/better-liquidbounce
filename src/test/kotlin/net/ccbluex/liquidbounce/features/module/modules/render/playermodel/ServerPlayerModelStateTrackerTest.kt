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

import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.ccbluex.liquidbounce.test.assertVec3Equals
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerPlayerModelStateTrackerTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

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
    fun `walk animation accelerates once per tick with vanilla smoothing`() {
        ServerPlayerModelStateTracker.onGameTick()
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.PosRot(0.0, 64.0, 0.0, 0f, 0f, true, false),
            nowNanos = 1L,
        )
        ServerPlayerModelStateTracker.onGameTick()
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.Pos(0.2, 64.0, 0.0, true, false),
            nowNanos = 50_000_001L,
        )

        val firstMovementTick = ServerPlayerModelStateTracker.snapshot
        assertEquals(0f, firstMovementTick.previousWalkAnimationSpeed, 0.0001f)
        assertEquals(0.32f, firstMovementTick.walkAnimationSpeed, 0.0001f)
        assertEquals(0.32f, firstMovementTick.walkAnimationPosition, 0.0001f)

        ServerPlayerModelStateTracker.onGameTick()
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.Pos(0.4, 64.0, 0.0, true, false),
            nowNanos = 100_000_001L,
        )

        val secondMovementTick = ServerPlayerModelStateTracker.snapshot
        assertEquals(0.32f, secondMovementTick.previousWalkAnimationSpeed, 0.0001f)
        assertEquals(0.512f, secondMovementTick.walkAnimationSpeed, 0.0001f)
        assertEquals(0.832f, secondMovementTick.walkAnimationPosition, 0.0001f)
    }

    @Test
    fun `render snapshot settles landing position while walk animation decays`() {
        ServerPlayerModelStateTracker.onGameTick()
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.PosRot(0.0, 64.0, 0.0, 0f, 0f, true, false),
            nowNanos = 1L,
        )
        ServerPlayerModelStateTracker.onGameTick()
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.Pos(0.2, 64.42, 0.0, false, false),
            nowNanos = 50_000_001L,
        )
        ServerPlayerModelStateTracker.onGameTick()
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.Pos(0.4, 64.0, 0.0, true, false),
            nowNanos = 100_000_001L,
        )

        val landing = ServerPlayerModelStateTracker.snapshotForRender(
            nowNanos = 100_000_002L,
            swingDurationTicks = 6,
        )
        assertVec3Equals(Vec3(0.2, 64.42, 0.0), landing.previousPosition!!, 0.0)
        assertEquals(0.512f, landing.walkAnimationSpeed, 0.0001f)
        assertEquals(0.832f, landing.walkAnimationPosition, 0.0001f)

        ServerPlayerModelStateTracker.onGameTick()
        val settled = ServerPlayerModelStateTracker.snapshotForRender(
            nowNanos = 150_000_002L,
            swingDurationTicks = 6,
        )

        assertVec3Equals(Vec3(0.4, 64.0, 0.0), settled.previousPosition!!, 0.0)
        assertVec3Equals(Vec3(0.4, 64.0, 0.0), settled.position!!, 0.0)
        assertEquals(0.512f, settled.previousWalkAnimationSpeed, 0.0001f)
        assertEquals(0.3072f, settled.walkAnimationSpeed, 0.0001f)
        assertEquals(1.1392f, settled.walkAnimationPosition, 0.0001f)

        ServerPlayerModelStateTracker.onGameTick()
        val secondIdleTick = ServerPlayerModelStateTracker.snapshotForRender(
            nowNanos = 200_000_002L,
            swingDurationTicks = 6,
        )
        assertEquals(0.3072f, secondIdleTick.previousWalkAnimationSpeed, 0.0001f)
        assertEquals(0.18432f, secondIdleTick.walkAnimationSpeed, 0.0001f)
        assertEquals(1.32352f, secondIdleTick.walkAnimationPosition, 0.0001f)
    }

    @Test
    fun `movement resumes after idle without resetting walk phase`() {
        ServerPlayerModelStateTracker.onGameTick()
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.PosRot(0.0, 64.0, 0.0, 0f, 0f, true, false),
            nowNanos = 1L,
        )
        ServerPlayerModelStateTracker.onGameTick()
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.Pos(0.2, 64.0, 0.0, true, false),
            nowNanos = 50_000_001L,
        )
        assertEquals(0.32f, ServerPlayerModelStateTracker.snapshot.walkAnimationSpeed, 0.0001f)

        ServerPlayerModelStateTracker.onGameTick()
        val firstIdleTick = ServerPlayerModelStateTracker.snapshotForRender(
            nowNanos = 100_000_002L,
            swingDurationTicks = 6,
        )
        assertEquals(0.192f, firstIdleTick.walkAnimationSpeed, 0.0001f)
        assertEquals(0.512f, firstIdleTick.walkAnimationPosition, 0.0001f)

        ServerPlayerModelStateTracker.onGameTick()
        val secondIdleTick = ServerPlayerModelStateTracker.snapshotForRender(
            nowNanos = 150_000_002L,
            swingDurationTicks = 6,
        )
        assertEquals(0.1152f, secondIdleTick.walkAnimationSpeed, 0.0001f)
        assertEquals(0.6272f, secondIdleTick.walkAnimationPosition, 0.0001f)

        ServerPlayerModelStateTracker.onGameTick()
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.Pos(0.4, 64.0, 0.0, true, false),
            nowNanos = 200_000_001L,
        )

        val resumed = ServerPlayerModelStateTracker.snapshot
        assertVec3Equals(Vec3(0.2, 64.0, 0.0), resumed.previousPosition!!, 0.0)
        assertVec3Equals(Vec3(0.4, 64.0, 0.0), resumed.position!!, 0.0)
        assertEquals(0.1152f, resumed.previousWalkAnimationSpeed, 0.0001f)
        assertEquals(0.38912f, resumed.walkAnimationSpeed, 0.0001f)
        assertEquals(1.01632f, resumed.walkAnimationPosition, 0.0001f)
    }

    @Test
    fun `multiple position packets in one tick advance walk animation once`() {
        ServerPlayerModelStateTracker.onGameTick()
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.PosRot(0.0, 64.0, 0.0, 0f, 0f, true, false),
            nowNanos = 1L,
        )
        ServerPlayerModelStateTracker.onGameTick()
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.Pos(0.1, 64.0, 0.0, true, false),
            nowNanos = 50_000_001L,
        )
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.Pos(0.2, 64.0, 0.0, true, false),
            nowNanos = 50_000_002L,
        )

        val snapshot = ServerPlayerModelStateTracker.snapshot
        assertEquals(0f, snapshot.previousWalkAnimationSpeed, 0.0001f)
        assertEquals(0.32f, snapshot.walkAnimationSpeed, 0.0001f)
        assertEquals(0.32f, snapshot.walkAnimationPosition, 0.0001f)
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
    fun `correction and reset clear interpolation and tick local walk animation`() {
        ServerPlayerModelStateTracker.onGameTick()
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.PosRot(1.0, 2.0, 3.0, 40f, 10f, true, false),
            nowNanos = 1L,
        )
        ServerPlayerModelStateTracker.onGameTick()
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.Pos(1.1, 2.0, 3.0, true, false),
            nowNanos = 50_000_001L,
        )
        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.Pos(1.2, 2.0, 3.0, true, false),
            nowNanos = 50_000_002L,
        )
        val phaseBeforeCorrection = ServerPlayerModelStateTracker.snapshot.walkAnimationPosition
        assertEquals(0.32f, phaseBeforeCorrection, 0.0001f)

        ServerPlayerModelStateTracker.correct(Vec3(20.0, 30.0, 40.0), 170f, -25f, nowNanos = 50_000_003L)

        val corrected = ServerPlayerModelStateTracker.snapshot
        assertEquals(corrected.position, corrected.previousPosition)
        assertEquals(corrected.rotation, corrected.previousRotation)
        assertVec3Equals(Vec3(20.0, 30.0, 40.0), corrected.position!!, 0.0)
        assertEquals(170f, corrected.rotation!!.yRot)
        assertEquals(0f, corrected.previousWalkAnimationSpeed, 0.0001f)
        assertEquals(0f, corrected.walkAnimationSpeed, 0.0001f)
        assertEquals(phaseBeforeCorrection, corrected.walkAnimationPosition, 0.0001f)

        ServerPlayerModelStateTracker.onPacketSent(
            ServerboundMovePlayerPacket.Pos(20.1, 30.0, 40.0, true, false),
            nowNanos = 50_000_004L,
        )

        val afterCorrection = ServerPlayerModelStateTracker.snapshot
        assertEquals(0f, afterCorrection.previousWalkAnimationSpeed, 0.0001f)
        assertEquals(0.16f, afterCorrection.walkAnimationSpeed, 0.0001f)
        assertEquals(0.48f, afterCorrection.walkAnimationPosition, 0.0001f)

        ServerPlayerModelStateTracker.reset()
        assertEquals(ServerPlayerModelSnapshot.EMPTY, ServerPlayerModelStateTracker.snapshot)
    }

}
