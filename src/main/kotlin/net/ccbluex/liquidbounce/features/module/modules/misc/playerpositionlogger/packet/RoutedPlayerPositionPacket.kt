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
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionPacketObservation
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionSample
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionState
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.capturePositionSample
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.capturePositionState
import net.minecraft.world.entity.player.Player

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

internal fun Player.routedPositionPacket(
    localPlayer: Player,
    observation: PlayerPositionPacketObservation,
    relatedEntityIds: List<Int> = emptyList(),
) = RoutedPlayerPositionPacket(
    identity = capturePositionSample(this === localPlayer).identity,
    clientState = capturePositionState(),
    observation = observation,
    relatedEntityIds = relatedEntityIds,
)
