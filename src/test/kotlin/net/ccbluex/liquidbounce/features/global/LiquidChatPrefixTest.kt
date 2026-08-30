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
package net.ccbluex.liquidbounce.features.global

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.TextColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LiquidChatPrefixTest {

    @Test
    fun `prefix retains visible text and established colors`() {
        val prefix = createLiquidChatPrefix("ClientChat")

        assertEquals("ClientChat ▸ ", prefix.string)
        assertEquals(colorValue(ChatFormatting.GRAY), prefix.style.color?.value)
        assertEquals(colorValue(ChatFormatting.BLUE), prefix.siblings[0].style.color?.value)
        assertEquals(colorValue(ChatFormatting.DARK_GRAY), prefix.siblings[1].style.color?.value)
    }

    private fun colorValue(formatting: ChatFormatting): Int = TextColor.fromLegacyFormat(formatting)!!.value
}
