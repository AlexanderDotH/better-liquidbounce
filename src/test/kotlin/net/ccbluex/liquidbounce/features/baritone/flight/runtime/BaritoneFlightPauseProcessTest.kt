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
package net.ccbluex.liquidbounce.features.baritone.flight.runtime

import baritone.api.process.PathingCommandType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaritoneFlightPauseProcessTest {

    @Test
    fun `flight lease temporarily pauses native locomotion without cancelling the owning process`() {
        var flightOwnsMovement = true
        val process = BaritoneFlightPauseProcess { flightOwnsMovement }

        assertTrue(process.isActive)
        assertTrue(process.isTemporary)
        assertTrue(process.priority() > 0.0)
        assertEquals(PathingCommandType.REQUEST_PAUSE, process.onTick(false, true).commandType)

        flightOwnsMovement = false

        assertFalse(process.isActive)
    }
}
