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

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal object FlightSweptCollision {
    fun bodyIntersectsCell(from: FlightVec3, to: FlightVec3, body: FlightBodyBounds, cell: FlightCell) =
        bodyIntersects(
            from,
            to,
            body,
            FlightAabb(
                minX = cell.x.toDouble(),
                minY = cell.y.toDouble(),
                minZ = cell.z.toDouble(),
                maxX = cell.x + 1.0,
                maxY = cell.y + 1.0,
                maxZ = cell.z + 1.0,
            ),
        )

    fun bodyIntersects(
        from: FlightVec3,
        to: FlightVec3,
        body: FlightBodyBounds,
        obstacle: FlightAabb,
    ): Boolean {
        val expanded = FlightAabb(
            obstacle.minX - body.maxXOffset,
            obstacle.minY - body.maxYOffset,
            obstacle.minZ - body.maxZOffset,
            obstacle.maxX - body.minXOffset,
            obstacle.maxY - body.minYOffset,
            obstacle.maxZ - body.minZOffset,
        )
        return segmentEntersOpenBox(from, to, expanded)
    }

    fun enclosingBox(first: FlightAabb, second: FlightAabb) = FlightAabb(
        minX = min(first.minX, second.minX),
        minY = min(first.minY, second.minY),
        minZ = min(first.minZ, second.minZ),
        maxX = max(first.maxX, second.maxX),
        maxY = max(first.maxY, second.maxY),
        maxZ = max(first.maxZ, second.maxZ),
    )

    private fun segmentEntersOpenBox(from: FlightVec3, to: FlightVec3, box: FlightAabb): Boolean {
        var entry = 0.0
        var exit = 1.0
        val starts = doubleArrayOf(from.x, from.y, from.z)
        val deltas = doubleArrayOf(to.x - from.x, to.y - from.y, to.z - from.z)
        val minimums = doubleArrayOf(box.minX, box.minY, box.minZ)
        val maximums = doubleArrayOf(box.maxX, box.maxY, box.maxZ)

        for (axis in starts.indices) {
            val delta = deltas[axis]
            if (abs(delta) <= FLIGHT_GEOMETRY_EPSILON) {
                if (starts[axis] <= minimums[axis] || starts[axis] >= maximums[axis]) return false
                continue
            }
            val first = (minimums[axis] - starts[axis]) / delta
            val second = (maximums[axis] - starts[axis]) / delta
            entry = max(entry, min(first, second))
            exit = min(exit, max(first, second))
            if (entry >= exit - FLIGHT_GEOMETRY_EPSILON) return false
        }
        return entry < 1.0 - FLIGHT_GEOMETRY_EPSILON && exit > FLIGHT_GEOMETRY_EPSILON
    }
}

internal const val FLIGHT_GEOMETRY_EPSILON = 1.0E-9
