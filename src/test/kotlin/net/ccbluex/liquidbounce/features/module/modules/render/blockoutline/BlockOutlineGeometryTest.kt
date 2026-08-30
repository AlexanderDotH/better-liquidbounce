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

package net.ccbluex.liquidbounce.features.module.modules.render.blockoutline

import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BlockOutlineGeometryTest {

    @Test
    fun `side-only outline collapses the selected face`() {
        flattenedFaces().forEach { (side, expected) ->
            assertEquals(expected, flattenBlockOutlineBox(BOX, side))
        }
    }

    companion object {
        private val BOX = AABB(1.0, 2.0, 3.0, 4.0, 5.0, 6.0)

        @JvmStatic
        fun flattenedFaces() = listOf(
            Direction.UP to AABB(1.0, 5.0, 3.0, 4.0, 5.0, 6.0),
            Direction.DOWN to AABB(1.0, 2.0, 3.0, 4.0, 2.0, 6.0),
            Direction.NORTH to AABB(1.0, 2.0, 3.0, 4.0, 5.0, 3.0),
            Direction.SOUTH to AABB(1.0, 2.0, 6.0, 4.0, 5.0, 6.0),
            Direction.WEST to AABB(1.0, 2.0, 3.0, 1.0, 5.0, 6.0),
            Direction.EAST to AABB(4.0, 2.0, 3.0, 4.0, 5.0, 6.0),
        )
    }
}
