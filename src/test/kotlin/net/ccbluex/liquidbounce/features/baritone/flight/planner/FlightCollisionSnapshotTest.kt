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

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class FlightCollisionSnapshotTest {

    @Test
    fun `the supplied player width decides whether a narrow opening is passable`() {
        val snapshot = snapshot(
            collisionBoxes = listOf(
                FlightAabb(minX = 1.2, minY = 0.0, minZ = -0.5, maxX = 1.4, maxY = 2.0, maxZ = 1.5),
            ),
        )
        val position = FlightVec3(0.5, 0.0, 0.5)

        assertTrue(snapshot.isPositionClear(position, FlightBodyBounds.centered(0.6, 1.8, 0.6)))
        assertFalse(snapshot.isPositionClear(position, FlightBodyBounds.centered(1.6, 1.8, 0.6)))
    }

    @Test
    fun `a swept player segment cannot tunnel through a collision box`() {
        val snapshot = snapshot(
            collisionBoxes = listOf(
                FlightAabb(minX = 2.0, minY = 0.0, minZ = 0.0, maxX = 3.0, maxY = 2.0, maxZ = 1.0),
            ),
        )
        val body = FlightBodyBounds.centered(0.6, 1.8, 0.6)

        assertFalse(
            snapshot.isSegmentClear(
                from = FlightVec3(0.5, 0.0, 0.5),
                to = FlightVec3(3.5, 0.0, 0.5),
                body = body,
            ),
        )
        assertTrue(
            snapshot.isSegmentClear(
                from = FlightVec3(0.5, 2.5, 0.5),
                to = FlightVec3(3.5, 2.5, 0.5),
                body = body,
            ),
        )
    }

    @Test
    fun `a segment entering an uncaptured cell fails closed`() {
        val loaded = buildSet {
            for (y in 0..1) add(FlightCell(0, y, 0))
        }
        val snapshot = FlightCollisionSnapshot(FlightWorldRevision(1), loaded, emptyList())
        val body = FlightBodyBounds.centered(0.6, 1.8, 0.6)

        assertTrue(snapshot.isPositionClear(FlightVec3(0.5, 0.0, 0.5), body))
        assertFalse(snapshot.isSegmentClear(FlightVec3(0.5, 0.0, 0.5), FlightVec3(1.5, 0.0, 0.5), body))
    }

    @Test
    fun `snapshot copies mutable capture collections`() {
        val loaded = loadedCells().toMutableSet()
        val collisions = mutableListOf<FlightAabb>()
        val snapshot = FlightCollisionSnapshot(FlightWorldRevision(4), loaded, collisions)
        val body = FlightBodyBounds.centered(0.6, 1.8, 0.6)

        loaded.clear()
        collisions += FlightAabb(
            minX = 0.0,
            minY = 0.0,
            minZ = 0.0,
            maxX = 1.0,
            maxY = 2.0,
            maxZ = 1.0,
        )

        assertTrue(snapshot.isPositionClear(FlightVec3(0.5, 0.0, 0.5), body))
        assertNotSame(loaded, snapshot.loadedCells)
    }

    @Test
    fun `standable lookup chooses the first fully supported anchor below`() {
        val floor = FlightAabb(
            minX = -2.0,
            minY = -1.0,
            minZ = -2.0,
            maxX = 3.0,
            maxY = 0.0,
            maxZ = 3.0,
        )
        val snapshot = snapshot(collisionBoxes = listOf(floor))
        val body = FlightBodyBounds.centered(0.6, 1.8, 0.6)

        assertTrue(snapshot.isStandable(FlightVec3(0.5, 0.0, 0.5), body))
        assertEquals(
            FlightVec3(0.5, 0.0, 0.5),
            snapshot.findStandableBelow(FlightVec3(0.5, 3.0, 0.5), body, maxDrop = 4),
        )
    }

    @Test
    fun `standable lookup preserves fractional collision surface heights`() {
        val slab = FlightAabb(
            minX = -2.0,
            minY = 0.0,
            minZ = -2.0,
            maxX = 3.0,
            maxY = 0.5,
            maxZ = 3.0,
        )
        val snapshot = snapshot(collisionBoxes = listOf(slab))
        val body = FlightBodyBounds.centered(0.6, 1.8, 0.6)

        assertEquals(
            FlightVec3(0.5, 0.5, 0.5),
            snapshot.findStandableBelow(FlightVec3(0.5, 3.2, 0.5), body, maxDrop = 4),
        )
    }

    private fun snapshot(collisionBoxes: Collection<FlightAabb>) = FlightCollisionSnapshot(
        revision = FlightWorldRevision(1),
        loadedCells = loadedCells(),
        collisionBoxes = collisionBoxes,
    )

    private fun loadedCells(): Set<FlightCell> = buildSet {
        for (x in -3..4) {
            for (y in -2..5) {
                for (z in -3..4) add(FlightCell(x, y, z))
            }
        }
    }
}
