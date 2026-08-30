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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes

import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes.GroundPacketDeliveryTracker
import net.ccbluex.liquidbounce.utils.network.MovePacketType
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

private const val VANILLA_CHECK_BYPASS_INTERVAL = 40
internal const val VANILLA_CHECK_BYPASS_Y_OFFSET = 0.04

internal fun shouldRunVanillaFlyCheckBypass(enabled: Boolean, tickCount: Int) =
    enabled && tickCount % VANILLA_CHECK_BYPASS_INTERVAL == 0

internal fun vanillaFlyCheckBypassY(currentY: Double) = currentY - VANILLA_CHECK_BYPASS_Y_OFFSET

internal fun applyVanillaFlyCheckBypass(packet: ServerboundMovePlayerPacket, currentY: Double) {
    packet.y = vanillaFlyCheckBypassY(currentY)
}

internal enum class VanillaFlyCheckBypassMode(override val tag: String) : Tagged {
    MOTION("Motion"),
    PACKET("Packet"),
}

internal fun resolveVanillaFlyCheckBypassMode(
    configuredMode: VanillaFlyCheckBypassMode,
    isFallFlying: Boolean,
) = if (isFallFlying) VanillaFlyCheckBypassMode.PACKET else configuredMode

internal fun shouldSendVanillaFlyPacketBypass(
    eventState: EventState,
    enabled: Boolean,
    tickCount: Int,
    configuredMode: VanillaFlyCheckBypassMode,
    isFallFlying: Boolean,
    movementSuspended: Boolean = false,
) = !movementSuspended &&
    eventState == EventState.POST &&
    shouldRunVanillaFlyCheckBypass(enabled, tickCount) &&
    resolveVanillaFlyCheckBypassMode(configuredMode, isFallFlying) == VanillaFlyCheckBypassMode.PACKET

internal fun resolveVanillaFlyElytraVerticalMotion(
    isFallFlying: Boolean,
    movementY: Double,
    requestedVerticalMotion: Double,
) = if (isFallFlying && movementY < 0.0) requestedVerticalMotion else movementY

internal fun shouldSuppressVanillaFlyServerSneak(input: Input) = input.shift && !input.jump

internal enum class VanillaFlyNoFallAction {
    NONE,
    GROUND_PACKET,
    PACKET_JUMP,
}

internal object VanillaFlyNoFall {
    private const val GROUND_PROBE_DEPTH = 10.0
    private const val GROUND_PROBE_EPSILON = 1.0E-7
    private const val PACKET_JUMP_Y_OFFSET = 1.0E-9
    private const val SERVER_FALL_DISTANCE_MARGIN = 0.25

    val packetType = MovePacketType.FULL

    fun shouldRun(
        enabled: Boolean,
        fallDamagePossible: Boolean,
        remoteKillPacketRouteActive: Boolean,
    ) = enabled && fallDamagePossible && !remoteKillPacketRouteActive

    fun shouldSendGroundPacket(
        fallDistance: Double,
        verticalMovement: Double,
        safeFallDistance: Double,
        tickCount: Int,
    ) = tickCount > 20 && fallDistance - verticalMovement > safeFallDistance

    fun shouldSendPacketJump(
        onGround: Boolean,
        fallDistance: Double,
        safeFallDistance: Double,
    ) = !onGround && fallDistance > safeFallDistance

    fun maximumSafeServerFallDistance(safeFallDistance: Double) =
        (safeFallDistance - SERVER_FALL_DISTANCE_MARGIN).coerceAtLeast(0.0)

    fun resolveAction(
        eligible: Boolean,
        nearGround: Boolean,
        groundPacketDue: Boolean,
        packetJumpDue: Boolean,
    ) = when {
        !eligible -> VanillaFlyNoFallAction.NONE
        nearGround && groundPacketDue -> VanillaFlyNoFallAction.GROUND_PACKET
        !nearGround && packetJumpDue -> VanillaFlyNoFallAction.PACKET_JUMP
        else -> VanillaFlyNoFallAction.NONE
    }

