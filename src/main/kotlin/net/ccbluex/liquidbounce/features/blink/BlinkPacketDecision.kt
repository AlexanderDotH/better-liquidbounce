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

import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket
import net.minecraft.network.protocol.game.ClientboundLoginPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundRespawnPacket
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket
import net.minecraft.network.protocol.game.ServerboundChatPacket
import net.minecraft.network.protocol.handshake.ClientIntentionPacket
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket
import net.minecraft.network.protocol.status.ServerboundStatusRequestPacket
import net.minecraft.sounds.SoundEvents

internal enum class BlinkPacketDecision {
    PASS,
    FLUSH,
    QUEUE,
}

internal fun blinkPacketDecision(packet: Packet<*>): BlinkPacketDecision = when (packet) {
    is ClientIntentionPacket,
    is ServerboundStatusRequestPacket,
    is ServerboundPingRequestPacket,
    is ServerboundChatPacket,
    is ClientboundSystemChatPacket,
    is ClientboundDisguisedChatPacket,
    is ServerboundChatCommandPacket,
    -> BlinkPacketDecision.PASS
    is ClientboundPlayerPositionPacket,
    is ClientboundDisconnectPacket,
    is ClientboundRespawnPacket,
    is ClientboundLoginPacket,
    -> BlinkPacketDecision.FLUSH
    is ClientboundSoundPacket if packet.sound.value() == SoundEvents.PLAYER_HURT -> BlinkPacketDecision.PASS
    is ClientboundSetHealthPacket if packet.health <= 0 -> BlinkPacketDecision.FLUSH
    else -> BlinkPacketDecision.QUEUE
}
