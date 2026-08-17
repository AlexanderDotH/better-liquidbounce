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
package net.ccbluex.liquidbounce.features.module.modules.misc.betterchat

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatNameHighlightPolicyTest {

    @Test
    fun `matching message color follows vanilla chat visibility`() {
        val color = Color4b(255, 196, 0, 68)

        val highlighted = ChatNameHighlightPolicy.colorFor(
            message = "Alex_42, look here",
            playerName = "Alex_42",
            color = color,
            visibility = 0.5f,
        )

        assertEquals(color.with(a = 34).argb, highlighted)
    }

    @Test
    fun `non-matching message has no highlight color`() {
        assertNull(
            ChatNameHighlightPolicy.colorFor(
                message = "general chat message",
                playerName = "Alex_42",
                color = Color4b(255, 196, 0, 68),
                visibility = 1f,
            ),
        )
    }
}
