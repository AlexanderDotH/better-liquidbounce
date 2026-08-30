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

@JvmRecord
internal data class PlayerPositionIdentity(
    val entityId: Int,
    val uuid: String,
    val name: String,
    val local: Boolean,
)

@JvmRecord
internal data class PlayerPositionSample(
    val identity: PlayerPositionIdentity,
    val state: PlayerPositionState,
)

@JvmRecord
internal data class PlayerPositionStateChange(
    val kind: PlayerPositionLogKind,
    val sample: PlayerPositionSample,
    val previousState: PlayerPositionState? = null,
)
