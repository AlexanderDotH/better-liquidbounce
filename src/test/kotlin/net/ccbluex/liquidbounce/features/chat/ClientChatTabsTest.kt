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

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientChatTabsTest {

    @BeforeTest
    fun resetTabs() = ClientChatTabs.reset()

    @Test
    fun `network IDs labels and tab order are stable`() {
        assertEquals(
            listOf(ChatNetwork.MINECRAFT, ChatNetwork.LIQUIDBOUNCE, ChatNetwork.FDPCLIENT),
            ClientChatTabs.tabOrder,
        )
        assertEquals(
            listOf(
                "minecraft" to "Minecraft",
                "liquidbounce" to "LiquidBounce",
                "fdpclient" to "FDPClient",
            ),
            ClientChatTabs.tabOrder.map { it.id to it.label },
        )
    }

    @Test
    fun `Minecraft remains visible while client channels can be toggled independently`() {
        assertEquals(listOf(ChatNetwork.MINECRAFT), ClientChatTabs.visibleNetworks)

        assertTrue(ClientChatTabs.setAvailable(ChatNetwork.LIQUIDBOUNCE, true))
        assertTrue(ClientChatTabs.setAvailable(ChatNetwork.FDPCLIENT, true))
        assertEquals(ClientChatTabs.tabOrder, ClientChatTabs.visibleNetworks)

        assertFalse(ClientChatTabs.setAvailable(ChatNetwork.MINECRAFT, false))
        assertFalse(ClientChatTabs.setAvailable(ChatNetwork.MINECRAFT, true))
        assertTrue(ClientChatTabs.isAvailable(ChatNetwork.MINECRAFT))
    }

    @Test
    fun `hiding the active client channel falls back to Minecraft`() {
        ClientChatTabs.setAvailable(ChatNetwork.FDPCLIENT, true)
        assertTrue(ClientChatTabs.switchTo(ChatNetwork.FDPCLIENT))

        ClientChatTabs.setAvailable(ChatNetwork.FDPCLIENT, false)

        assertEquals(ChatNetwork.MINECRAFT, ClientChatTabs.activeNetwork)
        assertFalse(ClientChatTabs.switchTo(ChatNetwork.FDPCLIENT))
    }

    @Test
    fun `switching clears only the selected channel unread count`() {
        ClientChatTabs.setAvailable(ChatNetwork.LIQUIDBOUNCE, true)
        ClientChatTabs.setAvailable(ChatNetwork.FDPCLIENT, true)
        ClientChatTabs.incrementUnread(ChatNetwork.LIQUIDBOUNCE, 3)
        ClientChatTabs.incrementUnread(ChatNetwork.FDPCLIENT, 2)

        ClientChatTabs.switchTo(ChatNetwork.LIQUIDBOUNCE)

        assertEquals(0, ClientChatTabs.unreadCount(ChatNetwork.LIQUIDBOUNCE))
        assertEquals(2, ClientChatTabs.unreadCount(ChatNetwork.FDPCLIENT))
    }

    @Test
    fun `drafts and scroll positions stay independent between all channels`() {
        ClientChatTabs.setDraft(ChatNetwork.MINECRAFT, "server message")
        ClientChatTabs.setDraft(ChatNetwork.LIQUIDBOUNCE, "lb message")
        ClientChatTabs.setDraft(ChatNetwork.FDPCLIENT, "fdp message")
        ClientChatTabs.setScrollPosition(ChatNetwork.MINECRAFT, 4)
        ClientChatTabs.setScrollPosition(ChatNetwork.LIQUIDBOUNCE, 7)
        ClientChatTabs.setScrollPosition(ChatNetwork.FDPCLIENT, 9)

        assertEquals("server message", ClientChatTabs.draft(ChatNetwork.MINECRAFT))
        assertEquals("lb message", ClientChatTabs.draft(ChatNetwork.LIQUIDBOUNCE))
        assertEquals("fdp message", ClientChatTabs.draft(ChatNetwork.FDPCLIENT))
        assertEquals(4, ClientChatTabs.scrollPosition(ChatNetwork.MINECRAFT))
        assertEquals(7, ClientChatTabs.scrollPosition(ChatNetwork.LIQUIDBOUNCE))
        assertEquals(9, ClientChatTabs.scrollPosition(ChatNetwork.FDPCLIENT))
    }

    @Test
    fun `cycling follows visible channel order and wraps in both directions`() {
        ClientChatTabs.setAvailable(ChatNetwork.LIQUIDBOUNCE, true)
        ClientChatTabs.setAvailable(ChatNetwork.FDPCLIENT, true)

        assertEquals(ChatNetwork.LIQUIDBOUNCE, ClientChatTabs.cycle(1))
        assertEquals(ChatNetwork.FDPCLIENT, ClientChatTabs.cycle(1))
        assertEquals(ChatNetwork.MINECRAFT, ClientChatTabs.cycle(1))
        assertEquals(ChatNetwork.FDPCLIENT, ClientChatTabs.cycle(-1))
    }

    @Test
    fun `connection status is independent and resettable`() {
        ClientChatTabs.setConnectionStatus(ChatNetwork.LIQUIDBOUNCE, ChatConnectionStatus.CONNECTED)
        ClientChatTabs.setConnectionStatus(ChatNetwork.FDPCLIENT, ChatConnectionStatus.CONNECTING)

        assertEquals(ChatConnectionStatus.CONNECTED, ClientChatTabs.connectionStatus(ChatNetwork.MINECRAFT))
        assertEquals(ChatConnectionStatus.CONNECTED, ClientChatTabs.connectionStatus(ChatNetwork.LIQUIDBOUNCE))
        assertEquals(ChatConnectionStatus.CONNECTING, ClientChatTabs.connectionStatus(ChatNetwork.FDPCLIENT))

        ClientChatTabs.reset()

        assertEquals(ChatConnectionStatus.DISCONNECTED, ClientChatTabs.connectionStatus(ChatNetwork.LIQUIDBOUNCE))
        assertEquals(ChatConnectionStatus.DISCONNECTED, ClientChatTabs.connectionStatus(ChatNetwork.FDPCLIENT))
    }
}
