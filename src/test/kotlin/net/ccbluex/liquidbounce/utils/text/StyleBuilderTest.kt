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
package net.ccbluex.liquidbounce.utils.text

import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.resources.Identifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class StyleBuilderTest {

    @Test
    fun `empty builder returns the canonical empty style`() {
        assertSame(Style.EMPTY, StyleBuilder().build())
    }

    @Test
    fun `builder preserves all base values without overrides`() {
        val base = fullStyle(0x123456, 0x789ABC, "base")

        assertEquals(base, StyleBuilder(base).build())
    }

    @Test
    fun `builder applies every explicit override`() {
        val expected = fullStyle(0xABCDEF, 0x135724, "override")
        val actual = StyleBuilder(fullStyle(0x123456, 0x789ABC, "base")).apply {
            color = expected.color
            shadowColor = expected.shadowColor
            bold = expected.isBold
            italic = expected.isItalic
            underlined = expected.isUnderlined
            strikethrough = expected.isStrikethrough
            obfuscated = expected.isObfuscated
            clickEvent = expected.clickEvent
            hoverEvent = expected.hoverEvent
            insertion = expected.insertion
            font = expected.font
        }.build()

        assertEquals(expected, actual)
    }

    private fun fullStyle(color: Int, shadowColor: Int, suffix: String): Style = Style.EMPTY
        .withColor(TextColor.fromRgb(color))
        .withShadowColor(shadowColor)
        .withBold(suffix == "override")
        .withItalic(suffix != "override")
        .withUnderlined(suffix == "override")
        .withStrikethrough(suffix != "override")
        .withObfuscated(suffix == "override")
        .withClickEvent(ClickEvent.RunCommand("/$suffix"))
        .withHoverEvent(HoverEvent.ShowText(Component.literal(suffix)))
        .withInsertion("insert-$suffix")
        .withFont(
            FontDescription.Resource(
                Identifier.fromNamespaceAndPath("liquidbounce_test", suffix),
            ),
        )
}
