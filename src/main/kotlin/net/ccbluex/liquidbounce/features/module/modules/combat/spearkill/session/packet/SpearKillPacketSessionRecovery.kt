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



import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.REMOTE_KILL_ROUTE_MAX_STEP_WAIT_TICKS
import net.minecraft.world.phys.Vec3

internal fun SpearKillPacketSessionState.beginRecovery(maxSpeed: Double) {
    require(maxSpeed.isFinite() && maxSpeed > 0.0) { "Maximum speed must be finite and positive" }
    pendingOffset = null
    pendingStepIsOutbound = false
    pendingStepIsPhysicalReturn = false
    remainingOutboundSteps = 0
    remainingStrikeHoldTicks = 0
    remainingPreStrikeHoldTicks = 0
    remainingStepWaitTicks = 0
    holdingStrikeThisTick = false
    holdingPreStrikeThisTick = false
    preStrikeHoldPending = false
    configuredStrikeHoldTicks = 0
    configuredPreStrikeHoldTicks = 0
    configuredTerminalSuffixSteps = 1
    configuredTerminalBurstSteps = 0
    configuredTerminalAuthorizationRequired = false
    configuredStepWaitTicks = 0
    chainedOutboundWindowOpen = false
    terminalAimLockComplete = false
    terminalCommitAuthorized = true
    movements.clear()

    if (committedOffset.lengthSqr() < SPEAR_KILL_PACKET_SESSION_EPSILON) {
        clear()
        return
    }

    movements.addAll(buildSpearKillFixedStepMovements(
        direction = committedOffset.scale(-1.0),
        distance = committedOffset.length(),
        maxSpeed = maxSpeed,
    ))
    physicalReturnStarted = physicalReturnEnabled
    if (physicalReturnStarted) {
        pendingPhysicalPositionOffset = committedOffset
    }
    recovering = true
}

internal fun SpearKillPacketSessionState.beginExactReturn() {
    chainedOutboundWindowOpen = false
    if (recovering) return

    pendingOffset = null
    pendingStepIsOutbound = false
    pendingStepIsPhysicalReturn = false
    remainingOutboundSteps = 0
    remainingStrikeHoldTicks = 0
    remainingPreStrikeHoldTicks = 0
    holdingStrikeThisTick = false
    holdingPreStrikeThisTick = false
    preStrikeHoldPending = false
    configuredStrikeHoldTicks = 0
    configuredPreStrikeHoldTicks = 0
    configuredTerminalSuffixSteps = 1
    configuredTerminalBurstSteps = 0
    configuredTerminalAuthorizationRequired = false
    terminalAimLockComplete = false
    terminalCommitAuthorized = true
    movements.clear()

    if (committedOffset.lengthSqr() < SPEAR_KILL_PACKET_SESSION_EPSILON) {
        clear()
        return
    }

    committedMovements.asReversed().forEach { movements += it.scale(-1.0) }
    physicalReturnStarted = physicalReturnEnabled
    if (physicalReturnStarted) {
        pendingPhysicalPositionOffset = committedOffset
    }
    recovering = true
}

/**
 * Returns the exact inverse of confirmed movement when [authoritativeOffset] still describes
 * this session. Unlike a synthesized straight return, this retraces the collision-safe route.
 */
internal fun SpearKillPacketSessionState.exactRecoveryMovementsFrom(authoritativeOffset: Vec3): List<Vec3>? {
    if (!authoritativeOffset.isFinite() ||
        authoritativeOffset.distanceToSqr(committedOffset) >= SPEAR_KILL_PACKET_SESSION_EPSILON
    ) {
        return null
    }

    val recordedOffset = committedMovements.fold(Vec3.ZERO, Vec3::add)
    if (recordedOffset.distanceToSqr(committedOffset) >= SPEAR_KILL_PACKET_SESSION_EPSILON) return null

    return committedMovements.asReversed().map { it.scale(-1.0) }
        .takeIf { it.isNotEmpty() }
}

/** Starts a physical recovery using an already collision-validated route back to zero. */
internal fun SpearKillPacketSessionState.beginPhysicalExactRecoveryFrom(
    authoritativeOffset: Vec3,
    recoveryMovements: List<Vec3>,
    stepWaitTicks: Int = 0,
) {
    beginExactRecoveryFrom(
        authoritativeOffset,
        recoveryMovements,
        physicalReturn = true,
        stepWaitTicks = stepWaitTicks,
    )
}

