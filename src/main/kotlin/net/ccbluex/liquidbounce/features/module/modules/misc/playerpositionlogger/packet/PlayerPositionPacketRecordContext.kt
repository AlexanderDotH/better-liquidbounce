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

import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionLogOrigin
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionLogRecord
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerServerPositionState
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.protocol.Packet
import net.minecraft.world.entity.player.Player
import java.util.UUID

internal data class PlayerPositionPacketRecordContext(
    val origin: TransferOrigin,
    val packet: Packet<*>,
    val cancelled: Boolean,
    val original: Boolean,
    val level: ClientLevel,
    val localPlayer: Player,
    val lastTransmittedState: PlayerServerPositionState,
    val playerName: (UUID) -> String?,
)

internal fun routePlayerPositionLogRecords(context: PlayerPositionPacketRecordContext): List<PlayerPositionLogRecord> =
    PlayerPositionPacketRouter.route(
        context.packet,
        context.level,
        context.localPlayer,
        context.lastTransmittedState,
        context.playerName,
    ).map { routed ->
        PlayerPositionLogRecord(
            origin = context.origin.toLogOrigin(),
            kind = routed.observation.kind,
            sample = routed.sample,
            identity = routed.identity,
            observation = routed.observation,
            packet = context.packet,
            cancelled = context.cancelled,
            original = context.original,
            relatedEntityIds = routed.relatedEntityIds,
        )
    }

private fun TransferOrigin.toLogOrigin() = when (this) {
    TransferOrigin.INCOMING -> PlayerPositionLogOrigin.INCOMING
    TransferOrigin.OUTGOING -> PlayerPositionLogOrigin.OUTGOING
}
