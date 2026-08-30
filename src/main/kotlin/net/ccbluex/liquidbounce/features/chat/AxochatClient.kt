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

package net.ccbluex.liquidbounce.features.chat

import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.ClientChatErrorEvent
import net.ccbluex.liquidbounce.event.events.ClientChatMessageEvent
import net.ccbluex.liquidbounce.event.events.ClientChatStateChange
import net.ccbluex.liquidbounce.features.chat.packet.AxochatPacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SBanUserPacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SLoginJWTPacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SMessagePacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SPrivateMessagePacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SRequestMojangInfoPacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SUnbanUserPacket

class AxochatClient {

    var isLoggedIn = false
        private set

    private val codec = AxochatPacketCodec()
    private val connection = AxochatConnection(
        onConnectStarted = { isLoggedIn = false },
        onMessage = ::handlePlainMessage,
        onStateChange = { state -> EventManager.callEvent(ClientChatStateChange(state)) },
        onError = { cause ->
            EventManager.callEvent(ClientChatErrorEvent(errorMessage(cause)))
        },
    )
    private val packetHandler = AxochatPacketHandler(
        LiveAxochatPacketEffects(
            send = ::sendPacket,
            loggedIn = { isLoggedIn = true },
            publicMessage = { packet ->
                EventManager.callEvent(ClientChatMessageEvent(
                    packet.user,
                    packet.content,
                    ClientChatMessageEvent.ChatGroup.PUBLIC_CHAT,
                ))
            },
            privateMessage = { packet ->
                EventManager.callEvent(ClientChatMessageEvent(
                    packet.user,
                    packet.content,
                    ClientChatMessageEvent.ChatGroup.PRIVATE_CHAT,
                ))
            },
        ),
    )

    val isConnected: Boolean
        get() = connection.isConnected

    /**
     * Connect to chat server via websocket.
     * Supports SSL and non-SSL connections.
     * Be aware SSL takes insecure certificates.
     */
    suspend fun connect() = connection.connect()

    fun disconnect() {
        connection.disconnect()
        isLoggedIn = false
    }

    suspend fun reconnect() {
        disconnect()
        connect()
    }

    /**
     * Request Mojang authentication details for login.
     */
    fun requestMojangLogin() = sendPacket(C2SRequestMojangInfoPacket())

    /**
     * Send chat message to server.
     */
    fun sendMessage(message: String) = sendPacket(C2SMessagePacket(message))

    /**
     * Send private chat message to server.
     */
    fun sendPrivateMessage(receiver: String, message: String) =
        sendPacket(C2SPrivateMessagePacket(receiver, message))

    /**
     * Ban user from server.
     */
    suspend fun banUser(target: String) =
        sendPacket(C2SBanUserPacket(AxochatUserIdResolver.resolve(target)))

    /**
     * Unban user from server.
     */
    suspend fun unbanUser(target: String) =
        sendPacket(C2SUnbanUserPacket(AxochatUserIdResolver.resolve(target)))

    /**
     * Login to web socket via JWT.
     */
    fun loginViaJwt(token: String) {
        EventManager.callEvent(ClientChatStateChange(ClientChatStateChange.State.LOGGING_IN))
        sendPacket(C2SLoginJWTPacket(token, allowMessages = true))
    }

    /**
     * Send packet to server.
     */
    internal fun sendPacket(packet: AxochatPacket.C2S) {
        connection.send(codec.encode(packet))
    }

    /**
     * Handle incoming message of websocket.
     */
    internal fun handlePlainMessage(message: String) {
        packetHandler.handle(codec.decode(message))
    }

    private companion object {
        fun errorMessage(cause: Throwable) =
            cause.localizedMessage ?: cause.message ?: cause.javaClass.name
    }
}
