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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar


import net.ccbluex.liquidbounce.utils.math.bottomCenter
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

/**
 * Finds collision-aware block routes for SpearKill's Packet mode via bidirectional A*.
 *
 * The default callbacks are world-backed and must stay on the client thread. SpearKill's runtime
 * supplies a complete collision snapshot and finishes this CPU search synchronously.
 * The waypoint list omits the origin, matching the historical path-builder contract.
 */
internal class SpearKillAStarRoutePlanner(
    val allowDiagonal: Boolean,
    val maxCost: Int,
    val maxIterations: Int = SPEAR_KILL_A_STAR_MAX_ITERATIONS,
    val stopRange: Double = SPEAR_KILL_A_STAR_STOP_RANGE,
    private val isPassable: (Vec3i) -> Boolean = ::spearKillWorldIsPassable,
    private val canTraverse: (Vec3, Vec3) -> Boolean = { _, _ -> true },
) {

    private val passabilityCache = HashMap<BlockPos, Boolean>()

    fun plan(origin: Vec3, destination: Vec3): List<Vec3>? {
        if (maxCost <= 0 || !origin.isFinite() || !destination.isFinite()) return null

        val start = BlockPos.containing(origin)
        val end = BlockPos.containing(destination)
        if (end.closerThan(start, stopRange)) return emptyList()

        fun nodePosition(node: Vec3i): Vec3 = when (node) {
            start -> origin
            end -> destination
            else -> node.bottomCenter
        }

        val path = bidirectionalAStarShortestPath(
            start = start,
            end = end,
            neighbors = { position: Vec3i ->
                spearKillBidirectionalNeighbors(
                    position = position,
                    allowDiagonal = allowDiagonal,
                    isPassable = ::isPassableCached,
                    canTraverse = { from, to ->
                        val fromPosition = nodePosition(from)
                        val toPosition = nodePosition(to)
                        canTraverse(fromPosition, toPosition) && canTraverse(toPosition, fromPosition)
                    },
                )
            },
            forwardHeuristic = { spearKillAStarHeuristic(it, end) },
            backwardHeuristic = { spearKillAStarHeuristic(it, start) },
            maxIterations = maxIterations,
            maxCost = maxCost.toDouble(),
        ) ?: return null

        // Exclude start node to preserve the original API contract.
        val route = path.nodes.drop(1)
        if (route.isEmpty() && !end.closerThan(start, stopRange)) return null
        return route.map(::nodePosition)
    }

    private fun isPassableCached(position: Vec3i): Boolean {
        val immutable = BlockPos(position.x, position.y, position.z)
        return passabilityCache.getOrPut(immutable) { isPassable(immutable) }
    }
}

/** Euclidean distance remains admissible for unit, diagonal, and squared-cost movement edges. */
internal fun spearKillAStarHeuristic(from: Vec3i, to: Vec3i): Double = sqrt(from.distSqr(to).toDouble())

/** Validates the one exact Packet step immediately before SpearKill sends it. */
internal fun isSpearKillPacketStepClear(
    sessionOrigin: Vec3,
    committedOffset: Vec3,
    candidateOffset: Vec3,
    maxStepLength: Double,
    segmentValidator: SpearKillAStarSegmentValidator,
): Boolean {
    if (!sessionOrigin.isFinite() || !committedOffset.isFinite() || !candidateOffset.isFinite() ||
        !maxStepLength.isFinite() || maxStepLength <= 0.0
    ) {
        return false
    }

    val movement = candidateOffset.subtract(committedOffset)
    if (movement.lengthSqr() <= SPEAR_KILL_A_STAR_POSITION_EPSILON_SQUARED ||
        movement.length() > maxStepLength + SPEAR_KILL_A_STAR_POSITION_EPSILON
    ) {
        return false
    }

    return segmentValidator.isClear(
        sessionOrigin.add(committedOffset),
        sessionOrigin.add(candidateOffset),
    )
}

/**
 * Collapses a clear straight run of A* nodes into the longest packet-sized step.
 *
 * The packet session deliberately sends one confirmed step per tick. A* normally returns one-block
 * nodes, so keeping each of them makes a long empty corridor needlessly slow despite [maxSpeed].
 * Only collinear nodes are joined and every shortcut is validated as one swept player-box segment.
 */
internal fun simplifySpearKillAStarWaypoints(
    origin: Vec3,
    waypoints: List<Vec3>,
    maxSpeed: Double,
    segmentValidator: SpearKillAStarSegmentValidator,
): List<Vec3> {
    if (!origin.isFinite() || !maxSpeed.isFinite() || maxSpeed <= 0.0) return waypoints

    val simplified = ArrayList<Vec3>(waypoints.size)
    var current = origin
    var index = 0

    while (index < waypoints.size) {
        val immediateWaypoint = waypoints[index]
        if (!immediateWaypoint.isFinite()) return waypoints

        val selectedIndex = selectSpearKillAStarShortcutIndex(
            current,
            waypoints,
            index,
            maxSpeed,
            segmentValidator,
        )

        current = waypoints[selectedIndex]
        simplified += current
        index = selectedIndex + 1
    }

    return simplified
}

private fun selectSpearKillAStarShortcutIndex(
    current: Vec3,
    waypoints: List<Vec3>,
    startIndex: Int,
    maxSpeed: Double,
    segmentValidator: SpearKillAStarSegmentValidator,
): Int {
    val direction = waypoints[startIndex].subtract(current)
    if (!isInitialSpearKillAStarShortcutClear(current, waypoints[startIndex], direction, maxSpeed, segmentValidator)) {
        return startIndex
    }

    var selectedIndex = startIndex
    for (candidateIndex in startIndex + 1 until waypoints.size) {
        if (!isSpearKillAStarShortcutCandidateClear(
                current,
                waypoints[candidateIndex - 1],
                waypoints[candidateIndex],
                direction,
                maxSpeed,
                segmentValidator,
            )
        ) {
            break
        }
        selectedIndex = candidateIndex
    }
    return selectedIndex
}

private fun isSpearKillAStarShortcutCandidateClear(
    current: Vec3,
    previous: Vec3,
    candidate: Vec3,
    direction: Vec3,
    maxSpeed: Double,
    segmentValidator: SpearKillAStarSegmentValidator,
): Boolean = candidate.isFinite() && previous.isFinite() &&
    isSpearKillAStarSameDirection(direction, candidate.subtract(previous)) &&
    current.distanceTo(candidate) <= maxSpeed && segmentValidator.isClear(current, candidate)

private fun isInitialSpearKillAStarShortcutClear(
    current: Vec3,
    immediate: Vec3,
    direction: Vec3,
    maxSpeed: Double,
    segmentValidator: SpearKillAStarSegmentValidator,
): Boolean = direction.lengthSqr() > SPEAR_KILL_A_STAR_POSITION_EPSILON_SQUARED &&
    current.distanceTo(immediate) <= maxSpeed && segmentValidator.isClear(current, immediate)

/**
 * Turns absolute A* waypoints into Packet-session deltas.
 *
 * Every outbound edge is expanded to [maxSpeed]-bounded segments and validated. The return trip
 * is the exact inverse of those outbound deltas in reverse order, then ends with a zero marker
 * for [SpearKillPacketBootSession]. A failed validation produces no partial route.
 */

private fun Vec3.isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
