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


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.AStarSpatialPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.AStarAttackPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.SpearKillRouteTargetPrediction
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.SpearKillRouteTargetSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.buildSpearKillAStarPathSchedule
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.shouldRefineSpearKillAStarApproach
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarAttackApproach
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.createSpearKillAStarAttackApproachCandidates
import net.minecraft.world.phys.Vec3

internal data class SpearKillTimedAStarPlanRequest(
    val approach: SpearKillAStarAttackApproach,
    val spatialContext: SpearKillAStarSpatialPlanContext,
    val seedPrediction: SpearKillRouteTargetPrediction,
    val eyeOffset: Vec3,
    val target: SpearKillRouteTargetSnapshot,
    val stepWaitTicks: Int,
    val strikeHoldTicks: Int,
    val terminalLungeDistance: Double,
    val hasAttackRay: (Vec3, Vec3) -> Boolean,
)

internal fun SpearKillModuleState.buildTimedAStarAttackPlanForApproach(
    request: SpearKillTimedAStarPlanRequest,
): AStarAttackPlan? = with(request) {
    val seedSpatialPlan = buildSpatialAStarAttackPlanForApproach(approach, spatialContext) ?: return null
    val seedSchedule = buildSpearKillAStarPathSchedule(
        outboundStepCount = seedSpatialPlan.packetRoute.outboundMovements.size,
        stepWaitTicks = stepWaitTicks,
        terminalSuffixCount = seedSpatialPlan.terminalSuffixCount,
        strikeHoldTicks = strikeHoldTicks,
    ) ?: return null
    val hitPrediction = target.predict(seedSchedule.hitTick)
    val refinedSpatialPlan = refineSpearKillAStarSpatialPlan(
        seedPrediction,
        hitPrediction,
        request,
    )
    return timePreferredSpearKillAStarPlan(
        seedSpatialPlan,
        refinedSpatialPlan,
        request,
    )
}

private fun SpearKillModuleState.refineSpearKillAStarSpatialPlan(
    seedPrediction: SpearKillRouteTargetPrediction,
    hitPrediction: SpearKillRouteTargetPrediction,
    request: SpearKillTimedAStarPlanRequest,
): AStarSpatialPlan? {
    if (!shouldRefineSpearKillAStarApproach(seedPrediction.position, hitPrediction.position)) return null
    val approach = request.approach
    val approachDirection = approach.terminalWaypoint.subtract(approach.plannerGoal)
    val refinedApproach = createSpearKillAStarAttackApproachCandidates(
        targetBox = hitPrediction.boundingBox,
        targetEyePosition = hitPrediction.eyePosition,
        playerEyeOffset = request.eyeOffset,
        preferredDirection = approachDirection,
        terminalLungeDistance = request.terminalLungeDistance,
        bearingCount = 1,
    ).firstOrNull()?.takeIf { candidate ->
        request.spatialContext.segmentValidator.isClear(candidate.plannerGoal, candidate.terminalWaypoint)
    }
    return refinedApproach?.let { buildSpatialAStarAttackPlanForApproach(it, request.spatialContext) }
}

private fun SpearKillModuleState.timePreferredSpearKillAStarPlan(
    seedSpatialPlan: AStarSpatialPlan,
    refinedSpatialPlan: AStarSpatialPlan?,
    request: SpearKillTimedAStarPlanRequest,
): AStarAttackPlan? {
    val preferredSpatialPlan = refinedSpatialPlan ?: seedSpatialPlan
    return timeSpatialAStarAttackPlan(
        spatialPlan = preferredSpatialPlan,
        eyeOffset = request.eyeOffset,
        target = request.target,
        stepWaitTicks = request.stepWaitTicks,
        strikeHoldTicks = request.strikeHoldTicks,
        hasAttackRay = request.hasAttackRay,
    ) ?: refinedSpatialPlan?.let {
        timeSpatialAStarAttackPlan(
            spatialPlan = seedSpatialPlan,
            eyeOffset = request.eyeOffset,
            target = request.target,
            stepWaitTicks = request.stepWaitTicks,
            strikeHoldTicks = request.strikeHoldTicks,
            hasAttackRay = request.hasAttackRay,
        )
    }
}
