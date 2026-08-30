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
package net.ccbluex.liquidbounce.features.module.modules.world.surround.planner

import net.ccbluex.liquidbounce.utils.block.DIRECTIONS_EXCLUDING_UP
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import kotlin.math.floor

internal object SurroundGeometry {

    fun addEntitySurround(
        entity: Entity,
        positions: HashSet<BlockPos>,
        blocked: HashSet<BlockPos>,
        y: Double,
        down: Boolean = false,
    ) {
        val box = entity.boundingBox
        val maxX = getMax(box, Direction.Axis.X)
        val maxZ = getMax(box, Direction.Axis.Z)
        val hole = listOf(
            BlockPos.containing(box.minX, y, box.minZ),
            BlockPos.containing(box.minX, y, maxZ),
            BlockPos.containing(maxX, y, box.minZ),
            BlockPos.containing(maxX, y, maxZ),
        )

        blocked.addAll(hole)
        val directions = if (down) DIRECTIONS_EXCLUDING_UP else Direction.BY_2D_DATA
        hole.forEach { holePos ->
            for (direction in directions) {
                val pos = holePos.relative(direction)
                if (holePos !in blocked) {
                    positions += pos
                }
            }
        }
    }

    fun getMax(boundingBox: AABB, axis: Direction.Axis): Double {
        val max = boundingBox.max(axis)
        val min = boundingBox.min(axis)
        return if (max == floor(min) + 1.0) min else max
    }
}
