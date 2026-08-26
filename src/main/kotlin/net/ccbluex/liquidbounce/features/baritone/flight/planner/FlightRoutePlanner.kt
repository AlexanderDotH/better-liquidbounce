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

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Bounded deterministic 3D A* over an immutable collision snapshot. */
class FlightRoutePlanner {

    fun plan(request: FlightPlanRequest): FlightPlanResult {
        if (!request.snapshot.isPositionClear(request.start, request.body)) {
            return request.failure(FlightPlanStatus.START_BLOCKED)
        }
        if (goalIsKnownAndBlocked(request)) {
            return request.failure(FlightPlanStatus.GOAL_BLOCKED, safeLanding(request, request.start))
        }
        return FlightSearch(request).solve()
    }

    private fun goalIsKnownAndBlocked(request: FlightPlanRequest): Boolean {
        if (!request.snapshot.isPositionCaptured(request.goal, request.body)) return false
        if (!request.snapshot.isPositionClear(request.goal, request.body)) return true
        return request.requireStandableGoal && !request.snapshot.isStandable(request.goal, request.body)
    }

    private fun safeLanding(request: FlightPlanRequest, from: FlightVec3): FlightVec3? {
        val landing = request.snapshot.findStandableBelow(from, request.body, request.limits.maxLandingDrop)
            ?: return null
        return landing.takeIf { request.snapshot.isSegmentClear(from, it, request.body) }
    }

    private fun FlightPlanRequest.failure(status: FlightPlanStatus, landing: FlightVec3? = null) = FlightPlanResult(
        status = status,
        snapshot = snapshot,
        landingAnchor = landing,
        replanKey = replanKey,
    )

    @Suppress("TooManyFunctions")
    private class FlightSearch(private val request: FlightPlanRequest) {
        private val snapshot = request.snapshot
        private val startNode = FlightGridNode.ZERO
        private val targetNode = FlightGridNode(
            x = (request.goal.x - request.start.x).roundToInt(),
            y = (request.goal.y - request.start.y).roundToInt(),
            z = (request.goal.z - request.start.z).roundToInt(),
        )
        private val queue = PriorityQueue(FLIGHT_QUEUE_COMPARATOR)
        private val costs = HashMap<FlightGridNode, Double>()
        private val previous = HashMap<FlightGridNode, FlightGridNode>()

        private var expandedNodes = 0
        private var bestProgress = startNode
        private var bestFrontier: FlightGridNode? = null
        private var bestLanding: FlightGridNode? = null
        private var routeCostBudgetReached = false

        fun solve(): FlightPlanResult {
            enqueueStart()
            while (queue.isNotEmpty() && expandedNodes < request.limits.maxExpandedNodes) {
                val current = queue.poll()
                if (current.cost > costs.getValue(current.node) + COST_EPSILON) continue
                if (reachesGoal(current.node)) return complete(current.node)

                expandedNodes++
                considerLanding(current.node)
                expand(current)
            }

            if (queue.isNotEmpty() || routeCostBudgetReached) return partial(FlightPlanStatus.BUDGET_EXHAUSTED)
            val frontier = bestFrontier ?: return noRoute()
            return partial(FlightPlanStatus.LOADED_FRONTIER, frontier)
        }

        private fun enqueueStart() {
            costs[startNode] = 0.0
            queue += FlightQueueEntry(startNode, 0.0, heuristic(startNode))
        }

        private fun expand(current: FlightQueueEntry) {
            for (direction in FLIGHT_DIRECTIONS) {
                if (!request.capabilities.allows(direction)) continue
                when (edgeState(current.node, direction)) {
                    FlightEdgeState.UNLOADED -> considerFrontier(current.node, current.node + direction)
                    FlightEdgeState.BLOCKED -> Unit
                    FlightEdgeState.CLEAR -> relax(current, current.node + direction)
                }
            }
        }

        private fun relax(current: FlightQueueEntry, adjacent: FlightGridNode) {
            val candidateCost = current.cost + current.node.distanceTo(adjacent)
            if (candidateCost > request.limits.maxRouteCost) {
                routeCostBudgetReached = true
                return
            }
            if (candidateCost + COST_EPSILON >= costs.getOrDefault(adjacent, Double.POSITIVE_INFINITY)) return

            costs[adjacent] = candidateCost
            previous[adjacent] = current.node
            queue += FlightQueueEntry(adjacent, candidateCost, candidateCost + heuristic(adjacent))
            considerProgress(adjacent)
            considerLanding(adjacent)
        }

