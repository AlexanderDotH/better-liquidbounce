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
package net.ccbluex.liquidbounce.features.rotation.contract

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RotationLagStateTest {

    @Test
    fun `unbound lag providers fail closed`() = RotationLagState.withProvidersForTest {
        assertFalse(RotationLagState.isFakeLagging())
    }

    @Test
    fun `either lag provider pauses rotation`() {
        RotationLagState.withProvidersForTest(blinkLag = { true }) {
            assertTrue(RotationLagState.isFakeLagging())
        }
        RotationLagState.withProvidersForTest(backtrackLag = { true }) {
            assertTrue(RotationLagState.isFakeLagging())
        }
    }

    @Test
    fun `blink lag retains original short circuit order`() {
        var backtrackQueried = false

        RotationLagState.withProvidersForTest(
            blinkLag = { true },
            backtrackLag = {
                backtrackQueried = true
                true
            },
        ) {
            assertTrue(RotationLagState.isFakeLagging())
            assertFalse(backtrackQueried)
        }
    }
}
