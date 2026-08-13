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

package net.ccbluex.liquidbounce.features.module.modules.combat

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillFailureNotificationGateTest {

    @Test
    fun `rapid route failures produce only one notification`() {
        val gate = SpearKillFailureNotificationGate(cooldownTicks = 200)

        assertTrue(gate.shouldNotify(currentTick = 100))
        assertFalse(gate.shouldNotify(currentTick = 101))
        assertFalse(gate.shouldNotify(currentTick = 299))
    }

    @Test
    fun `notification resumes after the cooldown or a lifecycle reset`() {
        val gate = SpearKillFailureNotificationGate(cooldownTicks = 200)

        assertTrue(gate.shouldNotify(currentTick = 100))
        assertTrue(gate.shouldNotify(currentTick = 300))
        assertFalse(gate.shouldNotify(currentTick = 301))

        gate.clear()
        assertTrue(gate.shouldNotify(currentTick = 0))
    }
}
