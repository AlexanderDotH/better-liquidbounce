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
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.hypot

private const val WALK_SPEED_SMOOTHING = 0.4f
private const val DEFAULT_SWING_DURATION_TICKS = 6
private const val NANOS_PER_TICK = 50_000_000L

data class ServerPlayerModelSnapshot(
    val previousPosition: Vec3? = null,
    val position: Vec3? = null,
    val previousRotation: Rotation? = null,
    val rotation: Rotation? = null,
    val input: Input = Input.EMPTY,
    val onGround: Boolean = false,
    val horizontalCollision: Boolean = false,
    val selectedHotbarSlot: Int? = null,
    val activeUseHand: InteractionHand? = null,
    val useStartedAtNanos: Long? = null,
    val swingHand: InteractionHand? = null,
    val swingStartedAtNanos: Long? = null,
    val lastPositionTick: Long? = null,
    val previousWalkAnimationSpeed: Float = 0f,
    val walkAnimationSpeed: Float = 0f,
    val walkAnimationPosition: Float = 0f,
    internal val walkAnimationTick: Long = 0L,
    internal val walkAnimationDistance: Float = 0f,
) {
    val isInitialized: Boolean
        get() = position != null && rotation != null

    companion object {
        @JvmField
        val EMPTY = ServerPlayerModelSnapshot()
    }
}

/**
 * Last local-player state that reached Minecraft's connection write path.
 * It is an estimate of the server view until a correction packet arrives.
 */
object ServerPlayerModelStateTracker {

    private const val MAX_WALK_SPEED = 1f
    private const val WALK_DISTANCE_SCALE = 4f
    private const val USE_TIMEOUT_NANOS = 60_000_000_000L

    private val state = AtomicReference(ServerPlayerModelSnapshot.EMPTY)
    private val gameTick = AtomicLong()
    private val currentSwingDurationTicks = AtomicInteger(DEFAULT_SWING_DURATION_TICKS)

    @get:JvmStatic
    val snapshot: ServerPlayerModelSnapshot
        get() = state.get()

    @JvmStatic
    fun onGameTick() {
        val currentTick = gameTick.incrementAndGet()
        state.updateAndGet { current ->
            advanceWalkAnimationToTick(current, currentTick)
        }
    }

    @JvmStatic
    fun onPacketSent(packet: Packet<*>) {
        onPacketSent(packet, System.nanoTime())
    }

    internal fun onPacketSent(packet: Packet<*>, nowNanos: Long) {
        state.updateAndGet { current ->
            val normalized = advanceWalkAnimationToTick(expireUse(current, nowNanos), gameTick.get())
            reducePacket(normalized, packet, nowNanos, currentSwingDurationTicks.get())
        }
    }

    @JvmStatic
    fun correct(position: Vec3, yaw: Float, pitch: Float) {
        correct(position, yaw, pitch, System.nanoTime())
    }

    internal fun correct(position: Vec3, yaw: Float, pitch: Float, nowNanos: Long) {
        val rotation = Rotation(yaw, pitch)
        state.updateAndGet { current ->
            val normalized = advanceWalkAnimationToTick(expireUse(current, nowNanos), gameTick.get())
            normalized.copy(
                previousPosition = position,
                position = position,
                previousRotation = rotation,
                rotation = rotation,
                lastPositionTick = normalized.walkAnimationTick,
                previousWalkAnimationSpeed = 0f,
                walkAnimationSpeed = 0f,
                walkAnimationDistance = 0f,
            )
        }
    }

    @JvmStatic
    fun reset() {
        gameTick.set(0L)
        currentSwingDurationTicks.set(DEFAULT_SWING_DURATION_TICKS)
        state.set(ServerPlayerModelSnapshot.EMPTY)
    }

