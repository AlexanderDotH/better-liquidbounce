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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarPacketRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarSegmentValidator
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

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

private fun Vec3.hasFiniteProfileCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
