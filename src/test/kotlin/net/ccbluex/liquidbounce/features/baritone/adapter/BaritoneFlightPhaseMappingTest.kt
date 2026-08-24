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
package net.ccbluex.liquidbounce.features.baritone.adapter

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationPhase
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePhase
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaritoneFlightPhaseMappingTest {

    @Test
    fun `upstream command flight remains visible while its native process is temporarily paused`() {
        assertEquals(BaritonePhase.CALCULATING, navigationBaritonePhase(BaritoneNavigationPhase.PLANNING))
        assertEquals(BaritonePhase.CALCULATING, navigationBaritonePhase(BaritoneNavigationPhase.ARMING))
        assertEquals(BaritonePhase.PATHING, navigationBaritonePhase(BaritoneNavigationPhase.FLYING))
    }

    @Test
    fun `native walking phases remain authoritative`() {
        assertNull(navigationBaritonePhase(BaritoneNavigationPhase.IDLE))
        assertNull(navigationBaritonePhase(BaritoneNavigationPhase.WALK_FALLBACK))
    }

    @Test
    fun `terminal arrival and failure survive the cancellation emitted during cleanup`() {
        assertTrue(preservesResultAfterCancellation(BaritonePhase.ARRIVED))
        assertTrue(preservesResultAfterCancellation(BaritonePhase.FAILED))
        assertFalse(preservesResultAfterCancellation(BaritonePhase.PATHING))
    }
}
