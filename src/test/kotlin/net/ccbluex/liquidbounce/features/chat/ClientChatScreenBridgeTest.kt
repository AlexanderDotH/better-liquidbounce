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
    fun `client commands run before an external channel can receive input`() {
        var sends = 0

        val submission = ClientChatScreenBridge.routeInput(
            message = ".toggle fly",
            activeNetwork = ChatNetwork.LIQUIDBOUNCE,
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
            activeNetwork = ChatNetwork.FDPCLIENT,
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
            activeNetwork = ChatNetwork.FDPCLIENT,
            commandConsumed = false,
        ) { _, _ -> false }

        assertEquals(ChatSubmission.EXTERNAL_FAILED, submission)
        assertTrue(submission.cancelVanilla)
        assertTrue(submission.keepDraft)
    }

    @Test
    fun `ordinary text preserves the selected external channel`() {
        val sent = mutableListOf<Pair<ChatNetwork, String>>()
        val sender = { network: ChatNetwork, message: String ->
            sent += network to message
            true
        }

        val minecraft = ClientChatScreenBridge.routeInput("hello", ChatNetwork.MINECRAFT, false, sender)
        val liquidBounce = ClientChatScreenBridge.routeInput("hello LB", ChatNetwork.LIQUIDBOUNCE, false, sender)
        val fdp = ClientChatScreenBridge.routeInput("hello FDP", ChatNetwork.FDPCLIENT, false, sender)

        assertEquals(ChatSubmission.VANILLA, minecraft)
        assertEquals(ChatSubmission.EXTERNAL_SENT, liquidBounce)
        assertEquals(ChatSubmission.EXTERNAL_SENT, fdp)
        assertEquals(
            listOf(
                ChatNetwork.LIQUIDBOUNCE to "hello LB",
                ChatNetwork.FDPCLIENT to "hello FDP",
            ),
            sent,
        )
    }

    @Test
    fun `control tab cycles forward and shift reverses without consuming bare tab`() {
        assertEquals(0, ClientChatScreenBridge.cycleDirection(tab = true, control = false, shift = false))
        assertEquals(1, ClientChatScreenBridge.cycleDirection(tab = true, control = true, shift = false))
        assertEquals(-1, ClientChatScreenBridge.cycleDirection(tab = true, control = true, shift = true))
        assertEquals(0, ClientChatScreenBridge.cycleDirection(tab = false, control = true, shift = true))
    }

    @Test
    fun `switching and cycling preserve each channel draft and scroll`() {
        ClientChatTabs.setAvailable(ChatNetwork.LIQUIDBOUNCE, true)
        ClientChatTabs.setAvailable(ChatNetwork.FDPCLIENT, true)
        ClientChatTabs.setDraft(ChatNetwork.LIQUIDBOUNCE, "LB draft")
        ClientChatTabs.setDraft(ChatNetwork.FDPCLIENT, "FDP draft")
        ClientChatTabs.setScrollPosition(ChatNetwork.LIQUIDBOUNCE, 7)
        ClientChatTabs.setScrollPosition(ChatNetwork.FDPCLIENT, 9)

        val liquidBounce = ClientChatScreenBridge.switchTo("liquidbounce", "Minecraft draft", 3)!!
        val fdp = ClientChatScreenBridge.cycle(1, "Edited LB draft", 5)

        assertEquals(ChatTabTransition("LB draft", 7), liquidBounce)
        assertEquals(ChatTabTransition("FDP draft", 9), fdp)
        assertEquals("Edited LB draft", ClientChatTabs.draft(ChatNetwork.LIQUIDBOUNCE))
        assertEquals(5, ClientChatTabs.scrollPosition(ChatNetwork.LIQUIDBOUNCE))
    }

    @Test
    fun `visible tabs use separate labels icons and unread counts`() {
        ClientChatTabs.setAvailable(ChatNetwork.LIQUIDBOUNCE, true)
        ClientChatTabs.setAvailable(ChatNetwork.FDPCLIENT, true)
        ClientChatTabs.incrementUnread(ChatNetwork.FDPCLIENT, 12)

        val tabs = ClientChatScreenBridge.visibleTabs()

        assertEquals(listOf("Minecraft", "LiquidBounce", "FDPClient (12)"), tabs.map(ChatTabView::label))
        assertEquals(ChatNetwork.entries.map(ChatNetwork::icon), tabs.map(ChatTabView::icon))
        assertTrue(tabs.first().selected)
        assertFalse(tabs.last().selected)
    }

    @Test
    fun `legacy AxoChat is one honest combined tab`() {
        ClientChatTabs.setAvailable(ChatNetwork.LIQUIDBOUNCE, true)

        assertEquals(
            listOf("Minecraft", "LiquidBounce/FDP"),
            ClientChatScreenBridge.visibleTabs().map(ChatTabView::label),
        )
    }

    @Test
    fun `slash-prefilled chat always opens the Minecraft tab`() {
        ClientChatTabs.setAvailable(ChatNetwork.FDPCLIENT, true)
        ClientChatTabs.switchTo(ChatNetwork.FDPCLIENT)
        ClientChatTabs.setDraft(ChatNetwork.FDPCLIENT, "external draft")

        val transition = ClientChatScreenBridge.initialState("/")

        assertEquals(ChatNetwork.MINECRAFT, ClientChatTabs.activeNetwork)
        assertEquals(ChatTabTransition("/", 0), transition)
        assertEquals("external draft", ClientChatTabs.draft(ChatNetwork.FDPCLIENT))
    }

    @Test
    fun `chat channels use distinct brand colors`() {
        val liquidBounceColor = Color4b.fromHex("#0080FF")

        assertEquals(
            ClientBrandColors.color(ClientBrand.LIQUIDBOUNCE, liquidBounceColor),
            chatNetworkColor(ChatNetwork.LIQUIDBOUNCE, liquidBounceColor),
        )
        assertEquals(Color4b.fromHex("#FF5C5C"), chatNetworkColor(ChatNetwork.FDPCLIENT, liquidBounceColor))
        assertEquals(Color4b.WHITE, chatNetworkColor(ChatNetwork.MINECRAFT, liquidBounceColor))
    }
}
