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

package net.ccbluex.liquidbounce.integration.theme.component.components.minimap

import net.ccbluex.liquidbounce.features.module.modules.render.HudTheme
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class MinimapHudChromeTest {

    @Test
    fun `classic HUD retains the existing minimap chrome`() {
        val chrome = resolveMinimapHudChrome(HudTheme.CLASSIC, bundledHud = true)

        assertEquals(Color4b.DEFAULT_BG_COLOR, chrome.shadowColor)
        assertEquals(Color4b.WHITE, chrome.crosshairColor)
        assertEquals(Color4b.WHITE, chrome.borderColor)
        assertEquals(3.0F, chrome.shadowOffset)
        assertEquals(3.0F, chrome.shadowWidth)
    }

    @Test
    fun `modern chrome is used for the bundled HUD`() {
        val chrome = resolveMinimapHudChrome(HudTheme.MODERN, bundledHud = true)

        assertNotEquals(Color4b.DEFAULT_BG_COLOR, chrome.shadowColor)
        assertNotEquals(Color4b.WHITE, chrome.crosshairColor)
        assertNotEquals(Color4b.WHITE, chrome.borderColor)
        assertEquals(4.0F, chrome.shadowOffset)
        assertEquals(5.0F, chrome.shadowWidth)
    }

    @Test
    fun `external HUD themes retain the existing minimap chrome`() {
        val chrome = resolveMinimapHudChrome(HudTheme.MODERN, bundledHud = false)

        assertEquals(resolveMinimapHudChrome(HudTheme.CLASSIC, bundledHud = true), chrome)
    }
}