/** Starts a packet-only recovery before any physical-position fallback is allowed. */
internal fun SpearKillPacketSessionState.beginPacketExactRecoveryFrom(
    authoritativeOffset: Vec3,
    recoveryMovements: List<Vec3>,
    stepWaitTicks: Int,
) {
    beginExactRecoveryFrom(
        authoritativeOffset,
        recoveryMovements,
        physicalReturn = false,
        stepWaitTicks = stepWaitTicks,
    )
}

internal fun SpearKillPacketSessionState.beginExactRecoveryFrom(
    authoritativeOffset: Vec3,
    recoveryMovements: List<Vec3>,
    physicalReturn: Boolean,
    stepWaitTicks: Int,
) {
    require(authoritativeOffset.isFinite()) { "Authoritative offset must be finite" }
    require(
        recoveryMovements.isNotEmpty() &&
            recoveryMovements.all { it.isFinite() && it.lengthSqr() >= SPEAR_KILL_PACKET_SESSION_EPSILON },
    ) {
        "Exact recovery must contain finite non-zero movement"
    }
    val recoveredOffset = recoveryMovements.fold(authoritativeOffset, Vec3::add)
    require(recoveredOffset.lengthSqr() < SPEAR_KILL_PACKET_SESSION_EPSILON) { "Exact recovery must end at the session origin" }
    require(stepWaitTicks in 0..REMOTE_KILL_ROUTE_MAX_STEP_WAIT_TICKS) {
        "Exact recovery wait duration is outside the shared route range"
    }

    clear()
    committedOffset = authoritativeOffset
    movements.addAll(recoveryMovements)
    movements += Vec3.ZERO
    physicalReturnEnabled = physicalReturn
    physicalReturnStarted = physicalReturn
    configuredStepWaitTicks = stepWaitTicks
    chainedOutboundWindowOpen = false
    pendingPhysicalPositionOffset = authoritativeOffset.takeIf { physicalReturn }
    recovering = true
}

internal fun SpearKillPacketSessionState.beginRecoveryFrom(authoritativeOffset: Vec3, maxSpeed: Double) =
    beginRecoveryFrom(authoritativeOffset, maxSpeed, physicalReturn = false)

internal fun SpearKillPacketSessionState.beginPhysicalRecoveryFrom(authoritativeOffset: Vec3, maxSpeed: Double) =
    beginRecoveryFrom(authoritativeOffset, maxSpeed, physicalReturn = true)

internal fun SpearKillPacketSessionState.beginRecoveryFrom(
    authoritativeOffset: Vec3,
    maxSpeed: Double,
    physicalReturn: Boolean,
) {
    clear()
    committedOffset = authoritativeOffset
    physicalReturnEnabled = physicalReturn
    beginRecovery(maxSpeed)
}

internal fun SpearKillPacketSessionState.clear() {
    movements.clear()
    committedMovements.clear()
    pendingOffset = null
    pendingStepIsOutbound = false
    pendingStepIsPhysicalReturn = false
    pendingPhysicalPositionOffset = null
    remainingOutboundSteps = 0
    remainingStrikeHoldTicks = 0
    remainingPreStrikeHoldTicks = 0
    remainingStepWaitTicks = 0
    holdingStrikeThisTick = false
    holdingPreStrikeThisTick = false
    preStrikeHoldPending = false
    configuredStrikeHoldTicks = 0
    configuredPreStrikeHoldTicks = 0
    configuredTerminalSuffixSteps = 1
    configuredTerminalBurstSteps = 0
    configuredTerminalAuthorizationRequired = false
    configuredStepWaitTicks = 0
    physicalReturnEnabled = false
    physicalReturnStarted = false
    chainedOutboundWindowOpen = false
    lastDeliveredMovement = null
    terminalAimLockComplete = false
    terminalCommitAuthorized = true
    committedOffset = Vec3.ZERO
    recovering = false
}

internal fun SpearKillPacketSessionState.finishRecoveryIfComplete() {
    if (!recovering || movements.isNotEmpty()) return
    committedOffset = Vec3.ZERO
    lastDeliveredMovement = null
    recovering = false
}

internal fun SpearKillPacketSessionState.hasValidTerminalBurst(terminalBurstSteps: Int, terminalSuffixSteps: Int): Boolean =
    terminalBurstSteps == 0 || terminalBurstSteps in 2..terminalSuffixSteps

private fun Vec3.isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
