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
package net.ccbluex.liquidbounce.integration.theme.component.components.seedcracker

import net.ccbluex.liquidbounce.features.module.modules.render.HudTheme
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class SeedCrackerHudChromeTest {

    @Test
    fun `bundled classic HUD uses LiquidBounce card tokens and live colors`() {
        val liveAccent = Color4b(120, 151, 214)
        val classicSurface = Color4b(18, 9, 0)
        val chrome = resolveSeedCrackerHudChrome(
            hudTheme = HudTheme.CLASSIC,
            bundledHud = true,
            hudAccent = liveAccent,
            classicSurface = classicSurface,
        )

        assertEquals(classicSurface.alpha(128), chrome.backgroundColor)
        assertEquals(classicSurface.alpha(173), chrome.headerBackgroundColor)
        assertEquals(Color4b.TRANSPARENT, chrome.borderColor)
        assertEquals(liveAccent, chrome.accentColor)
        assertEquals(Color4b.WHITE, chrome.titleColor)
        assertEquals(Color4b(211, 211, 211), chrome.bodyColor)
        assertEquals(liveAccent, chrome.actionColor)
        assertEquals(Color4b(255, 255, 255, 20), chrome.progressTrackColor)
        assertEquals(5.0F, chrome.cornerRadius)
        assertEquals(0.0F, chrome.outlineWidth)
    }

    @Test
    fun `classic surface applies the same eighteen percent live Tint mix as the web HUD`() {
        val surface = resolveClassicHudSurface(
            defaultTint = Color4b.BLACK,
            configuredTint = Color4b(100, 50, 0),
        )

        assertEquals(Color4b(18, 9, 0), surface)
    }

    @Test
    fun `bundled modern HUD uses graphite glass and the live accent`() {
        val liveAccent = Color4b(120, 151, 214)
        val chrome = resolveSeedCrackerHudChrome(
            hudTheme = HudTheme.MODERN,
            bundledHud = true,
            hudAccent = liveAccent,
        )

        assertEquals(Color4b(15, 18, 23, 230), chrome.backgroundColor)
        assertEquals(chrome.backgroundColor, chrome.headerBackgroundColor)
        assertEquals(Color4b(255, 255, 255, 26), chrome.borderColor)
        assertEquals(liveAccent, chrome.accentColor)
        assertEquals(Color4b(238, 241, 245), chrome.titleColor)
        assertEquals(Color4b(145, 154, 166), chrome.bodyColor)
        assertEquals(liveAccent.interpolateTo(Color4b.WHITE, 0.18), chrome.actionColor)
        assertEquals(Color4b(255, 255, 255, 18), chrome.progressTrackColor)
        assertEquals(10.0F, chrome.cornerRadius)
        assertEquals(1.0F, chrome.outlineWidth)
    }

    @Test
    fun `external HUD themes retain the complete classic fallback`() {
        val external = resolveSeedCrackerHudChrome(
            hudTheme = HudTheme.MODERN,
            bundledHud = false,
            hudAccent = Color4b.RED,
            classicSurface = Color4b.GREEN,
        )

        assertEquals(Color4b(24, 24, 32, 176), external.backgroundColor)
        assertEquals(external.backgroundColor, external.headerBackgroundColor)
        assertEquals(Color4b(255, 255, 255, 24), external.progressTrackColor)
        assertNotEquals(Color4b.RED, external.accentColor)
        assertNotEquals(Color4b.GREEN.alpha(128), external.backgroundColor)
    }
}
