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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration


import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.MaceKillRouteAdmissionContext
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.MaceKillRouteExecutionConfiguration
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.evaluateMaceKillRouteAdmission
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.shouldBlockMaceKillRouteAfterInstantCorrection
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.runtime.*

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.fightbot.MaceKillFightBotState
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.reach.MaceClipReachBlockReason
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillMovementOwnership
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal fun MaceKillModuleState.startRemoteRoute(target: LivingEntity, owner: MaceKillRouteOwner): Boolean {
    if (!admitMaceKillRoute(target, owner)) return false
    val configuration = currentMaceKillRouteExecutionConfiguration(owner)
    val origin = player.position()
    val prediction = predictedMaceKillTarget(target, origin, configuration.timing)
    val endpoint = findAdmittedMaceKillEndpoint(target, owner, origin, prediction)
        ?: return rejectRemoteRouteAdmission(target, owner)
    speedController.begin(player.deltaMovement.length(), configuration.targetSpeed)
    val planned = planAdmittedMaceKillRoute(target, owner, origin, endpoint, configuration)
        ?: return false
    if (!beginAdmittedMaceKillFallSafety(target, owner, planned)) return false
    installMaceKillRouteState(target, owner, origin, prediction, configuration, planned)
    stopKillAuraBlockingBeforeRoute()
    return startInstalledMaceKillRoute(target, owner, origin, planned)
}

private fun MaceKillModuleState.admitMaceKillRoute(
    target: LivingEntity,
    owner: MaceKillRouteOwner,
): Boolean {
    val failure = evaluateMaceKillRouteAdmission(currentMaceKillRouteAdmissionContext(target))
        ?: return true
    debugMaceKill("route-admission-rejected") {
        listOf("owner" to owner, "target" to target.id, "reason" to failure)
    }
    if (owner == MaceKillRouteOwner.FIGHT_BOT && !routeEngine.ownsMovement) {
        rejectFightBotMaceUse(target)
    }
    return false
}

private fun MaceKillModuleState.currentMaceKillRouteAdmissionContext(
    target: LivingEntity,
) = MaceKillRouteAdmissionContext(
    enabled = enabled,
    routeOwned = routeEngine.ownsMovement,
    conflictingMovementOwned = RemoteKillMovementOwnership.active && !routeEngine.ownsMovement,
    blinkRunning = integration.blinkRunning,
    passenger = player.isPassenger,
    gliding = player.isFallFlying,
    backoffActive = routeAdmissionBackoff.isBlocked(player.tickCount) ||
        instantRouteBackoff.isBlocked(player.tickCount) ||
        shouldBlockMaceKillRouteAfterInstantCorrection(
            instantRouting = isInstantPacketRoutingConfigured(),
            instantServerRejected = instantServerRejected,
        ),
    targetValid = isMaceKillTargetEligible(target),
    holdingMace = hasServerHeldMace(),
)

private fun MaceKillModuleState.findAdmittedMaceKillEndpoint(
    target: LivingEntity,
    owner: MaceKillRouteOwner,
    origin: Vec3,
    prediction: MaceKillRouteTargetPrediction,
): Vec3? {
    val endpoint = findMaceKillAttackEndpoint(
        target = target,
        origin = origin,
        targetPosition = prediction.position,
        targetEyePosition = prediction.eyePosition,
    )
    if (endpoint == null) {
        debugMaceKill("endpoint-rejected") {
            listOf("owner" to owner, "target" to target.id, "origin" to origin, "predicted" to prediction.position)
        }
    }
    return endpoint
}

private fun MaceKillModuleState.planAdmittedMaceKillRoute(
    target: LivingEntity,
    owner: MaceKillRouteOwner,
    origin: Vec3,
    endpoint: Vec3,
    configuration: MaceKillRouteExecutionConfiguration,
): MaceKillPlannedRoute? {
    val planned = buildMaceKillRoute(origin, endpoint, configuration)
    if (planned != null) {
        if (debugConsole.isInitialized()) debugConsole.value.clearTransition("route-plan-rejection")
        return planned
    }
    reportMaceKillRoutePlanRejection(target, owner, configuration)
    speedController.reset()
    val reason = lastInstantPlanBlockReason
    if (reason != null) {
        rejectInstantPlan(target, owner, reason)
    } else {
        rejectRemoteRouteAdmission(target, owner)
    }
    return null
}

private fun MaceKillModuleState.reportMaceKillRoutePlanRejection(
    target: LivingEntity,
    owner: MaceKillRouteOwner,
    configuration: MaceKillRouteExecutionConfiguration,
) {
    debugMaceKillChanged(
        channel = "route-plan-rejection",
        event = "route-plan-rejected",
        fingerprint = { owner to (configuration.routingMode to lastInstantPlanBlockReason) },
    ) {
        listOf(
            "owner" to owner,
            "target" to target.id,
            "routing" to configuration.routingMode,
            "instant-reason" to lastInstantPlanBlockReason,
        )
    }
}

private fun MaceKillModuleState.beginAdmittedMaceKillFallSafety(
    target: LivingEntity,
    owner: MaceKillRouteOwner,
    planned: MaceKillPlannedRoute,
): Boolean {
    if (beginMaceKillFallSafety(planned)) return true
    debugMaceKill("fall-safety-rejected") {
        listOf("owner" to owner, "target" to target.id, "steps" to planned.request.outboundMovements.size)
    }
    speedController.reset()
    rejectRemoteRouteAdmission(target, owner)
    return false
}

internal fun MaceKillModuleState.rejectRemoteRouteAdmission(
    target: LivingEntity,
    owner: MaceKillRouteOwner,
): Boolean {
    routeAdmissionBackoff.reject(player.tickCount)
    rejectedTargets.reject(target, player.tickCount)
    if (owner == MaceKillRouteOwner.FIGHT_BOT) {
        rejectFightBotMaceUse(target)
    } else {
        notifyMaceFailure("routeRejected")
    }
    return false
}

internal fun MaceKillModuleState.rejectInstantPlan(
    target: LivingEntity,
    owner: MaceKillRouteOwner,
    reason: MaceClipReachBlockReason,
): Boolean {
    val decision = maceKillInstantPlanRejectionDecision(reason)
    if (decision.applyGlobalBackoff) instantRouteBackoff.reject(player.tickCount)
    rejectedTargets.reject(target, player.tickCount)
    if (owner == MaceKillRouteOwner.FIGHT_BOT) {
        rejectFightBotMaceUse(target)
    } else {
        notifyMaceFailure(decision.notificationKey)
    }
    return false
}
