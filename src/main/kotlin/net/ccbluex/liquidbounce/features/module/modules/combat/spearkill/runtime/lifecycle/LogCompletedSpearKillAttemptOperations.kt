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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.SpearKillAttemptSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.debugSpearKill
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.spearKillDebugSessionFields
import net.ccbluex.liquidbounce.utils.client.player

internal fun SpearKillModuleState.logCompletedSpearKillAttempt(
    trigger: String,
    snapshot: SpearKillAttemptSnapshot?,
) {
    snapshot ?: return
    debugSpearKill("ATTEMPT_FINISH") {
        listOf(
            "tick" to player.tickCount,
            "trigger" to trigger,
            "attempt_id" to snapshot.attemptId,
            "outcome" to snapshot.outcome,
            "abort_reason" to snapshot.abortReason,
            "target_name" to snapshot.targetName,
            "target_source" to snapshot.targetSource,
            "route" to snapshot.plannedRouteMode,
            "outbound_steps" to snapshot.outboundStepCount,
            "planned_outbound_steps" to snapshot.plannedOutboundStepCount,
            "predicted_hit_tick" to snapshot.predictedHitTick,
            "terminal_authorized" to snapshot.terminalAuthorized,
            "terminal_authorization_tick" to snapshot.terminalAuthorizationTick,
            "setback" to snapshot.setback,
            "blocked" to snapshot.blocked,
            "recovery" to snapshot.recovery,
            "defeated" to snapshot.defeated,
            "target_removed" to snapshot.targetRemoved,
            "damage_evidence" to snapshot.damageEvidence,
        ) + spearKillDebugSessionFields()
    }
}
