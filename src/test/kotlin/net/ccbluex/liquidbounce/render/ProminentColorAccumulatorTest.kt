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
package net.ccbluex.liquidbounce.render

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.util.ARGB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProminentColorAccumulatorTest {

    @Test
    fun `neighboring shades form one prominent color family`() {
        val accumulator = ProminentColorAccumulator()

        accumulator.add(ARGB.color(255, 200, 70, 40), weight = 3)
        accumulator.add(ARGB.color(255, 214, 84, 54), weight = 3)
        accumulator.add(ARGB.color(255, 20, 80, 210), weight = 4)

        assertEquals(Color4b(207, 77, 47), accumulator.prominentColor())
    }

    @Test
    fun `transparent texture pixels cannot become prominent`() {
        val accumulator = ProminentColorAccumulator()

        accumulator.add(ARGB.color(0, 40, 220, 80), weight = 100)
        accumulator.add(ARGB.color(255, 12, 34, 56))

        assertEquals(Color4b(12, 34, 56), accumulator.prominentColor())
    }

    @Test
    fun `surface weight determines which texture color is prominent`() {
        val accumulator = ProminentColorAccumulator()

        accumulator.add(ARGB.color(255, 30, 60, 190), weight = 1)
        accumulator.add(ARGB.color(255, 210, 90, 45), weight = 4)

        assertEquals(Color4b(210, 90, 45), accumulator.prominentColor())
    }

    @Test
    fun `fully transparent textures have no prominent color`() {
        val accumulator = ProminentColorAccumulator()

        accumulator.add(ARGB.color(0, 255, 0, 255))

        assertNull(accumulator.prominentColor())
    }
}
