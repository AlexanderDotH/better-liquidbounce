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

package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel

import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.model.BhopStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BhopAnimationMathTest {

    @Test
    fun `style timing retains the normal low-hop and strafe multipliers`() {
        assertEquals(400L, BhopAnimationMath.styleInterval(BhopStyle.NORMAL, 400))
        assertEquals(300L, BhopAnimationMath.styleInterval(BhopStyle.LOW_HOP, 400))
        assertEquals(340L, BhopAnimationMath.styleInterval(BhopStyle.STRAFE, 400))
    }

    @Test
    fun `smooth stop interpolates only while strength falls`() {
        assertEquals(1.0, BhopAnimationMath.updateStrength(0.25, 1.0, 50, 200), 1.0E-9)
        assertEquals(0.75, BhopAnimationMath.updateStrength(1.0, 0.0, 50, 200), 1.0E-9)
        assertEquals(0.0, BhopAnimationMath.updateStrength(1.0, 0.0, 50, 0), 1.0E-9)
    }
}
