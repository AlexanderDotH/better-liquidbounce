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

package net.ccbluex.liquidbounce.features.baritone.core

import java.util.PriorityQueue
import kotlin.math.abs

private const val COLLINEAR_EPSILON = 1.0E-12

/**
 * Reduces path geometry for UI transport while retaining endpoints and the strongest directional deviations.
 * Straight runs are collapsed exactly; oversized routes use a point-budgeted Douglas-Peucker subdivision.
 */
class BaritoneRouteSimplifier(
    private val maxPoints: Int = MAX_POINTS,
) {
    init {
        require(maxPoints >= 2) { "A route needs capacity for both endpoints" }
    }

    fun simplify(points: Iterable<BaritoneRoutePoint>): List<BaritoneRoutePoint> {
        val directionChanges = collapseStraightRuns(points)
        if (directionChanges.size <= maxPoints) {
            return immutableListCopy(directionChanges)
        }
        return immutableListCopy(selectStrongestDeviations(directionChanges))
    }

    private fun collapseStraightRuns(points: Iterable<BaritoneRoutePoint>): List<BaritoneRoutePoint> {
        val collapsed = ArrayList<BaritoneRoutePoint>()
        for (point in points) {
            if (collapsed.lastOrNull() == point) {
                continue
            }
            collapsed.add(point)
            collapseTrailingStraightRun(collapsed)
        }
        return collapsed
    }

    private fun collapseTrailingStraightRun(points: MutableList<BaritoneRoutePoint>) {
        while (points.size >= 3) {
            val first = points[points.lastIndex - 2]
            val middle = points[points.lastIndex - 1]
            val last = points[points.lastIndex]
            if (!continuesInSameDirection(first, middle, last)) {
                return
            }
            points.removeAt(points.lastIndex - 1)
        }
    }

    private fun selectStrongestDeviations(points: List<BaritoneRoutePoint>): List<BaritoneRoutePoint> {
        val selected = BooleanArray(points.size)
        selected[0] = true
        selected[selected.lastIndex] = true
        var selectedCount = 2
        val candidates = PriorityQueue(SEGMENT_ORDER)
        points.segment(0, points.lastIndex)?.let(candidates::add)

        while (selectedCount < maxPoints && candidates.isNotEmpty()) {
            val segment = candidates.remove()
            selected[segment.split] = true
            selectedCount++
            points.segment(segment.start, segment.split)?.let(candidates::add)
            points.segment(segment.split, segment.end)?.let(candidates::add)
        }

        return points.filterIndexed { index, _ -> selected[index] }
    }

    companion object {
        const val MAX_POINTS = 512

        private val SEGMENT_ORDER = compareByDescending<RouteSegment> { it.deviationSquared }
            .thenByDescending { it.end - it.start }
            .thenBy { it.start }

        private fun continuesInSameDirection(
            first: BaritoneRoutePoint,
            middle: BaritoneRoutePoint,
            last: BaritoneRoutePoint,
        ): Boolean {
            val firstVector = middle - first
            val secondVector = last - middle
            val squaredLengths = firstVector.lengthSquared * secondVector.lengthSquared
            val crossIsZero = firstVector.crossSquared(secondVector) <= COLLINEAR_EPSILON * squaredLengths
            return crossIsZero && firstVector.dot(secondVector) > 0.0
        }
    }
}

private data class RouteVector(val x: Double, val y: Double, val z: Double) {
    val lengthSquared: Double
        get() = dot(this)

    fun dot(other: RouteVector): Double = x * other.x + y * other.y + z * other.z

    fun crossSquared(other: RouteVector): Double {
        val crossX = y * other.z - z * other.y
        val crossY = z * other.x - x * other.z
        val crossZ = x * other.y - y * other.x
        return crossX * crossX + crossY * crossY + crossZ * crossZ
    }
}

private data class RouteSegment(
    val start: Int,
    val end: Int,
    val split: Int,
    val deviationSquared: Double,
)

private operator fun BaritoneRoutePoint.minus(other: BaritoneRoutePoint) =
    RouteVector(x - other.x, y - other.y, z - other.z)

private fun List<BaritoneRoutePoint>.segment(start: Int, end: Int): RouteSegment? {
    if (end - start <= 1) {
        return null
    }

    val midpoint = (start + end) / 2.0
    var split = start + 1
    var greatestDeviation = -1.0
    for (index in start + 1 until end) {
        val deviation = this[index].distanceSquaredToSegment(this[start], this[end])
        val isBetterTie = abs(deviation - greatestDeviation) <= COLLINEAR_EPSILON &&
            abs(index - midpoint) < abs(split - midpoint)
        if (deviation > greatestDeviation || isBetterTie) {
            split = index
            greatestDeviation = deviation
        }
    }
    return RouteSegment(start, end, split, greatestDeviation)
}

private fun BaritoneRoutePoint.distanceSquaredToSegment(
    start: BaritoneRoutePoint,
    end: BaritoneRoutePoint,
): Double {
    val segment = end - start
    val lengthSquared = segment.lengthSquared
    if (lengthSquared == 0.0) {
        return (this - start).lengthSquared
    }

    val projection = ((this - start).dot(segment) / lengthSquared).coerceIn(0.0, 1.0)
    val closest = BaritoneRoutePoint(
        start.x + projection * segment.x,
        start.y + projection * segment.y,
        start.z + projection * segment.z,
    )
    return (this - closest).lengthSquared
}
