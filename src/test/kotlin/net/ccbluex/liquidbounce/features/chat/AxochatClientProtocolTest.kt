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
package net.ccbluex.liquidbounce.features.chat

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.features.chat.packet.AxoChatClientId
import net.ccbluex.liquidbounce.features.chat.packet.AxoUserPresence
import net.ccbluex.liquidbounce.features.chat.packet.AxochatPacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SLoginJWTPacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SLoginMojangPacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SRequestUserPresencePacket
import net.ccbluex.liquidbounce.features.chat.packet.PacketSerializer
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AxochatClientProtocolTest {

    private val loginSerializer = PacketSerializer().apply {
        register<C2SLoginMojangPacket>("LoginMojang")
        register<C2SLoginJWTPacket>("LoginJWT")
        register<C2SRequestUserPresencePacket>("RequestUserPresence")
    }

    private val gson = GsonBuilder()
        .registerTypeAdapter(AxochatPacket.C2S::class.java, loginSerializer)
        .create()

    @Test
    fun `LiquidBounce login packets serialize the client identity`() {
        val uuid = UUID.fromString("30a9237d-2a07-42f5-82af-2039f6653f7b")
        val mojang = C2SLoginMojangPacket("Alex", uuid, true, AxoChatClientId.LIQUIDBOUNCE)
        val jwt = C2SLoginJWTPacket("token", true, AxoChatClientId.LIQUIDBOUNCE)

        assertEquals(
            JsonParser.parseString(
                """
                {
                    "m": "LoginMojang",
                    "c": {
                        "name": "Alex",
                        "uuid": "$uuid",
                        "allow_messages": true,
                        "client_id": "liquidbounce"
                    }
                }
                """.trimIndent()
            ),
            JsonParser.parseString(gson.toJson(mojang, AxochatPacket.C2S::class.java))
        )
        assertEquals(
            JsonParser.parseString(
                """{"m":"LoginJWT","c":{"token":"token","allow_messages":true,"client_id":"liquidbounce"}}"""
            ),
            JsonParser.parseString(gson.toJson(jwt, AxochatPacket.C2S::class.java))
        )
    }

    @Test
    fun `presence request serializes only the requested UUIDs`() {
        val uuid = UUID.fromString("922ad8ba-4b1e-4c6c-b217-61dba0d21731")

        val json = gson.toJson(C2SRequestUserPresencePacket(listOf(uuid)), AxochatPacket.C2S::class.java)

        assertEquals(
            JsonParser.parseString("""{"m":"RequestUserPresence","c":{"uuids":["$uuid"]}}"""),
            JsonParser.parseString(json)
        )
    }

    @Test
    fun `presence request refuses more than 100 UUIDs`() {
        assertFailsWith<IllegalArgumentException> {
            C2SRequestUserPresencePacket(List(101) { UUID.randomUUID() })
        }
    }

    @Test
    fun `presence response is delivered to the transport callback`() {
        val uuid = UUID.fromString("922ad8ba-4b1e-4c6c-b217-61dba0d21731")
        var received = emptyList<AxoUserPresence>()
        val client = AxochatClient(onUserPresence = { received = it })

        client.handlePlainMessage(
            """{"m":"UserPresence","c":{"users":[{"uuid":"$uuid","client_id":"fdpclient"}]}}"""
        )

        assertEquals(listOf(AxoUserPresence(uuid, AxoChatClientId.FDPCLIENT)), received)
    }
}
