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
 */

package net.ccbluex.liquidbounce.features.network

import net.ccbluex.liquidbounce.common.runtime.SilentPacketObservationHooks
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.SilentPacketSendEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.network.protocol.Packet

fun sendPacketSilently(packet: Packet<*>, bypassSilentPacketEvent: Boolean = false) {
    if (!bypassSilentPacketEvent && EventManager.callEvent(SilentPacketSendEvent(packet)).isCancelled) {
        return
    }

    val packetEvent = PacketEvent(TransferOrigin.OUTGOING, packet, false)
    RotationManager.trackPacket(packetEvent.packet, incoming = false, cancelled = packetEvent.isCancelled)
    SilentPacketObservationHooks.observe(packetEvent.packet)
    mc.connection?.connection?.send(packetEvent.packet, null)
}
