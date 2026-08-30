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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning.*
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
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillRouteRequest
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.AStarAttackPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillAttackStartResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPacketSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.SpearKillRoutingAttempt
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.SpearKillSpeedProfile
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.buildSpearKillProfiledAttackMovements
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.buildSpearKillProfiledMovements
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.calculateSpearKillAttackDirection
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.classifySpearKillAStarStartFailure
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.hasSpearKillRefreshableTerminalDamageWindow
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.spearKillPacketTravelTicks
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.activePacketStepWaitTicks
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.usesPacketMovementMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.beginSpearKillAttempt
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.entity.PositionExtrapolation
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.startAStarPacketAttack(
    target: LivingEntity,
    settings: SpearKillPacketSessionSettings,
    routeMode: String,
): SpearKillAttackStartResult {
    clearAStarRenderPath()
    val origin = player.position()
    val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON)
    val damageUseDuration = kineticWeapon?.computeDamageUseDuration()
    val plan = calculateAStarAttackPlan(
        target = target,
        routeOrigin = origin,
        sessionOrigin = origin,
        settings = settings,
    )
    val startResult = classifySpearKillAStarStartFailure(
        routeFound = plan != null && kineticWeapon != null,
        serverRouteAccepted = isAStarServerRouteAccepted(origin, plan, kineticWeapon != null),
        hasRefreshableTerminalDamageWindow = hasAStarRefreshableDamageWindow(
            plan,
            kineticWeapon?.delayTicks,
            damageUseDuration,
            settings,
        ),
    )
    if (startResult != SpearKillAttackStartResult.STARTED || plan == null) {
        packetSessionSettings = null
        return startResult
    }
    if (!beginVirtualFallSafety(plan.packetRoute.outboundMovements, origin)) {
        packetSessionSettings = null
        return SpearKillAttackStartResult.BLOCKED
    }
    installAStarPacketAttack(target, origin, plan, settings, routeMode)
    return SpearKillAttackStartResult.STARTED
}

private fun SpearKillModuleState.isAStarServerRouteAccepted(
    origin: Vec3,
    plan: AStarAttackPlan?,
    hasKineticWeapon: Boolean,
): Boolean = plan == null || !hasKineticWeapon || isServerAcceptedSpearKillRoute(
    sessionOrigin = origin,
    routeOrigin = origin,
    route = plan.packetRoute,
    routingAttempt = SpearKillRoutingAttempt.A_STAR,
)

private fun hasAStarRefreshableDamageWindow(
    plan: AStarAttackPlan?,
    delayTicks: Int?,
    damageUseDuration: Int?,
    settings: SpearKillPacketSessionSettings,
): Boolean = plan != null && delayTicks != null && damageUseDuration != null &&
    hasSpearKillRefreshableTerminalDamageWindow(
        delayTicks,
        damageUseDuration,
        plan.terminalSuffixCount,
        settings.stepWaitTicks,
        settings.strikeHoldTicks,
    )

@Suppress("LongParameterList")
private fun SpearKillModuleState.installAStarPacketAttack(
    target: LivingEntity,
    origin: Vec3,
    plan: AStarAttackPlan,
    settings: SpearKillPacketSessionSettings,
    routeMode: String,
) {
    packetAStarAttackActive = true
    directTerminalReplanInstalled = false
    packetSessionOrigin = origin
    physicalReturnPositioner.clear()
    returnRecoveryTracker.begin(origin)
    remoteKillRouteEngine.start(target, RemoteKillRouteRequest(
        origin = origin,
        outboundMovements = plan.packetRoute.outboundMovements,
        strikeHoldTicks = settings.strikeHoldTicks,
        stepWaitTicks = settings.stepWaitTicks,
        physicalReturn = true,
        preStrikeHoldTicks = plan.preStrikeHoldTicks,
        terminalSuffixSteps = plan.terminalSuffixCount,
        requireTerminalAuthorization = true,
    ))
    plannedAStarRenderPath = plan.renderPath
    plannedAStarApproach = plan.approach
    plannedAStarTargetPosition = plan.targetPosition
    plannedAStarTargetVelocity = plan.targetVelocity
    aStarPlanTick = player.tickCount
    beginSpearKillAttempt(
        target = target,
        routeMode = routeMode,
        outboundSteps = plan.packetRoute.outboundMovements.size,
        hitTicks = plan.schedule.hitTick,
        terminalAuthorizationRequired = true,
    )
}

internal fun SpearKillModuleState.createDirectAttackMovements(
    target: LivingEntity,
    distance: Double,
    profile: SpearKillSpeedProfile,
): List<Vec3> {
    val stepCount = buildSpearKillProfiledMovements(Vec3(1.0, 0.0, 0.0), distance, profile).size
    val ticks = if (usesPacketMovementMode) {
        spearKillPacketTravelTicks(stepCount, activePacketStepWaitTicks)
    } else {
        stepCount
    }

    val predictedTargetPosition = PositionExtrapolation.getBestForEntity(target)
        .getPositionInTicks(ticks.toDouble())
    val direction = calculateSpearKillAttackDirection(
        playerEyePosition = player.eyePosition,
        predictedTargetPosition = predictedTargetPosition,
        targetEyeOffset = target.eyePosition.subtract(target.position()),
        fallbackDirection = player.lookAngle,
    )

    return buildSpearKillProfiledAttackMovements(direction, distance, profile)
}
