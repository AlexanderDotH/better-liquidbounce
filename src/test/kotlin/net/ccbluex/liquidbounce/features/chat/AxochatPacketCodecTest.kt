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

import net.ccbluex.liquidbounce.features.chat.packet.C2SMessagePacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SRequestMojangInfoPacket
import net.ccbluex.liquidbounce.features.chat.packet.S2CNewJWTPacket
import net.ccbluex.liquidbounce.features.chat.packet.S2CSuccessPacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class AxochatPacketCodecTest {

    private val codec = AxochatPacketCodec()

    @Test
    fun `message packet keeps its exact websocket envelope`() {
        assertEquals(
            """{"m":"Message","c":{"content":"hello"}}""",
            codec.encode(C2SMessagePacket("hello")),
        )
    }

    @Test
    fun `bodyless request keeps its exact websocket envelope`() {
        assertEquals(
            """{"m":"RequestMojangInfo"}""",
            codec.encode(C2SRequestMojangInfoPacket()),
        )
    }

    @Test
    fun `registered server packets retain their concrete types and payloads`() {
        val success = codec.decode("""{"m":"Success","c":{"reason":"Login"}}""")
        val jwt = codec.decode("""{"m":"NewJWT","c":{"token":"secret"}}""")

        assertEquals(S2CSuccessPacket("Login"), assertInstanceOf(S2CSuccessPacket::class.java, success))
        assertEquals(S2CNewJWTPacket("secret"), assertInstanceOf(S2CNewJWTPacket::class.java, jwt))
    }
}
