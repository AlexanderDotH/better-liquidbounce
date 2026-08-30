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
internal fun buildSpearKillAStarPacketMovements(
    origin: Vec3,
    outboundWaypoints: List<Vec3>,
    maxSpeed: Double,
    segmentValidator: SpearKillAStarSegmentValidator,
    maxVerticalStep: Double = maxSpeed,
): List<Vec3>? = buildSpearKillAStarPacketRoute(
    origin = origin,
    outboundWaypoints = outboundWaypoints,
    maxSpeed = maxSpeed,
    segmentValidator = segmentValidator,
    maxVerticalStep = maxVerticalStep,
)?.roundTripMovements

internal data class SpearKillAStarPacketRoute(
    val outboundMovements: List<Vec3>,
    val roundTripMovements: List<Vec3>,
    val terminalBurstSteps: Int = 0,
) {
    init {
        require(terminalBurstSteps == 0 || terminalBurstSteps in 2..outboundMovements.size) {
            "A terminal burst must contain at least two outbound movements"
        }
    }

    /** Physical burst packets share one client tick and therefore one acceleration confirmation. */
    val outboundTickCount: Int
        get() = outboundMovements.size - terminalBurstSteps + if (terminalBurstSteps > 0) 1 else 0
}

internal fun buildSpearKillAStarPacketRoute(
    origin: Vec3,
    outboundWaypoints: List<Vec3>,
    maxSpeed: Double,
    segmentValidator: SpearKillAStarSegmentValidator,
    maxVerticalStep: Double = maxSpeed,
): SpearKillAStarPacketRoute? {
    if (!origin.isFinite() || outboundWaypoints.isEmpty() ||
        !hasValidSpearKillPacketStepBounds(maxSpeed, maxVerticalStep)
    ) {
        return null
    }

    val outbound = buildSpearKillAStarOutboundMovements(
        origin = origin,
        waypoints = outboundWaypoints,
        maxSpeed = maxSpeed,
        segmentValidator = segmentValidator,
        maxVerticalStep = maxVerticalStep,
    ) ?: return null

    val outboundEndpoint = outbound.fold(origin, Vec3::add)
    if (outboundEndpoint.distanceToSqr(outboundWaypoints.last()) >
        SPEAR_KILL_A_STAR_POSITION_EPSILON_SQUARED
    ) {
        return null
    }

    val inbound = buildSpearKillAStarReturnMovements(
        origin,
        outboundEndpoint,
        outbound,
        segmentValidator,
    ) ?: return null

    val roundTrip = buildList(outbound.size + inbound.size + 1) {
        addAll(outbound)
        addAll(inbound)
        add(Vec3.ZERO)
    }
    return SpearKillAStarPacketRoute(outbound, roundTrip)
}

private fun buildSpearKillAStarReturnMovements(
    origin: Vec3,
    outboundEndpoint: Vec3,
    outbound: List<Vec3>,
    segmentValidator: SpearKillAStarSegmentValidator,
): List<Vec3>? {
    val inbound = ArrayList<Vec3>(outbound.size)
    var returnPosition = outboundEndpoint
    for (outboundMovement in outbound.asReversed()) {
        val movement = outboundMovement.scale(-1.0)
        val nextPosition = returnPosition.add(movement)
        if (!segmentValidator.isClear(returnPosition, nextPosition)) return null
        inbound += movement
        returnPosition = nextPosition
    }
    return inbound.takeIf {
        returnPosition.distanceToSqr(origin) <= SPEAR_KILL_A_STAR_POSITION_EPSILON_SQUARED
    }
}

private fun Vec3.isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
