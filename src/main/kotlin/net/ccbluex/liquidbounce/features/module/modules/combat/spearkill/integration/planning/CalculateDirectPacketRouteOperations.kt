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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.DirectPacketRoutePlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_DIRECT_SNAPSHOT_HORIZONTAL_MARGIN
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_DIRECT_SNAPSHOT_VERTICAL_MARGIN
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_ROUTE_SNAPSHOT_MAX_CELLS
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.SpearKillCollisionSnapshotBuilder
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPacketSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPlayerRouteSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.SpearKillRouteTargetSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.SpearKillRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.buildSpearKillProfiledMovements
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.captureSpearKillRouteTargetSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.spearKillDirectRouteHitTicks
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.createInstantDirectPacketRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.calculateRouteSynchronously
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.calculateDirectPacketRoute(
    target: LivingEntity,
    routeOrigin: Vec3,
    travel: Double,
    settings: SpearKillPacketSessionSettings,
    sessionOrigin: Vec3,
): DirectPacketRoutePlan? {
    val playerSnapshot = captureSpearKillPlayerRouteSnapshot(sessionOrigin, settings.transport.stepLimit)
    if (!travel.isFinite() || travel <= 0.0) return null
    val estimatedHitTicks = estimateDirectPacketRouteHitTicks(
        travel,
        settings,
        playerSnapshot,
    ) ?: return null
    val targetSnapshot = captureSpearKillRouteTargetSnapshot(target, estimatedHitTicks)
    if (settings.routingMode == SpearKillRoutingMode.INSTANT) {
        return createInstantDirectPacketRoute(
            target = targetSnapshot,
            routeOrigin = routeOrigin,
            settings = settings,
            sessionOrigin = sessionOrigin,
            playerSnapshot = playerSnapshot,
            estimatedHitTicks = estimatedHitTicks,
        )
    }
    return calculateCollisionCheckedDirectPacketRoute(
        targetSnapshot,
        routeOrigin,
        travel,
        settings,
        sessionOrigin,
        playerSnapshot,
    )
}

@Suppress("LongParameterList")
private fun SpearKillModuleState.calculateCollisionCheckedDirectPacketRoute(
    target: SpearKillRouteTargetSnapshot,
    routeOrigin: Vec3,
    travel: Double,
    settings: SpearKillPacketSessionSettings,
    sessionOrigin: Vec3,
    playerSnapshot: SpearKillPlayerRouteSnapshot,
): DirectPacketRoutePlan? {
    val snapshotBuilder = SpearKillCollisionSnapshotBuilder.corridor(
        points = listOf(routeOrigin) + target.collisionCorridorPositions(),
        horizontalMargin = SPEAR_KILL_DIRECT_SNAPSHOT_HORIZONTAL_MARGIN,
        verticalMargin = SPEAR_KILL_DIRECT_SNAPSHOT_VERTICAL_MARGIN,
        maxCells = SPEAR_KILL_ROUTE_SNAPSHOT_MAX_CELLS,
    )
    return calculateRouteSynchronously(snapshotBuilder) { collisionSnapshot ->
        createDirectPacketRoute(
            target = target,
            routeOrigin = routeOrigin,
            travel = travel,
            settings = settings,
            sessionOrigin = sessionOrigin,
            playerSnapshot = playerSnapshot,
            collisionSnapshot = collisionSnapshot,
        )
    }
}

internal fun SpearKillModuleState.estimateDirectPacketRouteHitTicks(
    travel: Double,
    settings: SpearKillPacketSessionSettings,
    playerSnapshot: SpearKillPlayerRouteSnapshot,
): Int? {
    val estimatedTickCount = if (settings.routingMode == SpearKillRoutingMode.INSTANT) {
        1
    } else {
        buildSpearKillProfiledMovements(
            direction = Vec3(1.0, 0.0, 0.0),
            distance = travel,
            profile = playerSnapshot.speedProfile,
        ).size
    }
    return spearKillDirectRouteHitTicks(
        routingMode = settings.routingMode,
        outboundTickCount = estimatedTickCount,
        stepWaitTicks = settings.stepWaitTicks,
        strikeHoldTicks = settings.strikeHoldTicks,
    )
}