        private fun edgeState(from: FlightGridNode, direction: FlightGridNode): FlightEdgeState {
            val origin = from.toPoint(request.start)
            for (intermediate in direction.intermediateOffsets()) {
                when (segmentState(origin, (from + intermediate).toPoint(request.start))) {
                    FlightEdgeState.CLEAR -> Unit
                    FlightEdgeState.BLOCKED -> return FlightEdgeState.BLOCKED
                    FlightEdgeState.UNLOADED -> return FlightEdgeState.UNLOADED
                }
            }
            return segmentState(origin, (from + direction).toPoint(request.start))
        }

        private fun segmentState(from: FlightVec3, to: FlightVec3): FlightEdgeState {
            if (!snapshot.isPositionCaptured(to, request.body) ||
                !snapshot.isSegmentCaptured(from, to, request.body)
            ) {
                return FlightEdgeState.UNLOADED
            }
            if (!snapshot.isPositionClear(to, request.body) ||
                !snapshot.isSegmentClear(from, to, request.body)
            ) {
                return FlightEdgeState.BLOCKED
            }
            return FlightEdgeState.CLEAR
        }

        private fun reachesGoal(node: FlightGridNode): Boolean {
            if (node != targetNode) return false
            val from = node.toPoint(request.start)
            if (!snapshot.isSegmentCaptured(from, request.goal, request.body)) return false
            return snapshot.isSegmentClear(from, request.goal, request.body)
        }

        private fun considerProgress(candidate: FlightGridNode) {
            if (isBetterCandidate(candidate, bestProgress)) bestProgress = candidate
        }

        private fun considerFrontier(candidate: FlightGridNode, outside: FlightGridNode) {
            if (heuristic(outside) + COST_EPSILON >= heuristic(candidate)) return
            val current = bestFrontier
            if (current == null || isBetterCandidate(candidate, current)) bestFrontier = candidate
        }

        private fun considerLanding(candidate: FlightGridNode) {
            if (!snapshot.isStandable(candidate.toPoint(request.start), request.body)) return
            val current = bestLanding
            if (current == null || isBetterCandidate(candidate, current)) bestLanding = candidate
        }

        private fun isBetterCandidate(candidate: FlightGridNode, current: FlightGridNode): Boolean {
            val candidateDistance = heuristic(candidate)
            val currentDistance = heuristic(current)
            if (candidateDistance + COST_EPSILON < currentDistance) return true
            if (abs(candidateDistance - currentDistance) > COST_EPSILON) return false
            val candidateCost = costs.getOrDefault(candidate, Double.POSITIVE_INFINITY)
            val currentCost = costs.getOrDefault(current, Double.POSITIVE_INFINITY)
            if (candidateCost + COST_EPSILON < currentCost) return true
            return candidateCost == currentCost && FLIGHT_NODE_COMPARATOR.compare(candidate, current) < 0
        }

        private fun complete(node: FlightGridNode): FlightPlanResult {
            val route = routeTo(node, includeExactGoal = true, complete = true)
            return result(FlightPlanStatus.COMPLETE, route, safeLanding(route.points.last()))
        }

        private fun partial(status: FlightPlanStatus, node: FlightGridNode = bestProgress): FlightPlanResult {
            val route = routeTo(node, includeExactGoal = false, complete = false)
            return result(status, route, safeLanding(route.points.last()))
        }

        private fun noRoute(): FlightPlanResult = result(
            status = FlightPlanStatus.NO_ROUTE,
            landing = safeLanding(bestProgress.toPoint(request.start))
                ?: bestLanding?.toPoint(request.start),
        )

        private fun result(
            status: FlightPlanStatus,
            route: FlightRoute? = null,
            landing: FlightVec3? = null,
        ) = FlightPlanResult(
            status = status,
            snapshot = snapshot,
            route = route,
            landingAnchor = landing,
            replanKey = request.replanKey,
        )

        private fun routeTo(
            destination: FlightGridNode,
            includeExactGoal: Boolean,
            complete: Boolean,
        ): FlightRoute {
            val rawPoints = reconstruct(destination).map { node -> node.toPoint(request.start) }.toMutableList()
            if (includeExactGoal && rawPoints.last() != request.goal) rawPoints += request.goal
            val points = simplify(rawPoints)
            val remaining = if (complete) 0.0 else points.last().distanceTo(request.goal)
            val initialDistance = request.start.distanceTo(request.goal)
            val fraction = if (complete || initialDistance <= COST_EPSILON) {
                1.0
            } else {
                (1.0 - remaining / initialDistance).coerceIn(0.0, 1.0)
            }
            return FlightRoute(
                points = points,
                totalDistance = points.zipWithNext().sumOf { (from, to) -> from.distanceTo(to) },
                progress = FlightRouteProgress(fraction, remaining, expandedNodes),
            )
        }

