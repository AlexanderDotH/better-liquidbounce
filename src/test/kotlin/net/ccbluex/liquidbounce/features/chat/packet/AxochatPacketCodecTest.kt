/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.chat.packet

import com.google.gson.JsonParser
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AxochatPacketCodecTest {

    private val codec = AxochatPacketCodec()

    @Test
    fun `online user request uses the public protocol contract`() {
        val player = UUID.fromString("30a9237d-2a07-42f5-82af-2039f6653f7b")

        val json = JsonParser.parseString(
            codec.encode(C2SRequestOnlineUsersPacket(requestId = 7L, users = listOf(player)))
        ).asJsonObject

        assertEquals("RequestOnlineUsers", json["m"].asString)
        assertEquals(7L, json["c"].asJsonObject["request_id"].asLong)
        assertEquals(player.toString(), json["c"].asJsonObject["users"].asJsonArray.single().asString)
    }

    @Test
    fun `online user response is decoded with its request identity`() {
        val player = UUID.fromString("922ad8ba-4b1e-4c6c-b217-61dba0d21731")

        val packet = assertIs<S2COnlineUsersPacket>(codec.decode(
            """{"m":"OnlineUsers","c":{"request_id":11,"users":["$player"]}}"""
        ))

        assertEquals(11L, packet.requestId)
        assertEquals(listOf(player), packet.users)
    }

    @Test
    fun `existing chat message still decodes through the shared codec`() {
        val player = UUID.fromString("922ad8ba-4b1e-4c6c-b217-61dba0d21731")

        val packet = assertIs<S2CMessagePacket>(codec.decode("""
            {
              "m": "Message",
              "c": {
                "author_id": "42",
                "author_info": {"name": "Alex", "uuid": "$player"},
                "content": "Hi"
              }
            }
        """.trimIndent()))

        assertEquals("42", packet.id)
        assertEquals(AxoUser("Alex", player), packet.user)
        assertEquals("Hi", packet.content)
    }

    @Test
    fun `unknown server packet is ignored for forward compatibility`() {
        assertNull(codec.decode("""{"m":"FuturePacket","c":{}}"""))
    }
}