    fun snapshotForRender(nowNanos: Long, swingDurationTicks: Int): ServerPlayerModelSnapshot {
        currentSwingDurationTicks.set(swingDurationTicks.coerceAtLeast(1))
        val current = state.updateAndGet { current ->
            val normalized = advanceWalkAnimationToTick(current, gameTick.get())
            val withoutTimedOutUse = expireUse(normalized, nowNanos)
            val swingStartedAt = withoutTimedOutUse.swingStartedAtNanos
            if (swingStartedAt != null && nowNanos - swingStartedAt >= swingDurationTicks * NANOS_PER_TICK) {
                withoutTimedOutUse.copy(swingHand = null, swingStartedAtNanos = null)
            } else {
                withoutTimedOutUse
            }
        }
        return settleMovementForRender(current, gameTick.get())
    }

    private fun reducePacket(
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
        is ServerboundPlayerActionPacket -> if (
            packet.action == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
        ) {
            current.copy(activeUseHand = null, useStartedAtNanos = null)
        } else {
            current
        }
        is ServerboundSwingPacket -> reduceSwing(current, packet.hand, nowNanos, swingDurationTicks)
        else -> current
    }

    private fun reduceMovement(
        current: ServerPlayerModelSnapshot,
        packet: ServerboundMovePlayerPacket,
    ): ServerPlayerModelSnapshot {
        val nextPosition = if (packet.hasPosition()) {
            Vec3(
                packet.getX(current.position?.x ?: 0.0),
                packet.getY(current.position?.y ?: 0.0),
                packet.getZ(current.position?.z ?: 0.0),
            )
        } else {
            current.position
        }
        val nextRotation = if (packet.hasRotation()) {
            Rotation(
                packet.getYRot(current.rotation?.yaw ?: 0f),
                packet.getXRot(current.rotation?.pitch ?: 0f),
            )
        } else {
            current.rotation
        }

        if (!packet.hasPosition()) {
            val previousRotation = if (packet.hasRotation()) {
                current.rotation ?: nextRotation
            } else {
                current.previousRotation
            }
            return current.copy(
                previousRotation = previousRotation,
                rotation = nextRotation,
                onGround = packet.isOnGround,
                horizontalCollision = packet.horizontalCollision(),
            )
        }

        val previousPosition = current.position ?: nextPosition
        val packetDistance = if (previousPosition != null && nextPosition != null) {
            hypot(nextPosition.x - previousPosition.x, nextPosition.z - previousPosition.z)
        } else {
            0.0
        }
        val walkDistance = current.walkAnimationDistance + packetDistance.toFloat()
        val targetWalkSpeed = (walkDistance * WALK_DISTANCE_SCALE).coerceAtMost(MAX_WALK_SPEED)
        val walkSpeed = smoothWalkSpeed(current.previousWalkAnimationSpeed, targetWalkSpeed)
        val walkPositionAtTickStart = current.walkAnimationPosition - current.walkAnimationSpeed

        return current.copy(
            previousPosition = previousPosition,
            position = nextPosition,
            previousRotation = if (packet.hasRotation()) current.rotation ?: nextRotation else current.previousRotation,
            rotation = nextRotation,
            onGround = packet.isOnGround,
            horizontalCollision = packet.horizontalCollision(),
            lastPositionTick = current.walkAnimationTick,
            walkAnimationSpeed = walkSpeed,
            walkAnimationPosition = walkPositionAtTickStart + walkSpeed,
            walkAnimationDistance = walkDistance,
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

    private fun expireUse(current: ServerPlayerModelSnapshot, nowNanos: Long): ServerPlayerModelSnapshot {
        val startedAt = current.useStartedAtNanos ?: return current
        return if (nowNanos - startedAt >= USE_TIMEOUT_NANOS) {
            current.copy(activeUseHand = null, useStartedAtNanos = null)
        } else {
            current
        }
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
    if (startedAt != null && nowNanos - startedAt in 0L until restartAfterNanos) {
        return current
    }

    return current.copy(swingHand = hand, swingStartedAtNanos = nowNanos)
}

private fun advanceWalkAnimationToTick(
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

private fun settleMovementForRender(
    snapshot: ServerPlayerModelSnapshot,
    currentTick: Long,
): ServerPlayerModelSnapshot {
    val lastPositionTick = snapshot.lastPositionTick ?: return snapshot
    if (lastPositionTick >= currentTick) {
        return snapshot
    }

    return snapshot.copy(
        previousPosition = snapshot.position,
    )
}
