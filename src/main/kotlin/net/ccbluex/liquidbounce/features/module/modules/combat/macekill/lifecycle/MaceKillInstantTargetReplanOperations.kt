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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.runtime.*

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.reach.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillRouteRequest
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal fun MaceKillModuleState.replanInstantTargetBeforeStrike(
    target: LivingEntity,
    session: MaceClipReachSession,
) {
    if (!canReplanInstantMaceKillTarget(target, session)) return
    if (routeSession.recovering && handleRecoveredMaceKillInstantTarget(target, session)) return
    if (routeSession.requiresDelivery || !routeSession.canReplaceRemainingOutbound) return
    val replan = prepareInstantMaceKillTargetReplan(target, session) ?: return
    if (!secureInstantMaceKillTargetReplan(session, replan)) return
    installInstantMaceKillTargetReplan(target, replan)
}

private fun MaceKillModuleState.canReplanInstantMaceKillTarget(
    target: LivingEntity,
    session: MaceClipReachSession,
): Boolean {
    if (session.strikeCommitted) return false
    val previous = plannedTargetPosition ?: return false
    return target.position().distanceToSqr(previous) >= MACE_KILL_TARGET_REPLAN_DISTANCE_SQUARED
}

private fun MaceKillModuleState.handleRecoveredMaceKillInstantTarget(
    target: LivingEntity,
    session: MaceClipReachSession,
): Boolean {
    val endpointReady = routeEngine.activeRequest?.endpoint?.let {
        isRemoteEndpointReady(player, target, it)
    } == true
    return when (maceKillInstantTargetMovementAction(true, endpointReady)) {
        MaceKillInstantTargetMovementAction.KEEP_CONFIRMED_ENDPOINT -> {
            plannedTargetPosition = target.position()
            true
        }
        MaceKillInstantTargetMovementAction.REJECT -> {
            handleInstantSessionOutcome(session.recordReplanRejected())
            true
        }
        MaceKillInstantTargetMovementAction.REPLAN_UNCONFIRMED -> false
    }
}

private fun MaceKillModuleState.prepareInstantMaceKillTargetReplan(
    target: LivingEntity,
    session: MaceClipReachSession,
): InstantMaceKillTargetReplan? {
    val sessionOrigin = routeOrigin ?: return null
    val configuration = activeRouteConfiguration ?: return null
    val currentPosition = sessionOrigin.add(routeSession.committedOffset)
    val prediction = predictedMaceKillTarget(target, currentPosition, configuration.timing)
    val endpoint = findMaceKillAttackEndpoint(
        target,
        currentPosition,
        prediction.position,
        prediction.eyePosition,
    ) ?: return rejectInstantMaceKillTargetReplan(session)
    val result = session.replanTerminal(
        endpoint,
        MaceClipReachAnchorValidator { _, position -> isMaceKillAnchorValid(sessionOrigin, position) },
    )
    val plan = (result as? MaceClipReachReplanResult.Applied)?.plan
        ?: return handleRejectedMaceKillInstantReplan(session, result)
    val confirmedCount = session.confirmedOutboundMovementCount
    val remainingOutbound = plan.outboundMovements.drop(confirmedCount)
    if (remainingOutbound.isEmpty()) return rejectInstantMaceKillTargetReplan(session)
    return InstantMaceKillTargetReplan(
        sessionOrigin,
        currentPosition,
        configuration,
        prediction,
        plan,
        confirmedCount,
        remainingOutbound,
    )
}

private fun MaceKillModuleState.handleRejectedMaceKillInstantReplan(
    session: MaceClipReachSession,
    result: MaceClipReachReplanResult,
): Nothing? {
    val rejected = result as MaceClipReachReplanResult.Rejected
    if (rejected.reason != MaceClipReachReplanBlockReason.STRIKE_COMMITTED) {
        handleInstantSessionOutcome(session.outcome)
    }
    return null
}

private fun MaceKillModuleState.rejectInstantMaceKillTargetReplan(
    session: MaceClipReachSession,
): Nothing? {
    handleInstantSessionOutcome(session.recordReplanRejected())
    return null
}

private fun MaceKillModuleState.secureInstantMaceKillTargetReplan(
    session: MaceClipReachSession,
    replan: InstantMaceKillTargetReplan,
): Boolean {
    val movements = replan.remainingOutbound + replan.plan.returnMovements
    val safe = replanMaceKillFallSafety(
        replan.currentPosition,
        movements,
        replan.remainingOutbound.size,
        MaceKillGroundPolicy.CLIP_ANCHOR_SPOOF,
    )
    val installed = safe && routeSession.replaceRemainingOutbound(
        replan.remainingOutbound,
        MACE_KILL_CHAIN_EVIDENCE_HOLD_TICKS,
        completeReturnMovements = replan.plan.returnMovements,
    )
    if (installed) return true
    handleInstantSessionOutcome(session.recordReplanRejected())
    return false
}

private fun MaceKillModuleState.installInstantMaceKillTargetReplan(
    target: LivingEntity,
    replan: InstantMaceKillTargetReplan,
) {
    routeEngine.handoff(
        target,
        RemoteKillRouteRequest(
            origin = replan.sessionOrigin,
            outboundMovements = replan.plan.outboundMovements,
            strikeHoldTicks = MACE_KILL_CHAIN_EVIDENCE_HOLD_TICKS,
            stepWaitTicks = replan.configuration.timing.stepWaitTicks,
            returnMovements = replan.plan.returnMovements,
        ),
    )
    instantRecoveryPlan = replan.plan
    plannedTargetPosition = replan.prediction.position
    routeRenderPath = routePositions(replan.sessionOrigin, replan.plan.outboundMovements)
    debugMaceKill("instant-target-replan") {
        listOf(
            "target" to target.id,
            "confirmed" to replan.confirmedCount,
            "remaining" to replan.remainingOutbound.size,
        )
    }
}

private data class InstantMaceKillTargetReplan(
    val sessionOrigin: Vec3,
    val currentPosition: Vec3,
    val configuration: MaceKillRouteExecutionConfiguration,
    val prediction: MaceKillRouteTargetPrediction,
    val plan: MaceClipReachPlan,
    val confirmedCount: Int,
    val remainingOutbound: List<Vec3>,
)
