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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.AStarAttackPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillAStarSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.SpearKillCollisionSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPlayerRouteSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.SpearKillRouteTargetPrediction
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.SpearKillRouteTargetSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.SpearKillSpeedProfile
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.calculateSpearKillAttackDirection
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.calculateSpearKillProfiledTravel
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.isBetterSpearKillTimedAStarPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.spearKillAStarCandidateLowerBoundHitTick
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarAttackApproach
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarRoutePlanner
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarSegmentValidator
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.createSpearKillAStarAttackApproachCandidates
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.filterSpearKillAStarApproachesByTerminalClearance
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.spearKillPacketTravelTicks
import net.minecraft.world.phys.Vec3

internal data class SpearKillAStarPlanRequest(
    val target: SpearKillRouteTargetSnapshot,
    val routeOrigin: Vec3,
    val sessionOrigin: Vec3,
    val stepWaitTicks: Int,
    val strikeHoldTicks: Int,
    val aStar: SpearKillAStarSessionSettings,
    val playerSnapshot: SpearKillPlayerRouteSnapshot,
    val collisionSnapshot: SpearKillCollisionSnapshot,
)

private data class SpearKillAStarPlanningContext(
    val target: SpearKillRouteTargetSnapshot,
    val routeOrigin: Vec3,
    val seedPrediction: SpearKillRouteTargetPrediction,
    val eyeOffset: Vec3,
    val lookAngle: Vec3,
    val stepWaitTicks: Int,
    val strikeHoldTicks: Int,
    val terminalLungeDistance: Double,
    val spatialContext: SpearKillAStarSpatialPlanContext,
    val collisionSnapshot: SpearKillCollisionSnapshot,
)

internal fun SpearKillModuleState.createAStarAttackPlan(
    request: SpearKillAStarPlanRequest,
): AStarAttackPlan? {
    val context = prepareSpearKillAStarPlanningContext(request)
    val approaches = createSpearKillAStarApproaches(context)
    return selectBestSpearKillAStarPlan(context, approaches)
}

private fun prepareSpearKillAStarPlanningContext(
    request: SpearKillAStarPlanRequest,
): SpearKillAStarPlanningContext {
    val speedProfile = request.playerSnapshot.speedProfile
    val effectiveMaxSpeed = speedProfile.maximumStepLimit
    val seedStepCount = calculateSpearKillProfiledTravel(
        distance = request.routeOrigin.distanceTo(request.target.observedPosition),
        profile = speedProfile,
    ).stepCount
    val seedPrediction = request.target.predict(spearKillPacketTravelTicks(seedStepCount, request.stepWaitTicks))
    val segmentValidator = request.collisionSnapshot.createSegmentValidator(
        origin = request.sessionOrigin,
        playerBoundingBox = request.playerSnapshot.sessionBoundingBox,
    )
    val spatialContext = createSpearKillAStarSpatialContext(request, speedProfile, segmentValidator)
    return SpearKillAStarPlanningContext(
        request.target,
        request.routeOrigin,
        seedPrediction,
        request.playerSnapshot.eyeOffset,
        request.playerSnapshot.lookAngle,
        request.stepWaitTicks,
        request.strikeHoldTicks,
        effectiveMaxSpeed,
        spatialContext,
        request.collisionSnapshot,
    )
}

private fun createSpearKillAStarSpatialContext(
    request: SpearKillAStarPlanRequest,
    speedProfile: SpearKillSpeedProfile,
    segmentValidator: SpearKillAStarSegmentValidator,
): SpearKillAStarSpatialPlanContext {
    val routePlanner = SpearKillAStarRoutePlanner(
        allowDiagonal = request.aStar.diagonal,
        maxCost = request.aStar.maxCost,
        isPassable = request.collisionSnapshot::isPassable,
        canTraverse = segmentValidator::isClear,
    )
    return SpearKillAStarSpatialPlanContext(
        request.routeOrigin,
        routePlanner,
        segmentValidator,
        speedProfile.maximumStepLimit,
        speedProfile,
        request.aStar.lineOfSightShortcuts,
        request.playerSnapshot.safeVerticalStep,
    )
}

private fun createSpearKillAStarApproaches(
    context: SpearKillAStarPlanningContext,
) = with(context) {
    val routeEyePosition = routeOrigin.add(eyeOffset)
    val preferredDirection = calculateSpearKillAttackDirection(
        playerEyePosition = routeEyePosition,
        predictedTargetPosition = seedPrediction.position,
        targetEyeOffset = seedPrediction.eyePosition.subtract(seedPrediction.position),
        fallbackDirection = lookAngle,
    )
    filterSpearKillAStarApproachesByTerminalClearance(
        approaches = createSpearKillAStarAttackApproachCandidates(
            targetBox = seedPrediction.boundingBox,
            targetEyePosition = seedPrediction.eyePosition,
            playerEyeOffset = eyeOffset,
            preferredDirection = preferredDirection,
            terminalLungeDistance = terminalLungeDistance,
        ),
        segmentValidator = spatialContext.segmentValidator,
    )
}

private fun SpearKillModuleState.selectBestSpearKillAStarPlan(
    context: SpearKillAStarPlanningContext,
    approaches: List<SpearKillAStarAttackApproach>,
): AStarAttackPlan? = with(context) {
    var best: AStarAttackPlan? = null
    for (approach in approaches) {
        val lowerBoundHitTick = spearKillAStarCandidateLowerBoundHitTick(
            routeOrigin = routeOrigin,
            plannerGoal = approach.plannerGoal,
            stepLimit = spatialContext.effectiveMaxSpeed,
            terminalLungeDistance = terminalLungeDistance,
            stepWaitTicks = stepWaitTicks,
            strikeHoldTicks = strikeHoldTicks,
        )
        if (best != null && lowerBoundHitTick > best.schedule.hitTick) continue
        val candidate = buildSpearKillTimedAStarCandidate(context, approach) ?: continue
        if (isBetterSpearKillAStarCandidate(candidate, best)) best = candidate
    }
    return best
}

private fun SpearKillModuleState.buildSpearKillTimedAStarCandidate(
    context: SpearKillAStarPlanningContext,
    approach: SpearKillAStarAttackApproach,
): AStarAttackPlan? = buildTimedAStarAttackPlanForApproach(
    SpearKillTimedAStarPlanRequest(
        approach,
        context.spatialContext,
        context.seedPrediction,
        context.eyeOffset,
        context.target,
        context.stepWaitTicks,
        context.strikeHoldTicks,
        context.terminalLungeDistance,
        context.collisionSnapshot::isRayClear,
    ),
)

private fun isBetterSpearKillAStarCandidate(
    candidate: AStarAttackPlan,
    best: AStarAttackPlan?,
): Boolean {
    best ?: return true
    return isBetterSpearKillTimedAStarPlan(
        candidateHitTick = candidate.schedule.hitTick,
        candidateOutboundSteps = candidate.packetRoute.outboundMovements.size,
        bestHitTick = best.schedule.hitTick,
        bestOutboundSteps = best.packetRoute.outboundMovements.size,
    )
}
