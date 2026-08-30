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

import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes.GroundPacketDeliveryTracker
import net.ccbluex.liquidbounce.utils.math.anyNotEmpty
import net.ccbluex.liquidbounce.utils.movement.remote.RemoteMovementOwnership
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3

internal class VanillaFlyNoFallRuntime(
    private val enabled: () -> Boolean,
) : MinecraftShortcuts {
    internal val deliveryTracker = GroundPacketDeliveryTracker()
    internal val serverState = VanillaFlyServerFallState()
    internal var deliveredMovementPacketsThisTick = 0

    fun beginTick() {
        deliveredMovementPacketsThisTick = 0
    }

    fun clear() {
        deliveryTracker.clear()
        serverState.clear()
        deliveredMovementPacketsThisTick = 0
    }

    fun isTrackedGroundPacket(packet: ServerboundMovePlayerPacket) = deliveryTracker.reassertGround(packet)

    fun forecastPacketCount(target: Vec3): Int {
        if (!eligible) return 0
        serverState.initialize(player.position(), player.fallDistance.toDouble())
        return serverState.groundingPositions(
            target = target,
            safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE),
        ).size
    }

    fun run() {
        if (!eligible) {
            deliveryTracker.clear()
            serverState.clear()
            return
        }
        serverState.initialize(player.position(), player.fallDistance.toDouble())
        val safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE)
        when (resolveAction(safeFallDistance)) {
            VanillaFlyNoFallAction.NONE -> Unit
            VanillaFlyNoFallAction.GROUND_PACKET -> sendGroundPacket()
            VanillaFlyNoFallAction.PACKET_JUMP -> sendPacketJump()
        }
    }

    internal val eligible
        get() = VanillaFlyNoFall.shouldRun(
            enabled = enabled(),
            fallDamagePossible = !player.isCreative && !player.isSpectator &&
                !player.abilities.invulnerable && !player.abilities.flying,
            remoteKillPacketRouteActive = RemoteMovementOwnership.active,
        )

    internal fun sendGroundPacket(position: Vec3? = null): Boolean {
        val groundPosition = position ?: serverState.position
        val packet = VanillaFlyNoFall.packetType.generatePacket().apply {
            groundPosition ?: return@apply
            x = groundPosition.x
            y = groundPosition.y
            z = groundPosition.z
        }
        VanillaFlyNoFall.sendProtectedGroundPacket(deliveryTracker, packet) { network.send(it) }
        return groundPosition == null || serverState.position == groundPosition && serverState.fallDistance == 0.0
    }

    private fun resolveAction(safeFallDistance: Double) = VanillaFlyNoFall.resolveAction(
        eligible = true,
        nearGround = isGroundNearby(),
        groundPacketDue = VanillaFlyNoFall.shouldSendGroundPacket(
            fallDistance = player.fallDistance.toDouble(),
            verticalMovement = player.deltaMovement.y,
            safeFallDistance = safeFallDistance,
            tickCount = player.tickCount,
        ),
        packetJumpDue = VanillaFlyNoFall.shouldSendPacketJump(
            onGround = player.onGround(),
            fallDistance = player.fallDistance.toDouble(),
            safeFallDistance = safeFallDistance,
        ),
    )

    private fun isGroundNearby(): Boolean {
        if (player.onGround()) return true
        val probeBox = VanillaFlyNoFall.groundProbeBox(player.boundingBox)
        val minimum = BlockPos.containing(probeBox.minX, probeBox.minY, probeBox.minZ)
        val maximum = BlockPos.containing(probeBox.maxX, probeBox.maxY, probeBox.maxZ)
        if (!world.hasChunksAt(minimum, maximum)) return false
        return world.getBlockCollisions(player, probeBox).anyNotEmpty()
    }

    private fun sendPacketJump() {
        network.send(VanillaFlyNoFall.packetType.generatePacket().apply(VanillaFlyNoFall::applyPacketJump))
        player.resetFallDistance()
    }
}
