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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.allowsTargetChain
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.runtime.*

import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillRouteRequest
import net.ccbluex.liquidbounce.features.input.InputTracker.isPressedOnAny
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal fun MaceKillModuleState.tryStartTargetChain(defeatedTarget: LivingEntity): Boolean {
    if (!canStartMaceKillTargetChain()) return false
    val prepared = prepareMaceKillTargetChain(defeatedTarget) ?: return false
    if (!secureMaceKillTargetChainFallSafety(prepared)) return false
    installMaceKillTargetChain(prepared)
    return true
}

private fun MaceKillModuleState.canStartMaceKillTargetChain(): Boolean =
    routeSession.canStartChainedOutbound &&
        routeChainCount < MACE_KILL_MAX_CHAIN_TARGETS &&
        activeRouteOwner.allowsTargetChain &&
        mc.options.keyAttack.isPressedOnAny

private fun MaceKillModuleState.prepareMaceKillTargetChain(
    defeatedTarget: LivingEntity,
): MaceKillTargetChain? {
    val sessionOrigin = routeOrigin ?: return null
    val configuration = activeRouteConfiguration ?: return null
    val chainOrigin = sessionOrigin.add(routeSession.committedOffset)
    val target = findNextMaceKillChainTarget(defeatedTarget, chainOrigin) ?: return null
    val prediction = predictedMaceKillTarget(target, chainOrigin, configuration.timing)
    val route = planMaceKillTargetChain(target, chainOrigin, prediction, configuration) ?: return null
    val recovery = routeSession.exactRecoveryMovementsFrom(routeSession.committedOffset) ?: return null
    if (route.clipReachPlan != null || route.primingPackets != 0) return null
    if (!routeSession.startChainedOutbound(
            route.request.outboundMovements,
            route.request.strikeHoldTicks,
        )
    ) {
        return null
    }
    return MaceKillTargetChain(sessionOrigin, chainOrigin, target, prediction, route, recovery)
}

private fun MaceKillModuleState.findNextMaceKillChainTarget(
    defeatedTarget: LivingEntity,
    chainOrigin: Vec3,
): LivingEntity? = world.getEntitiesOfClass(
    LivingEntity::class.java,
    AABB.ofSize(
        chainOrigin,
        maximumTargetRange.toDouble() * 2.0,
        maximumTargetRange.toDouble() * 2.0,
        maximumTargetRange.toDouble() * 2.0,
    ),
) { candidate -> candidate !== defeatedTarget && isMaceKillTargetEligible(candidate) }
    .minByOrNull { candidate -> chainOrigin.distanceToSqr(candidate.position()) }

private fun MaceKillModuleState.planMaceKillTargetChain(
    target: LivingEntity,
    chainOrigin: Vec3,
    prediction: MaceKillRouteTargetPrediction,
    configuration: MaceKillRouteExecutionConfiguration,
): MaceKillPlannedRoute? {
    val endpoint = findMaceKillAttackEndpoint(
        target,
        chainOrigin,
        prediction.position,
        prediction.eyePosition,
    ) ?: return null
    val routeBox = routeOriginBoundingBox ?: player.boundingBox
    return buildMaceKillRoute(
        chainOrigin,
        endpoint,
        configuration,
        routeBox.move(routeSession.committedOffset),
        allowVanillaVClip = activeVanillaVClipSegments.isEmpty(),
    )
}

private fun MaceKillModuleState.secureMaceKillTargetChainFallSafety(chain: MaceKillTargetChain): Boolean {
    val movements = chain.route.request.outboundMovements +
        chain.route.request.returnMovements + chain.committedRecovery
    val vClipSegments = activeVanillaVClipSegments + chain.route.vanillaVClipSegments
    if (replanMaceKillFallSafety(
            chain.chainOrigin,
            movements,
            chain.route.request.outboundMovements.size,
            vanillaVClipSegments = vClipSegments,
        )
    ) {
        activeVanillaVClipSegments = vClipSegments
        return true
    }
    beginSafeRouteAbort()
    return false
}

private fun MaceKillModuleState.installMaceKillTargetChain(chain: MaceKillTargetChain) {
    val fullOutbound = listOf(routeSession.committedOffset) + chain.route.request.outboundMovements
    routeEngine.handoff(
        chain.target,
        RemoteKillRouteRequest(
            origin = chain.sessionOrigin,
            outboundMovements = fullOutbound,
            strikeHoldTicks = chain.route.request.strikeHoldTicks,
            stepWaitTicks = chain.route.request.stepWaitTicks,
        ),
    )
    activeRouteTarget = chain.target
    plannedTargetPosition = chain.prediction.position
    routeRenderPath = chain.route.renderPath
    routeChainCount++
    evidenceTargetId = null
    evidenceDeadlineTick = 0
    debugMaceKill("target-chain") { listOf("target" to chain.target.id, "chain" to routeChainCount) }
}

private data class MaceKillTargetChain(
    val sessionOrigin: Vec3,
    val chainOrigin: Vec3,
    val target: LivingEntity,
    val prediction: MaceKillRouteTargetPrediction,
    val route: MaceKillPlannedRoute,
    val committedRecovery: List<Vec3>,
)
