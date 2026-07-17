/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.misc.playercheatdetector

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DetectorWorkBudgetTest {

    @Test
    fun `movement sampling is throttled by configured interval`() {
        val budget = DetectorWorkBudget()

        assertTrue(budget.shouldSampleMovement(tick = 10, intervalTicks = 3))
        assertFalse(budget.shouldSampleMovement(tick = 11, intervalTicks = 3))
        assertFalse(budget.shouldSampleMovement(tick = 12, intervalTicks = 3))
        assertTrue(budget.shouldSampleMovement(tick = 13, intervalTicks = 3))
    }

    @Test
    fun `block actions are capped per tick and reset on the next tick`() {
        val budget = DetectorWorkBudget()

        assertTrue(budget.tryConsumeBlockAction(tick = 20, maxActionsPerTick = 2))
        assertTrue(budget.tryConsumeBlockAction(tick = 20, maxActionsPerTick = 2))
        assertFalse(budget.tryConsumeBlockAction(tick = 20, maxActionsPerTick = 2))
        assertTrue(budget.tryConsumeBlockAction(tick = 21, maxActionsPerTick = 2))
    }

    @Test
    fun `zero block budget disables block action processing`() {
        val budget = DetectorWorkBudget()

        assertFalse(budget.tryConsumeBlockAction(tick = 20, maxActionsPerTick = 0))
    }
}
