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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Immutable collision data captured on the Minecraft thread and safe to route against elsewhere. */
@Suppress("TooManyFunctions")
class FlightCollisionSnapshot(
    val revision: FlightWorldRevision,
    loadedCells: Collection<FlightCell>,
    collisionBoxes: Collection<FlightAabb>,
) {
    val loadedCells: Set<FlightCell> = Collections.unmodifiableSet(LinkedHashSet(loadedCells))
    val collisionBoxes: List<FlightAabb> = Collections.unmodifiableList(ArrayList(collisionBoxes))

    private val collisionsByCell: Map<FlightCell, List<FlightAabb>> = indexCollisions(this.collisionBoxes)

    fun isPositionCaptured(position: FlightVec3, body: FlightBodyBounds): Boolean =
        cellsOccupiedBy(body.at(position)).all(loadedCells::contains)

    fun isPositionClear(position: FlightVec3, body: FlightBodyBounds): Boolean {
        val playerBox = body.at(position)
        if (!cellsOccupiedBy(playerBox).all(loadedCells::contains)) return false
        return collisionCandidates(playerBox).none(playerBox::intersects)
    }

    fun isSegmentCaptured(from: FlightVec3, to: FlightVec3, body: FlightBodyBounds): Boolean {
        val fromBox = body.at(from)
        val toBox = body.at(to)
        return cellsOccupiedBy(enclosingBox(fromBox, toBox)).all { cell ->
            !sweptBodyIntersectsCell(from, to, body, cell) || cell in loadedCells
        }
    }

    /** Analytic swept-AABB validation; obstacles cannot be skipped by large movement steps. */
    fun isSegmentClear(from: FlightVec3, to: FlightVec3, body: FlightBodyBounds): Boolean {
        if (!isSegmentCaptured(from, to, body)) return false
        val sweptBounds = enclosingBox(body.at(from), body.at(to))
        return collisionCandidates(sweptBounds).none { obstacle ->
            sweptBodyIntersects(from, to, body, obstacle)
        }
    }

    /** A conservative landing anchor requires the complete footprint to have nearby support. */
    fun isStandable(position: FlightVec3, body: FlightBodyBounds): Boolean {
        if (!isPositionClear(position, body)) return false
        val playerBox = body.at(position)
        return supportPoints(playerBox).all { (x, z) -> hasSupportAt(x, playerBox.minY, z) }
    }

    fun findStandableBelow(
        position: FlightVec3,
        body: FlightBodyBounds,
        maxDrop: Int,
    ): FlightVec3? {
        require(maxDrop >= 0) { "Maximum landing drop cannot be negative" }
        val currentBox = body.at(position)
        val minimumAnchorY = position.y - maxDrop
        return buildSet {
            add(position.y)
            collisionBoxes.asSequence()
                .filter { collision -> collision.overlapsHorizontalFootprint(currentBox) }
                .map { collision -> collision.maxY - body.minYOffset }
                .filter { candidateY ->
                    candidateY <= position.y + GEOMETRY_EPSILON &&
                        candidateY >= minimumAnchorY - GEOMETRY_EPSILON
                }
                .forEach(::add)
        }
            .asSequence()
            .sortedDescending()
            .map { candidateY -> position.copy(y = candidateY) }
            .firstOrNull { candidate -> isStandable(candidate, body) }
    }

    private fun FlightAabb.overlapsHorizontalFootprint(footprint: FlightAabb): Boolean =
        maxX > footprint.minX && minX < footprint.maxX && maxZ > footprint.minZ && minZ < footprint.maxZ

    private fun collisionCandidates(query: FlightAabb): Set<FlightAabb> = buildSet {
        cellsOccupiedBy(query).forEach { cell -> collisionsByCell[cell]?.let(::addAll) }
    }

    private fun hasSupportAt(x: Double, feetY: Double, z: Double): Boolean {
        val below = FlightCell(floorToInt(x), floorToInt(Math.nextDown(feetY)), floorToInt(z))
        if (below !in loadedCells) return false
        return collisionsByCell[below].orEmpty().any { collision -> collision.supports(x, feetY, z) }
    }

    private fun FlightAabb.supports(x: Double, feetY: Double, z: Double): Boolean =
        containsHorizontalPoint(x, z) && maxY <= feetY + GEOMETRY_EPSILON && maxY >= feetY - MAX_SUPPORT_GAP

    private fun FlightAabb.containsHorizontalPoint(x: Double, z: Double): Boolean =
        x >= minX - GEOMETRY_EPSILON && x <= maxX + GEOMETRY_EPSILON &&
            z >= minZ - GEOMETRY_EPSILON && z <= maxZ + GEOMETRY_EPSILON

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

    private fun sweptBodyIntersectsCell(
        from: FlightVec3,
        to: FlightVec3,
        body: FlightBodyBounds,
        cell: FlightCell,
    ): Boolean = sweptBodyIntersects(
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

    private fun sweptBodyIntersects(
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

    private fun segmentEntersOpenBox(from: FlightVec3, to: FlightVec3, box: FlightAabb): Boolean {
        var entry = 0.0
        var exit = 1.0
        val starts = doubleArrayOf(from.x, from.y, from.z)
        val deltas = doubleArrayOf(to.x - from.x, to.y - from.y, to.z - from.z)
        val minimums = doubleArrayOf(box.minX, box.minY, box.minZ)
        val maximums = doubleArrayOf(box.maxX, box.maxY, box.maxZ)

        for (axis in starts.indices) {
            val delta = deltas[axis]
            if (abs(delta) <= GEOMETRY_EPSILON) {
                if (starts[axis] <= minimums[axis] || starts[axis] >= maximums[axis]) return false
                continue
            }

            val first = (minimums[axis] - starts[axis]) / delta
            val second = (maximums[axis] - starts[axis]) / delta
            entry = max(entry, min(first, second))
            exit = min(exit, max(first, second))
            if (entry >= exit - GEOMETRY_EPSILON) return false
        }
        return entry < 1.0 - GEOMETRY_EPSILON && exit > GEOMETRY_EPSILON
    }

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
        floorToInt(minimum)..(ceil(maximum).toInt() - 1)

    private fun floorToInt(value: Double): Int = floor(value).toInt()

    private fun enclosingBox(first: FlightAabb, second: FlightAabb) = FlightAabb(
        minX = min(first.minX, second.minX),
        minY = min(first.minY, second.minY),
        minZ = min(first.minZ, second.minZ),
        maxX = max(first.maxX, second.maxX),
        maxY = max(first.maxY, second.maxY),
        maxZ = max(first.maxZ, second.maxZ),
    )

    private companion object {
        const val GEOMETRY_EPSILON = 1.0E-9
        const val MAX_SUPPORT_GAP = 0.0625
        const val SUPPORT_INSET = 1.0E-4
    }
}
