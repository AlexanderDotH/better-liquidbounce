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

import net.ccbluex.liquidbounce.test.assertVec3Equals
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VelocityPositionMathTest {

    @Test
    fun `desync cap retains nearby predictions and clamps distant ones`() {
        assertEquals(Vec3(2.0, 0.0, 0.0), VelocityPositionMath.capDesync(Vec3(2.0, 0.0, 0.0), Vec3.ZERO, 3f))
        assertEquals(Vec3(3.0, 0.0, 0.0), VelocityPositionMath.capDesync(Vec3(10.0, 0.0, 0.0), Vec3.ZERO, 3f))
    }

    @Test
    fun `tiny recoil follows the hit direction without exceeding its cap`() {
        assertEquals(Vec3.ZERO, VelocityPositionMath.tinyRecoil(Vec3.ZERO, Vec3(10.0, 0.0, 0.0), 0f))
        assertVec3Equals(
            Vec3(0.2, 0.0, 0.0),
            VelocityPositionMath.tinyRecoil(Vec3.ZERO, Vec3(10.0, 0.0, 0.0), 0.2f),
            tolerance = 1.0E-8,
        )
    }

    @Test
    fun `recovery interpolation keeps the configured fraction`() {
        assertEquals(Vec3(2.5, 5.0, 7.5), VelocityPositionMath.lerp(Vec3.ZERO, Vec3(10.0, 20.0, 30.0), 0.25f))
    }
}
