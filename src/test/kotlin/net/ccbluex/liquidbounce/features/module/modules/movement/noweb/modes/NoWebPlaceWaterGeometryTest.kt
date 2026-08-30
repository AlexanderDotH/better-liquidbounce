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
package net.ccbluex.liquidbounce.features.module.modules.movement.noweb.modes

import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoWebPlaceWaterGeometryTest {

    @Test
    fun `face center keeps exact coordinates for every direction`() {
        val box = AABB(2.0, 4.0, 6.0, 6.0, 10.0, 14.0)
        val center = Vec3(4.0, 7.0, 10.0)
        val expected = mapOf(
            Direction.DOWN to Vec3(center.x, box.minY, center.z),
            Direction.UP to Vec3(center.x, box.maxY, center.z),
            Direction.NORTH to Vec3(center.x, center.y, box.minZ),
            Direction.SOUTH to Vec3(center.x, center.y, box.maxZ),
            Direction.WEST to Vec3(box.minX, center.y, center.z),
            Direction.EAST to Vec3(box.maxX, center.y, center.z),
        )

        expected.forEach { (direction, point) ->
            assertEquals(point, noWebCenterOnSide(box, direction), direction.name)
        }
    }

    @Test
    fun `interaction range is squared without rounding changes`() {
        assertEquals(20.25, noWebSquaredRange(4.5))
    }
}
