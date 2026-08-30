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

/** Immutable collision data captured on the Minecraft thread and safe to route against elsewhere. */
class FlightCollisionSnapshot(
    val revision: FlightWorldRevision,
    loadedCells: Collection<FlightCell>,
    collisionBoxes: Collection<FlightAabb>,
) {
    val loadedCells: Set<FlightCell> = Collections.unmodifiableSet(LinkedHashSet(loadedCells))
    val collisionBoxes: List<FlightAabb> = Collections.unmodifiableList(ArrayList(collisionBoxes))

    private val collisionIndex = FlightCollisionIndex(this.loadedCells, this.collisionBoxes)
    private val landingSupport = FlightLandingSupport(collisionIndex)

    fun isPositionCaptured(position: FlightVec3, body: FlightBodyBounds): Boolean =
        collisionIndex.contains(body.at(position))

    fun isPositionClear(position: FlightVec3, body: FlightBodyBounds): Boolean {
        val playerBox = body.at(position)
        if (!collisionIndex.contains(playerBox)) return false
        return collisionIndex.candidates(playerBox).none(playerBox::intersects)
    }

    fun isSegmentCaptured(from: FlightVec3, to: FlightVec3, body: FlightBodyBounds): Boolean {
        val fromBox = body.at(from)
        val toBox = body.at(to)
        return collisionIndex.occupiedCells(FlightSweptCollision.enclosingBox(fromBox, toBox)).all { cell ->
            !FlightSweptCollision.bodyIntersectsCell(from, to, body, cell) || collisionIndex.isLoaded(cell)
        }
    }

    /** Analytic swept-AABB validation; obstacles cannot be skipped by large movement steps. */
    fun isSegmentClear(from: FlightVec3, to: FlightVec3, body: FlightBodyBounds): Boolean {
        if (!isSegmentCaptured(from, to, body)) return false
        val sweptBounds = FlightSweptCollision.enclosingBox(body.at(from), body.at(to))
        return collisionIndex.candidates(sweptBounds).none { obstacle ->
            FlightSweptCollision.bodyIntersects(from, to, body, obstacle)
        }
    }

    /** A conservative landing anchor requires the complete footprint to have nearby support. */
    fun isStandable(position: FlightVec3, body: FlightBodyBounds): Boolean {
        if (!isPositionClear(position, body)) return false
        return landingSupport.fullySupports(body.at(position))
    }

    fun findStandableBelow(position: FlightVec3, body: FlightBodyBounds, maxDrop: Int): FlightVec3? {
        require(maxDrop >= 0) { "Maximum landing drop cannot be negative" }
        val currentBox = body.at(position)
        val minimumAnchorY = position.y - maxDrop
        return buildSet {
            add(position.y)
            collisionBoxes.asSequence()
                .filter { collision -> collision.overlapsHorizontalFootprint(currentBox) }
                .map { collision -> collision.maxY - body.minYOffset }
                .filter { candidateY ->
                    candidateY <= position.y + FLIGHT_GEOMETRY_EPSILON &&
                        candidateY >= minimumAnchorY - FLIGHT_GEOMETRY_EPSILON
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
}
