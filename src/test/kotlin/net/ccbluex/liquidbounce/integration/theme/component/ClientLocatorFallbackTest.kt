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

package net.ccbluex.liquidbounce.integration.theme.component

import net.ccbluex.liquidbounce.features.module.modules.render.HudTheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClientLocatorFallbackTest {

    @Test
    fun `only the visible bundled modern HUD enables client player markers`() {
        assertTrue(resolveClientLocatorFallbackPolicy(true, false, HudTheme.MODERN, true))
        assertFalse(resolveClientLocatorFallbackPolicy(false, false, HudTheme.MODERN, true))
        assertFalse(resolveClientLocatorFallbackPolicy(true, true, HudTheme.MODERN, true))
        assertFalse(resolveClientLocatorFallbackPolicy(true, false, HudTheme.CLASSIC, true))
        assertFalse(resolveClientLocatorFallbackPolicy(true, false, HudTheme.MODERN, false))
    }

    @Test
    fun `fallback is selected only when the server has no waypoints and a player is eligible`() {
        assertTrue(shouldUseClientLocatorFallback(false, true, true))
        assertFalse(shouldUseClientLocatorFallback(true, true, true))
        assertFalse(shouldUseClientLocatorFallback(false, false, true))
        assertFalse(shouldUseClientLocatorFallback(false, true, false))
    }

    @Test
    fun `fallback excludes players vanilla or local safety rules should hide`() {
        fun eligible(
            isLocal: Boolean = false,
            isSpectator: Boolean = false,
            isRemoved: Boolean = false,
            isAlive: Boolean = true,
            isBot: Boolean = false,
            hasPlayerInfo: Boolean = true,
            isCrouching: Boolean = false,
            isInvisible: Boolean = false,
        ) = isEligibleLocatorPlayer(
            isLocal,
            isSpectator,
            isRemoved,
            isAlive,
            isBot,
            hasPlayerInfo,
            isCrouching,
            isInvisible,
        )

        assertTrue(eligible())
        assertFalse(eligible(isLocal = true))
        assertFalse(eligible(isSpectator = true))
        assertFalse(eligible(isRemoved = true))
        assertFalse(eligible(isAlive = false))
        assertFalse(eligible(isBot = true))
        assertFalse(eligible(hasPlayerInfo = false))
        assertFalse(eligible(isCrouching = true))
        assertFalse(eligible(isInvisible = true))
    }

    @Test
    fun `player heads follow vanilla locator projection and visibility bounds`() {
        assertEquals(96, resolveLocatorMarkerX(guiWidth = 200, yawDegrees = 0.0))
        assertEquals(182, resolveLocatorMarkerX(guiWidth = 200, yawDegrees = 60.0))
        assertNull(resolveLocatorMarkerX(guiWidth = 200, yawDegrees = -60.0))
        assertNull(resolveLocatorMarkerX(guiWidth = 200, yawDegrees = 60.01))

        assertEquals(0.0, resolveLocatorMarkerOffset(0.0))
        assertEquals(1.0, resolveLocatorMarkerOffset(60.0))
        assertNull(resolveLocatorMarkerOffset(-60.0))
    }
}
