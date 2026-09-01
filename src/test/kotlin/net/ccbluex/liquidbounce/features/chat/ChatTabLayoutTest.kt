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
    fun `narrow rows retain every full label inside the viewport`() {
        val tabs = listOf(
            ChatTabSpec("minecraft", "Minecraft", 48, selected = true),
            ChatTabSpec("axochat", "LiquidBounce/FDP (12)", 104),
            ChatTabSpec("essential", "Essential", 48),
        )

        val bounds = ChatTabLayout.arrange(tabs, viewportWidth = 50, rowTop = 20)

        assertEquals(tabs.map(ChatTabSpec::label), bounds.map(ChatTabBounds::label))
        assertTrue(bounds.all { it.width > 0 && it.left >= 2 && it.right <= 48 })
        assertTrue(bounds.zipWithNext().all { (left, right) -> left.right <= right.left })
    }

    @Test
    fun `hitboxes include their top left edges and exclude bottom right edges`() {
        val bounds = ChatTabLayout.arrange(
            listOf(
                ChatTabSpec("minecraft", "Minecraft", 20),
                ChatTabSpec("axochat", "LiquidBounce/FDP", 20),
            ),
            viewportWidth = 100,
            rowTop = 30,
        )
        val first = bounds.first()
        val second = bounds.last()

        assertEquals("minecraft", ChatTabLayout.hitTest(bounds, first.left.toDouble(), first.top.toDouble()))
        assertEquals("minecraft", ChatTabLayout.hitTest(bounds, first.right - 0.01, first.bottom - 0.01))
        assertNull(ChatTabLayout.hitTest(bounds, first.right.toDouble(), first.top.toDouble()))
        assertEquals("axochat", ChatTabLayout.hitTest(bounds, second.left.toDouble(), second.top.toDouble()))
        assertNull(ChatTabLayout.hitTest(bounds, second.left.toDouble(), second.bottom.toDouble()))
    }
}
