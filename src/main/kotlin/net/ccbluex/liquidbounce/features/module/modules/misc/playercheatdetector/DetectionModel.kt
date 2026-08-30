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
package net.ccbluex.liquidbounce.features.module.modules.misc.playercheatdetector

import net.ccbluex.liquidbounce.common.Tagged
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.sqrt

enum class PlayerCheatCheck(override val tag: String) : Tagged {
    MOVEMENT("Movement"),
    VELOCITY("Velocity"),
    REACH("Reach"),
    SCAFFOLD("Scaffold"),
    BREAKING("Breaking"),
}

enum class DetectorStrictness(
    override val tag: String,
    val horizontalLimit: Double,
    val upwardLimit: Double,
    val hoverTicks: Int,
    val reachLimit: Double,
    val placeLimit: Double,
    val notificationViolationLevel: Double,
) : Tagged {
    CONSERVATIVE("Conservative", 1.05, 0.90, 8, 4.40, 6.20, 2.4),
    NORMAL("Normal", 0.85, 0.75, 6, 3.90, 5.45, 2.0),
    STRICT("Strict", 0.70, 0.65, 4, 3.45, 4.95, 1.6),
}

enum class DetectionCapability {
    SUPPORTED,
    DEGRADED_OBSERVER,
    UNSUPPORTED_NO_SIGNAL,
}

enum class DetectionSeverity {
    INFO,
    ERROR,
}

enum class ObservedActionType {
    VELOCITY,
    SWING,
    DAMAGE,
    BLOCK_PLACE,
    BLOCK_BREAK,
}

data class ObservedMovementFrame(
    val playerId: UUID,
    val playerName: String,
    val entityId: Int,
    val tick: Int,
    val position: Vec3,
    val previousPosition: Vec3?,
    val delta: Vec3,
    val boundingBox: AABB,
    val eyeY: Double,
    val yaw: Float,
    val pitch: Float,
    val onGround: Boolean,
    val nearGround: Boolean,
    val inFluid: Boolean,
    val swimming: Boolean,
    val fallFlying: Boolean,
    val passenger: Boolean,
    val sprinting: Boolean,
    val crouching: Boolean,
    val hurtTime: Int,
    val swingTime: Int,
    val teleportLike: Boolean,
) {
    val horizontalSpeed: Double
        get() = sqrt(delta.x * delta.x + delta.z * delta.z)

    val exemptFromMovementChecks: Boolean
        get() = teleportLike || passenger || fallFlying || inFluid || swimming

    val eyePosition: Vec3
        get() = Vec3(position.x, eyeY, position.z)
}

data class ObservedActionFrame(
    val playerId: UUID,
    val playerName: String,
    val entityId: Int,
    val tick: Int,
    val type: ObservedActionType,
    val position: Vec3,
    val eyeY: Double,
    val vector: Vec3? = null,
    val targetId: UUID? = null,
    val targetName: String? = null,
    val targetBoundingBox: AABB? = null,
    val targetPosition: Vec3? = null,
    val blockPos: BlockPos? = null,
) {
    val eyePosition: Vec3
        get() = Vec3(position.x, eyeY, position.z)
}

data class DetectionFlag(
    val playerId: UUID,
    val playerName: String,
    val check: PlayerCheatCheck,
    val checkName: String,
    val sourceStableKey: String?,
    val confidence: Int,
    val severity: DetectionSeverity,
    val verbose: String,
    val observedAtTick: Int,
)

data class DetectionNotice(
    val flag: DetectionFlag,
    val violationLevel: Double,
)
