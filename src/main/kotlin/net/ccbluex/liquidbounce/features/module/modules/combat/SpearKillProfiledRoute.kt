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
    val approach: SpearKillAStarAttackApproach,
    val packetRoute: SpearKillAStarPacketRoute,
)

/**
 * Lower targets use a collision-validated run-up and one logical full-speed dive. Other targets
 * retain the lateral terminal approach. A dive is physically segmented but remains one tick.
 */
@Suppress("LongParameterList")
internal fun buildSpearKillProfiledDirectAttackRoute(
    origin: Vec3,
    targetBox: AABB,
    targetEyePosition: Vec3,
    playerEyeOffset: Vec3,
    preferredDirection: Vec3,
    profile: SpearKillSpeedProfile,
    segmentValidator: SpearKillAStarSegmentValidator,
    maxVerticalStep: Double = profile.maximumStepLimit,
): SpearKillProfiledDirectAttackRoute? = if (
    targetBox.maxY < origin.y - SPEAR_KILL_VERTICAL_DIVE_EPSILON
) {
    buildSpearKillProfiledVerticalDiveRoute(
        origin = origin,
        targetBox = targetBox,
        targetEyePosition = targetEyePosition,
        playerEyeOffset = playerEyeOffset,
        profile = profile,
        segmentValidator = segmentValidator,
        maxVerticalStep = maxVerticalStep,
    )
} else {
    buildSpearKillProfiledLateralAttackRoute(
        origin = origin,
        targetBox = targetBox,
        targetEyePosition = targetEyePosition,
        playerEyeOffset = playerEyeOffset,
        preferredDirection = preferredDirection,
        profile = profile,
        segmentValidator = segmentValidator,
        maxVerticalStep = maxVerticalStep,
    )
}

@Suppress("LongParameterList")
private fun buildSpearKillProfiledVerticalDiveRoute(
    origin: Vec3,
    targetBox: AABB,
    targetEyePosition: Vec3,
    playerEyeOffset: Vec3,
    profile: SpearKillSpeedProfile,
    segmentValidator: SpearKillAStarSegmentValidator,
    maxVerticalStep: Double,
): SpearKillProfiledDirectAttackRoute? {
    if (!maxVerticalStep.isFinite() || maxVerticalStep <= 0.0) return null

    val refined = refineSpearKillVerticalDiveApproach(
        origin = origin,
        targetBox = targetBox,
        targetEyePosition = targetEyePosition,
        playerEyeOffset = playerEyeOffset,
        profile = profile,
        segmentValidator = segmentValidator,
        maxVerticalStep = maxVerticalStep,
    ) ?: return null
    val (approach, approachRoute) = refined
    val terminalMovement = approach.terminalWaypoint.subtract(approach.plannerGoal)
    val terminalMovements = buildSpearKillFixedStepMovements(
        direction = terminalMovement,
        distance = terminalMovement.length(),
        maxSpeed = maxVerticalStep,
    )
    var terminalPosition = approachRoute.position
    for (movement in terminalMovements) {
        val next = terminalPosition.add(movement)
        if (!segmentValidator.isClear(terminalPosition, next)) return null
        terminalPosition = next
    }
    val outbound = SpearKillProfiledRouteCursor(
        movements = approachRoute.movements + terminalMovements,
        position = terminalPosition,
    )
    val packetRoute = buildSpearKillProfiledRoundTrip(
        origin = origin,
        outbound = outbound,
        destination = approach.terminalWaypoint,
        segmentValidator = segmentValidator,
        terminalBurstSteps = terminalMovements.size.takeIf { it > 1 } ?: 0,
    ) ?: return null
    if (!isSpearKillAStarTerminalStepValid(
            outboundMovements = packetRoute.outboundMovements,
            approach = approach,
            stepLimit = profile.maximumStepLimit,
        )
    ) {
        return null
    }
    return SpearKillProfiledDirectAttackRoute(approach, packetRoute)
}

@Suppress("LongParameterList")
private fun buildSpearKillProfiledLateralAttackRoute(
    origin: Vec3,
    targetBox: AABB,
    targetEyePosition: Vec3,
    playerEyeOffset: Vec3,
    preferredDirection: Vec3,
    profile: SpearKillSpeedProfile,
    segmentValidator: SpearKillAStarSegmentValidator,
    maxVerticalStep: Double,
): SpearKillProfiledDirectAttackRoute? {
    val approach = createSpearKillAStarAttackApproachCandidates(
        targetBox = targetBox,
        targetEyePosition = targetEyePosition,
        playerEyeOffset = playerEyeOffset,
        preferredDirection = preferredDirection,
        terminalLungeDistance = profile.maximumStepLimit,
        bearingCount = 1,
    ).firstOrNull { candidate ->
        segmentValidator.isClear(candidate.plannerGoal, candidate.terminalWaypoint)
    } ?: return null
    val packetRoute = buildSpearKillProfiledAStarPacketRoute(
        origin = origin,
        outboundWaypoints = listOf(approach.plannerGoal, approach.terminalWaypoint),
        profile = profile,
        segmentValidator = segmentValidator,
        maxVerticalStep = maxVerticalStep,
    ) ?: return null
    if (!isSpearKillAStarTerminalStepValid(
            outboundMovements = packetRoute.outboundMovements,
            approach = approach,
            stepLimit = profile.maximumStepLimit,
        )
    ) {
        return null
    }
    return SpearKillProfiledDirectAttackRoute(approach, packetRoute)
}

@Suppress("LongParameterList")
private fun refineSpearKillVerticalDiveApproach(
    origin: Vec3,
    targetBox: AABB,
    targetEyePosition: Vec3,
    playerEyeOffset: Vec3,
    profile: SpearKillSpeedProfile,
    segmentValidator: SpearKillAStarSegmentValidator,
    maxVerticalStep: Double,
): Pair<SpearKillAStarAttackApproach, SpearKillProfiledRouteCursor>? {
    var terminalLungeDistance = profile.maximumStepLimit
    repeat(SPEAR_KILL_VERTICAL_DIVE_PROFILE_REFINEMENTS) {
        val approach = createSpearKillVerticalDiveAttackApproach(
            targetBox = targetBox,
            targetEyePosition = targetEyePosition,
            playerEyeOffset = playerEyeOffset,
            terminalLungeDistance = terminalLungeDistance,
        ) ?: return null
        val approachVerticalStep = if (approach.plannerGoal.y >= origin.y) {
            profile.maximumStepLimit
        } else {
            maxVerticalStep
        }
        val approachRoute = buildSpearKillProfiledOutbound(
            origin = origin,
            waypoints = listOf(approach.plannerGoal),
            profile = profile,
            segmentValidator = segmentValidator,
            maxVerticalStep = approachVerticalStep,
        ) ?: return null
        val resolvedLungeDistance = profile.stepAt(approachRoute.movements.size).stepLimit
        if (abs(resolvedLungeDistance - terminalLungeDistance) <= SPEAR_KILL_VERTICAL_DIVE_EPSILON) {
            return approach to approachRoute
        }
        terminalLungeDistance = resolvedLungeDistance
    }
    return null
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

private const val SPEAR_KILL_VERTICAL_DIVE_PROFILE_REFINEMENTS = 32
private const val SPEAR_KILL_VERTICAL_DIVE_EPSILON = 1.0E-9
