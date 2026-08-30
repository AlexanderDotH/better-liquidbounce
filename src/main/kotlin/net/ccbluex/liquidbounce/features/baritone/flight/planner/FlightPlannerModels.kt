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

import kotlin.math.sqrt

data class FlightVec3(val x: Double, val y: Double, val z: Double) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "Flight coordinates must be finite" }
    }

    fun distanceTo(other: FlightVec3): Double {
        val deltaX = other.x - x
        val deltaY = other.y - y
        val deltaZ = other.z - z
        return sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
    }

    internal operator fun plus(offset: FlightCell) = FlightVec3(x + offset.x, y + offset.y, z + offset.z)
}

data class FlightCell(val x: Int, val y: Int, val z: Int)

data class FlightAabb(
    val minX: Double,
    val minY: Double,
    val minZ: Double,
    val maxX: Double,
    val maxY: Double,
    val maxZ: Double,
) {
    init {
        val coordinates = doubleArrayOf(minX, minY, minZ, maxX, maxY, maxZ)
        require(coordinates.all(Double::isFinite)) { "Flight collision coordinates must be finite" }
        require(minX < maxX && minY < maxY && minZ < maxZ) { "Flight collision boxes must have volume" }
    }

    internal fun intersects(other: FlightAabb): Boolean =
        overlaps(minX, maxX, other.minX, other.maxX) &&
            overlaps(minY, maxY, other.minY, other.maxY) &&
            overlaps(minZ, maxZ, other.minZ, other.maxZ)

    private fun overlaps(firstMin: Double, firstMax: Double, secondMin: Double, secondMax: Double) =
        firstMax > secondMin && firstMin < secondMax
}

data class FlightBodyBounds(
    val minXOffset: Double,
    val minYOffset: Double,
    val minZOffset: Double,
    val maxXOffset: Double,
    val maxYOffset: Double,
    val maxZOffset: Double,
) {
    init {
        val coordinates = doubleArrayOf(
            minXOffset,
            minYOffset,
            minZOffset,
            maxXOffset,
            maxYOffset,
            maxZOffset,
        )
        require(coordinates.all(Double::isFinite)) { "Player collision offsets must be finite" }
        require(minXOffset < maxXOffset && minYOffset < maxYOffset && minZOffset < maxZOffset) {
            "Player collision bounds must have volume"
        }
    }

    internal fun at(position: FlightVec3) = FlightAabb(
        minX = position.x + minXOffset,
        minY = position.y + minYOffset,
        minZ = position.z + minZOffset,
        maxX = position.x + maxXOffset,
        maxY = position.y + maxYOffset,
        maxZ = position.z + maxZOffset,
    )

    companion object {
        fun centered(width: Double, height: Double, depth: Double): FlightBodyBounds {
            require(width.isFinite() && width > 0.0) { "Player width must be positive and finite" }
            require(height.isFinite() && height > 0.0) { "Player height must be positive and finite" }
            require(depth.isFinite() && depth > 0.0) { "Player depth must be positive and finite" }
            return FlightBodyBounds(-width / 2.0, 0.0, -depth / 2.0, width / 2.0, height, depth / 2.0)
        }
    }
}
