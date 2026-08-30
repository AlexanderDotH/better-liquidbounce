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

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes.outgoingMovementPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3

internal fun VanillaFlyNoFallRuntime.handleSafetyPacket(event: PacketEvent) {
    val packet = event.outgoingMovementPacket ?: return
    deliveryTracker.reassertGround(packet)
}

internal fun VanillaFlyNoFallRuntime.handleSegmentationPacket(event: PacketEvent) {
    val packet = event.outgoingMovementPacket ?: return
    if (event.isCancelled) return
    if (!eligible) {
        serverState.clear()
        return
    }
    if (deliveryTracker.reassertGround(packet)) return
    serverState.initialize(player.position(), player.fallDistance.toDouble())
    val serverPosition = serverState.position ?: return
    val target = Vec3(
        packet.getX(serverPosition.x),
        packet.getY(serverPosition.y),
        packet.getZ(serverPosition.z),
    )
    serverState.groundingPositions(
        target = target,
        safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE),
    ).forEach { groundingPosition ->
        if (!sendGroundPacket(groundingPosition)) {
            event.cancelEvent()
            return
        }
    }
}

internal fun VanillaFlyNoFallRuntime.handleFinalPacket(event: PacketEvent) {
    if (event.isServerCorrection()) {
        deliveryTracker.clear()
        serverState.invalidatePosition()
        return
    }
    val packet = event.outgoingMovementPacket ?: return
    if (VanillaFlyNoFall.confirmGroundPacketDelivery(deliveryTracker, packet, event.isCancelled)) {
        player.resetFallDistance()
    }
    if (!event.isCancelled) deliveredMovementPacketsThisTick++
    if (event.isCancelled || !eligible) return
    val serverPosition = serverState.position ?: return
    serverState.confirm(
        position = Vec3(
            packet.getX(serverPosition.x),
            packet.getY(serverPosition.y),
            packet.getZ(serverPosition.z),
        ),
        onGround = packet.isOnGround,
    )
}

private fun PacketEvent.isServerCorrection() = origin == TransferOrigin.INCOMING &&
    packet is ClientboundPlayerPositionPacket && !isCancelled
