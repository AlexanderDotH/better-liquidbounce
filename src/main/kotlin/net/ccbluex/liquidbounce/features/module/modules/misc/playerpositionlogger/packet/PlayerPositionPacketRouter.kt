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
package net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.packet

import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionLogKind
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionPacketObservation
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionPacketState
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionSupplementalLogFactory
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerServerPositionState
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.capturePositionState

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket
import net.minecraft.network.protocol.game.ClientboundRespawnPacket
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket
import net.minecraft.world.entity.player.Player
import java.util.UUID

internal object PlayerPositionPacketRouter {

    fun route(
        packet: Packet<*>,
        level: ClientLevel,
        localPlayer: Player,
        lastTransmittedState: PlayerServerPositionState,
        playerName: (UUID) -> String?,
    ): List<RoutedPlayerPositionPacket> =
        routeOutgoing(packet, localPlayer, lastTransmittedState)
            ?: routeLocalIncoming(packet, localPlayer)
            ?: PlayerPositionRemotePacketRouter.route(packet, level, localPlayer, playerName)
            ?: emptyList()

    private fun routeOutgoing(
        packet: Packet<*>,
        localPlayer: Player,
        lastTransmittedState: PlayerServerPositionState,
    ): List<RoutedPlayerPositionPacket>? = when (packet) {
        is ServerboundMovePlayerPacket -> listOf(localPlayer.routedPositionPacket(
            localPlayer,
            PlayerPositionPacketLogFactory.outgoingMovement(
                packet,
                localPlayer.capturePositionState(),
                lastTransmittedState,
            ),
        ))

        is ServerboundMoveVehiclePacket -> listOf(localPlayer.routedPositionPacket(
            localPlayer,
            PlayerPositionSupplementalLogFactory.vehicleMovement(packet).copy(
                relatedEntityId = localPlayer.vehicle?.id,
            ),
        ))

        is ServerboundAcceptTeleportationPacket -> listOf(localPlayer.routedPositionPacket(
            localPlayer,
            PlayerPositionPacketObservation(
                PlayerPositionLogKind.LOCAL_TELEPORT_ACK,
                PlayerPositionPacketState(),
                teleportId = packet.id,
            ),
        ))

        else -> null
    }

    private fun routeLocalIncoming(
        packet: Packet<*>,
        localPlayer: Player,
    ): List<RoutedPlayerPositionPacket>? = when (packet) {
        is ClientboundPlayerPositionPacket -> listOf(localPlayer.routedPositionPacket(
            localPlayer,
            PlayerPositionPacketLogFactory.localCorrection(packet, localPlayer.capturePositionState()),
        ))

        is ClientboundPlayerRotationPacket -> listOf(localPlayer.routedPositionPacket(
            localPlayer,
            PlayerPositionPacketLogFactory.localRotation(packet, localPlayer.capturePositionState()),
        ))

        is ClientboundExplodePacket -> packet.playerKnockback.orElse(null)?.let { knockback ->
            listOf(localPlayer.routedPositionPacket(
                localPlayer,
                PlayerPositionPacketLogFactory.explosionKnockback(
                    knockback,
                    localPlayer.capturePositionState(),
                ),
            ))
        }.orEmpty()

        is ClientboundRespawnPacket -> listOf(localPlayer.routedPositionPacket(
            localPlayer,
            PlayerPositionPacketObservation(
                PlayerPositionLogKind.WORLD_CHANGED,
                PlayerPositionPacketState(),
            ),
        ))

        else -> null
    }

}
