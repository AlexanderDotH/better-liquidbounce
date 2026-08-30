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

import net.minecraft.network.protocol.Packet

internal data class PlayerPositionLogRecord(
    val origin: PlayerPositionLogOrigin,
    val kind: PlayerPositionLogKind,
    val sample: PlayerPositionSample? = null,
    val identity: PlayerPositionIdentity? = sample?.identity,
    val previousClientState: PlayerPositionState? = null,
    val observation: PlayerPositionPacketObservation? = null,
    val packet: Packet<*>? = null,
    val cancelled: Boolean? = null,
    val original: Boolean? = null,
    val relatedEntityIds: List<Int> = emptyList(),
    val eventState: String? = null,
)

internal fun PlayerPositionLogRecord.toLogEntry(
    timestampMs: Long,
    tick: Int?,
    dimension: String?,
    lastTransmittedState: PlayerServerPositionState,
) = PlayerPositionLogEntry(
    timestampMs = timestampMs,
    monotonicNanos = System.nanoTime(),
    tick = tick,
    dimension = dimension,
    origin = origin,
    kind = kind,
    packetType = packet?.javaClass?.name?.substringAfter("net.minecraft.network.protocol.game."),
    packetId = packet?.type()?.id?.toString(),
    original = original,
    cancelled = cancelled,
    player = identity,
    previousClientState = previousClientState,
    clientState = sample?.state,
    lastTransmittedState = lastTransmittedState.takeIf { identity?.local == true },
    packetState = observation?.packetState,
    teleportId = observation?.teleportId,
    relatedEntityId = observation?.relatedEntityId,
    relatedEntityIds = relatedEntityIds,
    eventState = eventState,
)

internal fun PlayerPositionLogEntry.summary(): String {
    val name = player?.name ?: "world"
    val position = packetState?.resolvedPosition ?: clientState?.position
    val coordinates = position?.let { " @ ${it.x}, ${it.y}, ${it.z}" }.orEmpty()
    return "[PlayerPosition] ${origin.name} ${kind.name} $name$coordinates"
}
