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

import com.mojang.authlib.exceptions.InvalidCredentialsException
import net.ccbluex.liquidbounce.event.Event
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.ClientChatErrorEvent
import net.ccbluex.liquidbounce.event.events.ClientChatJwtTokenEvent
import net.ccbluex.liquidbounce.event.events.ClientChatStateChange
import net.ccbluex.liquidbounce.features.chat.packet.AxochatPacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SLoginMojangPacket
import net.ccbluex.liquidbounce.features.chat.packet.S2CErrorPacket
import net.ccbluex.liquidbounce.features.chat.packet.S2CMessagePacket
import net.ccbluex.liquidbounce.features.chat.packet.S2CMojangInfoPacket
import net.ccbluex.liquidbounce.features.chat.packet.S2CNewJWTPacket
import net.ccbluex.liquidbounce.features.chat.packet.S2CPrivateMessagePacket
import net.ccbluex.liquidbounce.features.chat.packet.S2CSuccessPacket
import net.ccbluex.liquidbounce.utils.client.mc

internal interface AxochatPacketEffects {
    fun authenticateMojang(sessionHash: String): C2SLoginMojangPacket
    fun sendPacket(packet: AxochatPacket.C2S)
    fun publishEvent(event: Event)
    fun publishPublicMessage(packet: S2CMessagePacket)
    fun publishPrivateMessage(packet: S2CPrivateMessagePacket)
    fun notify(message: String)
    fun markLoggedIn()
}

internal class LiveAxochatPacketEffects(
    private val send: (AxochatPacket.C2S) -> Unit,
    private val loggedIn: () -> Unit,
    private val publicMessage: (S2CMessagePacket) -> Unit,
    private val privateMessage: (S2CPrivateMessagePacket) -> Unit,
) : AxochatPacketEffects {

    override fun authenticateMojang(sessionHash: String): C2SLoginMojangPacket {
        mc.services.sessionService.joinServer(mc.user.profileId, mc.user.accessToken, sessionHash)
        return C2SLoginMojangPacket(mc.user.name, mc.user.profileId, allowMessages = true)
    }

    override fun sendPacket(packet: AxochatPacket.C2S) = send(packet)

    override fun publishEvent(event: Event) {
        EventManager.callEvent(event)
    }

    override fun publishPublicMessage(packet: S2CMessagePacket) = publicMessage(packet)

    override fun publishPrivateMessage(packet: S2CPrivateMessagePacket) = privateMessage(packet)

    override fun notify(message: String) = chat(message)

    override fun markLoggedIn() = loggedIn()
}

internal class AxochatPacketHandler(
    private val effects: AxochatPacketEffects,
) {

    fun handle(packet: AxochatPacket.S2C) {
        when (packet) {
            is S2CMojangInfoPacket -> handleMojangLogin(packet)
            is S2CMessagePacket -> effects.publishPublicMessage(packet)
            is S2CPrivateMessagePacket -> effects.publishPrivateMessage(packet)
            is S2CErrorPacket -> effects.publishEvent(ClientChatErrorEvent(translateError(packet.message)))
            is S2CSuccessPacket -> handleSuccess(packet.reason)
            is S2CNewJWTPacket -> effects.publishEvent(ClientChatJwtTokenEvent(packet.token))
        }
    }

    private fun handleMojangLogin(packet: S2CMojangInfoPacket) {
        effects.publishEvent(ClientChatStateChange(ClientChatStateChange.State.LOGGING_IN))
        runCatching {
            effects.sendPacket(effects.authenticateMojang(packet.sessionHash))
        }.onFailure { cause ->
            if (cause is InvalidCredentialsException) {
                effects.publishEvent(ClientChatStateChange(ClientChatStateChange.State.AUTHENTICATION_FAILED))
            } else {
                effects.publishEvent(ClientChatErrorEvent(errorMessage(cause)))
            }
        }
    }

    private fun handleSuccess(reason: String) {
        when (reason) {
            "Login" -> {
                effects.publishEvent(ClientChatStateChange(ClientChatStateChange.State.LOGGED_IN))
                effects.markLoggedIn()
            }
            "Ban" -> effects.notify("§7[§a§lChat§7] §9Successfully banned user!")
            "Unban" -> effects.notify("§7[§a§lChat§7] §9Successfully unbanned user!")
        }
    }

    private fun translateError(message: String): String = when (message) {
        "NotSupported" -> "This method is not supported!"
        "LoginFailed" -> "Login Failed!"
        "NotLoggedIn" -> "You must be logged in to use the chat!"
        "AlreadyLoggedIn" -> "You are already logged in!"
        "MojangRequestMissing" -> "Mojang request missing!"
        "NotPermitted" -> "You are missing the required permissions!"
        "NotBanned" -> "You are not banned!"
        "Banned" -> "You are banned!"
        "RateLimited" -> "You have been rate limited. Please try again later."
        "PrivateMessageNotAccepted" -> "Private message not accepted!"
        "EmptyMessage" -> "You are trying to send an empty message!"
        "MessageTooLong" -> "Message is too long!"
        "InvalidCharacter" -> "Message contains a non-ASCII character!"
        "InvalidId" -> "The given ID is invalid!"
        "Internal" -> "An internal server error occurred!"
        else -> message
    }

    private fun errorMessage(cause: Throwable) =
        cause.localizedMessage ?: cause.message ?: cause.javaClass.name
}
