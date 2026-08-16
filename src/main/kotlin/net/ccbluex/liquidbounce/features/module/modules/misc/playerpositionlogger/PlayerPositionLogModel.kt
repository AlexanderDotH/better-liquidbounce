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

import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket
import net.minecraft.network.protocol.game.VecDeltaCodec
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.phys.Vec3

@JvmRecord
internal data class LoggedVector(val x: Double, val y: Double, val z: Double) {
    fun toVec3() = Vec3(x, y, z)

    companion object {
        val ZERO = LoggedVector(0.0, 0.0, 0.0)

        fun from(vector: Vec3) = LoggedVector(vector.x, vector.y, vector.z)
    }
}

@JvmRecord
internal data class LoggedEncodedVector(val x: Int, val y: Int, val z: Int)

@JvmRecord
internal data class LoggedRotation(val yaw: Float, val pitch: Float)

@JvmRecord
internal data class LoggedPlayerRotation(
    val yaw: Float,
    val pitch: Float,
    val headYaw: Float,
    val bodyYaw: Float,
) {
    val packetRotation: LoggedRotation
        get() = LoggedRotation(yaw, pitch)
}

@JvmRecord
internal data class PlayerPositionState(
    val position: LoggedVector,
    val previousPosition: LoggedVector,
    val trackingPosition: LoggedVector,
    val positionCodecBase: LoggedVector,
    val velocity: LoggedVector,
    val rotation: LoggedPlayerRotation,
    val onGround: Boolean,
    val horizontalCollision: Boolean,
    val verticalCollision: Boolean,
    val fallDistance: Double,
    val passenger: Boolean,
    val vehicleEntityId: Int?,
    val pose: String,
) {
    fun toPositionMoveRotation() = PositionMoveRotation(
        position.toVec3(),
        velocity.toVec3(),
        rotation.yaw,
        rotation.pitch,
    )
}

@JvmRecord
internal data class PlayerPositionPacketState(
    val suppliedPosition: LoggedVector? = null,
    val resolvedPosition: LoggedVector? = null,
    val suppliedVelocity: LoggedVector? = null,
    val resolvedVelocity: LoggedVector? = null,
    val suppliedRotation: LoggedRotation? = null,
    val resolvedRotation: LoggedRotation? = null,
    val suppliedHeadYaw: Float? = null,
    val resolvedHeadYaw: Float? = null,
    val encodedDelta: LoggedEncodedVector? = null,
    val relativeFlags: List<String> = emptyList(),
    val onGround: Boolean? = null,
    val horizontalCollision: Boolean? = null,
    val hasPosition: Boolean? = null,
    val hasRotation: Boolean? = null,
)

internal enum class PlayerPositionLogKind {
    LOCAL_MOVEMENT,
    LOCAL_NETWORK_MOVEMENT,
    LOCAL_JUMP,
    LOCAL_CORRECTION,
    LOCAL_ROTATION,
    LOCAL_TELEPORT_ACK,
    LOCAL_VEHICLE_MOVEMENT,
    LOCAL_EXPLOSION_KNOCKBACK,
    LOCAL_VELOCITY,
    REMOTE_SPAWN,
    REMOTE_MOVEMENT,
    REMOTE_TELEPORT,
    REMOTE_POSITION_SYNC,
    REMOTE_VELOCITY,
    REMOTE_HEAD_ROTATION,
    PLAYER_MOUNT_CHANGE,
    PLAYER_REMOVED,
    STATE_INITIAL,
    STATE_CHANGED,
    STATE_REMOVED,
    WORLD_CHANGED,
}

@JvmRecord
internal data class PlayerPositionPacketObservation(
    val kind: PlayerPositionLogKind,
    val packetState: PlayerPositionPacketState,
    val teleportId: Int? = null,
    val relatedEntityId: Int? = null,
)

internal object PlayerPositionPacketLogFactory {

    fun outgoingMovement(
        packet: ServerboundMovePlayerPacket,
        source: PlayerPositionState,
        lastTransmittedState: PlayerServerPositionState? = null,
    ): PlayerPositionPacketObservation {
        val fallbackPosition = lastTransmittedState?.position ?: source.position
        val fallbackRotation = lastTransmittedState?.rotation ?: source.rotation.packetRotation
        val suppliedPosition = packet.takeIf { it.hasPosition() }?.let {
            LoggedVector(it.x, it.y, it.z)
        }
        val suppliedRotation = packet.takeIf { it.hasRotation() }?.let {
            LoggedRotation(it.yRot, it.xRot)
        }
        val resolvedPosition = LoggedVector(
            packet.getX(fallbackPosition.x),
            packet.getY(fallbackPosition.y),
            packet.getZ(fallbackPosition.z),
        )
        val resolvedRotation = LoggedRotation(
            packet.getYRot(fallbackRotation.yaw),
            packet.getXRot(fallbackRotation.pitch),
        )

        return PlayerPositionPacketObservation(
            PlayerPositionLogKind.LOCAL_MOVEMENT,
            PlayerPositionPacketState(
                suppliedPosition = suppliedPosition,
                resolvedPosition = resolvedPosition,
                suppliedRotation = suppliedRotation,
                resolvedRotation = resolvedRotation,
                onGround = packet.isOnGround,
                horizontalCollision = packet.horizontalCollision(),
                hasPosition = packet.hasPosition(),
                hasRotation = packet.hasRotation(),
            ),
        )
    }

