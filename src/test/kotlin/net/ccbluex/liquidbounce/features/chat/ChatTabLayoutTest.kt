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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatTabLayoutTest {

    @Test
    fun `icon tabs stack beside the chat and end above the input`() {
        val tabs = listOf(
            ChatTabSpec("minecraft", "Minecraft", ChatNetwork.MINECRAFT.icon, 48, selected = true),
            ChatTabSpec("liquidbounce", "LiquidBounce", ChatNetwork.LIQUIDBOUNCE.icon, 72),
            ChatTabSpec("fdpclient", "FDPClient (12)", ChatNetwork.FDPCLIENT.icon, 76),
        )

        val bounds = ChatTabLayout.arrangeSide(
            tabs,
            viewportWidth = 320,
            requestedLeft = 120,
            bottom = 260,
        )

        assertEquals(tabs.map(ChatTabSpec::label), bounds.map(ChatTabBounds::label))
        assertEquals(tabs.map(ChatTabSpec::icon), bounds.map(ChatTabBounds::icon))
        assertTrue(bounds.all { it.left == 120 && it.right <= 318 && it.height == ChatTabLayout.ROW_HEIGHT })
        assertEquals(260, bounds.last().bottom)
        assertTrue(bounds.zipWithNext().all { (top, bottom) -> top.bottom < bottom.top })
    }

    @Test
    fun `hitboxes include their top left edges and exclude bottom right edges`() {
        val bounds = ChatTabLayout.arrangeSide(
            listOf(
                ChatTabSpec("minecraft", "Minecraft", ChatNetwork.MINECRAFT.icon, 20),
                ChatTabSpec("liquidbounce", "LiquidBounce", ChatNetwork.LIQUIDBOUNCE.icon, 20),
            ),
            viewportWidth = 200,
            requestedLeft = 100,
            bottom = 100,
        )
        val first = bounds.first()
        val second = bounds.last()

        assertEquals("minecraft", ChatTabLayout.hitTest(bounds, first.left.toDouble(), first.top.toDouble()))
        assertEquals("minecraft", ChatTabLayout.hitTest(bounds, first.right - 0.01, first.bottom - 0.01))
        assertNull(ChatTabLayout.hitTest(bounds, first.right.toDouble(), first.top.toDouble()))
        assertEquals("liquidbounce", ChatTabLayout.hitTest(bounds, second.left.toDouble(), second.top.toDouble()))
        assertNull(ChatTabLayout.hitTest(bounds, second.left.toDouble(), second.bottom.toDouble()))
    }

    private val ChatTabBounds.height
        get() = bottom - top
}
