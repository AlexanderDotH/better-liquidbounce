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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.policy

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.KILL_AURA_INHERITED_TARGET_SOURCE
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.SpearKillAttemptPlan

internal data class SpearKillAttemptStartPolicyInput(
    val targetIdentity: String,
    val targetName: String,
    val targetEntityId: Int,
    val targetSourceOverride: String?,
    val inheritsKillAuraTarget: Boolean,
    val configuredTargetSource: String,
    val routeMode: String,
    val outboundSteps: Int,
    val currentTick: Int,
    val hitTicks: Int,
    val chargeTicks: Int,
    val terminalAuthorizationRequired: Boolean,
)

internal fun resolveSpearKillAttemptStartPlan(
    input: SpearKillAttemptStartPolicyInput,
): SpearKillAttemptPlan {
    val targetSource = input.targetSourceOverride ?: if (input.inheritsKillAuraTarget) {
        KILL_AURA_INHERITED_TARGET_SOURCE
    } else {
        input.configuredTargetSource
    }

    return SpearKillAttemptPlan(
        targetIdentity = input.targetIdentity,
        targetName = input.targetName.ifBlank { "entity-${input.targetEntityId}" },
        targetSource = targetSource,
        plannedRouteMode = input.routeMode,
        plannedOutboundStepCount = input.outboundSteps,
        predictedHitTick = input.currentTick + input.hitTicks,
        chargeTicks = input.chargeTicks,
        terminalAuthorizationRequired = input.terminalAuthorizationRequired,
    )
}
