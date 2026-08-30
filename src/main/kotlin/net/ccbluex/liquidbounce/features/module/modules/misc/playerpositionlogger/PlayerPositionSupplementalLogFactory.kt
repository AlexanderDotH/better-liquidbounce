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

package net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket
import net.minecraft.world.phys.Vec3

internal object PlayerPositionSupplementalLogFactory {
    fun vehicleMovement(packet: ServerboundMoveVehiclePacket) = PlayerPositionPacketObservation(
        PlayerPositionLogKind.LOCAL_VEHICLE_MOVEMENT,
        PlayerPositionPacketState(
            suppliedPosition = LoggedVector.from(packet.position),
            resolvedPosition = LoggedVector.from(packet.position),
            suppliedRotation = LoggedRotation(packet.yRot, packet.xRot),
            resolvedRotation = LoggedRotation(packet.yRot, packet.xRot),
            onGround = packet.onGround,
            hasPosition = true,
            hasRotation = true,
        ),
    )

    fun remoteSpawn(packet: ClientboundAddEntityPacket) = PlayerPositionPacketObservation(
        PlayerPositionLogKind.REMOTE_SPAWN,
        PlayerPositionPacketState(
            suppliedPosition = LoggedVector(packet.x, packet.y, packet.z),
            resolvedPosition = LoggedVector(packet.x, packet.y, packet.z),
            suppliedVelocity = LoggedVector.from(packet.movement),
            resolvedVelocity = LoggedVector.from(packet.movement),
            suppliedRotation = LoggedRotation(packet.yRot, packet.xRot),
            resolvedRotation = LoggedRotation(packet.yRot, packet.xRot),
            suppliedHeadYaw = packet.yHeadRot,
            resolvedHeadYaw = packet.yHeadRot,
            hasPosition = true,
            hasRotation = true,
        ),
        relatedEntityId = packet.id,
    )

    fun remoteHeadRotation(packet: ClientboundRotateHeadPacket, source: PlayerPositionState, entityId: Int) =
        PlayerPositionPacketObservation(
            PlayerPositionLogKind.REMOTE_HEAD_ROTATION,
            PlayerPositionPacketState(
                resolvedPosition = source.position,
                resolvedRotation = source.rotation.packetRotation,
                suppliedHeadYaw = packet.yHeadRot,
                resolvedHeadYaw = packet.yHeadRot,
                hasPosition = false,
                hasRotation = true,
            ),
            relatedEntityId = entityId,
        )

    fun localNetworkMovement(position: Vec3, onGround: Boolean) = PlayerPositionPacketObservation(
        PlayerPositionLogKind.LOCAL_NETWORK_MOVEMENT,
        PlayerPositionPacketState(
            resolvedPosition = LoggedVector.from(position),
            onGround = onGround,
            hasPosition = true,
        ),
    )

    fun localJump(motion: Float, yaw: Float) = PlayerPositionPacketObservation(
        PlayerPositionLogKind.LOCAL_JUMP,
        PlayerPositionPacketState(
            resolvedVelocity = LoggedVector(0.0, motion.toDouble(), 0.0),
            resolvedRotation = LoggedRotation(yaw, 0f),
        ),
    )
}
