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
import net.minecraft.client.gui.Hud
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModernContextualBarTest {

    @Test
    fun `only the visible bundled modern hotbar owns contextual rendering`() {
        fun enabled(
            hudRunning: Boolean = true,
            appearanceHidden: Boolean = false,
            hudTheme: HudTheme = HudTheme.MODERN,
            bundledHud: Boolean = true,
            hotbarEnabled: Boolean = true,
        ) = resolveModernContextualBarPolicy(
            hudRunning,
            appearanceHidden,
            hudTheme,
            bundledHud,
            hotbarEnabled,
        )

        assertTrue(enabled())
        assertFalse(enabled(hudRunning = false))
        assertFalse(enabled(appearanceHidden = true))
        assertFalse(enabled(hudTheme = HudTheme.CLASSIC))
        assertFalse(enabled(bundledHud = false))
        assertFalse(enabled(hotbarEnabled = false))
    }

    @Test
    fun `modern ownership preserves vanilla contextual state before suppressing its pixels`() {
        Hud.ContextualInfo.entries.forEach { contextualInfo ->
            assertEquals(
                contextualInfo,
                resolveContextualInfoForPresentation(
                    original = contextualInfo,
                    disableExperienceBar = true,
                    modernContextualBar = true,
                ),
            )
        }

        assertEquals(
            Hud.ContextualInfo.EMPTY,
            resolveContextualInfoForPresentation(
                original = Hud.ContextualInfo.EXPERIENCE,
                disableExperienceBar = true,
                modernContextualBar = false,
            ),
        )
        assertEquals(
            Hud.ContextualInfo.LOCATOR,
            resolveContextualInfoForPresentation(
                original = Hud.ContextualInfo.LOCATOR,
                disableExperienceBar = true,
                modernContextualBar = false,
            ),
        )
    }

    @Test
    fun `browser progress is finite and clamped for xp and vehicle charge`() {
        assertEquals(0f, normalizeContextualProgress(Float.NaN))
        assertEquals(0f, normalizeContextualProgress(-0.2f))
        assertEquals(0.45f, normalizeContextualProgress(0.45f))
        assertEquals(1f, normalizeContextualProgress(1.2f))
    }
}
