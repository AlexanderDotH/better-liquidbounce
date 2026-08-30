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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPacketSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillRoutingAttempt
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.isSpearKillPacketRouteServerAccepted
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.usesSpearKillPrimedEndpointOnlyPreflight
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarPacketRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.isSpearKillPrimedEndpointFree
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.isServerAcceptedSpearKillRoute(
    sessionOrigin: Vec3,
    routeOrigin: Vec3,
    route: SpearKillAStarPacketRoute,
    routingAttempt: SpearKillRoutingAttempt,
    endpointOnly: Boolean = false,
): Boolean {
    if (endpointOnly) {
        var position = routeOrigin
        return route.roundTripMovements.all { movement ->
            position = position.add(movement)
            isSpearKillPrimedEndpointFree(sessionOrigin, position)
        }
    }
    val playerBoundingBox = spearKillServerCollisionBoxAt(sessionOrigin)
    val segmentValidator = when (routingAttempt) {
        SpearKillRoutingAttempt.DIRECT -> createServerValidatedSpearKillDirectPacketSegmentValidator(
            origin = sessionOrigin,
            playerBoundingBox = playerBoundingBox,
        )
        SpearKillRoutingAttempt.A_STAR -> createServerMovementSpearKillSegmentValidator(
            origin = sessionOrigin,
            playerBoundingBox = playerBoundingBox,
        )
    }
    return isSpearKillPacketRouteServerAccepted(
        origin = routeOrigin,
        route = route,
        segmentValidator = segmentValidator,
    )
}

internal fun SpearKillModuleState.isServerAcceptedSpearKillDirectRoute(
    sessionOrigin: Vec3,
    routeOrigin: Vec3,
    route: SpearKillAStarPacketRoute,
    settings: SpearKillPacketSessionSettings,
): Boolean = isServerAcceptedSpearKillRoute(
    sessionOrigin = sessionOrigin,
    routeOrigin = routeOrigin,
    route = route,
    routingAttempt = SpearKillRoutingAttempt.DIRECT,
    endpointOnly = usesSpearKillPrimedEndpointOnlyPreflight(
        settings.primedInstant,
        settings.priming,
    ),
)
