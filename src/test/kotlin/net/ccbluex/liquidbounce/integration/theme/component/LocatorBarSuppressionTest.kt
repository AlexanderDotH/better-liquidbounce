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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocatorBarSuppressionTest {

    @Test
    fun `bundled modern HUD restores the locator bar`() {
        val suppress = resolveLocatorBarSuppression(
            tweakEnabled = true,
            hudTheme = HudTheme.MODERN,
            bundledHud = true,
        )

        assertFalse(suppress)
    }

    @Test
    fun `bundled classic HUD preserves locator bar suppression`() {
        val suppress = resolveLocatorBarSuppression(
            tweakEnabled = true,
            hudTheme = HudTheme.CLASSIC,
            bundledHud = true,
        )

        assertTrue(suppress)
    }

    @Test
    fun `external modern HUD preserves locator bar suppression`() {
        val suppress = resolveLocatorBarSuppression(
            tweakEnabled = true,
            hudTheme = HudTheme.MODERN,
            bundledHud = false,
        )

        assertTrue(suppress)
    }

    @Test
    fun `disabled tweak never suppresses the locator bar`() {
        HudTheme.entries.forEach { hudTheme ->
            assertFalse(
                resolveLocatorBarSuppression(
                    tweakEnabled = false,
                    hudTheme = hudTheme,
                    bundledHud = true,
                ),
            )
            assertFalse(
                resolveLocatorBarSuppression(
                    tweakEnabled = false,
                    hudTheme = hudTheme,
                    bundledHud = false,
                ),
            )
        }
    }
}
