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

package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.utils.math.bottomCenter
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

/**
 * Finds collision-aware block routes for SpearKill's Packet mode via bidirectional A*.
 *
 * World lookups run on the client thread. The waypoint list omits the origin, matching the
 * historical path-builder contract.
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

        val direction = immediateWaypoint.subtract(current)
        var selectedIndex = index
        if (direction.lengthSqr() > SPEAR_KILL_A_STAR_POSITION_EPSILON_SQUARED &&
            current.distanceTo(immediateWaypoint) <= maxSpeed &&
            segmentValidator.isClear(current, immediateWaypoint)
        ) {
            var candidateIndex = index + 1
            while (candidateIndex < waypoints.size) {
                val candidate = waypoints[candidateIndex]
                val previousWaypoint = waypoints[candidateIndex - 1]
                val nextDirection = candidate.subtract(previousWaypoint)
                if (!candidate.isFinite() || !previousWaypoint.isFinite() ||
                    !isSpearKillAStarSameDirection(direction, nextDirection) ||
                    current.distanceTo(candidate) > maxSpeed ||
                    !segmentValidator.isClear(current, candidate)
                ) {
                    break
                }

                selectedIndex = candidateIndex
                candidateIndex++
            }
        }

        current = waypoints[selectedIndex]
        simplified += current
        index = selectedIndex + 1
    }

    return simplified
}

/**
 * Turns absolute A* waypoints into Packet-session deltas.
 *
 * Every outbound edge is expanded to [maxSpeed]-bounded segments and validated. The return trip
 * is the exact inverse of those outbound deltas in reverse order, then ends with a zero marker
 * for [SpearKillPacketBootSession]. A failed validation produces no partial route.
 */
internal fun buildSpearKillAStarPacketMovements(
    origin: Vec3,
    outboundWaypoints: List<Vec3>,
    maxSpeed: Double,
    segmentValidator: SpearKillAStarSegmentValidator,
): List<Vec3>? = buildSpearKillAStarPacketRoute(
    origin = origin,
    outboundWaypoints = outboundWaypoints,
    maxSpeed = maxSpeed,
    segmentValidator = segmentValidator,
)?.roundTripMovements

internal data class SpearKillAStarPacketRoute(
    val outboundMovements: List<Vec3>,
    val roundTripMovements: List<Vec3>,
)

internal fun buildSpearKillAStarPacketRoute(
    origin: Vec3,
    outboundWaypoints: List<Vec3>,
    maxSpeed: Double,
    segmentValidator: SpearKillAStarSegmentValidator,
): SpearKillAStarPacketRoute? {
    if (!origin.isFinite() || outboundWaypoints.isEmpty() || !maxSpeed.isFinite() || maxSpeed <= 0.0) {
        return null
    }

    val outbound = buildSpearKillAStarOutboundMovements(
        origin = origin,
        waypoints = outboundWaypoints,
        maxSpeed = maxSpeed,
        segmentValidator = segmentValidator,
    ) ?: return null

    val outboundEndpoint = outbound.fold(origin, Vec3::add)
    if (outboundEndpoint.distanceToSqr(outboundWaypoints.last()) >
        SPEAR_KILL_A_STAR_POSITION_EPSILON_SQUARED
    ) {
        return null
    }

    val inbound = ArrayList<Vec3>(outbound.size)
    var returnPosition = outboundEndpoint
    for (outboundMovement in outbound.asReversed()) {
        val movement = outboundMovement.scale(-1.0)
        val nextPosition = returnPosition.add(movement)
        if (!segmentValidator.isClear(returnPosition, nextPosition)) return null
        inbound += movement
        returnPosition = nextPosition
    }
    if (returnPosition.distanceToSqr(origin) > SPEAR_KILL_A_STAR_POSITION_EPSILON_SQUARED) return null

    val roundTrip = buildList(outbound.size + inbound.size + 1) {
        addAll(outbound)
        addAll(inbound)
        add(Vec3.ZERO)
    }
    return SpearKillAStarPacketRoute(outbound, roundTrip)
}

