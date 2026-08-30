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
package net.ccbluex.liquidbounce.common.combat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CombatActivityTest {

    @Test
    fun `activity is read from the port for every query`() {
        var active = false

        CombatActivity.withPortForTest(CombatActivityPort { active }) {
            assertFalse(CombatActivity.isInCombat)
            active = true
            assertTrue(CombatActivity.isInCombat)
        }
    }

    @Test
    fun `activity defaults to inactive before client composition`() {
        CombatActivity.withPortForTest(null) {
            assertFalse(CombatActivity.isInCombat)
        }
    }
}
