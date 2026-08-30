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
package net.ccbluex.liquidbounce.features.baritone.flight.planner

import java.util.Collections
import kotlin.math.ceil
import kotlin.math.floor

internal class FlightCollisionIndex(
    private val loadedCells: Set<FlightCell>,
    collisionBoxes: List<FlightAabb>,
) {
    private val collisionsByCell = indexCollisions(collisionBoxes)

    fun contains(box: FlightAabb): Boolean = cellsOccupiedBy(box).all(loadedCells::contains)

    fun candidates(query: FlightAabb): Set<FlightAabb> = buildSet {
        cellsOccupiedBy(query).forEach { cell -> collisionsByCell[cell]?.let(::addAll) }
    }

    fun isLoaded(cell: FlightCell): Boolean = cell in loadedCells

    fun collisions(cell: FlightCell): List<FlightAabb> = collisionsByCell[cell].orEmpty()

    fun occupiedCells(box: FlightAabb): Sequence<FlightCell> = cellsOccupiedBy(box)

    private fun indexCollisions(boxes: List<FlightAabb>): Map<FlightCell, List<FlightAabb>> {
        val mutable = HashMap<FlightCell, MutableList<FlightAabb>>()
        boxes.forEach { collision ->
            cellsOccupiedBy(collision).forEach { cell -> mutable.getOrPut(cell, ::mutableListOf) += collision }
        }
        return mutable.mapValues { (_, collisions) -> Collections.unmodifiableList(collisions.toList()) }
    }

    private fun cellsOccupiedBy(box: FlightAabb): Sequence<FlightCell> = sequence {
        for (x in cellRange(box.minX, box.maxX)) {
            for (y in cellRange(box.minY, box.maxY)) {
                for (z in cellRange(box.minZ, box.maxZ)) yield(FlightCell(x, y, z))
            }
        }
    }

    private fun cellRange(minimum: Double, maximum: Double): IntRange =
        floor(minimum).toInt()..(ceil(maximum).toInt() - 1)
}
