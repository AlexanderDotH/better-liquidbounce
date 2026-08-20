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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MerchantCpsGateTest {

    @Test
    fun `each attempt waits for a randomized cps interval`() {
        val sampledCps = ArrayDeque(listOf(4, 8))
        val gate = MerchantCpsGate { sampledCps.removeFirst() }

        gate.recordAttempt(tick = 10, cps = 4..8)
        assertFalse(gate.canAttempt(tick = 14))
        assertTrue(gate.canAttempt(tick = 15))

        gate.recordAttempt(tick = 15, cps = 4..8)
        assertFalse(gate.canAttempt(tick = 17))
        assertTrue(gate.canAttempt(tick = 18))
    }

    @Test
    fun `cps period keeps its fractional duration`() {
        assertEquals(1.0, MerchantCpsGate.delayTicks(20))
        assertEquals(0.2, MerchantCpsGate.delayTicks(100))
    }

    @Test
    fun `reset permits an immediate attempt`() {
        val gate = MerchantCpsGate { 4 }
        gate.recordAttempt(tick = 10, cps = 4..8)

        gate.reset()

        assertTrue(gate.canAttempt(tick = 10))
    }

    @Test
    fun `fractional deadlines preserve eight cps instead of rounding every interval up`() {
        val gate = MerchantCpsGate { 8 }
        val clickTicks = mutableListOf<Int>()

        for (tick in 0 until 20) {
            if (!gate.canAttempt(tick)) {
                continue
            }
            clickTicks += tick
            gate.recordAttempt(tick, 8..8)
        }

        assertEquals(listOf(0, 3, 5, 8, 10, 13, 15, 18), clickTicks)
    }
}
