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

import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.model.ScaffoldYawMode
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScaffoldActionMathTest {

    @Test
    fun `stationary scaffold keeps the target view yaw`() {
        assertEquals(
            37f,
            ScaffoldActionMath.yaw(Vec3.ZERO, 37f, ScaffoldYawMode.MOVEMENT),
            1.0E-6f,
        )
    }

    @Test
    fun `reverse and snap modes retain their movement yaw policies`() {
        val movement = Vec3(0.0, 0.0, 1.0)
        assertEquals(-180f, ScaffoldActionMath.yaw(movement, 0f, ScaffoldYawMode.REVERSE), 1.0E-6f)
        assertEquals(0f, ScaffoldActionMath.yaw(movement, 0f, ScaffoldYawMode.SNAP_45), 1.0E-6f)
    }

    @Test
    fun `scaffold swing peaks halfway through its action window`() {
        assertEquals(1f, ScaffoldActionMath.swingProgress(110L, 220L), 1.0E-6f)
    }
}