    fun localCorrection(
        packet: ClientboundPlayerPositionPacket,
        source: PlayerPositionState,
    ): PlayerPositionPacketObservation {
        val absolute = PositionMoveRotation.calculateAbsolute(
            source.toPositionMoveRotation(),
            packet.change,
            packet.relatives,
        )

        return positionChange(
            PlayerPositionLogKind.LOCAL_CORRECTION,
            packet.change,
            absolute,
            packet.relatives.map { it.name }.sorted(),
            teleportId = packet.id,
        )
    }

    fun remoteMovement(
        packet: ClientboundMoveEntityPacket,
        source: PlayerPositionState,
    ): PlayerPositionPacketObservation {
        val resolvedPosition = if (packet.hasPosition()) {
            VecDeltaCodec().apply { base = source.positionCodecBase.toVec3() }
                .decode(packet.xa.toLong(), packet.ya.toLong(), packet.za.toLong())
                .let(LoggedVector::from)
        } else {
            source.position
        }
        val resolvedRotation = if (packet.hasRotation()) {
            LoggedRotation(packet.yRot, packet.xRot)
        } else {
            source.rotation.packetRotation
        }

        return PlayerPositionPacketObservation(
            PlayerPositionLogKind.REMOTE_MOVEMENT,
            PlayerPositionPacketState(
                resolvedPosition = resolvedPosition,
                resolvedRotation = resolvedRotation,
                encodedDelta = LoggedEncodedVector(packet.xa.toInt(), packet.ya.toInt(), packet.za.toInt()),
                onGround = packet.isOnGround,
                hasPosition = packet.hasPosition(),
                hasRotation = packet.hasRotation(),
            ),
        )
    }

    fun remoteTeleport(
        packet: ClientboundTeleportEntityPacket,
        source: PlayerPositionState,
    ): PlayerPositionPacketObservation {
        val absolute = PositionMoveRotation.calculateAbsolute(
            source.toPositionMoveRotation(),
            packet.change,
            packet.relatives,
        )

        return positionChange(
            PlayerPositionLogKind.REMOTE_TELEPORT,
            packet.change,
            absolute,
            packet.relatives.map { it.name }.sorted(),
            relatedEntityId = packet.id,
            onGround = packet.onGround,
        )
    }

    fun localRotation(
        packet: ClientboundPlayerRotationPacket,
        source: PlayerPositionState,
    ): PlayerPositionPacketObservation {
        val resolvedYaw = if (packet.relativeY) source.rotation.yaw + packet.yRot else packet.yRot
        val resolvedPitch = if (packet.relativeX) source.rotation.pitch + packet.xRot else packet.xRot
        val relativeFlags = buildList {
            if (packet.relativeY) add("Y_ROT")
            if (packet.relativeX) add("X_ROT")
        }

        return PlayerPositionPacketObservation(
            PlayerPositionLogKind.LOCAL_ROTATION,
            PlayerPositionPacketState(
                resolvedPosition = source.position,
                suppliedRotation = LoggedRotation(packet.yRot, packet.xRot),
                resolvedRotation = LoggedRotation(resolvedYaw, resolvedPitch.coerceIn(-90f, 90f)),
                relativeFlags = relativeFlags,
                hasPosition = false,
                hasRotation = true,
            ),
        )
    }

    fun explosionKnockback(
        knockback: Vec3,
        source: PlayerPositionState,
    ) = PlayerPositionPacketObservation(
        PlayerPositionLogKind.LOCAL_EXPLOSION_KNOCKBACK,
        PlayerPositionPacketState(
            suppliedVelocity = LoggedVector.from(knockback),
            resolvedVelocity = LoggedVector.from(source.velocity.toVec3().add(knockback)),
        ),
    )

    fun remotePositionSync(
        packet: ClientboundEntityPositionSyncPacket,
    ) = positionChange(
        PlayerPositionLogKind.REMOTE_POSITION_SYNC,
        packet.values,
        packet.values,
        emptyList(),
        relatedEntityId = packet.id,
        onGround = packet.onGround,
    )

    fun velocity(
        packet: ClientboundSetEntityMotionPacket,
        local: Boolean,
    ) = PlayerPositionPacketObservation(
        if (local) PlayerPositionLogKind.LOCAL_VELOCITY else PlayerPositionLogKind.REMOTE_VELOCITY,
        PlayerPositionPacketState(
            suppliedVelocity = LoggedVector.from(packet.movement),
            resolvedVelocity = LoggedVector.from(packet.movement),
        ),
        relatedEntityId = packet.id,
    )

    private fun positionChange(
        kind: PlayerPositionLogKind,
        supplied: PositionMoveRotation,
        resolved: PositionMoveRotation,
        relativeFlags: List<String>,
        teleportId: Int? = null,
        relatedEntityId: Int? = null,
        onGround: Boolean? = null,
    ) = PlayerPositionPacketObservation(
        kind,
        PlayerPositionPacketState(
            suppliedPosition = LoggedVector.from(supplied.position),
            resolvedPosition = LoggedVector.from(resolved.position),
            suppliedVelocity = LoggedVector.from(supplied.deltaMovement),
            resolvedVelocity = LoggedVector.from(resolved.deltaMovement),
            suppliedRotation = LoggedRotation(supplied.yRot, supplied.xRot),
            resolvedRotation = LoggedRotation(resolved.yRot, resolved.xRot),
            relativeFlags = relativeFlags,
            onGround = onGround,
            hasPosition = true,
            hasRotation = true,
        ),
        teleportId,
        relatedEntityId,
    )
}

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

    fun remoteHeadRotation(
        packet: ClientboundRotateHeadPacket,
        source: PlayerPositionState,
        entityId: Int,
    ) = PlayerPositionPacketObservation(
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
