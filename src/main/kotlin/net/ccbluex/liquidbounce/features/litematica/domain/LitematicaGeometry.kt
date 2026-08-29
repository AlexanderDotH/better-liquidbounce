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

package net.ccbluex.liquidbounce.features.litematica.domain

import kotlin.math.max
import kotlin.math.min

data class LitematicaPoint(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) {
            "Litematica point coordinates must be finite"
        }
    }
}

data class LitematicaPosition(
    val x: Int,
    val y: Int,
    val z: Int,
) : Comparable<LitematicaPosition> {

    fun distanceSquaredTo(point: LitematicaPoint): Double {
        val xDistance = x + BLOCK_CENTER - point.x
        val yDistance = y + BLOCK_CENTER - point.y
        val zDistance = z + BLOCK_CENTER - point.z
        return xDistance * xDistance + yDistance * yDistance + zDistance * zDistance
    }

    override fun compareTo(other: LitematicaPosition): Int = compareValuesBy(
        this,
        other,
        LitematicaPosition::x,
        LitematicaPosition::y,
        LitematicaPosition::z,
    )

    private companion object {
        const val BLOCK_CENTER = 0.5
    }
}

data class LitematicaBounds(
    val min: LitematicaPosition,
    val max: LitematicaPosition,
) {
    init {
        require(min.x <= max.x && min.y <= max.y && min.z <= max.z) {
            "Litematica bounds must be ordered on every axis"
        }
    }

    fun contains(position: LitematicaPosition): Boolean = position.x in min.x..max.x &&
        position.y in min.y..max.y && position.z in min.z..max.z

    fun distanceSquaredTo(point: LitematicaPoint): Double {
        val xDistance = axisDistance(point.x, min.x, max.x)
        val yDistance = axisDistance(point.y, min.y, max.y)
        val zDistance = axisDistance(point.z, min.z, max.z)
        return xDistance * xDistance + yDistance * yDistance + zDistance * zDistance
    }

    private fun axisDistance(coordinate: Double, minimum: Int, maximum: Int): Double {
        val closest = coordinate.coerceIn(minimum.toDouble(), maximum.toDouble() + 1.0)
        return coordinate - closest
    }

    companion object {
        fun enclosing(positions: Collection<LitematicaPosition>): LitematicaBounds {
            require(positions.isNotEmpty()) { "Cannot create Litematica bounds from no positions" }
            return positions.drop(1).fold(LitematicaBounds(positions.first(), positions.first())) { bounds, position ->
                LitematicaBounds(
                    min = LitematicaPosition(
                        min(bounds.min.x, position.x),
                        min(bounds.min.y, position.y),
                        min(bounds.min.z, position.z),
                    ),
                    max = LitematicaPosition(
                        max(bounds.max.x, position.x),
                        max(bounds.max.y, position.y),
                        max(bounds.max.z, position.z),
                    ),
                )
            }
        }
    }
}
