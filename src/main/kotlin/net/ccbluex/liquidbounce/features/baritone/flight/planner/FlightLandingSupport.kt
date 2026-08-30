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

import kotlin.math.floor
import kotlin.math.min

internal class FlightLandingSupport(
    private val collisionIndex: FlightCollisionIndex,
) {
    fun fullySupports(box: FlightAabb): Boolean = supportPoints(box).all { (x, z) ->
        hasSupportAt(x, box.minY, z)
    }

    private fun hasSupportAt(x: Double, feetY: Double, z: Double): Boolean {
        val below = FlightCell(floor(x).toInt(), floor(Math.nextDown(feetY)).toInt(), floor(z).toInt())
        if (!collisionIndex.isLoaded(below)) return false
        return collisionIndex.collisions(below).any { collision -> collision.supports(x, feetY, z) }
    }

    private fun FlightAabb.supports(x: Double, feetY: Double, z: Double): Boolean =
        containsHorizontalPoint(x, z) && maxY <= feetY + FLIGHT_GEOMETRY_EPSILON &&
            maxY >= feetY - MAX_SUPPORT_GAP

    private fun FlightAabb.containsHorizontalPoint(x: Double, z: Double): Boolean =
        x >= minX - FLIGHT_GEOMETRY_EPSILON && x <= maxX + FLIGHT_GEOMETRY_EPSILON &&
            z >= minZ - FLIGHT_GEOMETRY_EPSILON && z <= maxZ + FLIGHT_GEOMETRY_EPSILON

    private fun supportPoints(box: FlightAabb): List<Pair<Double, Double>> {
        val insetX = min(SUPPORT_INSET, (box.maxX - box.minX) / 4.0)
        val insetZ = min(SUPPORT_INSET, (box.maxZ - box.minZ) / 4.0)
        val west = box.minX + insetX
        val east = box.maxX - insetX
        val north = box.minZ + insetZ
        val south = box.maxZ - insetZ
        return listOf(
            west to north,
            west to south,
            east to north,
            east to south,
            (box.minX + box.maxX) / 2.0 to (box.minZ + box.maxZ) / 2.0,
        )
    }

    private companion object {
        const val MAX_SUPPORT_GAP = 0.0625
        const val SUPPORT_INSET = 1.0E-4
    }
}
