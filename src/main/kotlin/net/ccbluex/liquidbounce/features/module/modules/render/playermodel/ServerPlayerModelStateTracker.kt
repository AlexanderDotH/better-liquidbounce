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
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
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
            advanceServerPlayerWalkAnimation(current, currentTick)
        }
    }

    @JvmStatic
    fun onPacketSent(packet: Packet<*>) {
        onPacketSent(packet, System.nanoTime())
    }

    internal fun onPacketSent(packet: Packet<*>, nowNanos: Long) {
        state.updateAndGet { current ->
            val normalized = advanceServerPlayerWalkAnimation(
                expireServerPlayerUse(current, nowNanos),
                gameTick.get(),
            )
            reduceServerPlayerModelPacket(normalized, packet, nowNanos, currentSwingDurationTicks.get())
        }
    }

    @JvmStatic
    fun correct(position: Vec3, yaw: Float, pitch: Float) {
        correct(position, yaw, pitch, System.nanoTime())
    }

    internal fun correct(position: Vec3, yaw: Float, pitch: Float, nowNanos: Long) {
        val rotation = Rotation(yaw, pitch)
        state.updateAndGet { current ->
            val normalized = advanceServerPlayerWalkAnimation(
                expireServerPlayerUse(current, nowNanos),
                gameTick.get(),
            )
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
            val normalized = advanceServerPlayerWalkAnimation(current, gameTick.get())
            val withoutTimedOutUse = expireServerPlayerUse(normalized, nowNanos)
            val swingStartedAt = withoutTimedOutUse.swingStartedAtNanos
            if (swingStartedAt != null && nowNanos - swingStartedAt >= swingDurationTicks * NANOS_PER_TICK) {
                withoutTimedOutUse.copy(swingHand = null, swingStartedAtNanos = null)
            } else {
                withoutTimedOutUse
            }
        }
        return settleServerPlayerMovementForRender(current, gameTick.get())
    }
}
