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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.policy.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.SpearKillFightBotState
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.SpearKillFightBotTerminal
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.SPEAR_KILL_DAMAGE_EVIDENCE_WINDOW_TICKS
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.SpearKillAttemptSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.debugSpearKill
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.hasActiveAttackPath
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.spearKillDebugSessionFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.spearKillDebugTargetFields
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.world.entity.LivingEntity

internal fun SpearKillModuleState.completeSpearKillAttempt(trigger: String): SpearKillAttemptSnapshot? =
    attemptTracker.complete().also { logCompletedSpearKillAttempt(trigger, it) }

internal fun SpearKillModuleState.beginSpearKillAttempt(
    target: LivingEntity,
    routeMode: String,
    outboundSteps: Int,
    hitTicks: Int,
    terminalAuthorizationRequired: Boolean,
    targetSourceOverride: String? = null,
) {
    if (attemptTracker.current != null) {
        completeSpearKillAttempt("superseded")
    }
    val plan = resolveSpearKillAttemptStartPlan(
        SpearKillAttemptStartPolicyInput(
            targetIdentity = target.uuid.toString(),
            targetName = target.scoreboardName,
            targetEntityId = target.id,
            targetSourceOverride = targetSourceOverride,
            inheritsKillAuraTarget = pendingKillAuraTarget === target,
            configuredTargetSource = targetSource.name,
            routeMode = routeMode,
            outboundSteps = outboundSteps,
            currentTick = player.tickCount,
            hitTicks = hitTicks,
            chargeTicks = player.ticksUsingItem,
            terminalAuthorizationRequired = terminalAuthorizationRequired,
        ),
    )
    damageEvidenceTracker.clear()
    damageEvidenceTracker.arm(
        targetEntityId = target.id,
        predictedHitTick = plan.predictedHitTick,
        windowTicks = packetSessionSettings?.damageEvidenceWindowTicks
            ?: SPEAR_KILL_DAMAGE_EVIDENCE_WINDOW_TICKS,
    )
    attemptRouteCompleted = false
    val attempt = attemptTracker.begin(plan)
    logSpearKillAttemptStart(target, attempt, hitTicks)
}

private fun SpearKillModuleState.logSpearKillAttemptStart(
    target: LivingEntity,
    attempt: SpearKillAttemptSnapshot,
    hitTicks: Int,
) {
    debugSpearKill("ATTEMPT_START") {
        listOf(
            "tick" to player.tickCount,
            "attempt_id" to attempt.attemptId,
            "target_source" to attempt.targetSource,
            "route" to attempt.plannedRouteMode,
            "outbound_steps" to attempt.plannedOutboundStepCount,
            "hit_ticks" to hitTicks,
            "predicted_hit_tick" to attempt.predictedHitTick,
            "charge_ticks" to player.ticksUsingItem,
            "terminal_authorization_required" to attempt.terminalAuthorizationRequired,
        ) + spearKillDebugTargetFields(target) + spearKillDebugSessionFields()
    }
}

internal fun SpearKillModuleState.recordRejectedSpearKillAttempt(
    target: LivingEntity,
    routeMode: String,
) {
    beginSpearKillAttempt(
        target = target,
        routeMode = routeMode,
        outboundSteps = 0,
        hitTicks = 0,
        terminalAuthorizationRequired = false,
    )
    damageEvidenceTracker.clear()
    attemptTracker.markBlocked()
    completeSpearKillAttempt("route-rejected")
}

internal fun SpearKillModuleState.requestSpearKillAttemptCompletion() {
    if (attemptTracker.current == null) return
    if (!attemptRouteCompleted) {
        debugSpearKill("ROUTE_COMPLETE") {
            listOf(
                "tick" to player.tickCount,
                "awaiting_damage_evidence" to damageEvidenceTracker.isArmed,
            ) + spearKillDebugSessionFields()
        }
    }
    attemptRouteCompleted = true
    if (!hasActiveAttackPath) {
        clearAStarTargetLock()
        packetAStarAttackActive = false
        clearAStarRenderPath()
        activeMovementTransport = null
        resetVirtualFallSafety()
    }
    if (!damageEvidenceTracker.isArmed) {
        completeSpearKillAttempt("route-complete-without-evidence-window")
        attemptRouteCompleted = false
    }
    if (!hasActiveAttackPath && fightBotSpearState == SpearKillFightBotState.RouteActive) {
        clearFightBotSpearUseEffect(SpearKillFightBotTerminal.Completion)
    }
}
