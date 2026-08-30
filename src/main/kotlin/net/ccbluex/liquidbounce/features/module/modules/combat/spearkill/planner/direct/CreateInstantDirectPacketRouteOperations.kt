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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.DirectPacketRoutePlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_DIRECT_PREDICTION_REFINEMENT_LIMIT
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPacketSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPlayerRouteSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.SpearKillRouteTargetSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.SpearKillRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.spearKillDirectRouteHitTicks
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.createInstantDirectRouteCandidate
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.hasValidAStarTerminalAttackRay
import net.minecraft.world.phys.Vec3

private data class SpearKillInstantDirectRouteRequest(
    val target: SpearKillRouteTargetSnapshot,
    val routeOrigin: Vec3,
    val settings: SpearKillPacketSessionSettings,
    val sessionOrigin: Vec3,
    val playerSnapshot: SpearKillPlayerRouteSnapshot,
    val estimatedHitTicks: Int,
)

@Suppress("ReturnCount")
internal fun SpearKillModuleState.createInstantDirectPacketRoute(
    target: SpearKillRouteTargetSnapshot,
    routeOrigin: Vec3,
    settings: SpearKillPacketSessionSettings,
    sessionOrigin: Vec3,
    playerSnapshot: SpearKillPlayerRouteSnapshot,
    estimatedHitTicks: Int,
): DirectPacketRoutePlan? = createInstantDirectPacketRoute(
    SpearKillInstantDirectRouteRequest(
        target,
        routeOrigin,
        settings,
        sessionOrigin,
        playerSnapshot,
        estimatedHitTicks,
    ),
)

@Suppress("ReturnCount")
private fun SpearKillModuleState.createInstantDirectPacketRoute(
    request: SpearKillInstantDirectRouteRequest,
): DirectPacketRoutePlan? = with(request) {
    if (!isEligibleSpearKillInstantDirectRoute(request)) return null
    val eyeOffset = playerSnapshot.eyeOffset
    var hitTicks = estimatedHitTicks
    repeat(SPEAR_KILL_DIRECT_PREDICTION_REFINEMENT_LIMIT) {
        val candidate = createInstantDirectRouteCandidate(
            target = target,
            routeOrigin = routeOrigin,
            sessionOrigin = sessionOrigin,
            playerSnapshot = playerSnapshot,
            hitTicks = hitTicks,
        ) ?: return null
        val outboundTickCount = candidate.route.outboundTickCount
        val actualHitTicks = spearKillDirectRouteHitTicks(
            routingMode = settings.routingMode,
            outboundTickCount = outboundTickCount,
            stepWaitTicks = settings.stepWaitTicks,
            strikeHoldTicks = settings.strikeHoldTicks,
        )
        if (actualHitTicks != hitTicks) {
            hitTicks = actualHitTicks
            return@repeat
        }
        if (!hasValidAStarTerminalAttackRay(
                targetBox = candidate.targetBox,
                eyeOffset = eyeOffset,
                approach = candidate.approach,
            )
        ) {
            return null
        }
        return DirectPacketRoutePlan(candidate.route, target)
    }
    return null
}

private fun isEligibleSpearKillInstantDirectRoute(request: SpearKillInstantDirectRouteRequest): Boolean =
    request.settings.routingMode == SpearKillRoutingMode.INSTANT &&
        request.routeOrigin.distanceTo(request.target.observedPosition) in 3.0..request.playerSnapshot.maximumTargetDistance
