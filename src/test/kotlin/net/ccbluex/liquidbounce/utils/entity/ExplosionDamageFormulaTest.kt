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
package net.ccbluex.liquidbounce.utils.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExplosionDamageFormulaTest {

    @Test
    fun `formula retains double precision until the final float conversion`() {
        val damage = ExplosionDamageFormula.calculate(
            distanceSquared = 10.0,
            explosionRange = 8.0F,
            exposure = 0.7F,
        )

        assertEquals(0x4031de9b495ea704L, damage.toRawBits())
        assertEquals(17.869556F, damage.toFloat())
    }

    @Test
    fun `formula retains vanilla boundary values`() {
        assertEquals(85.0, ExplosionDamageFormula.calculate(0.0, 12.0F, 1.0F))
        assertEquals(37.9140625, ExplosionDamageFormula.calculate(9.0, 12.0F, 0.75F))
        assertEquals(1.0, ExplosionDamageFormula.calculate(144.0, 12.0F, 1.0F))
    }
}
