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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlightRoutePlannerTest {

    private val planner = FlightRoutePlanner()
    private val body = FlightBodyBounds.centered(width = 0.6, height = 1.8, depth = 0.6)

    @Test
    fun `open loaded space produces a complete collision validated route`() {
        val result = planner.plan(
            request(
                snapshot = openSnapshot(),
                start = point(0, 0, 0),
                goal = point(3, 0, 0),
            ),
        )

        assertEquals(FlightPlanStatus.COMPLETE, result.status)
        val route = assertNotNull(result.route)
        assertEquals(listOf(point(0, 0, 0), point(3, 0, 0)), route.points)
        assertEquals(1.0, route.progress.fraction)
        assertEquals(0.0, route.progress.distanceRemaining)
        assertTrue(route.points.zipWithNext().all { (from, to) ->
            result.snapshot.isSegmentClear(from, to, body)
        })
    }

    @Test
    fun `route simplification preserves turns required to avoid a wall`() {
        val wall = FlightAabb(
            minX = 1.0,
            minY = 0.0,
            minZ = 0.0,
            maxX = 2.0,
            maxY = 2.0,
            maxZ = 1.0,
        )
        val result = planner.plan(
            request(
                snapshot = openSnapshot(collisionBoxes = listOf(wall)),
                start = point(0, 0, 0),
                goal = point(3, 0, 0),
            ),
        )

        assertEquals(FlightPlanStatus.COMPLETE, result.status)
        val route = assertNotNull(result.route)
        assertTrue(route.points.size > 2)
        assertTrue(route.points.zipWithNext().all { (from, to) ->
            result.snapshot.isSegmentClear(from, to, body)
        })
    }

    @Test
    fun `route simplification preserves non-diagonal traversal`() {
        val result = planner.plan(
            request(
                snapshot = openSnapshot(),
                start = point(0, 0, 0),
                goal = point(2, 2, 0),
                capabilities = FlightTraversalCapabilities(diagonal = false),
            ),
        )

        assertEquals(FlightPlanStatus.COMPLETE, result.status)
        val route = assertNotNull(result.route)
        assertTrue(route.points.zipWithNext().all { (from, to) ->
            listOf(to.x - from.x, to.y - from.y, to.z - from.z).count { it != 0.0 } == 1
        })
    }

    @Test
    fun `diagonal movement cannot cut through two blocked cardinal corners`() {
        val cells = loadedCells(0..1, 0..2, 0..1)
        val walls = listOf(
            FlightAabb(minX = 1.0, minY = 0.0, minZ = 0.0, maxX = 2.0, maxY = 2.0, maxZ = 1.0),
            FlightAabb(minX = 0.0, minY = 0.0, minZ = 1.0, maxX = 1.0, maxY = 2.0, maxZ = 2.0),
        )
        val snapshot = FlightCollisionSnapshot(FlightWorldRevision(2), cells, walls)

        val result = planner.plan(
            request(
                snapshot = snapshot,
                start = point(0, 0, 0),
                goal = point(1, 0, 1),
                capabilities = FlightTraversalCapabilities(ascend = false, descend = false),
            ),
        )

        assertEquals(FlightPlanStatus.NO_ROUTE, result.status)
        assertNull(result.route)
    }

    @Test
    fun `ascend and descend capabilities independently constrain vertical routes`() {
        val snapshot = openSnapshot()
        val cannotAscend = planner.plan(
            request(
                snapshot = snapshot,
                start = point(0, 0, 0),
                goal = point(0, 2, 0),
                capabilities = FlightTraversalCapabilities(horizontal = false, ascend = false, descend = true),
            ),
        )
        val canAscend = planner.plan(
            request(
                snapshot = snapshot,
                start = point(0, 0, 0),
                goal = point(0, 2, 0),
                capabilities = FlightTraversalCapabilities(horizontal = false, ascend = true, descend = false),
            ),
        )
        val cannotDescend = planner.plan(
            request(
                snapshot = snapshot,
                start = point(0, 2, 0),
                goal = point(0, 0, 0),
                capabilities = FlightTraversalCapabilities(horizontal = false, ascend = true, descend = false),
            ),
        )

        assertEquals(FlightPlanStatus.NO_ROUTE, cannotAscend.status)
        assertEquals(FlightPlanStatus.COMPLETE, canAscend.status)
        assertEquals(FlightPlanStatus.NO_ROUTE, cannotDescend.status)
    }

    @Test
    fun `an unloaded destination returns the closest validated loaded frontier`() {
        val cells = loadedCells(0..2, 0..2, 0..0)
        val snapshot = FlightCollisionSnapshot(FlightWorldRevision(3), cells, emptyList())

        val result = planner.plan(
            request(
                snapshot = snapshot,
                start = point(0, 0, 0),
                goal = point(5, 0, 0),
                capabilities = FlightTraversalCapabilities(ascend = false, descend = false),
            ),
        )

        assertEquals(FlightPlanStatus.LOADED_FRONTIER, result.status)
        val route = assertNotNull(result.route)
        assertEquals(point(2, 0, 0), route.points.last())
        assertTrue(route.progress.fraction in 0.0..<1.0)
        assertEquals(3.0, route.progress.distanceRemaining)
    }

    @Test
    fun `expansion budget returns deterministic partial progress`() {
        val request = request(
            snapshot = openSnapshot(x = 0..10, z = 0..0),
            start = point(0, 0, 0),
            goal = point(8, 0, 0),
            capabilities = FlightTraversalCapabilities(ascend = false, descend = false),
            limits = FlightSearchLimits(maxExpandedNodes = 2),
        )

        val first = planner.plan(request)
        val second = planner.plan(request)

        assertEquals(FlightPlanStatus.BUDGET_EXHAUSTED, first.status)
        assertEquals(first, second)
        assertTrue(assertNotNull(first.route).progress.fraction in 0.0..<1.0)
        assertEquals(request.replanKey, first.replanKey)
    }

    @Test
    fun `blocked goal is reported before search begins`() {
        val snapshot = openSnapshot(
            collisionBoxes = listOf(
                FlightAabb(minX = 3.0, minY = 0.0, minZ = 0.0, maxX = 4.0, maxY = 2.0, maxZ = 1.0),
            ),
        )

        val result = planner.plan(request(snapshot, point(0, 0, 0), point(3, 0, 0)))

        assertEquals(FlightPlanStatus.GOAL_BLOCKED, result.status)
        assertNull(result.route)
    }

    @Test
    fun `standable goal policy rejects a clear unsupported destination`() {
        val snapshot = openSnapshot()

        val result = planner.plan(
            request(
                snapshot = snapshot,
                start = point(0, 1, 0),
                goal = point(2, 1, 0),
                requireStandableGoal = true,
            ),
        )

        assertEquals(FlightPlanStatus.GOAL_BLOCKED, result.status)
    }

    @Test
    fun `failed search publishes the safest explored landing anchor`() {
        val floor = FlightAabb(
            minX = -1.0,
            minY = -1.0,
            minZ = -5.0,
            maxX = 4.0,
            maxY = 0.0,
            maxZ = 6.0,
        )
        val wall = FlightAabb(
            minX = 2.0,
            minY = 0.0,
            minZ = -5.0,
            maxX = 3.0,
            maxY = 4.0,
            maxZ = 6.0,
        )
        val snapshot = openSnapshot(x = 0..3, z = -4..4, collisionBoxes = listOf(floor, wall))

        val result = planner.plan(
            request(
                snapshot = snapshot,
                start = point(0, 0, 0),
                goal = point(3, 0, 0),
                capabilities = FlightTraversalCapabilities(ascend = false, descend = false),
            ),
        )

        assertEquals(FlightPlanStatus.NO_ROUTE, result.status)
        assertNotNull(result.landingAnchor)
        assertTrue(snapshot.isStandable(result.landingAnchor, body))
    }

    @Test
    fun `world revision and goal are part of deterministic replanning identity`() {
        val first = request(openSnapshot(revision = 7), point(0, 0, 0), point(3, 0, 0))
        val newWorld = first.copy(snapshot = openSnapshot(revision = 8))
        val newGoal = first.copy(goal = point(4, 0, 0))

        assertNotEquals(first.replanKey, newWorld.replanKey)
        assertNotEquals(first.replanKey, newGoal.replanKey)
        assertFalse(first.replanKey.toString().isBlank())
    }

    private fun request(
        snapshot: FlightCollisionSnapshot,
        start: FlightVec3,
        goal: FlightVec3,
        capabilities: FlightTraversalCapabilities = FlightTraversalCapabilities(),
        limits: FlightSearchLimits = FlightSearchLimits(),
        requireStandableGoal: Boolean = false,
    ) = FlightPlanRequest(
        snapshot = snapshot,
        start = start,
        goal = goal,
        body = body,
        capabilities = capabilities,
        limits = limits,
        requireStandableGoal = requireStandableGoal,
    )

    private fun openSnapshot(
        revision: Long = 1,
        x: IntRange = -2..10,
        z: IntRange = -2..2,
        collisionBoxes: Collection<FlightAabb> = emptyList(),
    ) = FlightCollisionSnapshot(
        revision = FlightWorldRevision(revision),
        loadedCells = loadedCells(x, -2..5, z),
        collisionBoxes = collisionBoxes,
    )

    private fun loadedCells(x: IntRange, y: IntRange, z: IntRange): Set<FlightCell> = buildSet {
        for (cellX in x) {
            for (cellY in y) {
                for (cellZ in z) add(FlightCell(cellX, cellY, cellZ))
            }
        }
    }

    private fun point(x: Int, y: Int, z: Int) = FlightVec3(x + 0.5, y.toDouble(), z + 0.5)
}
