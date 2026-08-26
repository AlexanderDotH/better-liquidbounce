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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrialChamberHudTypographyTest {

    @Test
    fun `secondary labels stay readable without making the compact card oversized`() {
        assertEquals(0.84F, TrialChamberHudTypography.SECTION_LABEL_SCALE)
        assertEquals(0.86F, TrialChamberHudTypography.BADGE_TEXT_SCALE)
        assertEquals(0.76F, TrialChamberHudTypography.LOOT_LABEL_SCALE)
    }

    @Test
    fun `values retain clear visual priority at a compact scale`() {
        assertEquals(0.92F, TrialChamberHudTypography.INLINE_VALUE_SCALE)
        assertEquals(0.92F, TrialChamberHudTypography.LOOT_VALUE_SCALE)
    }

    @Test
    fun `native HUD text uses a shadow against changing world backgrounds`() {
        assertTrue(TrialChamberHudTypography.TEXT_SHADOW)
    }
}
