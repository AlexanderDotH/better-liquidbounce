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
package net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Tracks movement packets protected by NoFall through the complete [PacketEvent] pipeline. Identity semantics ensure
 * that another movement packet cannot confirm or consume a pending protection attempt.
 */
internal class GroundPacketDeliveryTracker {

    private val pendingPackets = Collections.synchronizedMap(
        IdentityHashMap<ServerboundMovePlayerPacket, Unit>()
    )

    internal val pendingCount: Int
        get() = pendingPackets.size

    fun protect(packet: ServerboundMovePlayerPacket) {
        pendingPackets[packet] = Unit
        packet.onGround = true
    }

    fun reassertGround(packet: ServerboundMovePlayerPacket): Boolean {
        if (!pendingPackets.containsKey(packet)) {
            return false
        }

        packet.onGround = true
        return true
    }

    fun confirmFinalState(packet: ServerboundMovePlayerPacket, cancelled: Boolean): Boolean {
        if (pendingPackets.remove(packet) == null) {
            return false
        }

        return !cancelled && packet.onGround
    }

    fun discard(packet: ServerboundMovePlayerPacket) {
        pendingPackets.remove(packet)
    }

    fun clear() {
        pendingPackets.clear()
    }
}

internal val PacketEvent.outgoingMovementPacket: ServerboundMovePlayerPacket?
    get() = packet.takeIf { origin == TransferOrigin.OUTGOING } as? ServerboundMovePlayerPacket
