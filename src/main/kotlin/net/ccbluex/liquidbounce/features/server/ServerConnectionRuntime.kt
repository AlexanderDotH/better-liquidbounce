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

package net.ccbluex.liquidbounce.features.server

import net.ccbluex.fastutil.objectRBTreeSetOf
import net.ccbluex.liquidbounce.api.thirdparty.IpInfoApi
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.waitMatchesWithTimeout
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.network
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FIRST_PRIORITY
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket
import java.net.InetAddress
import kotlin.random.Random
import kotlin.time.Duration

internal object ServerConnectionRuntime {

    fun reconnect(serverInfo: ServerData) {
        val serverAddress = ServerAddress.parseString(serverInfo.ip)
        mc.execute {
            ConnectScreen.startConnecting(
                JoinMultiplayerScreen(TitleScreen()),
                mc,
                serverAddress,
                serverInfo,
                false,
                null,
            )
        }
    }

    suspend fun captureCommandSuggestions(listener: EventListener, timeout: Duration): Set<String>? {
        val completionId = Random.nextInt(0, 32767)
        network.send(ServerboundCommandSuggestionPacket(completionId, "/"))
        val packet = listener.waitMatchesWithTimeout<PacketEvent>(timeout, priority = FIRST_PRIORITY) {
            it.packet is ClientboundCommandSuggestionsPacket && it.packet.id == completionId
        }?.packet ?: return null

        packet as ClientboundCommandSuggestionsPacket
        return pluginNames(packet.toSuggestions().list.map { it.text })
    }

    suspend fun requestHostingInformation(
        address: ServerAddress,
        update: (IpInfoApi.IpData?) -> Unit,
    ) {
        val hostAddress = address.host
        val ipAddress = try {
            InetAddress.getByName(hostAddress)
        } catch (exception: Exception) {
            logger.error("Failed to resolve host address: $hostAddress", exception)
            return
        }

        update(runCatching { IpInfoApi.someoneElse(ipAddress.hostAddress) }.getOrNull())
    }

    fun pluginNames(suggestions: Iterable<String>): Set<String> =
        suggestions.mapNotNullTo(objectRBTreeSetOf()) { suggestion ->
            val command = suggestion.split(':')
            if (command.size > 1) command[0].replace("/", "") else null
        }
}
