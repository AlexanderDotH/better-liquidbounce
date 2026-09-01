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

import net.ccbluex.liquidbounce.features.chat.packet.AxoChatClientId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClientChatChannelContractTest {

    @BeforeTest
    fun resetTabs() = ClientChatTabs.reset()

    @Test
    fun `LiquidBounce and FDP are separate icon tabs`() {
        assertEquals(
            listOf(ChatNetwork.MINECRAFT, ChatNetwork.LIQUIDBOUNCE, ChatNetwork.FDPCLIENT),
            ClientChatTabs.tabOrder,
        )
        assertEquals(
            listOf("Minecraft", "LiquidBounce", "FDPClient"),
            ClientChatTabs.tabOrder.map(ChatNetwork::label),
        )
        assertEquals(
            listOf(
                "minecraft:icon/draft_report",
                "liquidbounce:client_icons/liquidbounce",
                "liquidbounce:client_icons/fdpclient",
            ),
            ClientChatTabs.tabOrder.map { it.icon.toString() },
        )
        assertNotNull(javaClass.getResource("/assets/liquidbounce/textures/gui/sprites/client_icons/fdpclient.png"))
    }

    @Test
    fun `LiquidBounce and FDP keep drafts unread counts and histories separate`() {
        ClientChatTabs.setAvailable(ChatNetwork.LIQUIDBOUNCE, true)
        ClientChatTabs.setAvailable(ChatNetwork.FDPCLIENT, true)
        ClientChatTabs.setDraft(ChatNetwork.LIQUIDBOUNCE, "lb draft")
        ClientChatTabs.setDraft(ChatNetwork.FDPCLIENT, "fdp draft")
        ClientChatTabs.incrementUnread(ChatNetwork.LIQUIDBOUNCE)
        ClientChatTabs.incrementUnread(ChatNetwork.FDPCLIENT, 2)

        assertEquals("lb draft", ClientChatTabs.draft(ChatNetwork.LIQUIDBOUNCE))
        assertEquals("fdp draft", ClientChatTabs.draft(ChatNetwork.FDPCLIENT))
        assertEquals(1, ClientChatTabs.unreadCount(ChatNetwork.LIQUIDBOUNCE))
        assertEquals(2, ClientChatTabs.unreadCount(ChatNetwork.FDPCLIENT))
        assertTrue(ChatMessageRoutePolicy.isVisible(ChatNetwork.LIQUIDBOUNCE, ChatNetwork.LIQUIDBOUNCE))
        assertFalse(ChatMessageRoutePolicy.isVisible(ChatNetwork.LIQUIDBOUNCE, ChatNetwork.FDPCLIENT))
    }

    @Test
    fun `ordinary text is sent only to the selected client channel`() {
        val sent = mutableListOf<Pair<ChatNetwork, String>>()
        val sender = { network: ChatNetwork, message: String ->
            sent += network to message
            true
        }

        assertEquals(
            ChatSubmission.EXTERNAL_SENT,
            ClientChatScreenBridge.routeInput("hello LB", ChatNetwork.LIQUIDBOUNCE, false, sender),
        )
        assertEquals(
            ChatSubmission.EXTERNAL_SENT,
            ClientChatScreenBridge.routeInput("hello FDP", ChatNetwork.FDPCLIENT, false, sender),
        )
        assertEquals(
            listOf(
                ChatNetwork.LIQUIDBOUNCE to "hello LB",
                ChatNetwork.FDPCLIENT to "hello FDP",
            ),
            sent,
        )
    }

    @Test
    fun `AxoChat client identities map to exactly one visible channel`() {
        assertEquals(ChatNetwork.LIQUIDBOUNCE, AxoChatClientId.LIQUIDBOUNCE.chatNetwork)
        assertEquals(ChatNetwork.FDPCLIENT, AxoChatClientId.FDPCLIENT.chatNetwork)
        assertEquals(null, AxoChatClientId.LEGACY.chatNetwork)
    }
}
