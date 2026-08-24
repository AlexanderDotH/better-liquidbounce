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
package net.ccbluex.liquidbounce.features.baritone.adapter

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneHorizontalPosition
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaritoneExploreFilterTest {

    @Test
    fun `bounded explore includes nearby chunks and excludes chunks beyond the radius`() {
        val chunks = BaritoneExploreFilter.chunks(BaritoneHorizontalPosition(8, 8), radius = 32)

        assertTrue(BaritoneHorizontalPosition(0, 0) in chunks)
        assertTrue(BaritoneHorizontalPosition(1, 0) in chunks)
        assertFalse(BaritoneHorizontalPosition(4, 0) in chunks)
    }
}
