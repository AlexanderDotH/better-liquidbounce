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

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatMessageRoutePolicyTest {

    @Test
    fun `only messages from the active network are visible`() {
        assertTrue(ChatMessageRoutePolicy.isVisible(ChatNetwork.AXOCHAT, ChatNetwork.AXOCHAT))
        assertFalse(ChatMessageRoutePolicy.isVisible(ChatNetwork.MINECRAFT, ChatNetwork.AXOCHAT))
    }

    @Test
    fun `history keeps the newest one hundred messages from each network`() {
        val messages = (0..ChatMessageRoutePolicy.MAX_MESSAGES_PER_NETWORK).flatMap { index ->
            listOf(
                RoutedMessage(index, ChatNetwork.MINECRAFT),
                RoutedMessage(index, ChatNetwork.AXOCHAT),
            )
        }.toMutableList()

        ChatMessageRoutePolicy.prune(
            messages,
            infiniteHistory = false,
            networkOf = RoutedMessage::network,
        )

        assertEquals(100, messages.count { it.network == ChatNetwork.MINECRAFT })
        assertEquals(100, messages.count { it.network == ChatNetwork.AXOCHAT })
        assertEquals(99, messages.last { it.network == ChatNetwork.MINECRAFT }.id)
        assertEquals(99, messages.last { it.network == ChatNetwork.AXOCHAT }.id)

        val infiniteHistory = (0..ChatMessageRoutePolicy.MAX_MESSAGES_PER_NETWORK)
            .map { RoutedMessage(it, ChatNetwork.AXOCHAT) }
            .toMutableList()
        ChatMessageRoutePolicy.prune(
            infiniteHistory,
            infiniteHistory = true,
            networkOf = RoutedMessage::network,
        )
        assertEquals(101, infiniteHistory.size)
    }

    @Test
    fun `route clear removes only the active network unless all history is requested`() {
        assertTrue(ChatMessageRoutePolicy.shouldClear(ChatNetwork.AXOCHAT, ChatNetwork.AXOCHAT, clearAll = false))
        assertFalse(ChatMessageRoutePolicy.shouldClear(ChatNetwork.MINECRAFT, ChatNetwork.AXOCHAT, clearAll = false))
        assertTrue(ChatMessageRoutePolicy.shouldClear(ChatNetwork.MINECRAFT, ChatNetwork.AXOCHAT, clearAll = true))
    }

    @Test
    fun `queue routing retains the native Minecraft visibility filter`() {
        val source = Path.of(
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/gui/MixinChatComponent.java"
        ).toFile().readText()

        assertFalse(source.contains("@Inject(method = \"addMessageToQueue\", at = @At(\"HEAD\"), cancellable = true)"))
        assertTrue(source.contains("@ModifyExpressionValue("))
        assertTrue(source.contains("method = \"addMessageToQueue\""))
    }

    private data class RoutedMessage(val id: Int, val network: ChatNetwork)
}
