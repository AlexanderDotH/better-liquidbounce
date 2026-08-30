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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.terminal

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.SpearKillPreparedSetback
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPrimedPendingStep
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.debugSpearKill
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.spearKillDebugSessionFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.spearKillDebugTargetFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.spearKillDebugVector
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.delivery.spearKillCorrectionPosition
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

@Suppress("LongParameterList")
internal fun SpearKillModuleState.logSpearKillPrimedBurstDecision(
    event: String,
    reason: String,
    origin: Vec3,
    destination: Vec3,
    movement: Vec3,
    windowOrigin: Vec3,
    noFallRequired: Boolean,
    maxPacketsRemaining: Int? = null,
) {
    debugSpearKill(event) {
        listOf(
            "tick" to player.tickCount,
            "reason" to reason,
            "origin" to spearKillDebugVector(origin),
            "destination" to spearKillDebugVector(destination),
            "movement" to spearKillDebugVector(movement),
            "movement_length" to movement.length(),
            "window_origin" to spearKillDebugVector(windowOrigin),
            "owned_pre_final_packets" to ownedMovementPacketsThisTick,
            "no_fall_required" to noFallRequired,
            "max_packets_remaining" to maxPacketsRemaining,
        ) + spearKillDebugSessionFields()
    }
}

internal fun SpearKillModuleState.logSpearKillPrimedBurstPlan(
    step: SpearKillPrimedPendingStep,
    movement: Vec3,
    windowOrigin: Vec3,
) {
    val plan = step.plan
    debugSpearKill("PRIMED_BURST_PLAN") {
        listOf(
            "tick" to player.tickCount,
            "origin" to spearKillDebugVector(step.origin),
            "destination" to spearKillDebugVector(step.destination),
            "movement" to spearKillDebugVector(movement),
            "movement_length" to movement.length(),
            "window_origin" to spearKillDebugVector(windowOrigin),
            "requested_distance" to plan.requestedDistance,
            "required_server_packets" to plan.requiredServerPackets,
            "target_priming_packets" to plan.targetPrimingPackets,
            "dedicated_priming_packets" to plan.dedicatedPrimingPackets,
            "total_pre_final_packets" to plan.totalPreFinalPackets,
            "final_packet_ordinal" to plan.finalPacketOrdinal,
            "server_counted_packets" to plan.serverCountedPackets,
            "owned_packet_budget" to plan.totalOwnedPacketBudget,
            "source_predicted_accepted" to plan.sourcePredictedAccepted,
            "movement_profile" to plan.movementProfile,
            "priming_packet_type" to plan.primingPacketType,
            "no_fall_required" to step.noFallPacketRequired,
        ) + spearKillDebugTargetFields(lockedAStarTarget) + spearKillDebugSessionFields()
    }
}

internal fun SpearKillModuleState.logSpearKillSetbackPrepare(
    packet: ClientboundPlayerPositionPacket,
    targetPlayer: Player,
    routeTarget: LivingEntity?,
) {
    debugSpearKill("SETBACK_PREPARE") {
        listOf(
            "tick" to targetPlayer.tickCount,
            "passenger" to targetPlayer.isPassenger,
            "correction" to spearKillDebugVector(spearKillCorrectionPosition(packet)),
        ) + spearKillDebugTargetFields(routeTarget) + spearKillDebugSessionFields()
    }
}

internal fun SpearKillModuleState.logSpearKillSetbackRecoveryFailed(
    targetPlayer: Player,
    sessionOrigin: Vec3,
    routeTarget: LivingEntity?,
) {
    debugSpearKill("SETBACK_RECOVERY_FAILED") {
        listOf(
            "tick" to targetPlayer.tickCount,
            "reason" to "rollback-prepare-rejected",
            "session_origin" to spearKillDebugVector(sessionOrigin),
        ) + spearKillDebugTargetFields(routeTarget) + spearKillDebugSessionFields()
    }
}

internal fun SpearKillModuleState.logSpearKillSetbackRecoveryArmed(
    targetPlayer: Player,
    preparedSetback: SpearKillPreparedSetback,
) {
    debugSpearKill("SETBACK_RECOVERY_ARMED") {
        listOf(
            "tick" to targetPlayer.tickCount,
            "session_origin" to spearKillDebugVector(preparedSetback.sessionOrigin),
            "authoritative_offset" to spearKillDebugVector(preparedSetback.authoritativeOffset),
            "physical_return" to preparedSetback.physicalReturn,
            "exact_recovery_steps" to preparedSetback.exactRecoveryMovements?.size,
            "pending_fall_distance" to pendingSetbackFallDistance,
        ) + spearKillDebugSessionFields()
    }
}
