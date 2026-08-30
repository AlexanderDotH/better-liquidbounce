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

import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionIdentity
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionLogKind
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionPacketObservation
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionPacketState
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionSupplementalLogFactory
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.capturePositionState
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.player.Player
import java.util.UUID

internal object PlayerPositionRemotePacketRouter {

    fun route(
        packet: Packet<*>,
        level: ClientLevel,
        localPlayer: Player,
        playerName: (UUID) -> String?,
    ): List<RoutedPlayerPositionPacket>? = routeMovement(packet, level, localPlayer)
        ?: routeLifecycle(packet, level, localPlayer, playerName)

    private fun routeMovement(
        packet: Packet<*>,
        level: ClientLevel,
        localPlayer: Player,
    ): List<RoutedPlayerPositionPacket>? = when (packet) {
        is ClientboundMoveEntityPacket -> packet.player(level)?.let { target ->
            target.routed(PlayerPositionPacketLogFactory.remoteMovement(packet, target.capturePositionState()), localPlayer)
        }.orEmpty()
        is ClientboundTeleportEntityPacket -> level.player(packet.id)?.let { target ->
            target.routed(PlayerPositionPacketLogFactory.remoteTeleport(packet, target.capturePositionState()), localPlayer)
        }.orEmpty()
        is ClientboundEntityPositionSyncPacket -> level.player(packet.id)?.let { target ->
            target.routed(PlayerPositionPacketLogFactory.remotePositionSync(packet), localPlayer)
        }.orEmpty()
        is ClientboundSetEntityMotionPacket -> level.player(packet.id)?.let { target ->
            target.routed(PlayerPositionPacketLogFactory.velocity(packet, target === localPlayer), localPlayer)
        }.orEmpty()
        is ClientboundRotateHeadPacket -> packet.player(level)?.let { target ->
            target.routed(
                PlayerPositionSupplementalLogFactory.remoteHeadRotation(packet, target.capturePositionState(), target.id),
                localPlayer,
            )
        }.orEmpty()
        else -> null
    }

    private fun routeLifecycle(
        packet: Packet<*>,
        level: ClientLevel,
        localPlayer: Player,
        playerName: (UUID) -> String?,
    ): List<RoutedPlayerPositionPacket>? = when (packet) {
        is ClientboundAddEntityPacket -> if (packet.type == EntityTypes.PLAYER) {
            listOf(spawn(packet, playerName(packet.uuid)))
        } else {
            emptyList()
        }
        is ClientboundSetPassengersPacket -> passengers(packet, level, localPlayer)
        is ClientboundRemoveEntitiesPacket -> removedPlayers(packet, level, localPlayer)
        else -> null
    }

    private fun spawn(packet: ClientboundAddEntityPacket, name: String?) = RoutedPlayerPositionPacket(
        identity = PlayerPositionIdentity(packet.id, packet.uuid.toString(), name ?: packet.uuid.toString(), false),
        clientState = null,
        observation = PlayerPositionSupplementalLogFactory.remoteSpawn(packet),
    )

    private fun passengers(
        packet: ClientboundSetPassengersPacket,
        level: ClientLevel,
        localPlayer: Player,
    ): List<RoutedPlayerPositionPacket> {
        val passengerIds = packet.passengers.toList()
        return buildList {
            level.player(packet.vehicle)?.let(::add)
            passengerIds.mapNotNullTo(this) { level.player(it) }
        }.distinctBy(Player::getId).map { affected ->
            affected.routedPositionPacket(
                localPlayer,
                PlayerPositionPacketObservation(
                    PlayerPositionLogKind.PLAYER_MOUNT_CHANGE,
                    PlayerPositionPacketState(),
                    relatedEntityId = packet.vehicle,
                ),
                passengerIds,
            )
        }
    }

    private fun removedPlayers(
        packet: ClientboundRemoveEntitiesPacket,
        level: ClientLevel,
        localPlayer: Player,
    ) = packet.entityIds.mapNotNull { entityId ->
        level.player(entityId)?.routedPositionPacket(
            localPlayer,
            PlayerPositionPacketObservation(
                PlayerPositionLogKind.PLAYER_REMOVED,
                PlayerPositionPacketState(),
                relatedEntityId = entityId,
            ),
        )
    }

    private fun Player.routed(observation: PlayerPositionPacketObservation, localPlayer: Player) =
        listOf(routedPositionPacket(localPlayer, observation))

    private fun ClientboundMoveEntityPacket.player(level: ClientLevel) = getEntity(level) as? Player

    private fun ClientboundRotateHeadPacket.player(level: ClientLevel) = getEntity(level) as? Player

    private fun ClientLevel.player(entityId: Int) = getEntity(entityId) as? Player
}
