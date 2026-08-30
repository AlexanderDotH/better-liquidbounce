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

internal fun flattenBlockOutlineBox(box: AABB, side: Direction): AABB = when (side) {
    Direction.UP -> box.withY(box.maxY)
    Direction.DOWN -> box.withY(box.minY)
    Direction.NORTH -> box.withZ(box.minZ)
    Direction.SOUTH -> box.withZ(box.maxZ)
    Direction.WEST -> box.withX(box.minX)
    Direction.EAST -> box.withX(box.maxX)
}

private fun AABB.withX(x: Double) = AABB(x, minY, minZ, x, maxY, maxZ)

private fun AABB.withY(y: Double) = AABB(minX, y, minZ, maxX, y, maxZ)

private fun AABB.withZ(z: Double) = AABB(minX, minY, z, maxX, maxY, z)
