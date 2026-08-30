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
package net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.packet

import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.LoggedEncodedVector
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.LoggedPlayerRotation
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.LoggedRotation
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.LoggedVector
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionLogKind
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionState
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerServerPositionState

import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.entity.Relative
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerPositionPacketLogFactoryTest {

    @Test
    fun `position-only outgoing packet keeps explicit coordinates and resolves omitted rotation`() {
        val source = state(
            position = LoggedVector(1.0, 2.0, 3.0),
            yaw = 35f,
            pitch = -10f,
        )
        val packet = ServerboundMovePlayerPacket.Pos(8.0, 9.0, 10.0, true, false)

        val observation = PlayerPositionPacketLogFactory.outgoingMovement(packet, source)

        assertEquals(PlayerPositionLogKind.LOCAL_MOVEMENT, observation.kind)
        assertEquals(LoggedVector(8.0, 9.0, 10.0), observation.packetState.suppliedPosition)
        assertEquals(LoggedVector(8.0, 9.0, 10.0), observation.packetState.resolvedPosition)
        assertNull(observation.packetState.suppliedRotation)
        assertEquals(LoggedRotation(35f, -10f), observation.packetState.resolvedRotation)
        assertTrue(observation.packetState.hasPosition!!)
        assertFalse(observation.packetState.hasRotation!!)
        assertTrue(observation.packetState.onGround!!)
        assertFalse(observation.packetState.horizontalCollision!!)
    }

    @Test
    fun `partial outgoing packet inherits fields from last transmitted server state`() {
        val client = state(
            position = LoggedVector(20.0, 70.0, 5.0),
            yaw = 90f,
            pitch = 20f,
        )
        val transmitted = PlayerServerPositionState(
            previousPosition = LoggedVector(2.0, 64.0, 3.0),
            position = LoggedVector(3.0, 64.0, 4.0),
            previousRotation = LoggedRotation(10f, 2f),
            rotation = LoggedRotation(15f, 3f),
            onGround = true,
            horizontalCollision = false,
        )

        val rotation = PlayerPositionPacketLogFactory.outgoingMovement(
            ServerboundMovePlayerPacket.Rot(30f, -5f, false, true),
            client,
            transmitted,
        )
        val status = PlayerPositionPacketLogFactory.outgoingMovement(
            ServerboundMovePlayerPacket.StatusOnly(true, false),
            client,
            transmitted,
        )

        assertEquals(LoggedVector(3.0, 64.0, 4.0), rotation.packetState.resolvedPosition)
        assertEquals(LoggedRotation(30f, -5f), rotation.packetState.resolvedRotation)
        assertEquals(LoggedVector(3.0, 64.0, 4.0), status.packetState.resolvedPosition)
        assertEquals(LoggedRotation(15f, 3f), status.packetState.resolvedRotation)
    }

    @Test
    fun `incoming correction resolves relative position velocity and rotation`() {
        val source = state(
            position = LoggedVector(10.0, 64.0, 2.0),
            velocity = LoggedVector(0.2, -0.1, 0.0),
            yaw = 30f,
            pitch = 10f,
        )
        val packet = ClientboundPlayerPositionPacket(
            17,
            PositionMoveRotation(
                Vec3(4.0, 70.0, -3.0),
                Vec3(0.3, 1.0, 0.5),
                15f,
                -40f,
            ),
            setOf(Relative.X, Relative.DELTA_X, Relative.Y_ROT),
        )

        val observation = PlayerPositionPacketLogFactory.localCorrection(packet, source)

        assertEquals(PlayerPositionLogKind.LOCAL_CORRECTION, observation.kind)
        assertEquals(LoggedVector(4.0, 70.0, -3.0), observation.packetState.suppliedPosition)
        assertEquals(LoggedVector(14.0, 70.0, -3.0), observation.packetState.resolvedPosition)
        assertEquals(LoggedVector(0.3, 1.0, 0.5), observation.packetState.suppliedVelocity)
        assertEquals(LoggedVector(0.5, 1.0, 0.5), observation.packetState.resolvedVelocity)
        assertEquals(LoggedRotation(15f, -40f), observation.packetState.suppliedRotation)
        assertEquals(LoggedRotation(45f, -40f), observation.packetState.resolvedRotation)
        assertEquals(setOf("X", "DELTA_X", "Y_ROT"), observation.packetState.relativeFlags.toSet())
        assertEquals(17, observation.teleportId)
    }

    @Test
    fun `remote relative movement resolves compressed deltas against tracking base`() {
        val source = state(
            position = LoggedVector(10.0, 64.0, -2.0),
            positionCodecBase = LoggedVector(10.25, 64.0, -2.0),
            yaw = 15f,
            pitch = 5f,
        )
        val packet = ClientboundMoveEntityPacket.PosRot(
            42,
            4096,
            -2048,
            0,
            64,
            -32,
            true,
        )

        val observation = PlayerPositionPacketLogFactory.remoteMovement(packet, source)

        assertEquals(PlayerPositionLogKind.REMOTE_MOVEMENT, observation.kind)
        assertEquals(LoggedEncodedVector(4096, -2048, 0), observation.packetState.encodedDelta)
        assertEquals(LoggedVector(11.25, 63.5, -2.0), observation.packetState.resolvedPosition)
        assertEquals(LoggedRotation(90f, -45f), observation.packetState.resolvedRotation)
        assertTrue(observation.packetState.hasPosition!!)
        assertTrue(observation.packetState.hasRotation!!)
        assertTrue(observation.packetState.onGround!!)
    }

    @Test
    fun `remote teleport resolves relative position and movement`() {
        val source = state(
            position = LoggedVector(20.0, 70.0, 5.0),
            velocity = LoggedVector(0.1, 0.2, 0.3),
            yaw = 45f,
            pitch = 10f,
        )
        val packet = ClientboundTeleportEntityPacket(
            42,
            PositionMoveRotation(
                Vec3(-3.0, 80.0, 1.0),
                Vec3(0.4, -0.2, 0.0),
                15f,
                -30f,
            ),
            setOf(Relative.X, Relative.Z, Relative.DELTA_X, Relative.DELTA_Y, Relative.Y_ROT),
            false,
        )

        val observation = PlayerPositionPacketLogFactory.remoteTeleport(packet, source)

        assertEquals(PlayerPositionLogKind.REMOTE_TELEPORT, observation.kind)
        assertEquals(LoggedVector(17.0, 80.0, 6.0), observation.packetState.resolvedPosition)
        assertEquals(LoggedVector(0.5, 0.0, 0.0), observation.packetState.resolvedVelocity)
        assertEquals(LoggedRotation(60f, -30f), observation.packetState.resolvedRotation)
        assertFalse(observation.packetState.onGround!!)
        assertEquals(42, observation.relatedEntityId)
    }

    @Test
    fun `local rotation packet resolves relative yaw and absolute pitch`() {
        val source = state(
            position = LoggedVector(2.0, 64.0, 8.0),
            yaw = 30f,
            pitch = 10f,
        )
        val packet = ClientboundPlayerRotationPacket(15f, true, -20f, false)

        val observation = PlayerPositionPacketLogFactory.localRotation(packet, source)

        assertEquals(PlayerPositionLogKind.LOCAL_ROTATION, observation.kind)
        assertEquals(LoggedRotation(15f, -20f), observation.packetState.suppliedRotation)
        assertEquals(LoggedRotation(45f, -20f), observation.packetState.resolvedRotation)
        assertEquals(listOf("Y_ROT"), observation.packetState.relativeFlags)
        assertFalse(observation.packetState.hasPosition!!)
        assertTrue(observation.packetState.hasRotation!!)
    }

    @Test
    fun `explosion knockback records additive velocity`() {
        val source = state(
            position = LoggedVector(2.0, 64.0, 8.0),
            velocity = LoggedVector(0.1, -0.2, 0.3),
        )

        val observation = PlayerPositionPacketLogFactory.explosionKnockback(Vec3(0.4, 0.5, -0.1), source)

        assertEquals(PlayerPositionLogKind.LOCAL_EXPLOSION_KNOCKBACK, observation.kind)
        assertEquals(LoggedVector(0.4, 0.5, -0.1), observation.packetState.suppliedVelocity)
        val resolved = observation.packetState.resolvedVelocity!!
        assertEquals(0.5, resolved.x, 1e-12)
        assertEquals(0.3, resolved.y, 1e-12)
        assertEquals(0.2, resolved.z, 1e-12)
    }

    @Test
    fun `position sync and velocity preserve authoritative player state`() {
        val syncedValues = PositionMoveRotation(
            Vec3(30.0, 90.0, -4.0),
            Vec3(0.2, 0.1, -0.3),
            120f,
            -15f,
        )

        val sync = PlayerPositionPacketLogFactory.remotePositionSync(
            ClientboundEntityPositionSyncPacket(42, syncedValues, true),
        )
        val velocity = PlayerPositionPacketLogFactory.velocity(
            ClientboundSetEntityMotionPacket(42, Vec3(-0.5, 1.25, 0.75)),
            local = false,
        )

        assertEquals(PlayerPositionLogKind.REMOTE_POSITION_SYNC, sync.kind)
        assertEquals(LoggedVector(30.0, 90.0, -4.0), sync.packetState.resolvedPosition)
        assertEquals(LoggedVector(0.2, 0.1, -0.3), sync.packetState.resolvedVelocity)
        assertEquals(LoggedRotation(120f, -15f), sync.packetState.resolvedRotation)
        assertTrue(sync.packetState.onGround!!)
        assertEquals(42, sync.relatedEntityId)

        assertEquals(PlayerPositionLogKind.REMOTE_VELOCITY, velocity.kind)
        assertEquals(LoggedVector(-0.5, 1.25, 0.75), velocity.packetState.suppliedVelocity)
        assertEquals(LoggedVector(-0.5, 1.25, 0.75), velocity.packetState.resolvedVelocity)
        assertEquals(42, velocity.relatedEntityId)
    }

    private fun state(
        position: LoggedVector,
        positionCodecBase: LoggedVector = position,
        velocity: LoggedVector = LoggedVector.ZERO,
        yaw: Float = 0f,
        pitch: Float = 0f,
    ) = PlayerPositionState(
        position = position,
        previousPosition = position,
        trackingPosition = position,
        positionCodecBase = positionCodecBase,
        velocity = velocity,
        rotation = LoggedPlayerRotation(yaw, pitch, yaw, yaw),
        onGround = false,
        horizontalCollision = false,
        verticalCollision = false,
        fallDistance = 0.0,
        passenger = false,
        vehicleEntityId = null,
        pose = "standing",
    )
}
