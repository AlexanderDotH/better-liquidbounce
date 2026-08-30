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

package net.ccbluex.liquidbounce.features.module.modules.render.customambience

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CustomAmbienceTimePolicyTest {

    @Test
    fun `disabled and no-change ambience preserve the server clock`() {
        assertEquals(7_321L, resolveWorldClockTime(false, ModuleCustomAmbience.TimeType.NIGHT, 7_321L))
        assertEquals(7_321L, resolveWorldClockTime(true, ModuleCustomAmbience.TimeType.NO_CHANGE, 7_321L))
    }

    @Test
    fun `configured ambience times retain their historical ticks`() {
        assertEquals(23_041L, resolveWorldClockTime(true, ModuleCustomAmbience.TimeType.DAWN, 0L))
        assertEquals(1_000L, resolveWorldClockTime(true, ModuleCustomAmbience.TimeType.DAY, 0L))
        assertEquals(6_000L, resolveWorldClockTime(true, ModuleCustomAmbience.TimeType.NOON, 0L))
        assertEquals(12_610L, resolveWorldClockTime(true, ModuleCustomAmbience.TimeType.DUSK, 0L))
        assertEquals(13_000L, resolveWorldClockTime(true, ModuleCustomAmbience.TimeType.NIGHT, 0L))
        assertEquals(18_000L, resolveWorldClockTime(true, ModuleCustomAmbience.TimeType.MID_NIGHT, 0L))
    }
}
