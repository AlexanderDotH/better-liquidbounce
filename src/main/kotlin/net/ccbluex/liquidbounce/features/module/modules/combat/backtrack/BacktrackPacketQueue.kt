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
package net.ccbluex.liquidbounce.features.module.modules.combat.backtrack

import net.ccbluex.liquidbounce.event.events.BlinkPacketAction
import net.ccbluex.liquidbounce.event.events.BlinkPacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.features.blink.TrackedEntityPosition
import net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode.VelocityReduce
import net.ccbluex.liquidbounce.utils.client.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.entity.squareBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket
import net.minecraft.network.protocol.game.ServerboundChatPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.Entity

internal class BacktrackPacketQueue(
    private val shouldCancelPackets: () -> Boolean,
    private val hasQueuedIncoming: () -> Boolean,
    private val trackedTarget: () -> Entity?,
    private val position: TrackedEntityPosition,
    private val clear: () -> Unit,
) : MinecraftShortcuts {

    fun handle(event: BlinkPacketEvent) {
        if (event.origin != TransferOrigin.INCOMING || VelocityReduce.ownsIncomingBlinkQueue) return

        val packet = event.packet
        val shouldCancel = shouldCancelPackets()
        val hasQueued = hasQueuedIncoming()
        if (packet == null) {
            if (shouldCancel || hasQueued) event.action = BlinkPacketAction.PASS
            return
        }
        if (!hasQueued && !shouldCancel) return

        when (backtrackPacketDisposition(packet)) {
            BacktrackPacketDisposition.PASS -> {
                event.action = BlinkPacketAction.PASS
                return
            }
            BacktrackPacketDisposition.CLEAR -> {
                clear()
                return
            }
            BacktrackPacketDisposition.TRACK -> Unit
        }

        updateTrackedPosition(packet, event)
    }

    private fun updateTrackedPosition(packet: Packet<*>, event: BlinkPacketEvent) {
        val trackedTarget = trackedTarget() ?: return
        val trackedPosition = position.handlePacket(packet, world, trackedTarget)
        if (trackedPosition != null &&
            trackedTarget.squareBoxedDistanceTo(player, trackedPosition) <
            trackedTarget.squaredBoxedDistanceTo(player)
        ) {
            event.action = BlinkPacketAction.FLUSH
            return
        }
        event.action = BlinkPacketAction.QUEUE
    }
}

private enum class BacktrackPacketDisposition {
    PASS,
    CLEAR,
    TRACK,
}

private fun backtrackPacketDisposition(packet: Packet<*>): BacktrackPacketDisposition = when (packet) {
    is ServerboundChatPacket,
    is ClientboundSystemChatPacket,
    is ServerboundChatCommandPacket -> BacktrackPacketDisposition.PASS

    is ClientboundPlayerPositionPacket,
    is ClientboundDisconnectPacket -> BacktrackPacketDisposition.CLEAR

    is ClientboundSoundPacket -> if (packet.sound.value() == SoundEvents.PLAYER_HURT) {
        BacktrackPacketDisposition.PASS
    } else {
        BacktrackPacketDisposition.TRACK
    }

    is ClientboundSetHealthPacket -> if (packet.health <= 0) {
        BacktrackPacketDisposition.CLEAR
    } else {
        BacktrackPacketDisposition.TRACK
    }

    else -> BacktrackPacketDisposition.TRACK
}