    fun groundProbeBox(playerBoundingBox: AABB) = AABB(
        playerBoundingBox.minX,
        playerBoundingBox.minY - GROUND_PROBE_DEPTH - GROUND_PROBE_EPSILON,
        playerBoundingBox.minZ,
        playerBoundingBox.maxX,
        playerBoundingBox.minY,
        playerBoundingBox.maxZ,
    )

    fun applyPacketJump(packet: ServerboundMovePlayerPacket) {
        packet.y += PACKET_JUMP_Y_OFFSET
    }

    inline fun sendProtectedGroundPacket(
        tracker: GroundPacketDeliveryTracker,
        packet: ServerboundMovePlayerPacket,
        send: (ServerboundMovePlayerPacket) -> Unit,
    ) {
        tracker.protect(packet)
        try {
            send(packet)
        } finally {
            tracker.discard(packet)
        }
    }

    fun confirmGroundPacketDelivery(
        tracker: GroundPacketDeliveryTracker,
        packet: ServerboundMovePlayerPacket,
        cancelled: Boolean,
    ) = tracker.confirmFinalState(packet, cancelled)
}

/**
 * Mirrors the server's last confirmed movement position and accumulated downward distance. A fast client-side
 * descent can then be represented by several safe grounded packets without changing the requested endpoint.
 */
internal class VanillaFlyServerFallState {

    var position: Vec3? = null
        private set

    var fallDistance = 0.0
        private set

    fun initialize(position: Vec3, fallDistance: Double) {
        if (this.position != null || !position.isFinite || !fallDistance.isFinite()) {
            return
        }

        this.position = position
        this.fallDistance = maxOf(this.fallDistance, fallDistance.coerceAtLeast(0.0))
    }

    fun groundingPositions(target: Vec3, safeFallDistance: Double): List<Vec3> {
        val start = position ?: return emptyList()
        if (!target.isFinite || !safeFallDistance.isFinite()) {
            return emptyList()
        }

        val totalDescent = start.y - target.y
        val maximumSafeDescent = VanillaFlyNoFall.maximumSafeServerFallDistance(safeFallDistance)
        if (totalDescent <= 0.0 || maximumSafeDescent <= 0.0) {
            return emptyList()
        }

        val groundingPositions = mutableListOf<Vec3>()
        var accumulatedDescent = 0.0
        var currentFallDistance = fallDistance
        while (currentFallDistance + totalDescent - accumulatedDescent > maximumSafeDescent) {
            accumulatedDescent += (maximumSafeDescent - currentFallDistance).coerceAtLeast(0.0)
            groundingPositions += start.lerp(target, accumulatedDescent / totalDescent)
            currentFallDistance = 0.0
        }

        return groundingPositions
    }

    fun confirm(position: Vec3, onGround: Boolean) {
        if (!position.isFinite) {
            clear()
            return
        }

        this.position?.let { previousPosition ->
            fallDistance += (previousPosition.y - position.y).coerceAtLeast(0.0)
        }
        if (onGround) {
            fallDistance = 0.0
        }
        this.position = position
    }

    fun invalidatePosition() {
        position = null
    }

    fun clear() {
        position = null
        fallDistance = 0.0
    }
}

internal inline fun applyVanillaFlyElytraVerticalMotion(
    event: PlayerMoveEvent,
    isFallFlying: Boolean,
    requestedVerticalMotion: Double,
    setVelocityY: (Double) -> Unit,
) {
    if (!isFallFlying || event.movement.y >= 0.0) {
        return
    }

    val resolvedMotion = resolveVanillaFlyElytraVerticalMotion(
        isFallFlying = true,
        movementY = event.movement.y,
        requestedVerticalMotion = requestedVerticalMotion,
    )
    event.movement.y = resolvedMotion
    setVelocityY(resolvedMotion)
}
