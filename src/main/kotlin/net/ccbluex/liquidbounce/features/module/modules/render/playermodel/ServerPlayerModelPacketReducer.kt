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

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.Vec3
import kotlin.math.hypot

private const val WALK_SPEED_SMOOTHING = 0.4f
private const val MAX_WALK_SPEED = 1f
private const val WALK_DISTANCE_SCALE = 4f
private const val USE_TIMEOUT_NANOS = 60_000_000_000L
private const val NANOS_PER_TICK = 50_000_000L

internal fun reduceServerPlayerModelPacket(
    current: ServerPlayerModelSnapshot,
    packet: Packet<*>,
    nowNanos: Long,
    swingDurationTicks: Int,
): ServerPlayerModelSnapshot = when (packet) {
    is ServerboundMovePlayerPacket -> reduceMovement(current, packet)
    is ServerboundPlayerInputPacket -> current.copy(input = packet.input())
    is ServerboundSetCarriedItemPacket -> current.copy(
        selectedHotbarSlot = packet.slot,
        activeUseHand = current.activeUseHand.takeUnless { it == InteractionHand.MAIN_HAND },
        useStartedAtNanos = current.useStartedAtNanos.takeUnless {
            current.activeUseHand == InteractionHand.MAIN_HAND
        },
    )
    is ServerboundUseItemPacket -> reduceUseItem(current, packet, nowNanos)
    is ServerboundPlayerActionPacket -> reducePlayerAction(current, packet)
    is ServerboundSwingPacket -> reduceSwing(current, packet.hand, nowNanos, swingDurationTicks)
    else -> current
}

private fun reducePlayerAction(
    current: ServerPlayerModelSnapshot,
    packet: ServerboundPlayerActionPacket,
): ServerPlayerModelSnapshot = if (packet.action == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
    current.copy(activeUseHand = null, useStartedAtNanos = null)
} else {
    current
}

private fun reduceMovement(
    current: ServerPlayerModelSnapshot,
    packet: ServerboundMovePlayerPacket,
): ServerPlayerModelSnapshot {
    val nextPosition = resolveMovementPosition(current, packet)
    val nextRotation = resolveMovementRotation(current, packet)

    if (!packet.hasPosition()) {
        return reduceRotationOnly(current, packet, nextRotation)
    }

    val previousPosition = current.position ?: nextPosition
    val walk = reduceWalkAnimation(current, previousPosition, nextPosition)

    return current.copy(
        previousPosition = previousPosition,
        position = nextPosition,
        previousRotation = if (packet.hasRotation()) current.rotation ?: nextRotation else current.previousRotation,
        rotation = nextRotation,
        onGround = packet.isOnGround,
        horizontalCollision = packet.horizontalCollision(),
        lastPositionTick = current.walkAnimationTick,
        walkAnimationSpeed = walk.speed,
        walkAnimationPosition = walk.position,
        walkAnimationDistance = walk.distance,
    )
}

private fun resolveMovementPosition(
    current: ServerPlayerModelSnapshot,
    packet: ServerboundMovePlayerPacket,
): Vec3? = if (packet.hasPosition()) {
    Vec3(
        packet.getX(current.position?.x ?: 0.0),
        packet.getY(current.position?.y ?: 0.0),
        packet.getZ(current.position?.z ?: 0.0),
    )
} else {
    current.position
}

private fun resolveMovementRotation(
    current: ServerPlayerModelSnapshot,
    packet: ServerboundMovePlayerPacket,
): Rotation? = if (packet.hasRotation()) {
    Rotation(
        packet.getYRot(current.rotation?.yaw ?: 0f),
        packet.getXRot(current.rotation?.pitch ?: 0f),
    )
} else {
    current.rotation
}

