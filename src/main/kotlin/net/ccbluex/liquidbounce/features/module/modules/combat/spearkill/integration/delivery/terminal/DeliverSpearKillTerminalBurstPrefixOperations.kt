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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_RECOVERY_POSITION_EPSILON_SQUARED
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.SpearKillFallSafetyPendingStepAction
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.SpearKillFallSafetyPendingStepGate
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.debugSpearKill
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.resolveSpearKillFallSafetyPendingStepAction
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.shouldProtectFallDamage
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.spearKillDebugSessionFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.spearKillDebugTargetFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.spearKillDebugVector
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.delivery.packetPositionOrigin
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.delivery.sendFallbackMovementPacket
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.delivery.sendSpearKillFallStabilizationPacket
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.applyConfirmedPhysicalReturnPosition
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.beginSafeExactReturn
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.pendingLogicalOutboundCompletion
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.pendingTerminalBurstMovement
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.world.phys.Vec3

/** Sends every non-final terminal segment now; the normal movement event carries the final one. */
internal fun SpearKillModuleState.deliverSpearKillTerminalBurstPrefix(): Boolean {
    while (packetBootSession.pendingTerminalBurstMovement != null &&
        !packetBootSession.pendingLogicalOutboundCompletion
    ) {
        val movement = packetBootSession.pendingMovement ?: return false
        if (!preparePendingSpearKillFallSafety(movement)) return false

        val expectedOffset = packetBootSession.virtualOffset
        awaitingVanillaMovementPacket = true
        sendFallbackMovementPacket()
        if (packetBootSession.committedOffset.distanceToSqr(expectedOffset) >
            SPEAR_KILL_RECOVERY_POSITION_EPSILON_SQUARED
        ) {
            return false
        }
        if (packetBootSession.prepareNextStep() == null) return false
    }
    return true
}

internal fun SpearKillModuleState.preparePendingSpearKillFallSafety(movement: Vec3): Boolean {
    val physicallyNearGround = isSpearKillPositionNearGround(
        packetPositionOrigin().add(packetBootSession.virtualOffset),
    )
    val gate = fallSafetyLifecycle.gatePendingMovement(
        movement,
        physicallyNearGround = physicallyNearGround,
    )
    val stabilizationRequired = gate == SpearKillFallSafetyPendingStepGate.CLEAR &&
        fallSafetyLifecycle.shouldStabilizePendingMovement(movement, shouldProtectFallDamage)

    return when (resolveSpearKillFallSafetyPendingStepAction(gate, stabilizationRequired)) {
        SpearKillFallSafetyPendingStepAction.DELIVER -> true
        SpearKillFallSafetyPendingStepAction.STABILIZE -> {
            val delivery = sendSpearKillFallStabilizationPacket()
            if (!delivery.delivered) {
                confirmRemoteSpearKillPacketStep(delivered = false)
                awaitingVanillaMovementPacket = false
            }
            false
        }
        SpearKillFallSafetyPendingStepAction.BLOCKED -> {
            rejectPendingSpearKillFallSafety(movement)
            false
        }
    }
}

internal fun SpearKillModuleState.gatePendingSpearKillFallSafety(movement: Vec3): Boolean = when (
    fallSafetyLifecycle.gatePendingMovement(
        movement,
        physicallyNearGround = isSpearKillPositionNearGround(
            packetPositionOrigin().add(packetBootSession.virtualOffset),
        ),
    )
) {
    SpearKillFallSafetyPendingStepGate.CLEAR -> true
    SpearKillFallSafetyPendingStepGate.BLOCKED -> {
        rejectPendingSpearKillFallSafety(movement)
        false
    }
}

internal fun SpearKillModuleState.rejectPendingSpearKillFallSafety(movement: Vec3) {
    debugSpearKill("FALL_SAFETY_BLOCK") {
        listOf(
            "tick" to player.tickCount,
            "movement" to spearKillDebugVector(movement),
            "movement_length" to movement.length(),
            "confirmed_fall_distance" to fallSafetyLifecycle.confirmedFallDistance,
        ) + spearKillDebugTargetFields(lockedAStarTarget) + spearKillDebugSessionFields()
    }
    confirmRemoteSpearKillPacketStep(delivered = false)
    awaitingVanillaMovementPacket = false
    beginSafeExactReturn()
    applyConfirmedPhysicalReturnPosition()
}
