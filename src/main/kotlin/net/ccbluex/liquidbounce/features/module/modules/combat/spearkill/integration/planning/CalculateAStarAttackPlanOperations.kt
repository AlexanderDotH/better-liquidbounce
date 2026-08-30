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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.tick.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.terminal.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.AStarAttackPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_A_STAR_SNAPSHOT_HORIZONTAL_MARGIN
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_A_STAR_SNAPSHOT_VERTICAL_MARGIN
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_ROUTE_SNAPSHOT_MAX_CELLS
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.SpearKillCollisionSnapshotBuilder
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPacketSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.captureSpearKillRouteTargetSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.spearKillPacketTravelTicks
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.SpearKillAStarPlanRequest
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.createAStarAttackPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.calculateRouteSynchronously
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.calculateAStarAttackPlan(
    target: LivingEntity,
    routeOrigin: Vec3,
    sessionOrigin: Vec3,
    settings: SpearKillPacketSessionSettings,
): AStarAttackPlan? {
    val playerSnapshot = captureSpearKillPlayerRouteSnapshot(sessionOrigin, settings.transport.stepLimit)
    val estimatedHitTicks = spearKillPacketTravelTicks(
        stepCount = settings.aStar.maxCost.coerceAtLeast(1),
        stepWaitTicks = settings.stepWaitTicks,
    ) + SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS + settings.strikeHoldTicks
    val targetSnapshot = captureSpearKillRouteTargetSnapshot(target, estimatedHitTicks)
    val snapshotBuilder = SpearKillCollisionSnapshotBuilder.corridor(
        points = listOf(routeOrigin) + targetSnapshot.collisionCorridorPositions(),
        horizontalMargin = SPEAR_KILL_A_STAR_SNAPSHOT_HORIZONTAL_MARGIN,
        verticalMargin = SPEAR_KILL_A_STAR_SNAPSHOT_VERTICAL_MARGIN,
        maxCells = SPEAR_KILL_ROUTE_SNAPSHOT_MAX_CELLS,
    )
    return calculateRouteSynchronously(snapshotBuilder) { collisionSnapshot ->
        createAStarAttackPlan(
            SpearKillAStarPlanRequest(
                targetSnapshot,
                routeOrigin,
                sessionOrigin,
                settings.stepWaitTicks,
                settings.strikeHoldTicks,
                settings.aStar,
                playerSnapshot,
                collisionSnapshot,
            ),
        )
    }
}
