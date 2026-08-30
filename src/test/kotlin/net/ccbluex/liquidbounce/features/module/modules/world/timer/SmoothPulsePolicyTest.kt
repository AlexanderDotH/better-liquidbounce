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
package net.ccbluex.liquidbounce.features.module.modules.world.timer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SmoothPulsePolicyTest {

    @Test
    fun `delay controls the initial phase`() {
        assertEquals(SmoothPulsePhase.DELAY, SmoothPulsePolicy.initialPhase(durations(delay = 40)))
        assertEquals(SmoothPulsePhase.RAMP_UP, SmoothPulsePolicy.initialPhase(durations(delay = 0)))
    }

    @Test
    fun `zero-length hold and delay phases are skipped`() {
        val durations = durations(delay = 0, hold = 0)

        assertEquals(
            SmoothPulsePhase.RAMP_DOWN,
            SmoothPulsePolicy.nextActivePhase(SmoothPulsePhase.RAMP_UP, durations),
        )
        assertEquals(
            SmoothPulsePhase.RAMP_UP,
            SmoothPulsePolicy.nextActivePhase(SmoothPulsePhase.RAMP_DOWN, durations),
        )
    }

    @Test
    fun `phase speeds retain base hold and smooth ramp values`() {
        val durations = durations(delay = 40, rampUp = 8, hold = 4, rampDown = 8)

        assertEquals(1.0F, SmoothPulsePolicy.speed(SmoothPulsePhase.DELAY, 0, 1.0F, 1.16F, durations))
        assertEquals(1.16F, SmoothPulsePolicy.speed(SmoothPulsePhase.HOLD, 0, 1.0F, 1.16F, durations))
        assertEquals(
            1.006875F,
            SmoothPulsePolicy.speed(SmoothPulsePhase.RAMP_UP, 0, 1.0F, 1.16F, durations),
            0.000001F,
        )
        assertEquals(
            1.153125F,
            SmoothPulsePolicy.speed(SmoothPulsePhase.RAMP_DOWN, 0, 1.0F, 1.16F, durations),
            0.000001F,
        )
    }

    private fun durations(
        delay: Int,
        rampUp: Int = 8,
        hold: Int = 4,
        rampDown: Int = 8,
    ) = SmoothPulseDurations(delay, rampUp, hold, rampDown)
}
