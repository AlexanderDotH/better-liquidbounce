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
package net.ccbluex.liquidbounce.features.module.modules.movement.vclip

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import java.util.Collections
import java.util.IdentityHashMap

internal data class VClipPacketDelivery(
    val requiredOnGround: Boolean,
)

/**
 * Owns the final ground state of V-Clip movement packets through the outgoing packet pipeline.
 */
internal class VClipPacketDeliveryTracker {

    private val pendingPackets = Collections.synchronizedMap(
        IdentityHashMap<ServerboundMovePlayerPacket, Boolean>()
    )
    private val deliveredPackets = Collections.synchronizedMap(
        IdentityHashMap<ServerboundMovePlayerPacket, VClipPacketDelivery>()
    )

    internal val pendingCount: Int
        get() = pendingPackets.size

    fun protect(packet: ServerboundMovePlayerPacket, requiredOnGround: Boolean = packet.onGround) {
        deliveredPackets.remove(packet)
        pendingPackets[packet] = requiredOnGround
        packet.onGround = requiredOnGround
    }

    fun reassertRequiredState(packet: ServerboundMovePlayerPacket): Boolean {
        val requiredOnGround = pendingPackets[packet] ?: return false
        packet.onGround = requiredOnGround
        return true
    }

    fun confirmFinalState(packet: ServerboundMovePlayerPacket, cancelled: Boolean): VClipPacketDelivery? {
        val requiredOnGround = pendingPackets.remove(packet) ?: return null
        packet.onGround = requiredOnGround
        if (cancelled) {
            return null
        }

        return VClipPacketDelivery(requiredOnGround).also { delivery ->
            deliveredPackets[packet] = delivery
        }
    }

    fun finalizeProtectedMovement(event: PacketEvent) {
        val packet = event.packet as? ServerboundMovePlayerPacket ?: return
        if (!reassertRequiredState(packet)) return
        confirmFinalState(packet, event.isCancelled)
    }

    fun takeDelivery(packet: ServerboundMovePlayerPacket): VClipPacketDelivery? {
        return deliveredPackets.remove(packet)
    }

    fun discard(packet: ServerboundMovePlayerPacket) {
        pendingPackets.remove(packet)
        deliveredPackets.remove(packet)
    }

    fun clear() {
        pendingPackets.clear()
        deliveredPackets.clear()
    }
}
