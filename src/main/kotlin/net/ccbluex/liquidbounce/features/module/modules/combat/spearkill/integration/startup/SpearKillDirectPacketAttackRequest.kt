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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillAttackStartResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPacketSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.startSpearKillDirectPacketSession
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.startSpearKillInstantPacketSession
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.beginSpearKillAttempt
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal data class SpearKillDirectPacketAttackRequest(
    val target: LivingEntity,
    val distance: Double,
    val settings: SpearKillPacketSessionSettings,
    val routeMode: String,
    val origin: Vec3,
)

internal fun SpearKillModuleState.startDirectPacketAttack(
    target: LivingEntity,
    distance: Double,
    settings: SpearKillPacketSessionSettings,
    routeMode: String,
): SpearKillAttackStartResult {
    val request = SpearKillDirectPacketAttackRequest(target, distance, settings, routeMode, player.position())
    return when (val preparation = prepareDirectPacketAttack(request)) {
        is SpearKillDirectAttackPreparation.Rejected -> {
            reportDirectPacketAttackRejection(request, preparation)
            preparation.result
        }
        is SpearKillDirectAttackPreparation.Ready -> {
            installDirectPacketAttack(request, preparation)
            reportDirectPacketAttackReady(request, preparation)
            beginPreparedDirectPacketAttempt(request, preparation)
            SpearKillAttackStartResult.STARTED
        }
    }
}

private fun SpearKillModuleState.installDirectPacketAttack(
    request: SpearKillDirectPacketAttackRequest,
    preparation: SpearKillDirectAttackPreparation.Ready,
) {
    val settings = request.settings
    val route = preparation.plan.route
    motionPacketHeading = null
    packetSessionSettings = settings
    packetAStarAttackActive = false
    clearAStarRenderPath()
    plannedAStarApproach = null
    packetSessionOrigin = request.origin
    physicalReturnPositioner.clear()
    returnRecoveryTracker.begin(request.origin)
    if (preparation.instantBurst != null) {
        startSpearKillInstantPacketSession(
            remoteKillRouteEngine,
            request.target,
            request.origin,
            preparation.instantBurst,
        )
    } else {
        startSpearKillDirectPacketSession(
            remoteKillRouteEngine,
            request.target,
            request.origin,
            route,
            settings.stepWaitTicks,
            settings.strikeHoldTicks,
        )
    }
    primedSessionPacketsDelivered = 0
    directTerminalReplanInstalled = false
    lockedAStarTarget = request.target
    plannedAStarTargetPosition = preparation.plan.targetSnapshot.observedPosition
    plannedAStarTargetVelocity = preparation.plan.targetSnapshot.velocity
    aStarPlanTick = player.tickCount
}

private fun SpearKillModuleState.beginPreparedDirectPacketAttempt(
    request: SpearKillDirectPacketAttackRequest,
    preparation: SpearKillDirectAttackPreparation.Ready,
) {
    beginSpearKillAttempt(
        target = request.target,
        routeMode = request.routeMode,
        outboundSteps = preparation.plan.route.outboundMovements.size,
        hitTicks = preparation.hitTicks,
        terminalAuthorizationRequired = preparation.instantBurst == null,
    )
}
