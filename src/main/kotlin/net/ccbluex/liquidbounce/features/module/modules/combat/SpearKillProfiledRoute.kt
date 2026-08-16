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
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

private data class SpearKillProfiledRouteCursor(
    val movements: List<Vec3>,
    val position: Vec3,
)

internal data class SpearKillProfiledDirectAttackRoute(
    val line: SpearKillDirectAttackLine,
    val approach: SpearKillAStarAttackApproach,
    val packetRoute: SpearKillAStarPacketRoute,
)

/** Builds one collision-validated, terminal-loaded diagonal without lateral or vertical staging. */
@Suppress("LongParameterList", "ReturnCount")
internal fun buildSpearKillProfiledDirectAttackRoute(
    origin: Vec3,
    targetBox: AABB,
    targetEyePosition: Vec3,
    playerEyeOffset: Vec3,
    preferredDirection: Vec3,
    profile: SpearKillSpeedProfile,
    segmentValidator: SpearKillAStarSegmentValidator,
    kineticRequirements: SpearKillKineticDamageRequirements? = null,
    targetMovement: Vec3 = Vec3.ZERO,
): SpearKillProfiledDirectAttackRoute? {
    val line = solveSpearKillDirectAttackLine(
        origin = origin,
        targetBox = targetBox,
        targetEyePosition = targetEyePosition,
        playerEyeOffset = playerEyeOffset,
        fallbackDirection = preferredDirection,
    ) ?: return null
    val displacement = line.terminalWaypoint.subtract(origin)
    val movements = buildSpearKillTerminalLoadedProfiledMovements(
        direction = displacement,
        distance = displacement.length(),
        profile = profile,
        maxVerticalStep = profile.maximumStepLimit,
    ) ?: return null
    var position = origin
    for (movement in movements) {
        val next = position.add(movement)
        if (!segmentValidator.isClear(position, next)) return null
        position = next
    }
    val outbound = SpearKillProfiledRouteCursor(movements, position)
    val terminalMovement = movements.lastOrNull() ?: return null
    val approach = SpearKillAStarAttackApproach(
        plannerGoal = line.terminalWaypoint.subtract(terminalMovement),
        terminalWaypoint = line.terminalWaypoint,
    )
    if (kineticRequirements != null && !estimateSpearKillKineticDamage(
            deliveredMovement = terminalMovement,
            targetMovement = targetMovement,
            lookDirection = line.direction,
            requirements = kineticRequirements,
        ).meetsRequirements
    ) {
        return null
    }
    val packetRoute = buildSpearKillProfiledRoundTrip(
        origin = origin,
        outbound = outbound,
        destination = line.terminalWaypoint,
        segmentValidator = segmentValidator,
    ) ?: return null
    return SpearKillProfiledDirectAttackRoute(line, approach, packetRoute)
}

/** Profile-aware A* segmentation with the same exact inverse recovery contract as PacketBoot. */
internal fun buildSpearKillProfiledAStarPacketRoute(
    origin: Vec3,
    outboundWaypoints: List<Vec3>,
    profile: SpearKillSpeedProfile,
    segmentValidator: SpearKillAStarSegmentValidator,
    maxVerticalStep: Double = profile.maximumStepLimit,
): SpearKillAStarPacketRoute? {
    if (!origin.hasFiniteProfileCoordinates() || outboundWaypoints.isEmpty() ||
        !maxVerticalStep.isFinite() || maxVerticalStep <= 0.0
    ) {
        return null
    }
    val outbound = buildSpearKillProfiledOutbound(
        origin,
        outboundWaypoints,
        profile,
        segmentValidator,
        maxVerticalStep,
    ) ?: return null
    return buildSpearKillProfiledRoundTrip(origin, outbound, outboundWaypoints.last(), segmentValidator)
}

private fun buildSpearKillProfiledOutbound(
    origin: Vec3,
    waypoints: List<Vec3>,
    profile: SpearKillSpeedProfile,
    segmentValidator: SpearKillAStarSegmentValidator,
    maxVerticalStep: Double,
): SpearKillProfiledRouteCursor? {
    val movements = ArrayList<Vec3>()
    var current = origin
    for (waypoint in waypoints) {
        if (!waypoint.hasFiniteProfileCoordinates()) return null
        while (current.distanceToSqr(waypoint) > SPEAR_KILL_PROFILE_EPSILON_SQUARED) {
            val step = nextSpearKillProfiledRouteStep(
                current,
                waypoint,
                profile,
                movements.size,
                maxVerticalStep,
            ) ?: return null
            val next = current.add(step)
            if (!segmentValidator.isClear(current, next)) return null
            movements += step
            current = next
        }
    }
    return SpearKillProfiledRouteCursor(movements, current)
}

private fun nextSpearKillProfiledRouteStep(
    current: Vec3,
    waypoint: Vec3,
    profile: SpearKillSpeedProfile,
    stepIndex: Int,
    maxVerticalStep: Double,
): Vec3? {
    if (stepIndex >= SPEAR_KILL_MAX_PROFILE_STEPS) return null
    val remaining = waypoint.subtract(current)
    val cap = profile.stepAt(stepIndex).stepLimit
    var step = if (remaining.length() <= cap) remaining else boundedSpearKillProfileStep(remaining, cap)
    if (abs(step.y) > maxVerticalStep) {
        step = step.scale(maxVerticalStep / abs(step.y))
    }
    return step.takeIf { it.lengthSqr() > SPEAR_KILL_PROFILE_EPSILON_SQUARED }
}

private fun buildSpearKillProfiledRoundTrip(
    origin: Vec3,
    outbound: SpearKillProfiledRouteCursor,
    destination: Vec3,
    segmentValidator: SpearKillAStarSegmentValidator,
    terminalBurstSteps: Int = 0,
): SpearKillAStarPacketRoute? {
    if (outbound.movements.isEmpty() ||
        outbound.position.distanceToSqr(destination) > SPEAR_KILL_PROFILE_EPSILON_SQUARED
    ) {
        return null
    }
    val inbound = ArrayList<Vec3>(outbound.movements.size)
    var current = outbound.position
    for (outboundMovement in outbound.movements.asReversed()) {
        val movement = outboundMovement.scale(-1.0)
        val next = current.add(movement)
        if (!segmentValidator.isClear(current, next)) return null
        inbound += movement
        current = next
    }
    if (current.distanceToSqr(origin) > SPEAR_KILL_PROFILE_EPSILON_SQUARED) return null
    return SpearKillAStarPacketRoute(
        outboundMovements = outbound.movements,
        roundTripMovements = buildList(outbound.movements.size + inbound.size + 1) {
            addAll(outbound.movements)
            addAll(inbound)
            add(Vec3.ZERO)
        },
        terminalBurstSteps = terminalBurstSteps,
    )
}

private fun Vec3.hasFiniteProfileCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