internal fun shouldReplanSpearKillAStarTarget(
    plannedPosition: Vec3,
    currentPosition: Vec3,
    ticksSincePlan: Int,
    plannedVelocity: Vec3 = Vec3.ZERO,
): Boolean = ticksSincePlan >= SPEAR_KILL_A_STAR_REPLAN_INTERVAL_TICKS &&
    plannedPosition
        .add(plannedVelocity.scale(ticksSincePlan.toDouble()))
        .distanceToSqr(currentPosition) >= SPEAR_KILL_A_STAR_REPLAN_DISTANCE_SQUARED

internal fun buildSpearKillAStarOutboundMovements(
    origin: Vec3,
    waypoints: List<Vec3>,
    maxSpeed: Double,
    segmentValidator: SpearKillAStarSegmentValidator,
): List<Vec3>? {
    val outbound = ArrayList<Vec3>()
    var current = origin
    for (waypoint in waypoints) {
        if (!waypoint.isFinite() || !appendSpearKillAStarBoundedMovements(
                from = current,
                to = waypoint,
                maxSpeed = maxSpeed,
                segmentValidator = segmentValidator,
                destination = outbound,
            )
        ) {
            return null
        }
        current = waypoint
    }
    return outbound.takeIf { it.isNotEmpty() }
}

/**
 * Attack-start gates after look-ray selection.
 * - A*: no LOS/travel gate (pathfinder owns reachability)
 * - Packet (non-A*): LOS only — virtual steps do not need a clear body corridor
 * - Motion: LOS + clear direct travel
 */
internal fun isSpearKillAStarTargetEligible(
    hasLineOfSight: Boolean,
    hasClearDirectTravel: Boolean,
    packetAStarEnabled: Boolean,
    packetMovementMode: Boolean = false,
): Boolean = when {
    packetAStarEnabled -> true
    packetMovementMode -> hasLineOfSight
    else -> hasLineOfSight && hasClearDirectTravel
}

private fun appendSpearKillAStarBoundedMovements(
    from: Vec3,
    to: Vec3,
    maxSpeed: Double,
    segmentValidator: SpearKillAStarSegmentValidator,
    destination: MutableList<Vec3>,
): Boolean {
    val distance = from.distanceTo(to)
    if (distance <= SPEAR_KILL_A_STAR_POSITION_EPSILON) return true
    if (!distance.isFinite() || !maxSpeed.isFinite() || maxSpeed <= 0.0) return false

    val fixedSteps = if (distance <= maxSpeed + SPEAR_KILL_A_STAR_POSITION_EPSILON) {
        listOf(to.subtract(from))
    } else {
        buildSpearKillFixedStepMovements(
            direction = to.subtract(from),
            distance = distance,
            maxSpeed = maxSpeed,
        )
    }

    var stepStart = from
    for (stepIndex in fixedSteps.indices) {
        val stepEnd = if (stepIndex == fixedSteps.lastIndex) {
            to
        } else {
            stepStart.add(fixedSteps[stepIndex])
        }
        val movement = stepEnd.subtract(stepStart)
        if (!movement.isFinite() || movement.length() > maxSpeed + SPEAR_KILL_A_STAR_POSITION_EPSILON ||
            !segmentValidator.isClear(stepStart, stepEnd)
        ) {
            return false
        }
        destination += movement
        stepStart = stepEnd
    }
    return true
}

private fun isSpearKillAStarSameDirection(first: Vec3, second: Vec3): Boolean {
    if (first.lengthSqr() <= SPEAR_KILL_A_STAR_POSITION_EPSILON_SQUARED ||
        second.lengthSqr() <= SPEAR_KILL_A_STAR_POSITION_EPSILON_SQUARED
    ) {
        return false
    }

    return first.normalize().distanceToSqr(second.normalize()) <= SPEAR_KILL_A_STAR_POSITION_EPSILON_SQUARED
}

private fun Vec3.isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

private const val SPEAR_KILL_A_STAR_POSITION_EPSILON = 1.0E-9
private const val SPEAR_KILL_A_STAR_POSITION_EPSILON_SQUARED = 1.0E-18
private const val SPEAR_KILL_A_STAR_REPLAN_INTERVAL_TICKS = 3
private const val SPEAR_KILL_A_STAR_REPLAN_DISTANCE_SQUARED = 0.5 * 0.5
