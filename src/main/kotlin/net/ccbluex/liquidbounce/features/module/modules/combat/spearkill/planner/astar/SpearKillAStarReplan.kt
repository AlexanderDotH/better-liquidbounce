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


import net.minecraft.world.phys.Vec3

/**
 * Finds collision-aware block routes for SpearKill's Packet mode via bidirectional A*.
 *
 * The default callbacks are world-backed and must stay on the client thread. SpearKill's runtime
 * supplies a complete collision snapshot and finishes this CPU search synchronously.
 * The waypoint list omits the origin, matching the historical path-builder contract.
 */
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
    maxVerticalStep: Double = maxSpeed,
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
                maxVerticalStep = maxVerticalStep,
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

internal fun appendSpearKillAStarBoundedMovements(
    from: Vec3,
    to: Vec3,
    maxSpeed: Double,
    segmentValidator: SpearKillAStarSegmentValidator,
    destination: MutableList<Vec3>,
    maxVerticalStep: Double,
): Boolean {
    val distance = from.distanceTo(to)
    if (distance <= SPEAR_KILL_A_STAR_POSITION_EPSILON) return true
    if (!distance.isFinite() || !hasValidSpearKillPacketStepBounds(maxSpeed, maxVerticalStep)) {
        return false
    }

    val fixedSteps = buildSpearKillAStarBoundedBaseMovements(from, to, distance, maxSpeed)
    var stepStart = from
    for (stepIndex in fixedSteps.indices) {
        val baseEnd = if (stepIndex == fixedSteps.lastIndex) {
            to
        } else {
            stepStart.add(fixedSteps[stepIndex])
        }
        if (!appendSpearKillVerticalStepParts(
                from = stepStart,
                to = baseEnd,
                maxSpeed = maxSpeed,
                maxVerticalStep = maxVerticalStep,
                segmentValidator = segmentValidator,
                destination = destination,
            )
        ) {
            return false
        }
        stepStart = baseEnd
    }
    return true
}

private fun buildSpearKillAStarBoundedBaseMovements(
    from: Vec3,
    to: Vec3,
    distance: Double,
    maxSpeed: Double,
): List<Vec3> = if (distance <= maxSpeed + SPEAR_KILL_A_STAR_POSITION_EPSILON) {
    listOf(to.subtract(from))
} else {
    buildSpearKillFixedStepMovements(
        direction = to.subtract(from),
        distance = distance,
        maxSpeed = maxSpeed,
    )
}

internal fun isSpearKillAStarSameDirection(first: Vec3, second: Vec3): Boolean {
    if (first.lengthSqr() <= SPEAR_KILL_A_STAR_POSITION_EPSILON_SQUARED ||
        second.lengthSqr() <= SPEAR_KILL_A_STAR_POSITION_EPSILON_SQUARED
    ) {
        return false
    }

    return first.normalize().distanceToSqr(second.normalize()) <= SPEAR_KILL_A_STAR_POSITION_EPSILON_SQUARED
}

private fun Vec3.isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

internal const val SPEAR_KILL_A_STAR_POSITION_EPSILON = 1.0E-9
internal const val SPEAR_KILL_A_STAR_POSITION_EPSILON_SQUARED = 1.0E-18
internal const val SPEAR_KILL_A_STAR_REPLAN_INTERVAL_TICKS = 3
internal const val SPEAR_KILL_A_STAR_REPLAN_DISTANCE_SQUARED = 0.5 * 0.5
