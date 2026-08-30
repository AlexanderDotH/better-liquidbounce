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
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.common.attack.AcceptedAttackResult
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.reach.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.fightbot.MaceKillFightBotState
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.math.allEmpty
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal fun MaceKillModuleState.commitRemoteStrike(
    request: RemoteKillStrikeRequest<LivingEntity>,
): RemoteKillStrikeResult {
    if (shouldDeferMaceKillStrike(player.tickCount, remoteStrikeEarliestTick)) {
        return RemoteKillStrikeResult.Deferred
    }
    val research = researchExecution
    if (activeRouteOwner == MaceKillRouteOwner.RESEARCH && research != null) {
        return commitMaceKillResearchStrike(request, research)
    }
    val clipSession = activeClipReachSession
    validateMaceKillClipStrikeSession(request.target, clipSession)?.let { return it }
    if (!canCommitMaceKillRouteStrike(request.target)) {
        return RemoteKillStrikeResult.Rejected("target-or-weapon-invalid")
    }
    val result = commitMaceAttackAtEndpoint(request.target, request.endpoint)
    if (result == RemoteKillStrikeResult.Committed && clipSession != null) {
        if (!primeMaceKillClipReturn(request.endpoint, clipSession)) {
            return RemoteKillStrikeResult.Rejected("return-priming-rejected")
        }
        commitMaceKillClipStrike(clipSession)
    }
    return result
}

private fun MaceKillModuleState.commitMaceKillResearchStrike(
    request: RemoteKillStrikeRequest<LivingEntity>,
    research: MaceKillResearchExecution,
): RemoteKillStrikeResult {
    researchRuntime.recordPhaseStarted(
        research.sessionId,
        MaceClipResearchPhase.STRIKE,
        player.tickCount,
        request.endpoint,
    )
    val result = if (research.descriptor.request is MaceClipResearchProbeRequest.Move) {
        RemoteKillStrikeResult.Committed
    } else {
        commitMaceAttackAtEndpoint(request.target, request.endpoint).also { strikeResult ->
            researchRuntime.recordStrikeAttempt(
                research.sessionId,
                strikeResult == RemoteKillStrikeResult.Committed,
            )
        }
    }
    researchRuntime.recordPhaseCompleted(
        research.sessionId,
        MaceClipResearchPhase.STRIKE,
        player.tickCount,
        request.endpoint,
    )
    return result
}

private fun MaceKillModuleState.validateMaceKillClipStrikeSession(
    target: LivingEntity,
    session: MaceClipReachSession?,
): RemoteKillStrikeResult.Rejected? {
    if (session == null) return null
    val outcome = session.evaluate(
        player.tickCount.toLong(),
        target.isAlive && !target.isRemoved && target.level() === world,
    )
    if (outcome == MaceClipReachSessionOutcome.ACTIVE) return null
    handleInstantSessionOutcome(outcome)
    return RemoteKillStrikeResult.Rejected("instant-session-terminal")
}

private fun MaceKillModuleState.canCommitMaceKillRouteStrike(target: LivingEntity): Boolean =
    activeRouteTarget === target && isMaceKillTargetEligible(target) && hasServerHeldMace()

private fun MaceKillModuleState.primeMaceKillClipReturn(
    endpoint: Vec3,
    session: MaceClipReachSession,
): Boolean {
    val packetCount = session.plan.profile.parameters.primingPacketCount
    if (sendMaceKillPrimingPackets(endpoint, packetCount)) {
        debugMaceKill("return-prime") { listOf("packets" to packetCount) }
        return true
    }
    session.recordReplanRejected()
    handleInstantSessionOutcome(MaceClipReachSessionOutcome.REPLAN_REJECTED)
    return false
}

private fun MaceKillModuleState.commitMaceKillClipStrike(session: MaceClipReachSession) {
    if (session.commitStrike(player.tickCount.toLong(), targetAlive = true)) return
    session.recordReplanRejected()
    handleInstantSessionOutcome(MaceClipReachSessionOutcome.REPLAN_REJECTED)
}

internal fun MaceKillModuleState.commitMaceAttackAtEndpoint(target: LivingEntity, endpoint: Vec3): RemoteKillStrikeResult {
    if (!target.isAlive || target.isRemoved || target.level() !== world || !hasServerHeldMace()) {
        return RemoteKillStrikeResult.Rejected("target-or-weapon-invalid")
    }
    val endpointBoundingBox = player.boundingBox.move(endpoint.subtract(player.position()))
    val fallResetResult = MacePostAttackFallResetPlanner.plan(
        MacePostAttackFallResetRequest(endpoint, endpointBoundingBox),
    ) { box -> world.getBlockCollisions(player, box).allEmpty() }
    val fallResetPlan = (fallResetResult as? MacePostAttackFallResetPlanResult.Ready)?.plan
        ?: return RemoteKillStrikeResult.Rejected("post-attack-fall-reset-unavailable")
    remoteStrikeTarget = target
    remoteStrikeEndpoint = endpoint
    remoteStrikeFallResetPlan = fallResetPlan
    val result = try {
        integration.attackTarget(target).also { attackResult ->
            if (attackResult == AcceptedAttackResult.APPLIED) {
                applyMaceStrikePackets(player, fallResetPlan.packets)
                debugMaceKill("post-strike-fall-reset") { listOf("rise" to fallResetPlan.rise) }
            }
        }
    } finally {
        remoteStrikeTarget = null
        remoteStrikeEndpoint = null
        remoteStrikeFallResetPlan = null
    }
    debugMaceKill("strike-result") {
        listOf("target" to target.id, "endpoint" to endpoint, "result" to result)
    }
    return when (result) {
        AcceptedAttackResult.APPLIED -> {
            rejectedTargets.allow(target)
            evidenceTargetId = target.id
            evidenceDeadlineTick = player.tickCount + MACE_KILL_DAMAGE_EVIDENCE_TICKS
            RemoteKillStrikeResult.Committed
        }
        AcceptedAttackResult.NOT_APPLIED -> RemoteKillStrikeResult.Rejected("mace-spoof-not-applied")
        AcceptedAttackResult.REJECTED -> RemoteKillStrikeResult.Rejected("accepted-attack-rejected")
    }
}

internal fun MaceKillModuleState.handleRemoteStrikeResult(result: RemoteKillStrikeResult?) {
    if (result is RemoteKillStrikeResult.Rejected) {
        debugMaceKill("strike-rejected") { listOf("reason" to result.reason) }
        routeAdmissionBackoff.reject(player.tickCount)
        activeRouteTarget?.let { rejectedTargets.reject(it, player.tickCount) }
        routeRejected = true
        holdAttackState = armMaceKillHoldAttackRetry(holdAttackState)
        if (activeRouteOwner == MaceKillRouteOwner.FIGHT_BOT) {
            fightBotMaceState = MaceKillFightBotState.Rejected
        }
    }
}