        private fun simplify(points: List<FlightVec3>): List<FlightVec3> {
            if (points.size <= 2) return points

            val simplified = mutableListOf(points.first())
            var anchorIndex = 0
            while (anchorIndex < points.lastIndex) {
                val nextIndex = (points.lastIndex downTo anchorIndex + 1).first { candidateIndex ->
                    request.capabilities.allows(points[anchorIndex], points[candidateIndex]) &&
                        snapshot.isSegmentClear(points[anchorIndex], points[candidateIndex], request.body)
                }
                simplified += points[nextIndex]
                anchorIndex = nextIndex
            }
            return simplified
        }

        private fun reconstruct(destination: FlightGridNode): List<FlightGridNode> {
            val reversed = mutableListOf(destination)
            var current = destination
            while (current != startNode) {
                current = previous[current] ?: return listOf(startNode)
                reversed += current
            }
            return reversed.asReversed()
        }

        private fun safeLanding(from: FlightVec3): FlightVec3? {
            val candidate = snapshot.findStandableBelow(from, request.body, request.limits.maxLandingDrop)
                ?: return null
            return candidate.takeIf { snapshot.isSegmentClear(from, it, request.body) }
        }

        private fun heuristic(node: FlightGridNode): Double = node.toPoint(request.start).distanceTo(request.goal)
    }

    private enum class FlightEdgeState {
        CLEAR,
        BLOCKED,
        UNLOADED,
    }

    private data class FlightQueueEntry(
        val node: FlightGridNode,
        val cost: Double,
        val estimatedTotalCost: Double,
    )

    private data class FlightGridNode(val x: Int, val y: Int, val z: Int) {
        operator fun plus(other: FlightGridNode) = FlightGridNode(x + other.x, y + other.y, z + other.z)

        fun toPoint(origin: FlightVec3): FlightVec3 = origin + FlightCell(x, y, z)

        fun distanceTo(other: FlightGridNode): Double {
            val deltaX = (other.x - x).toDouble()
            val deltaY = (other.y - y).toDouble()
            val deltaZ = (other.z - z).toDouble()
            return sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
        }

        fun intermediateOffsets(): List<FlightGridNode> {
            val axes = buildList {
                if (x != 0) add(FlightGridNode(x, 0, 0))
                if (y != 0) add(FlightGridNode(0, y, 0))
                if (z != 0) add(FlightGridNode(0, 0, z))
            }
            if (axes.size < 2) return emptyList()
            return buildList {
                val completeMask = (1 shl axes.size) - 1
                for (mask in 1 until completeMask) {
                    var offset = ZERO
                    for (index in axes.indices) {
                        if (mask and (1 shl index) != 0) offset += axes[index]
                    }
                    add(offset)
                }
            }
        }

        companion object {
            val ZERO = FlightGridNode(0, 0, 0)
        }
    }

    private companion object {
        const val COST_EPSILON = 1.0E-9

        val FLIGHT_NODE_COMPARATOR: Comparator<FlightGridNode> = compareBy<FlightGridNode>(
            FlightGridNode::x,
            FlightGridNode::y,
            FlightGridNode::z,
        )

        val FLIGHT_QUEUE_COMPARATOR: Comparator<FlightQueueEntry> =
            compareBy<FlightQueueEntry>(FlightQueueEntry::estimatedTotalCost, FlightQueueEntry::cost)
                .thenComparing { first, second -> FLIGHT_NODE_COMPARATOR.compare(first.node, second.node) }

        val FLIGHT_DIRECTIONS: List<FlightGridNode> = buildList {
            for (x in -1..1) {
                for (y in -1..1) {
                    for (z in -1..1) {
                        if (x != 0 || y != 0 || z != 0) add(FlightGridNode(x, y, z))
                    }
                }
            }
        }

        fun FlightTraversalCapabilities.allows(direction: FlightGridNode): Boolean {
            val horizontalMovement = direction.x != 0 || direction.z != 0
            if (horizontalMovement && !horizontal) return false
            if (direction.y > 0 && !ascend) return false
            if (direction.y < 0 && !descend) return false
            val changedAxes = listOf(direction.x, direction.y, direction.z).count { it != 0 }
            return diagonal || changedAxes == 1
        }

        fun FlightTraversalCapabilities.allows(from: FlightVec3, to: FlightVec3): Boolean {
            val deltaX = to.x - from.x
            val deltaY = to.y - from.y
            val deltaZ = to.z - from.z
            val horizontalMovement = abs(deltaX) > COST_EPSILON || abs(deltaZ) > COST_EPSILON
            if (horizontalMovement && !horizontal) return false
            if (deltaY > COST_EPSILON && !ascend) return false
            if (deltaY < -COST_EPSILON && !descend) return false
            val changedAxes = listOf(deltaX, deltaY, deltaZ).count { abs(it) > COST_EPSILON }
            return diagonal || changedAxes <= 1
        }
    }
}
