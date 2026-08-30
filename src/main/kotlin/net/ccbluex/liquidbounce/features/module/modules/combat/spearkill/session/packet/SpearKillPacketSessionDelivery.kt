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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet



import net.minecraft.world.phys.Vec3

internal fun SpearKillPacketSessionState.prepareNextStep(): Vec3? {
    pendingOffset?.let { return it }
    holdingStrikeThisTick = false
    holdingPreStrikeThisTick = false
    if (consumeScheduledPause()) return null
    if (awaitingTerminalCommitAuthorization) return null
    return prepareNextMovement()
}

private fun SpearKillPacketSessionState.consumeScheduledPause(): Boolean {
    when {
        remainingStrikeHoldTicks > 0 -> {
            remainingStrikeHoldTicks--
            holdingStrikeThisTick = true
        }
        remainingStepWaitTicks > 0 -> {
            remainingStepWaitTicks--
        }
        remainingPreStrikeHoldTicks > 0 -> {
            remainingPreStrikeHoldTicks--
            holdingPreStrikeThisTick = true
            completeTerminalAimLockIfReady()
        }
        preStrikeHoldPending && remainingOutboundSteps == configuredTerminalSuffixSteps -> {
            preStrikeHoldPending = false
            remainingPreStrikeHoldTicks = configuredPreStrikeHoldTicks - 1
            holdingPreStrikeThisTick = true
            completeTerminalAimLockIfReady()
        }
        else -> return false
    }
    return true
}

private fun SpearKillPacketSessionState.completeTerminalAimLockIfReady() {
    if (remainingPreStrikeHoldTicks == 0) terminalAimLockComplete = true
}

private fun SpearKillPacketSessionState.prepareNextMovement(): Vec3? {
    val movement = movements.firstOrNull() ?: return null
    if (movement.lengthSqr() < SPEAR_KILL_PACKET_SESSION_EPSILON) {
        movements.removeFirst()
        finishRecoveryIfComplete()
        return null
    }
    pendingStepIsOutbound = remainingOutboundSteps > 0
    pendingStepIsPhysicalReturn = physicalReturnStarted && !pendingStepIsOutbound
    chainedOutboundWindowOpen = chainedOutboundWindowOpen && pendingStepIsOutbound
    return committedOffset.add(movement).also { pendingOffset = it }
}

internal fun SpearKillPacketSessionState.confirmStep(delivered: Boolean) {
    val pending = pendingOffset ?: return
    if (!delivered) {
        discardPendingStep()
        return
    }

    val completedPhysicalReturnStep = pendingStepIsPhysicalReturn
    val completedTerminalBurstStep = pendingTerminalBurstStep
    commitDeliveredStep(pending, completedPhysicalReturnStep)
    discardPendingStep()
    discardTrailingStopMarkers()
    scheduleNextStepWait(completedTerminalBurstStep)
    finishRecoveryIfComplete()
}

private fun SpearKillPacketSessionState.discardPendingStep() {
    pendingOffset = null
    pendingStepIsOutbound = false
    pendingStepIsPhysicalReturn = false
}

private fun SpearKillPacketSessionState.commitDeliveredStep(
    pending: Vec3,
    completedPhysicalReturnStep: Boolean,
) {
    val movement = pending.subtract(committedOffset)
    committedOffset = pending
    committedMovements += movement
    lastDeliveredMovement = movement
    movements.removeFirst()
    if (pendingStepIsOutbound) {
        remainingOutboundSteps--
        if (remainingOutboundSteps == 0) beginReturnPhase()
    } else if (completedPhysicalReturnStep) {
        pendingPhysicalPositionOffset = committedOffset
    }
}

private fun SpearKillPacketSessionState.beginReturnPhase() {
    remainingStrikeHoldTicks = configuredStrikeHoldTicks
    recovering = true
    chainedOutboundWindowOpen = true
    if (physicalReturnEnabled) {
        physicalReturnStarted = true
        pendingPhysicalPositionOffset = committedOffset
    }
}

private fun SpearKillPacketSessionState.discardTrailingStopMarkers() {
    while (movements.firstOrNull()?.lengthSqr()?.let { it < SPEAR_KILL_PACKET_SESSION_EPSILON } == true) {
        movements.removeFirst()
    }
}

private fun SpearKillPacketSessionState.scheduleNextStepWait(completedTerminalBurstStep: Boolean) {
    val terminalBurstContinues = completedTerminalBurstStep && remainingOutboundSteps > 0
    remainingStepWaitTicks = if (terminalBurstContinues) {
        0
    } else if (movements.firstOrNull()?.lengthSqr()?.let { it >= SPEAR_KILL_PACKET_SESSION_EPSILON } == true) {
        configuredStepWaitTicks
    } else {
        0
    }
}

/**
 * Returns the confirmed absolute offset that the local player should adopt during a physical
 * return. Outbound and cancelled packets never produce an update.
 */
internal fun SpearKillPacketSessionState.consumePhysicalPositionOffset(): Vec3? = pendingPhysicalPositionOffset.also {
    pendingPhysicalPositionOffset = null
}
