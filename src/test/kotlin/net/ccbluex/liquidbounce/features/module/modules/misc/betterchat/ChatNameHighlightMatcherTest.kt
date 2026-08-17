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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatNameHighlightMatcherTest {

    @Test
    fun `mixed-case player name surrounded by punctuation is highlighted`() {
        assertTrue(ChatNameHighlightMatcher.containsMention("Hey, aLeX_42!", "Alex_42"))
    }

    @Test
    fun `player name with one substituted character is highlighted`() {
        assertTrue(ChatNameHighlightMatcher.containsMention("hey Alix_42", "Alex_42"))
    }

    @Test
    fun `player name with one missing character is highlighted`() {
        assertTrue(ChatNameHighlightMatcher.containsMention("hey Alex_4", "Alex_42"))
    }

    @Test
    fun `player name with one extra character is highlighted`() {
        assertTrue(ChatNameHighlightMatcher.containsMention("hey Alexx_42", "Alex_42"))
    }

    @Test
    fun `player name with two edits is not highlighted`() {
        assertFalse(ChatNameHighlightMatcher.containsMention("hey Alix_43", "Alex_42"))
    }

    @Test
    fun `transposed characters are not treated as one edit`() {
        assertFalse(ChatNameHighlightMatcher.containsMention("hey Aelx_42", "Alex_42"))
    }

    @Test
    fun `player name embedded in a substantially longer username is not highlighted`() {
        assertFalse(ChatNameHighlightMatcher.containsMention("hey NotAlex_42", "Alex_42"))
    }
}
