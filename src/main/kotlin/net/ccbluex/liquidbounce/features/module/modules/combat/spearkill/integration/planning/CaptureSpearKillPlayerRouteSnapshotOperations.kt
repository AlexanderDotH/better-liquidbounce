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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.DirectPacketRoutePlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_DIRECT_PREDICTION_REFINEMENT_LIMIT
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.SpearKillCollisionSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPacketSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPlayerRouteSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.SpearKillRouteTargetSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.buildSpearKillProfiledMovements
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.paceSpearKillNetworkRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.spearKillDirectRouteHitTicks
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.safeVirtualFallStep
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarSegmentValidator
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.buildPredictedDirectPacketRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.currentSpeedProfile
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.hasVisibleSpearKillAttackRay
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.kotlin.toDouble
import net.minecraft.world.phys.Vec3

private data class SpearKillDirectPacketRouteRequest(
    val target: SpearKillRouteTargetSnapshot,
    val routeOrigin: Vec3,
    val travel: Double,
    val settings: SpearKillPacketSessionSettings,
    val sessionOrigin: Vec3,
    val playerSnapshot: SpearKillPlayerRouteSnapshot,
    val collisionSnapshot: SpearKillCollisionSnapshot,
)

internal fun SpearKillModuleState.captureSpearKillPlayerRouteSnapshot(
    sessionOrigin: Vec3,
    stepDistance: Double,
) = SpearKillPlayerRouteSnapshot(
    eyeOffset = player.eyePosition.subtract(player.position()),
    lookAngle = player.lookAngle,
    sessionBoundingBox = spearKillServerCollisionBoxAt(sessionOrigin),
    speedProfile = currentSpeedProfile(stepDistance),
    safeVerticalStep = safeVirtualFallStep,
    maximumTargetDistance = maxTargetDistance.toDouble(),
)

@Suppress("ReturnCount")
internal fun SpearKillModuleState.createDirectPacketRoute(
    target: SpearKillRouteTargetSnapshot,
    routeOrigin: Vec3,
    travel: Double,
    settings: SpearKillPacketSessionSettings,
    sessionOrigin: Vec3,
    playerSnapshot: SpearKillPlayerRouteSnapshot,
    collisionSnapshot: SpearKillCollisionSnapshot,
): DirectPacketRoutePlan? = createDirectPacketRoute(
    SpearKillDirectPacketRouteRequest(
        target,
        routeOrigin,
        travel,
        settings,
        sessionOrigin,
        playerSnapshot,
        collisionSnapshot,
    ),
)

@Suppress("ReturnCount")
private fun SpearKillModuleState.createDirectPacketRoute(
    request: SpearKillDirectPacketRouteRequest,
): DirectPacketRoutePlan? = with(request) {
    if (routeOrigin.distanceTo(target.observedPosition) !in 3.0..playerSnapshot.maximumTargetDistance) return null
    if (!travel.isFinite() || travel <= 0.0) return null
    val eyeOffset = playerSnapshot.eyeOffset
    val routeEye = routeOrigin.add(eyeOffset)
    if (!hasVisibleSpearKillAttackRay(
            eye = routeEye,
            direction = target.observedPosition.add(target.eyeOffset).subtract(routeEye),
            targetBox = target.boundingBox,
            range = playerSnapshot.maximumTargetDistance,
            lineOfSight = collisionSnapshot::isRayClear,
        )
    ) {
        return null
    }
    val profile = playerSnapshot.speedProfile
    val stepCount = buildSpearKillProfiledMovements(Vec3(1.0, 0.0, 0.0), travel, profile).size
    val predictedHitTicks = spearKillDirectRouteHitTicks(
        routingMode = settings.routingMode,
        outboundTickCount = stepCount,
        stepWaitTicks = settings.stepWaitTicks,
        strikeHoldTicks = settings.strikeHoldTicks,
    )
    val segmentValidator = collisionSnapshot.createSegmentValidator(
        origin = sessionOrigin,
        playerBoundingBox = playerSnapshot.sessionBoundingBox,
    )
    refineSpearKillDirectPacketRoute(request, routeEye, predictedHitTicks, segmentValidator)
}

@Suppress("ReturnCount")
private fun SpearKillModuleState.refineSpearKillDirectPacketRoute(
    request: SpearKillDirectPacketRouteRequest,
    routeEye: Vec3,
    initialHitTicks: Int,
    segmentValidator: SpearKillAStarSegmentValidator,
): DirectPacketRoutePlan? = with(request) {
    var predictedHitTicks = initialHitTicks
    repeat(SPEAR_KILL_DIRECT_PREDICTION_REFINEMENT_LIMIT) {
        val rawRoute = buildPredictedDirectPacketRoute(
            target = target,
            routeOrigin = routeOrigin,
            routeEye = routeEye,
            profile = playerSnapshot.speedProfile,
            maxVerticalStep = playerSnapshot.speedProfile.maximumStepLimit,
            segmentValidator = segmentValidator,
            predictedHitTicks = predictedHitTicks,
            fallbackDirection = playerSnapshot.lookAngle,
            hasAttackRay = collisionSnapshot::isRayClear,
        ) ?: return null
        val route = if (settings.allowTerminalBurst) {
            rawRoute
        } else {
            paceSpearKillNetworkRoute(rawRoute)
        }
        val actualHitTicks = spearKillDirectRouteHitTicks(
            routingMode = settings.routingMode,
            outboundTickCount = route.outboundTickCount,
            stepWaitTicks = settings.stepWaitTicks,
            strikeHoldTicks = settings.strikeHoldTicks,
        )
        if (actualHitTicks == predictedHitTicks) return DirectPacketRoutePlan(route, target)
        predictedHitTicks = actualHitTicks
    }
    return null
}
