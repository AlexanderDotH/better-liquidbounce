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

import net.ccbluex.liquidbounce.features.misc.ClientBrand
import net.ccbluex.liquidbounce.features.misc.ClientBrandColors
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientChatScreenBridgeTest {

    @AfterTest
    fun resetTabs() = ClientChatTabs.reset()

    @Test
    fun `client commands run before an external provider can receive input`() {
        var sends = 0

        val submission = ClientChatScreenBridge.routeInput(
            message = ".toggle fly",
            activeNetwork = ChatNetwork.AXOCHAT,
            commandConsumed = true,
        ) { _, _ ->
            sends++
            true
        }

        assertEquals(ChatSubmission.CLIENT_COMMAND, submission)
        assertEquals(0, sends)
    }

    @Test
    fun `slash input remains on the vanilla server route`() {
        var sends = 0

        val submission = ClientChatScreenBridge.routeInput(
            message = "/msg Alex hello",
            activeNetwork = ChatNetwork.AXOCHAT,
            commandConsumed = false,
        ) { _, _ ->
            sends++
            true
        }

        assertEquals(ChatSubmission.VANILLA, submission)
        assertEquals(0, sends)
    }

    @Test
    fun `failed external sends keep the draft and never fall back to Minecraft`() {
        val submission = ClientChatScreenBridge.routeInput(
            message = "still here",
            activeNetwork = ChatNetwork.AXOCHAT,
            commandConsumed = false,
        ) { _, _ -> false }

        assertEquals(ChatSubmission.EXTERNAL_FAILED, submission)
        assertTrue(submission.cancelVanilla)
        assertTrue(submission.keepDraft)
    }

    @Test
    fun `successful ordinary text is intercepted only on an external tab`() {
        val sent = mutableListOf<Pair<ChatNetwork, String>>()
        val sender = { network: ChatNetwork, message: String ->
            sent += network to message
            true
        }

        val minecraft = ClientChatScreenBridge.routeInput("hello", ChatNetwork.MINECRAFT, false, sender)
        val axochat = ClientChatScreenBridge.routeInput("hello", ChatNetwork.AXOCHAT, false, sender)

        assertEquals(ChatSubmission.VANILLA, minecraft)
        assertEquals(ChatSubmission.EXTERNAL_SENT, axochat)
        assertEquals(listOf(ChatNetwork.AXOCHAT to "hello"), sent)
    }

    @Test
    fun `control tab cycles forward and shift reverses without consuming bare tab`() {
        assertEquals(0, ClientChatScreenBridge.cycleDirection(tab = true, control = false, shift = false))
        assertEquals(1, ClientChatScreenBridge.cycleDirection(tab = true, control = true, shift = false))
        assertEquals(-1, ClientChatScreenBridge.cycleDirection(tab = true, control = true, shift = true))
        assertEquals(0, ClientChatScreenBridge.cycleDirection(tab = false, control = true, shift = true))
    }

    @Test
    fun `switching and cycling preserve each tab draft and scroll`() {
        ClientChatTabs.setAvailable(ChatNetwork.AXOCHAT, true)
        ClientChatTabs.setDraft(ChatNetwork.AXOCHAT, "Axo draft")
        ClientChatTabs.setScrollPosition(ChatNetwork.AXOCHAT, 7)

        val axochat = ClientChatScreenBridge.switchTo("axochat", "Minecraft draft", 3)!!
        val minecraft = ClientChatScreenBridge.cycle(-1, "Edited Axo draft", 5)

        assertEquals(ChatTabTransition("Axo draft", 7), axochat)
        assertEquals(ChatTabTransition("Minecraft draft", 3), minecraft)
        assertEquals("Edited Axo draft", ClientChatTabs.draft(ChatNetwork.AXOCHAT))
        assertEquals(5, ClientChatTabs.scrollPosition(ChatNetwork.AXOCHAT))
    }

    @Test
    fun `visible tabs use full labels and append unread counts`() {
        ClientChatTabs.setAvailable(ChatNetwork.AXOCHAT, true)
        ClientChatTabs.incrementUnread(ChatNetwork.AXOCHAT, 12)

        val tabs = ClientChatScreenBridge.visibleTabs()

        assertEquals(listOf("Minecraft", "LiquidBounce/FDP (12)"), tabs.map(ChatTabView::label))
        assertTrue(tabs.first().selected)
        assertFalse(tabs.last().selected)
    }

    @Test
    fun `slash-prefilled chat always opens the Minecraft tab`() {
        ClientChatTabs.setAvailable(ChatNetwork.AXOCHAT, true)
        ClientChatTabs.switchTo(ChatNetwork.AXOCHAT)
        ClientChatTabs.setDraft(ChatNetwork.AXOCHAT, "external draft")

        val transition = ClientChatScreenBridge.initialState("/")

        assertEquals(ChatNetwork.MINECRAFT, ClientChatTabs.activeNetwork)
        assertEquals(ChatTabTransition("/", 0), transition)
        assertEquals("external draft", ClientChatTabs.draft(ChatNetwork.AXOCHAT))
    }

    @Test
    fun `chat networks use the same brand colors as client indicators`() {
        val liquidBounceColor = Color4b.fromHex("#0080FF")

        assertEquals(
            ClientBrandColors.color(ClientBrand.LIQUIDBOUNCE, liquidBounceColor),
            chatNetworkColor(ChatNetwork.AXOCHAT, liquidBounceColor),
        )
    }
}
