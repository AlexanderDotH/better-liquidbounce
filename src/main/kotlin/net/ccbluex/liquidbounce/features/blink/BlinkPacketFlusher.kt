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
package net.ccbluex.liquidbounce.features.blink

import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.network.handlePacket
import net.ccbluex.liquidbounce.features.network.sendPacketSilently
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3
import java.util.Queue

internal fun flushBlinkPackets(
    packetQueue: Queue<PacketSnapshot>,
    flushWhen: (PacketSnapshot) -> Boolean,
) {
    packetQueue.removeIf { snapshot ->
        flushWhen(snapshot).also { flush -> if (flush) flushBlinkSnapshot(snapshot) }
    }
}

internal fun flushBlinkPacketCount(packetQueue: Queue<PacketSnapshot>, count: Int) {
    var movementPackets = 0
    with(packetQueue.iterator()) {
        while (hasNext()) {
            val snapshot = next()
            if (snapshot.packet is ServerboundMovePlayerPacket && snapshot.packet.hasPos) {
                movementPackets += 1
            }
            flushBlinkSnapshot(snapshot)
            remove()
            if (movementPackets >= count) break
        }
    }
}

internal fun cancelBlinkPackets(packetQueue: Queue<PacketSnapshot>, firstPosition: Vec3?) {
    firstPosition?.let(player::setPos)
    packetQueue.asSequence()
        .filterNot { it.packet is ServerboundMovePlayerPacket }
        .forEach(::flushBlinkSnapshot)
    packetQueue.clear()
}

private fun flushBlinkSnapshot(snapshot: PacketSnapshot) {
    when (snapshot.origin) {
        TransferOrigin.OUTGOING -> sendPacketSilently(snapshot.packet)
        TransferOrigin.INCOMING -> handlePacket(snapshot.packet)
    }
}
