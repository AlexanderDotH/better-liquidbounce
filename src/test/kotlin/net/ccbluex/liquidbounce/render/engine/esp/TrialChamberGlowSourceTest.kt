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
package net.ccbluex.liquidbounce.render.engine.esp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrialChamberGlowSourceTest {

    @Test
    fun `Trial Chamber owns a stable dynamic glow layer before generic Block ESP`() {
        val source = EspGlowSource.TRIAL_CHAMBER

        assertEquals("Trial Chamber", source.displayName)
        assertFalse(source.useDepth)
        assertEquals(null, source.preparedLayer)
        assertTrue(source.ordinal > EspGlowSource.BASE_FINDER.ordinal)
        assertTrue(source.ordinal < EspGlowSource.BLOCK_ESP.ordinal)
    }
}
