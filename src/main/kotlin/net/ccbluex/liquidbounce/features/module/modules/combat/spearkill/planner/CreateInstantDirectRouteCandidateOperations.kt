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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.InstantDirectRouteCandidate
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPlayerRouteSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.SpearKillRouteTargetSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.buildSpearKillInstantDirectPacketRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.calculateSpearKillAttackDirection
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.solveSpearKillDirectAttackLine
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.spearKillInstantAimPredictionTicks
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarAttackApproach
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.isSpearKillPrimedEndpointFree
import net.minecraft.world.phys.Vec3

@Suppress("LongParameterList", "ReturnCount")
internal fun SpearKillModuleState.createInstantDirectRouteCandidate(
    target: SpearKillRouteTargetSnapshot,
    routeOrigin: Vec3,
    sessionOrigin: Vec3,
    playerSnapshot: SpearKillPlayerRouteSnapshot,
    hitTicks: Int,
): InstantDirectRouteCandidate? {
    val prediction = target.predict(spearKillInstantAimPredictionTicks(hitTicks))
    val direction = calculateSpearKillAttackDirection(
        playerEyePosition = routeOrigin.add(playerSnapshot.eyeOffset),
        predictedTargetPosition = prediction.position,
        targetEyeOffset = target.eyeOffset,
        fallbackDirection = playerSnapshot.lookAngle,
    )
    val line = solveSpearKillDirectAttackLine(
        origin = routeOrigin,
        targetBox = prediction.boundingBox,
        targetEyePosition = prediction.eyePosition,
        playerEyeOffset = playerSnapshot.eyeOffset,
        fallbackDirection = direction,
    ) ?: return null
    val endpointFree = { position: Vec3 -> isSpearKillPrimedEndpointFree(sessionOrigin, position) }
    val route = buildSpearKillInstantDirectPacketRoute(
        origin = routeOrigin,
        destination = line.terminalWaypoint,
        isEndpointFree = endpointFree,
    ) ?: return null
    return InstantDirectRouteCandidate(
        route = route,
        targetBox = prediction.boundingBox,
        approach = SpearKillAStarAttackApproach(
            plannerGoal = line.terminalWaypoint.subtract(route.outboundMovements.last()),
            terminalWaypoint = line.terminalWaypoint,
        ),
    )
}
