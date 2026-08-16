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
package net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundRespawnPacket
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.player.Player
import java.util.UUID

@JvmRecord
internal data class RoutedPlayerPositionPacket(
    val identity: PlayerPositionIdentity,
    val clientState: PlayerPositionState?,
    val observation: PlayerPositionPacketObservation,
    val relatedEntityIds: List<Int> = emptyList(),
) {
    val sample: PlayerPositionSample?
        get() = clientState?.let { PlayerPositionSample(identity, it) }
}

internal object PlayerPositionPacketRouter {

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun route(
        packet: Packet<*>,
        level: ClientLevel,
        localPlayer: Player,
        lastTransmittedState: PlayerServerPositionState,
        playerName: (UUID) -> String?,
    ): List<RoutedPlayerPositionPacket> = when (packet) {
        is ServerboundMovePlayerPacket -> listOf(localPlayer.route(
            localPlayer,
            PlayerPositionPacketLogFactory.outgoingMovement(
                packet,
                localPlayer.capturePositionState(),
                lastTransmittedState,
            ),
        ))

        is ServerboundMoveVehiclePacket -> listOf(localPlayer.route(
            localPlayer,
            PlayerPositionSupplementalLogFactory.vehicleMovement(packet).copy(
                relatedEntityId = localPlayer.vehicle?.id,
            ),
        ))

        is ServerboundAcceptTeleportationPacket -> listOf(localPlayer.route(
            localPlayer,
            PlayerPositionPacketObservation(
                PlayerPositionLogKind.LOCAL_TELEPORT_ACK,
                PlayerPositionPacketState(),
                teleportId = packet.id,
            ),
        ))

        is ClientboundPlayerPositionPacket -> listOf(localPlayer.route(
            localPlayer,
            PlayerPositionPacketLogFactory.localCorrection(packet, localPlayer.capturePositionState()),
        ))

        is ClientboundPlayerRotationPacket -> listOf(localPlayer.route(
            localPlayer,
            PlayerPositionPacketLogFactory.localRotation(packet, localPlayer.capturePositionState()),
        ))

        is ClientboundExplodePacket -> packet.playerKnockback.orElse(null)?.let { knockback ->
            listOf(localPlayer.route(
                localPlayer,
                PlayerPositionPacketLogFactory.explosionKnockback(
                    knockback,
                    localPlayer.capturePositionState(),
                ),
            ))
        }.orEmpty()

        is ClientboundMoveEntityPacket -> (packet.getEntity(level) as? Player)?.let { target ->
            listOf(target.route(
                localPlayer,
                PlayerPositionPacketLogFactory.remoteMovement(packet, target.capturePositionState()),
            ))
        }.orEmpty()

        is ClientboundTeleportEntityPacket -> (level.getEntity(packet.id) as? Player)?.let { target ->
            listOf(target.route(
                localPlayer,
                PlayerPositionPacketLogFactory.remoteTeleport(packet, target.capturePositionState()),
            ))
        }.orEmpty()

        is ClientboundEntityPositionSyncPacket -> (level.getEntity(packet.id) as? Player)?.let { target ->
            listOf(target.route(
                localPlayer,
                PlayerPositionPacketLogFactory.remotePositionSync(packet),
            ))
        }.orEmpty()

        is ClientboundSetEntityMotionPacket -> (level.getEntity(packet.id) as? Player)?.let { target ->
            listOf(target.route(
                localPlayer,
                PlayerPositionPacketLogFactory.velocity(packet, target === localPlayer),
            ))
        }.orEmpty()

        is ClientboundRotateHeadPacket -> (packet.getEntity(level) as? Player)?.let { target ->
            listOf(target.route(
                localPlayer,
                PlayerPositionSupplementalLogFactory.remoteHeadRotation(
                    packet,
                    target.capturePositionState(),
                    target.id,
                ),
            ))
        }.orEmpty()

        is ClientboundAddEntityPacket -> if (packet.type == EntityTypes.PLAYER) {
            listOf(spawn(packet, playerName(packet.uuid)))
        } else {
            emptyList()
        }

        is ClientboundSetPassengersPacket -> passengers(packet, level, localPlayer)
        is ClientboundRemoveEntitiesPacket -> removedPlayers(packet, level, localPlayer)

        is ClientboundRespawnPacket -> listOf(localPlayer.route(
            localPlayer,
            PlayerPositionPacketObservation(
                PlayerPositionLogKind.WORLD_CHANGED,
                PlayerPositionPacketState(),
            ),
        ))

        else -> emptyList()
    }

    private fun spawn(packet: ClientboundAddEntityPacket, name: String?) = RoutedPlayerPositionPacket(
        identity = PlayerPositionIdentity(
            packet.id,
            packet.uuid.toString(),
            name ?: packet.uuid.toString(),
            local = false,
        ),
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
            (level.getEntity(packet.vehicle) as? Player)?.let(::add)
            passengerIds.mapNotNullTo(this) { level.getEntity(it) as? Player }
        }.distinctBy(Player::getId).map { affected ->
            affected.route(
                localPlayer,
                PlayerPositionPacketObservation(
                    PlayerPositionLogKind.PLAYER_MOUNT_CHANGE,
                    PlayerPositionPacketState(),
                    relatedEntityId = packet.vehicle,
                ),
                relatedEntityIds = passengerIds,
            )
        }
    }

    private fun removedPlayers(
        packet: ClientboundRemoveEntitiesPacket,
        level: ClientLevel,
        localPlayer: Player,
    ) = packet.entityIds.mapNotNull { entityId ->
        (level.getEntity(entityId) as? Player)?.route(
            localPlayer,
            PlayerPositionPacketObservation(
                PlayerPositionLogKind.PLAYER_REMOVED,
                PlayerPositionPacketState(),
                relatedEntityId = entityId,
            ),
        )
    }

    private fun Player.route(
        localPlayer: Player,
        observation: PlayerPositionPacketObservation,
        relatedEntityIds: List<Int> = emptyList(),
    ) = RoutedPlayerPositionPacket(
        identity = capturePositionSample(this === localPlayer).identity,
        clientState = capturePositionState(),
        observation = observation,
        relatedEntityIds = relatedEntityIds,
    )
}
