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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.tick

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.debugSpearKill
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.debugSpearKillChanged
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.isSpearUseRequested
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.shouldRefreshSpearKillPrehold
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.shouldStartSpearKillAttempt
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.spearKillDebugSessionFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.spearKillDebugTargetFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.usesNetworkOptimizedRouting
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.usesPacketMovementMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.isDirectSpearKillTargetEligible
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.recordRejectedSpearKillAttempt
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.refreshSpearKillServerUse
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.rejectFightBotSpearUse
import net.minecraft.world.entity.LivingEntity

internal data class SpearKillAdmittedTickTarget(val entity: LivingEntity, val distance: Double)

internal fun SpearKillModuleState.admitSpearKillTickStart(
    charge: SpearKillTickChargeContext,
): SpearKillAdmittedTickTarget? {
    if (deferSpearKillTickStart(charge)) return null
    val (entity, distance) = requireNotNull(charge.target.target)
    if (usesPacketMovementMode && player.isPassenger) {
        debugBlockedSpearKillTickStart(entity, distance, "passenger")
        if (fightBotSpearTarget === entity) rejectFightBotSpearUse(entity)
        return null
    }
    if (!usesPacketMovementMode && !isDirectSpearKillTargetEligible(entity, distance)) {
        debugBlockedSpearKillTickStart(entity, distance, "direct-target-ineligible")
        rejectSpearKillTarget(entity)
        recordRejectedSpearKillAttempt(entity, "Direct")
        if (fightBotSpearTarget === entity) rejectFightBotSpearUse(entity)
        return null
    }
    val activeLockedTarget = lockedAStarTarget
    if ((activeLockedTarget != null && activeLockedTarget !== entity) || isSpearKillTargetRejected(entity)) {
        debugLockedSpearKillTickStart(entity, distance, activeLockedTarget)
        return null
    }
    lockedAStarTarget = entity
    return SpearKillAdmittedTickTarget(entity, distance)
}

private fun SpearKillModuleState.deferSpearKillTickStart(charge: SpearKillTickChargeContext): Boolean {
    val target = charge.target.target
    if (shouldRefreshSpearKillPrehold(
            isSpearUseRequested,
            charge.target.attackRequested && target != null,
            usesPacketMovementMode,
            player.ticksUsingItem,
            charge.weapon.delayTicks,
            charge.duration,
        )
    ) {
        debugSpearKillChargeRefresh(target, charge)
        refreshSpearKillServerUse()
        return true
    }
    if (!shouldStartSpearKillAttempt(
            false, charge.target.attackRequested, target != null, player.ticksUsingItem,
            charge.weapon.delayTicks, charge.duration,
        )
    ) {
        debugSpearKillStartGate(target, charge)
        return true
    }
    if (packetSetbackRecoveryAttempted) return debugSpearKillStartBlocker(target, "setback-recovery")
    if (usesNetworkOptimizedRouting && !networkOptimizer.canStartAttempt(player.tickCount)) {
        return debugSpearKillStartBlocker(target, "network-backoff")
    }
    return false
}

private fun SpearKillModuleState.debugSpearKillChargeRefresh(
    target: Pair<LivingEntity, Double>?,
    charge: SpearKillTickChargeContext,
) = debugSpearKill("CHARGE_REFRESH") {
    listOf(
        "tick" to player.tickCount,
        "target_id" to target?.first?.id,
        "ticks_using_item" to player.ticksUsingItem,
        "delay_ticks" to charge.weapon.delayTicks,
        "damage_use_duration" to charge.duration,
        "route_can_recover_charge" to usesPacketMovementMode,
    )
}

private fun SpearKillModuleState.debugSpearKillStartGate(
    target: Pair<LivingEntity, Double>?,
    charge: SpearKillTickChargeContext,
) = debugSpearKillChanged(
    channel = "start-gate",
    event = "START_GATE_BLOCKED",
    fingerprint = {
        listOf(
            target?.first?.id, charge.target.attackRequested, target != null,
            charge.duration >= charge.weapon.delayTicks,
        )
    },
) {
    listOf(
        "tick" to player.tickCount,
        "activation_satisfied" to charge.target.attackRequested,
        "has_target" to (target != null),
        "ticks_using_item" to player.ticksUsingItem,
        "delay_ticks" to charge.weapon.delayTicks,
        "damage_use_duration" to charge.duration,
    )
}

private fun SpearKillModuleState.debugSpearKillStartBlocker(
    target: Pair<LivingEntity, Double>?,
    reason: String,
): Boolean {
    debugSpearKillChanged(
        channel = "start-blocker",
        event = "START_BLOCKED",
        fingerprint = { listOf(target?.first?.id, reason) },
    ) {
        listOf("tick" to player.tickCount, "reason" to reason) + spearKillDebugSessionFields()
    }
    return true
}

private fun SpearKillModuleState.debugBlockedSpearKillTickStart(
    entity: LivingEntity,
    distance: Double,
    reason: String,
) = debugSpearKill("START_BLOCKED") {
    listOf("tick" to player.tickCount, "reason" to reason) + spearKillDebugTargetFields(entity, distance)
}

private fun SpearKillModuleState.debugLockedSpearKillTickStart(
    entity: LivingEntity,
    distance: Double,
    locked: LivingEntity?,
) = debugSpearKillChanged(
    channel = "start-blocker",
    event = "START_BLOCKED",
    fingerprint = { listOf(entity.id, "target-lock-or-rejection") },
) {
    listOf(
        "tick" to player.tickCount,
        "reason" to "target-lock-or-rejection",
        "active_locked_target_id" to locked?.id,
    ) + spearKillDebugTargetFields(entity, distance)
}