private fun reduceWalkAnimation(
    current: ServerPlayerModelSnapshot,
    previousPosition: Vec3?,
    nextPosition: Vec3?,
): WalkAnimationReduction {
    val packetDistance = if (previousPosition != null && nextPosition != null) {
        hypot(nextPosition.x - previousPosition.x, nextPosition.z - previousPosition.z)
    } else {
        0.0
    }
    val distance = current.walkAnimationDistance + packetDistance.toFloat()
    val targetSpeed = (distance * WALK_DISTANCE_SCALE).coerceAtMost(MAX_WALK_SPEED)
    val speed = smoothWalkSpeed(current.previousWalkAnimationSpeed, targetSpeed)
    val positionAtTickStart = current.walkAnimationPosition - current.walkAnimationSpeed
    return WalkAnimationReduction(speed, positionAtTickStart + speed, distance)
}

private data class WalkAnimationReduction(
    val speed: Float,
    val position: Float,
    val distance: Float,
)

private fun reduceRotationOnly(
    current: ServerPlayerModelSnapshot,
    packet: ServerboundMovePlayerPacket,
    nextRotation: Rotation?,
): ServerPlayerModelSnapshot {
    val previousRotation = if (packet.hasRotation()) current.rotation ?: nextRotation else current.previousRotation
    return current.copy(
        previousRotation = previousRotation,
        rotation = nextRotation,
        onGround = packet.isOnGround,
        horizontalCollision = packet.horizontalCollision(),
    )
}

private fun reduceUseItem(
    current: ServerPlayerModelSnapshot,
    packet: ServerboundUseItemPacket,
    nowNanos: Long,
): ServerPlayerModelSnapshot {
    val rotation = Rotation(packet.yRot, packet.xRot)
    return current.copy(
        previousRotation = current.rotation ?: rotation,
        rotation = rotation,
        activeUseHand = packet.hand,
        useStartedAtNanos = nowNanos,
    )
}

internal fun expireServerPlayerUse(
    current: ServerPlayerModelSnapshot,
    nowNanos: Long,
): ServerPlayerModelSnapshot {
    val startedAt = current.useStartedAtNanos ?: return current
    return if (nowNanos - startedAt >= USE_TIMEOUT_NANOS) {
        current.copy(activeUseHand = null, useStartedAtNanos = null)
    } else {
        current
    }
}

private fun reduceSwing(
    current: ServerPlayerModelSnapshot,
    hand: InteractionHand,
    nowNanos: Long,
    swingDurationTicks: Int,
): ServerPlayerModelSnapshot {
    val startedAt = current.swingStartedAtNanos
    val restartAfterNanos = swingDurationTicks.coerceAtLeast(1) / 2 * NANOS_PER_TICK
    if (startedAt != null && nowNanos - startedAt in 0L until restartAfterNanos) return current
    return current.copy(swingHand = hand, swingStartedAtNanos = nowNanos)
}

internal fun advanceServerPlayerWalkAnimation(
    snapshot: ServerPlayerModelSnapshot,
    currentTick: Long,
): ServerPlayerModelSnapshot {
    var advanced = snapshot
    while (advanced.walkAnimationTick < currentTick) {
        val walkSpeed = smoothWalkSpeed(advanced.walkAnimationSpeed, 0f)
        advanced = advanced.copy(
            previousWalkAnimationSpeed = advanced.walkAnimationSpeed,
            walkAnimationSpeed = walkSpeed,
            walkAnimationPosition = advanced.walkAnimationPosition + walkSpeed,
            walkAnimationTick = advanced.walkAnimationTick + 1L,
            walkAnimationDistance = 0f,
        )
    }
    return advanced
}

private fun smoothWalkSpeed(current: Float, target: Float): Float =
    current + (target - current) * WALK_SPEED_SMOOTHING

internal fun settleServerPlayerMovementForRender(
    snapshot: ServerPlayerModelSnapshot,
    currentTick: Long,
): ServerPlayerModelSnapshot {
    val lastPositionTick = snapshot.lastPositionTick ?: return snapshot
    if (lastPositionTick >= currentTick) return snapshot
    return snapshot.copy(previousPosition = snapshot.position)
}
