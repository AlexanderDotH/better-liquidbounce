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
package net.ccbluex.liquidbounce.integration.theme.component.components.trialchamber

import net.ccbluex.liquidbounce.features.module.modules.render.HudTheme
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrialChamberHudChromeTest {

    @Test
    fun `bundled classic HUD uses the live LiquidBounce card colors`() {
        val liveAccent = Color4b(120, 151, 214)
        val classicSurface = Color4b(18, 9, 0)

        val chrome = resolveTrialChamberHudChrome(
            hudTheme = HudTheme.CLASSIC,
            bundledHud = true,
            hudAccent = liveAccent,
            classicSurface = classicSurface,
        )

        assertEquals(classicSurface.alpha(190), chrome.backgroundColor)
        assertEquals(Color4b.TRANSPARENT, chrome.borderColor)
        assertEquals(liveAccent, chrome.accentColor)
        assertEquals(Color4b(230, 233, 237), chrome.labelColor)
        assertEquals(Color4b.WHITE, chrome.valueColor)
        assertEquals(Color4b(255, 255, 255, 18), chrome.panelColor)
        assertEquals(Color4b(255, 255, 255, 30), chrome.dividerColor)
        assertEquals(5.0F, chrome.cornerRadius)
        assertEquals(3.0F, chrome.panelRadius)
        assertEquals(0.0F, chrome.outlineWidth)
        assertEquals(10.0F, chrome.backgroundBlurRadius)
    }

    @Test
    fun `classic surface follows the configured bundled HUD tint`() {
        val surface = resolveTrialChamberClassicSurface(
            defaultTint = Color4b.BLACK,
            configuredTint = Color4b(100, 50, 0),
        )

        assertEquals(Color4b(18, 9, 0), surface)
    }

    @Test
    fun `bundled modern HUD uses graphite glass and the live accent`() {
        val liveAccent = Color4b(120, 151, 214)

        val chrome = resolveTrialChamberHudChrome(
            hudTheme = HudTheme.MODERN,
            bundledHud = true,
            hudAccent = liveAccent,
        )

        assertEquals(Color4b(15, 18, 23, 214), chrome.backgroundColor)
        assertEquals(Color4b(255, 255, 255, 26), chrome.borderColor)
        assertEquals(liveAccent, chrome.accentColor)
        assertEquals(Color4b(220, 225, 232), chrome.labelColor)
        assertEquals(Color4b(238, 241, 245), chrome.valueColor)
        assertEquals(Color4b(255, 255, 255, 18), chrome.panelColor)
        assertEquals(Color4b(255, 255, 255, 30), chrome.dividerColor)
        assertEquals(10.0F, chrome.cornerRadius)
        assertEquals(5.0F, chrome.panelRadius)
        assertEquals(1.0F, chrome.outlineWidth)
        assertEquals(12.0F, chrome.backgroundBlurRadius)
    }

    @Test
    fun `external HUD themes keep the legacy TrialChamber card`() {
        val external = resolveTrialChamberHudChrome(
            hudTheme = HudTheme.MODERN,
            bundledHud = false,
            hudAccent = Color4b.RED,
            classicSurface = Color4b.GREEN,
        )

        assertEquals(Color4b(14, 17, 24, 205), external.backgroundColor)
        assertEquals(Color4b(235, 240, 245), external.valueColor)
        assertEquals(Color4b(255, 255, 255, 18), external.panelColor)
        assertEquals(Color4b(255, 255, 255, 30), external.dividerColor)
        assertEquals(0.0F, external.cornerRadius)
        assertEquals(0.0F, external.outlineWidth)
        assertEquals(8.0F, external.backgroundBlurRadius)
        assertNotEquals(Color4b.RED, external.accentColor)
        assertNotEquals(Color4b.GREEN.alpha(128), external.backgroundColor)
    }

    @Test
    fun `bundled themes keep secondary text high contrast on their card surfaces`() {
        val classic = resolveTrialChamberHudChrome(
            hudTheme = HudTheme.CLASSIC,
            bundledHud = true,
            classicSurface = Color4b(18, 9, 0),
        )
        val modern = resolveTrialChamberHudChrome(
            hudTheme = HudTheme.MODERN,
            bundledHud = true,
        )

        assertTrue(classic.backgroundColor.a >= 190)
        assertTrue(contrastRatio(classic.labelColor, classic.backgroundColor) >= 10.0)
        assertTrue(contrastRatio(modern.labelColor, modern.backgroundColor) >= 10.0)
    }

    private fun contrastRatio(foreground: Color4b, background: Color4b): Double {
        val lighter = maxOf(relativeLuminance(foreground), relativeLuminance(background))
        val darker = minOf(relativeLuminance(foreground), relativeLuminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color4b): Double =
        0.2126 * linearChannel(color.r) + 0.7152 * linearChannel(color.g) + 0.0722 * linearChannel(color.b)

    private fun linearChannel(channel: Int): Double {
        val normalized = channel / 255.0
        return if (normalized <= 0.04045) normalized / 12.92 else Math.pow((normalized + 0.055) / 1.055, 2.4)
    }
}
