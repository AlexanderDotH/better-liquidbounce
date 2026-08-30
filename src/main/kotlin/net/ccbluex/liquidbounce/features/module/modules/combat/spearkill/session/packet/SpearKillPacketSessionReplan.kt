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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS



import net.minecraft.world.phys.Vec3

/**
 * Atomically replaces only movement that has not entered the packet pipeline yet.
 * Confirmed outbound deltas are retained and appended to the exact inverse return.
 */
internal fun SpearKillPacketSessionState.replaceRemainingOutbound(
    outboundMovements: List<Vec3>,
    strikeHoldTicks: Int,
    preStrikeHoldTicks: Int = 0,
    terminalSuffixSteps: Int = 1,
    terminalBurstSteps: Int = 0,
    requireTerminalAuthorization: Boolean = false,
    completeReturnMovements: List<Vec3>? = null,
): Boolean {
    if (!canReplaceRemainingOutbound) return false
    return installReplacementOutbound(
        outboundMovements = outboundMovements,
        strikeHoldTicks = strikeHoldTicks,
        preStrikeHoldTicks = preStrikeHoldTicks,
        terminalSuffixSteps = terminalSuffixSteps,
        terminalBurstSteps = terminalBurstSteps,
        requireTerminalAuthorization = requireTerminalAuthorization,
        completeReturnMovements = completeReturnMovements,
    )
}

/** Replaces an untouched return tail with another attack while retaining the first origin. */
internal fun SpearKillPacketSessionState.startChainedOutbound(
    outboundMovements: List<Vec3>,
    strikeHoldTicks: Int,
    preStrikeHoldTicks: Int = 0,
    terminalSuffixSteps: Int = 1,
    terminalBurstSteps: Int = 0,
    requireTerminalAuthorization: Boolean = false,
): Boolean {
    if (!canStartChainedOutbound) return false
    return installReplacementOutbound(
        outboundMovements = outboundMovements,
        strikeHoldTicks = strikeHoldTicks,
        preStrikeHoldTicks = preStrikeHoldTicks,
        terminalSuffixSteps = terminalSuffixSteps,
        terminalBurstSteps = terminalBurstSteps,
        requireTerminalAuthorization = requireTerminalAuthorization,
        completeReturnMovements = null,
    )
}

internal fun SpearKillPacketSessionState.installReplacementOutbound(
    outboundMovements: List<Vec3>,
    strikeHoldTicks: Int,
    preStrikeHoldTicks: Int,
    terminalSuffixSteps: Int,
    terminalBurstSteps: Int,
    requireTerminalAuthorization: Boolean,
    completeReturnMovements: List<Vec3>?,
): Boolean {
    val configuration = ReplacementOutboundConfiguration(
        strikeHoldTicks = strikeHoldTicks,
        preStrikeHoldTicks = preStrikeHoldTicks,
        terminalSuffixSteps = terminalSuffixSteps,
        terminalBurstSteps = terminalBurstSteps,
        requireTerminalAuthorization = requireTerminalAuthorization,
    )
    if (!hasValidReplacementConfiguration(outboundMovements, configuration, completeReturnMovements)) return false
    installReplacementMovements(outboundMovements, completeReturnMovements)
    configureReplacementSession(outboundMovements.size, configuration)
    return true
}

private data class ReplacementOutboundConfiguration(
    val strikeHoldTicks: Int,
    val preStrikeHoldTicks: Int,
    val terminalSuffixSteps: Int,
    val terminalBurstSteps: Int,
    val requireTerminalAuthorization: Boolean,
)

private fun SpearKillPacketSessionState.hasValidReplacementConfiguration(
    outboundMovements: List<Vec3>,
    configuration: ReplacementOutboundConfiguration,
    completeReturnMovements: List<Vec3>?,
): Boolean = configuration.strikeHoldTicks >= 0 &&
    configuration.preStrikeHoldTicks in 0..SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS &&
    configuration.terminalSuffixSteps in 1..outboundMovements.size &&
    hasValidTerminalBurst(configuration.terminalBurstSteps, configuration.terminalSuffixSteps) &&
    (!configuration.requireTerminalAuthorization || configuration.preStrikeHoldTicks >= 1) &&
    hasValidReplacementMovements(outboundMovements, completeReturnMovements)

private fun SpearKillPacketSessionState.installReplacementMovements(
    outboundMovements: List<Vec3>,
    completeReturnMovements: List<Vec3>?,
) {
    movements.clear()
    movements.addAll(outboundMovements)
    if (completeReturnMovements == null) {
        outboundMovements.asReversed().forEach { movements += it.scale(-1.0) }
        committedMovements.asReversed().forEach { movements += it.scale(-1.0) }
    } else {
        movements.addAll(completeReturnMovements)
    }
    movements += Vec3.ZERO
}

private fun SpearKillPacketSessionState.configureReplacementSession(
    outboundStepCount: Int,
    configuration: ReplacementOutboundConfiguration,
) {
    remainingOutboundSteps = outboundStepCount
    remainingStrikeHoldTicks = 0
    remainingPreStrikeHoldTicks = 0
    remainingStepWaitTicks = 0
    holdingStrikeThisTick = false
    holdingPreStrikeThisTick = false
    configuredStrikeHoldTicks = configuration.strikeHoldTicks
    configuredPreStrikeHoldTicks = configuration.preStrikeHoldTicks
    configuredTerminalSuffixSteps = configuration.terminalSuffixSteps
    configuredTerminalBurstSteps = configuration.terminalBurstSteps
    configuredTerminalAuthorizationRequired = configuration.requireTerminalAuthorization
    preStrikeHoldPending = configuration.preStrikeHoldTicks > 0
    terminalAimLockComplete = !configuration.requireTerminalAuthorization
    terminalCommitAuthorized = !configuration.requireTerminalAuthorization
    physicalReturnStarted = false
    chainedOutboundWindowOpen = false
    pendingPhysicalPositionOffset = null
    recovering = false
}

internal fun SpearKillPacketSessionState.hasValidReplacementMovements(
    outboundMovements: List<Vec3>,
    completeReturnMovements: List<Vec3>?,
): Boolean {
    if (outboundMovements.isEmpty() || outboundMovements.any(::isInvalidMovement)) return false
    if (completeReturnMovements == null) return true
    if (completeReturnMovements.isEmpty() || completeReturnMovements.any(::isInvalidMovement)) return false

    val finalOffset = (outboundMovements + completeReturnMovements).fold(committedOffset, Vec3::add)
    return finalOffset.lengthSqr() < SPEAR_KILL_PACKET_SESSION_EPSILON
}

internal fun SpearKillPacketSessionState.isInvalidMovement(movement: Vec3): Boolean =
    !movement.isFinite() || movement.lengthSqr() < SPEAR_KILL_PACKET_SESSION_EPSILON
