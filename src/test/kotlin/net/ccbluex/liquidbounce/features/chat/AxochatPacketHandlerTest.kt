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
import net.ccbluex.liquidbounce.event.events.ClientChatErrorEvent
import net.ccbluex.liquidbounce.event.events.ClientChatJwtTokenEvent
import net.ccbluex.liquidbounce.event.events.ClientChatStateChange
import net.ccbluex.liquidbounce.features.chat.packet.AxoUser
import net.ccbluex.liquidbounce.features.chat.packet.AxochatPacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SLoginMojangPacket
import net.ccbluex.liquidbounce.features.chat.packet.S2CErrorPacket
import net.ccbluex.liquidbounce.features.chat.packet.S2CMessagePacket
import net.ccbluex.liquidbounce.features.chat.packet.S2CMojangInfoPacket
import net.ccbluex.liquidbounce.features.chat.packet.S2CNewJWTPacket
import net.ccbluex.liquidbounce.features.chat.packet.S2CPrivateMessagePacket
import net.ccbluex.liquidbounce.features.chat.packet.S2CSuccessPacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class AxochatPacketHandlerTest {

    private val user = AxoUser("Alice", UUID.fromString("00000000-0000-0000-0000-000000000001"))

    @Test
    fun `messages errors and jwt keep their dispatch channels and order`() {
        val effects = RecordingEffects()
        val handler = AxochatPacketHandler(effects)

        handler.handle(S2CMessagePacket("1", user, "public"))
        handler.handle(S2CPrivateMessagePacket("2", user, "private"))
        handler.handle(S2CErrorPacket("RateLimited"))
        handler.handle(S2CNewJWTPacket("token"))

        assertEquals(
            listOf(
                "public:Alice:public",
                "private:Alice:private",
                "error:You have been rate limited. Please try again later.",
                "jwt:token",
            ),
            effects.timeline,
        )
    }

    @Test
    fun `successful login publishes state before exposing logged in flag`() {
        val effects = RecordingEffects()

        AxochatPacketHandler(effects).handle(S2CSuccessPacket("Login"))

        assertEquals(listOf("state:LOGGED_IN", "logged-in"), effects.timeline)
    }

    @Test
    fun `moderation success keeps exact user notifications`() {
        val effects = RecordingEffects()
        val handler = AxochatPacketHandler(effects)

        handler.handle(S2CSuccessPacket("Ban"))
        handler.handle(S2CSuccessPacket("Unban"))

        assertEquals(
            listOf(
                "chat:§7[§a§lChat§7] §9Successfully banned user!",
                "chat:§7[§a§lChat§7] §9Successfully unbanned user!",
            ),
            effects.timeline,
        )
    }

    @Test
    fun `mojang challenge authenticates then sends after logging in state`() {
        val effects = RecordingEffects()

        AxochatPacketHandler(effects).handle(S2CMojangInfoPacket("session-hash"))

        assertEquals(
            listOf("state:LOGGING_IN", "authenticate:session-hash", "send:C2SLoginMojangPacket"),
            effects.timeline,
        )
    }

    @Test
    fun `invalid mojang credentials retain authentication failed state`() {
        val effects = RecordingEffects(authenticationFailure = InvalidCredentialsException("invalid"))

        AxochatPacketHandler(effects).handle(S2CMojangInfoPacket("session-hash"))

        assertEquals(
            listOf("state:LOGGING_IN", "authenticate:session-hash", "state:AUTHENTICATION_FAILED"),
            effects.timeline,
        )
    }

    @Test
    fun `unexpected mojang failure retains client error fallback`() {
        val effects = RecordingEffects(authenticationFailure = IllegalStateException("offline"))

        AxochatPacketHandler(effects).handle(S2CMojangInfoPacket("session-hash"))

        assertEquals(
            listOf("state:LOGGING_IN", "authenticate:session-hash", "error:offline"),
            effects.timeline,
        )
    }

    private class RecordingEffects(
        private val authenticationFailure: Throwable? = null,
    ) : AxochatPacketEffects {
        val timeline = mutableListOf<String>()

        override fun authenticateMojang(sessionHash: String): C2SLoginMojangPacket {
            timeline += "authenticate:$sessionHash"
            authenticationFailure?.let { throw it }
            return C2SLoginMojangPacket("Player", UUID(0, 2), allowMessages = true)
        }

        override fun sendPacket(packet: AxochatPacket.C2S) {
            timeline += "send:${packet.javaClass.simpleName}"
        }

        override fun publishEvent(event: Event) {
            timeline += when (event) {
                is ClientChatStateChange -> "state:${event.state}"
                is ClientChatErrorEvent -> "error:${event.error}"
                is ClientChatJwtTokenEvent -> "jwt:${event.jwt}"
                else -> "event:${event.javaClass.simpleName}"
            }
        }

        override fun publishPublicMessage(packet: S2CMessagePacket) {
            timeline += "public:${packet.user.name}:${packet.content}"
        }

        override fun publishPrivateMessage(packet: S2CPrivateMessagePacket) {
            timeline += "private:${packet.user.name}:${packet.content}"
        }

        override fun notify(message: String) {
            timeline += "chat:$message"
        }

        override fun markLoggedIn() {
            timeline += "logged-in"
        }
    }
}
