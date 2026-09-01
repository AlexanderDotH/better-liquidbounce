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
import io.netty.buffer.UnpooledByteBufAllocator
import io.netty.handler.ssl.SslContextBuilder
import net.ccbluex.liquidbounce.event.events.ClientChatMessageEvent
import net.ccbluex.liquidbounce.features.chat.packet.AxoChatClientId
import net.ccbluex.liquidbounce.features.chat.packet.AxoUserPresence
import net.ccbluex.liquidbounce.features.chat.packet.AxochatPacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SLoginJWTPacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SLoginMojangPacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SMessagePacket
import net.ccbluex.liquidbounce.features.chat.packet.C2SRequestUserPresencePacket
import net.ccbluex.liquidbounce.features.chat.packet.PacketSerializer
import java.net.URI
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AxochatClientProtocolTest {

    private val serializer = PacketSerializer().apply {
        register<C2SLoginMojangPacket>("LoginMojang")
        register<C2SLoginJWTPacket>("LoginJWT")
        register<C2SRequestUserPresencePacket>("RequestUserPresence")
        register<C2SMessagePacket>("Message")
    }

    private val gson = GsonBuilder()
        .registerTypeAdapter(AxochatPacket.C2S::class.java, serializer)
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
    fun `public messages serialize the selected client channel`() {
        val liquidBounce = C2SMessagePacket("hello LB", AxoChatClientId.LIQUIDBOUNCE)
        val fdp = C2SMessagePacket("hello FDP", AxoChatClientId.FDPCLIENT)

        assertEquals(
            JsonParser.parseString(
                """{"m":"Message","c":{"content":"hello LB","channel":"liquidbounce"}}"""
            ),
            JsonParser.parseString(gson.toJson(liquidBounce, AxochatPacket.C2S::class.java)),
        )
        assertEquals(
            JsonParser.parseString(
                """{"m":"Message","c":{"content":"hello FDP","channel":"fdpclient"}}"""
            ),
            JsonParser.parseString(gson.toJson(fdp, AxochatPacket.C2S::class.java)),
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

    @Test
    fun `incoming messages keep channel and public-private group separate`() {
        val uuid = UUID.fromString("922ad8ba-4b1e-4c6c-b217-61dba0d21731")
        val received = mutableListOf<ReceivedMessage>()
        val client = AxochatClient(onMessage = { _, message, group, network ->
            received += ReceivedMessage(network, group, message)
        })

        client.handlePlainMessage(
            """
            {"m":"Message","c":{
              "author_id":"1","author_info":{"name":"Alex","uuid":"$uuid"},
              "content":"public","channel":"liquidbounce"
            }}
            """.trimIndent()
        )
        client.handlePlainMessage(
            """
            {"m":"PrivateMessage","c":{
              "author_id":"1","author_info":{"name":"Alex","uuid":"$uuid"},
              "content":"private","channel":"fdpclient"
            }}
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ReceivedMessage(
                    ChatNetwork.LIQUIDBOUNCE,
                    ClientChatMessageEvent.ChatGroup.PUBLIC_CHAT,
                    "public",
                ),
                ReceivedMessage(
                    ChatNetwork.FDPCLIENT,
                    ClientChatMessageEvent.ChatGroup.PRIVATE_CHAT,
                    "private",
                ),
            ),
            received,
        )
    }

    @Test
    fun `missing unknown and legacy channels are ignored instead of mixed`() {
        val uuid = UUID.fromString("922ad8ba-4b1e-4c6c-b217-61dba0d21731")
        val received = mutableListOf<String>()
        val client = AxochatClient(onMessage = { _, message, _, _ -> received += message })

        listOf(
            """
            {"m":"Message","c":{
              "author_info":{"name":"Alex","uuid":"$uuid"},"content":"missing"
            }}
            """.trimIndent(),
            """
            {"m":"Message","c":{
              "author_info":{"name":"Alex","uuid":"$uuid"},"content":"unknown","channel":"other"
            }}
            """.trimIndent(),
            """
            {"m":"Message","c":{
              "author_info":{"name":"Alex","uuid":"$uuid"},"content":"legacy","channel":"legacy"
            }}
            """.trimIndent(),
        ).forEach(client::handlePlainMessage)

        assertEquals(emptyList(), received)
    }

    @Test
    fun `login response advertises channel support`() {
        val supported = AxochatClient()
        val legacy = AxochatClient()

        supported.handlePlainMessage(
            """{"m":"Success","c":{"reason":"Login","supports_channels":true}}"""
        )
        legacy.handlePlainMessage("""{"m":"Success","c":{"reason":"Login"}}""")

        assertTrue(supported.isLoggedIn)
        assertTrue(supported.supportsClientChannels)
        assertTrue(legacy.isLoggedIn)
        assertFalse(legacy.supportsClientChannels)
    }

    @Test
    fun `TLS uses platform trust and verifies the AxoChat hostname`() {
        val uri = URI("wss://chat.liquidbounce.net:7886/ws")
        val handler = createAxochatSslHandler(
            SslContextBuilder.forClient().build(),
            UnpooledByteBufAllocator.DEFAULT,
            uri,
        )

        try {
            assertEquals(uri.host, handler.engine().peerHost)
            assertEquals("HTTPS", handler.engine().sslParameters.endpointIdentificationAlgorithm)
            assertFalse(
                Path.of("src/main/kotlin/net/ccbluex/liquidbounce/features/chat/AxochatClient.kt")
                    .toFile().readText().contains("InsecureTrustManagerFactory")
            )
        } finally {
            handler.engine().closeOutbound()
        }
    }

    private data class ReceivedMessage(
        val network: ChatNetwork,
        val group: ClientChatMessageEvent.ChatGroup,
        val content: String,
    )
}
