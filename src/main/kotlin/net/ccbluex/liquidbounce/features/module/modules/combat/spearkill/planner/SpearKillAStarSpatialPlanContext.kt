/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.AStarAttackPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.AStarSpatialPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.SpearKillRouteTargetSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.SpearKillSpeedProfile
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.buildSpearKillAStarPathSchedule
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.buildSpearKillProfiledAStarPacketRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.countSpearKillAStarTerminalSuffix
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarAttackApproach
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarRoutePlanner
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarSegmentValidator
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.buildSpearKillAStarRenderPath
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.compactSpearKillAStarWaypoints
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.isSpearKillAStarTerminalStepValid
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.resolveSpearKillAStarApproachRoute
import net.minecraft.world.phys.Vec3

internal data class SpearKillAStarSpatialPlanContext(
    val routeOrigin: Vec3,
    val routePlanner: SpearKillAStarRoutePlanner,
    val segmentValidator: SpearKillAStarSegmentValidator,
    val effectiveMaxSpeed: Double,
    val speedProfile: SpearKillSpeedProfile,
    val lineOfSightShortcuts: Boolean,
    val safeVerticalStep: Double,
)

@Suppress("ReturnCount")
internal fun SpearKillModuleState.buildSpatialAStarAttackPlanForApproach(
    approach: SpearKillAStarAttackApproach,
    context: SpearKillAStarSpatialPlanContext,
): AStarSpatialPlan? {
    val outboundWaypoints = buildSpearKillAStarOutboundWaypoints(
        approach,
        context,
    ) ?: return null
    val packetRoute = buildSpearKillProfiledAStarPacketRoute(
        origin = context.routeOrigin,
        outboundWaypoints = outboundWaypoints,
        profile = context.speedProfile,
        segmentValidator = context.segmentValidator,
        maxVerticalStep = context.safeVerticalStep,
    ) ?: return null
    if (!isSpearKillAStarTerminalStepValid(packetRoute.outboundMovements, approach, context.effectiveMaxSpeed)) {
        return null
    }
    val terminalSuffixCount = countSpearKillAStarTerminalSuffix(
        outboundMovements = packetRoute.outboundMovements,
        approach = approach,
        stepLimit = context.effectiveMaxSpeed,
    ) ?: return null

    return AStarSpatialPlan(
        approach = approach,
        packetRoute = packetRoute,
        renderPath = buildSpearKillAStarRenderPath(context.routeOrigin, outboundWaypoints),
        terminalSuffixCount = terminalSuffixCount,
    )
}

private fun buildSpearKillAStarOutboundWaypoints(
    approach: SpearKillAStarAttackApproach,
    context: SpearKillAStarSpatialPlanContext,
): List<Vec3>? {
    val route = resolveSpearKillAStarApproachRoute(
        origin = context.routeOrigin,
        plannerGoal = approach.plannerGoal,
        segmentValidator = context.segmentValidator,
        routeSearch = { context.routePlanner.plan(context.routeOrigin, approach.plannerGoal) },
    ) ?: return null
    val compactedRoute = compactSpearKillAStarWaypoints(
        origin = context.routeOrigin,
        waypoints = route,
        maxSpeed = context.effectiveMaxSpeed,
        segmentValidator = context.segmentValidator,
        lineOfSightShortcuts = context.lineOfSightShortcuts,
    )
    return compactedRoute + approach.plannerGoal + approach.terminalWaypoint
}

@Suppress("LongParameterList", "ReturnCount")
internal fun SpearKillModuleState.timeSpatialAStarAttackPlan(
    spatialPlan: AStarSpatialPlan,
    eyeOffset: Vec3,
    target: SpearKillRouteTargetSnapshot,
    stepWaitTicks: Int,
    strikeHoldTicks: Int,
    hasAttackRay: (Vec3, Vec3) -> Boolean,
): AStarAttackPlan? {
    val schedule = buildSpearKillAStarPathSchedule(
        outboundStepCount = spatialPlan.packetRoute.outboundMovements.size,
        stepWaitTicks = stepWaitTicks,
        terminalSuffixCount = spatialPlan.terminalSuffixCount,
        strikeHoldTicks = strikeHoldTicks,
    ) ?: return null
    val hitPrediction = target.predict(schedule.hitTick)
    if (!hasValidAStarTerminalAttackRay(
            targetBox = hitPrediction.boundingBox,
            eyeOffset = eyeOffset,
            approach = spatialPlan.approach,
            lineOfSight = hasAttackRay,
        )
    ) {
        return null
    }
    return AStarAttackPlan(
        approach = spatialPlan.approach,
        packetRoute = spatialPlan.packetRoute,
        renderPath = spatialPlan.renderPath,
        targetPosition = hitPrediction.observedPosition,
        targetVelocity = target.velocity,
        schedule = schedule,
        preStrikeHoldTicks = SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS,
        terminalSuffixCount = spatialPlan.terminalSuffixCount,
    )
}
