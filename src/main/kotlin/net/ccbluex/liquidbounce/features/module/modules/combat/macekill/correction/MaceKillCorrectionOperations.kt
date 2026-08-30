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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction

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
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.MaceClipResearchPhase
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

internal fun MaceKillModuleState.prepareRemoteCorrection(correctionPlayer: Player) {
    if (correctionPlayer !== mc.player || !routeEngine.ownsMovement) return
    val origin = routeOrigin ?: return
    activeClipReachSession?.let { session ->
        if (!session.strikeCommitted) instantServerRejected = true
        handleInstantSessionOutcome(session.recordCorrection(), abortRoute = false)
    }
    returnConfirmation.onCorrection()
    correctionState = MaceKillLocalCorrectionState(
        expectedPosition = origin.add(routeSession.virtualOffset),
        routeOrigin = origin,
        researchPhase = currentResearchPhase(),
    )
}

internal fun MaceKillModuleState.finishRemoteCorrection(correctionPlayer: Player) {
    if (correctionPlayer !== mc.player) return
    val state = correctionState.also { correctionState = null } ?: return
    val authoritativePosition = correctionPlayer.position()
    recordMaceKillCorrectionEvidence(correctionPlayer, state, authoritativePosition)
    val authoritativeOffset = authoritativePosition.subtract(state.routeOrigin)
    localPacketRouteOrigin = state.routeOrigin
    if (handleExactMaceKillOriginCorrection(correctionPlayer, state, authoritativeOffset)) return
    val recovery = prepareMaceKillCorrectionRecovery(
        correctionPlayer,
        state,
        authoritativePosition,
        authoritativeOffset,
    ) ?: run {
        rejectMaceKillCorrectionRecovery(
            "no-collision-aware-route",
            "authoritative" to authoritativePosition,
            "origin" to state.routeOrigin,
        )
        return
    }
    if (!installMaceKillCorrectionRecovery(recovery)) return
    completeMaceKillCorrectionRecovery(correctionPlayer, recovery)
}

private fun MaceKillModuleState.recordMaceKillCorrectionEvidence(
    correctionPlayer: Player,
    state: MaceKillLocalCorrectionState,
    authoritativePosition: Vec3,
) {
    val research = researchExecution ?: return
    researchRuntime.recordCorrection(
        research.sessionId,
        state.researchPhase ?: MaceClipResearchPhase.RETURN_DESCEND,
        correctionPlayer.tickCount,
        state.expectedPosition,
        authoritativePosition,
    )
    researchRuntime.recordCorrectionAuthoritativePosition(research.sessionId, authoritativePosition)
}

private fun MaceKillModuleState.handleExactMaceKillOriginCorrection(
    correctionPlayer: Player,
    state: MaceKillLocalCorrectionState,
    authoritativeOffset: Vec3,
): Boolean {
    if (authoritativeOffset.lengthSqr() >= MACE_KILL_EXACT_RETURN_EPSILON_SQUARED) return false
    correctionPlayer.setPos(state.routeOrigin)
    when (maceKillOriginCorrectionAction(routeSession.active)) {
        MaceKillOriginCorrectionAction.ABORT_ACTIVE_ROUTE -> {
            routeRejected = true
            routeEngine.clear()
            finishInactiveRouteOwnership()
        }
        MaceKillOriginCorrectionAction.CONFIRM_COMPLETED_RETURN ->
            returnConfirmation.onExactReturnDelivered(
                correctionPlayer.tickCount,
                maceKillReturnConfirmationTicks(activeRouteConfiguration?.routingMode),
            )
    }
    return true
}

internal fun MaceKillModuleState.rejectMaceKillCorrectionRecovery(
    reason: String,
    vararg details: Pair<String, Any?>,
) {
    debugMaceKill("correction-recovery-rejected") {
        listOf("reason" to reason) + details
    }
    routeEngine.clear()
    routeRejected = true
    notifyMaceFailure("correctionRecoveryFailed")
    finishInactiveRouteOwnership()
}

private fun MaceKillModuleState.completeMaceKillCorrectionRecovery(
    correctionPlayer: Player,
    recovery: MaceKillCorrectionRecovery,
) {
    motionRouteActive = false
    routeRenderPath = routePositions(recovery.authoritativePosition, recovery.movements)
    routeResumeTick = correctionPlayer.tickCount + networkSetbackBackoffTicks()
    debugMaceKillChanged(
        channel = "correction-recovery",
        event = "correction",
        fingerprint = {
            listOf(recovery.authoritativePosition, recovery.action, recovery.movements.size)
        },
    ) {
        listOf(
            "distance" to recovery.state.expectedPosition.distanceTo(recovery.authoritativePosition),
            "action" to recovery.action,
            "steps" to recovery.movements.size,
            "resume" to routeResumeTick,
        )
    }
}
