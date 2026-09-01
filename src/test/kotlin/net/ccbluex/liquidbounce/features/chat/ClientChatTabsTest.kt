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
            listOf(ChatNetwork.MINECRAFT, ChatNetwork.AXOCHAT),
            ClientChatTabs.tabOrder,
        )
        assertEquals(
            listOf(
                "minecraft" to "Minecraft",
                "axochat" to "LiquidBounce/FDP",
            ),
            ClientChatTabs.tabOrder.map { it.id to it.label },
        )
    }

    @Test
    fun `Minecraft remains visible while optional networks can be toggled`() {
        assertEquals(listOf(ChatNetwork.MINECRAFT), ClientChatTabs.visibleNetworks)

        assertTrue(ClientChatTabs.setAvailable(ChatNetwork.AXOCHAT, true))
        assertEquals(ClientChatTabs.tabOrder, ClientChatTabs.visibleNetworks)

        assertFalse(ClientChatTabs.setAvailable(ChatNetwork.MINECRAFT, false))
        assertFalse(ClientChatTabs.setAvailable(ChatNetwork.MINECRAFT, true))
        assertTrue(ClientChatTabs.isAvailable(ChatNetwork.MINECRAFT))
        assertEquals(ClientChatTabs.tabOrder, ClientChatTabs.visibleNetworks)
    }

    @Test
    fun `hiding the active optional network falls back to Minecraft`() {
        ClientChatTabs.setAvailable(ChatNetwork.AXOCHAT, true)
        assertTrue(ClientChatTabs.switchTo(ChatNetwork.AXOCHAT))

        ClientChatTabs.setAvailable(ChatNetwork.AXOCHAT, false)

        assertEquals(ChatNetwork.MINECRAFT, ClientChatTabs.activeNetwork)
        assertFalse(ClientChatTabs.switchTo(ChatNetwork.AXOCHAT))
        assertEquals(ChatNetwork.MINECRAFT, ClientChatTabs.activeNetwork)
    }

    @Test
    fun `switching clears only the selected network unread count`() {
        ClientChatTabs.setAvailable(ChatNetwork.AXOCHAT, true)
        ClientChatTabs.incrementUnread(ChatNetwork.AXOCHAT, 3)

        ClientChatTabs.switchTo(ChatNetwork.AXOCHAT)

        assertEquals(0, ClientChatTabs.unreadCount(ChatNetwork.AXOCHAT))
        ClientChatTabs.incrementUnread(ChatNetwork.MINECRAFT, 2)
        assertEquals(2, ClientChatTabs.unreadCount(ChatNetwork.MINECRAFT))
    }

    @Test
    fun `drafts and scroll positions stay independent between networks`() {
        ClientChatTabs.setDraft(ChatNetwork.MINECRAFT, "server message")
        ClientChatTabs.setDraft(ChatNetwork.AXOCHAT, "global message")
        ClientChatTabs.setScrollPosition(ChatNetwork.MINECRAFT, 4)
        ClientChatTabs.setScrollPosition(ChatNetwork.AXOCHAT, 9)

        assertEquals("server message", ClientChatTabs.draft(ChatNetwork.MINECRAFT))
        assertEquals("global message", ClientChatTabs.draft(ChatNetwork.AXOCHAT))
        assertEquals(4, ClientChatTabs.scrollPosition(ChatNetwork.MINECRAFT))
        assertEquals(9, ClientChatTabs.scrollPosition(ChatNetwork.AXOCHAT))
    }

    @Test
    fun `cycling follows only visible networks and wraps in both directions`() {
        ClientChatTabs.setAvailable(ChatNetwork.AXOCHAT, true)

        assertEquals(ChatNetwork.AXOCHAT, ClientChatTabs.cycle(1))
        assertEquals(ChatNetwork.MINECRAFT, ClientChatTabs.cycle(1))
        assertEquals(ChatNetwork.AXOCHAT, ClientChatTabs.cycle(-1))
    }

    @Test
    fun `connection status is resettable state`() {
        ClientChatTabs.setConnectionStatus(ChatNetwork.AXOCHAT, ChatConnectionStatus.CONNECTING)

        assertEquals(ChatConnectionStatus.CONNECTED, ClientChatTabs.connectionStatus(ChatNetwork.MINECRAFT))
        assertEquals(ChatConnectionStatus.CONNECTING, ClientChatTabs.connectionStatus(ChatNetwork.AXOCHAT))

        ClientChatTabs.reset()

        assertEquals(ChatConnectionStatus.DISCONNECTED, ClientChatTabs.connectionStatus(ChatNetwork.AXOCHAT))
    }
}
