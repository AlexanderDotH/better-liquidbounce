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

import net.ccbluex.liquidbounce.utils.block.AStarPathBuilder
import net.ccbluex.liquidbounce.utils.math.bottomCenter
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Finds collision-aware block routes for SpearKill's Packet mode.
 *
 * [AStarPathBuilder] reads the live world, so callers must invoke [plan] on the client thread.
 * The resulting waypoint list deliberately omits the origin, matching the path-builder contract.
 */
internal class SpearKillAStarRoutePlanner(
    override val allowDiagonal: Boolean,
    val maxCost: Int,
) : AStarPathBuilder {

    override val maxIterations: Int
        get() = SPEAR_KILL_A_STAR_MAX_ITERATIONS

    override val stopRange: Double
        get() = SPEAR_KILL_A_STAR_STOP_RANGE

    fun plan(origin: Vec3, destination: Vec3): List<Vec3>? {
        if (maxCost <= 0 || !origin.isFinite() || !destination.isFinite()) return null

        val start = BlockPos.containing(origin)
        val end = BlockPos.containing(destination)
        val route = findPath(
            start = start,
            end = end,
            maxCost = maxCost,
        )
        if (route.isEmpty() && !end.closerThan(start, stopRange)) return null

        return route.map { it.bottomCenter }
    }
}

/** Validates one virtual player movement segment before SpearKill emits it. */
internal fun interface SpearKillAStarSegmentValidator {
    fun isClear(from: Vec3, to: Vec3): Boolean
}

/**
 * Creates a collision validator for virtual movement relative to [origin].
 *
 * [hasCollision] receives the swept current-player bounding box, making the world lookup an
 * injected detail and keeping [buildSpearKillAStarPacketMovements] independently testable.
 */
internal fun createSpearKillAStarSegmentValidator(
    origin: Vec3,
    playerBoundingBox: AABB,
    hasCollision: (AABB) -> Boolean,
): SpearKillAStarSegmentValidator = SpearKillAStarSegmentValidator { from, to ->
    if (!from.isFinite() || !to.isFinite()) {
        false
    } else {
        val fromBox = playerBoundingBox.move(from.subtract(origin))
        val toBox = playerBoundingBox.move(to.subtract(origin))
        !hasCollision(fromBox.minmax(toBox))
    }
}

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
    val destination = outboundWaypoints.last()

    val inbound = outbound.asReversed().map { it.scale(-1.0) }
    var returnPosition = destination
    for (movement in inbound) {
        val nextPosition = returnPosition.add(movement)
        if (!segmentValidator.isClear(returnPosition, nextPosition)) return null
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
): Boolean = ticksSincePlan >= SPEAR_KILL_A_STAR_REPLAN_INTERVAL_TICKS &&
    plannedPosition.distanceToSqr(currentPosition) >= SPEAR_KILL_A_STAR_REPLAN_DISTANCE_SQUARED

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

/** A* Packet movement is the sole mode allowed to bypass the direct-path target checks. */
internal fun isSpearKillAStarTargetEligible(
    hasLineOfSight: Boolean,
    hasClearDirectTravel: Boolean,
    packetAStarEnabled: Boolean,
): Boolean = packetAStarEnabled || hasLineOfSight && hasClearDirectTravel

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

private const val SPEAR_KILL_A_STAR_MAX_ITERATIONS = 500
private const val SPEAR_KILL_A_STAR_STOP_RANGE = 1.0
private const val SPEAR_KILL_A_STAR_POSITION_EPSILON = 1.0E-9
private const val SPEAR_KILL_A_STAR_POSITION_EPSILON_SQUARED = 1.0E-18
private const val SPEAR_KILL_A_STAR_REPLAN_INTERVAL_TICKS = 1
private const val SPEAR_KILL_A_STAR_REPLAN_DISTANCE_SQUARED = 0.25 * 0.25
