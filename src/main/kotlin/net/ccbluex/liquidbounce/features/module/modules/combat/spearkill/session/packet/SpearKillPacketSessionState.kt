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



import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.world.phys.Vec3

internal const val SPEAR_KILL_PACKET_SESSION_EPSILON = 1.0E-12

internal class SpearKillPacketSessionState {
    internal val movements = ArrayDeque<Vec3>()
    internal val committedMovements = ArrayDeque<Vec3>()
    internal var pendingOffset: Vec3? = null
    internal var pendingStepIsOutbound = false
    internal var pendingStepIsPhysicalReturn = false
    internal var pendingPhysicalPositionOffset: Vec3? = null
    internal var remainingOutboundSteps = 0
    internal var remainingStrikeHoldTicks = 0
    internal var remainingPreStrikeHoldTicks = 0
    internal var remainingStepWaitTicks = 0
    internal var holdingStrikeThisTick = false
    internal var holdingPreStrikeThisTick = false
    internal var preStrikeHoldPending = false
    internal var configuredStrikeHoldTicks = 0
    internal var configuredPreStrikeHoldTicks = 0
    internal var configuredTerminalSuffixSteps = 1
    internal var configuredTerminalBurstSteps = 0
    internal var configuredTerminalAuthorizationRequired = false
    internal var configuredStepWaitTicks = 0
    internal var physicalReturnEnabled = false
    internal var physicalReturnStarted = false
    internal var chainedOutboundWindowOpen = false
    internal var lastDeliveredMovement: Vec3? = null
    internal var terminalCommitAuthorized = true

    var terminalAimLockComplete: Boolean = false
        internal set

    var committedOffset: Vec3 = Vec3.ZERO
        internal set

    var recovering: Boolean = false
        internal set

    val active: Boolean
        get() = movements.isNotEmpty() || pendingOffset != null || remainingStrikeHoldTicks > 0 ||
            remainingPreStrikeHoldTicks > 0 || remainingStepWaitTicks > 0 ||
            pendingPhysicalPositionOffset != null

    val virtualOffset: Vec3
        get() = pendingOffset ?: committedOffset

    val requiresDelivery: Boolean
        get() = pendingOffset != null

    val pendingOutboundStep: Boolean
        get() = pendingOffset != null && pendingStepIsOutbound

    /** True only for the final physical outbound movement before the strike hold begins. */
    val pendingFinalOutboundStep: Boolean
        get() = pendingOutboundStep && remainingOutboundSteps == 1

    val pendingMovement: Vec3?
        get() = pendingOffset?.subtract(committedOffset)

    val pendingTerminalBurstStart: Boolean
        get() = pendingTerminalBurstStep && remainingOutboundSteps == configuredTerminalBurstSteps

    val pendingLogicalOutboundCompletion: Boolean
        get() = pendingOutboundStep && (!pendingTerminalBurstStep || remainingOutboundSteps == 1)

    val pendingTerminalBurstMovement: Vec3?
        get() {
            if (!pendingTerminalBurstStep) return null
            return movements.asSequence()
                .take(remainingOutboundSteps)
                .fold(Vec3.ZERO, Vec3::add)
        }

    internal val pendingTerminalBurstStep: Boolean
        get() = pendingOutboundStep && configuredTerminalBurstSteps > 1 &&
            remainingOutboundSteps in 1..configuredTerminalBurstSteps

    /**
     * Rotation enforced for the current route phase.
     *
     * The next edge is exposed before packet preparation so RotationManager and item-use packets
     * cannot lag one edge behind movement. Cadence waits retain the delivered edge, while the
     * pre-strike hold deliberately pre-locks the first terminal edge.
     */
    val pathHeading: Rotation?
        get() = pathHeadingMovement()?.let(::spearKillKineticHeading)

    internal fun pathHeadingMovement(): Vec3? {
        pendingMovement?.let { return it }

        val nextMovement = movements.firstOrNull()?.takeIf { it.lengthSqr() >= SPEAR_KILL_PACKET_SESSION_EPSILON }
        val lockingTerminal = remainingPreStrikeHoldTicks > 0 ||
            holdingPreStrikeThisTick ||
            preStrikeHoldPending && remainingOutboundSteps == configuredTerminalSuffixSteps

        return when {
            holdingStrike -> lastDeliveredMovement
            lockingTerminal -> nextMovement ?: lastDeliveredMovement
            remainingStepWaitTicks == 0 -> nextMovement ?: lastDeliveredMovement
            else -> lastDeliveredMovement
        }
    }

    /** True from the final outbound confirmation until both strike-hold ticks have been consumed. */
    val holdingStrike: Boolean
        get() = remainingStrikeHoldTicks > 0 || holdingStrikeThisTick

    val holdingPreStrike: Boolean
        get() = remainingPreStrikeHoldTicks > 0 || holdingPreStrikeThisTick

    /** True while ambient movement packets must not collapse into the terminal kinetic lunge. */
    val holdingKineticBarrier: Boolean
        get() = holdingStrike || remainingPreStrikeHoldTicks > 0 || holdingPreStrikeThisTick

    val terminalSuffixSteps: Int
        get() = configuredTerminalSuffixSteps

    val awaitingTerminalCommitAuthorization: Boolean
        get() = configuredTerminalAuthorizationRequired &&
            !recovering &&
            pendingOffset == null &&
            remainingOutboundSteps == configuredTerminalSuffixSteps &&
            !terminalCommitAuthorized

    fun authorizeTerminalCommit(): Boolean {
        if (!awaitingTerminalCommitAuthorization || !terminalAimLockComplete) return false
        terminalCommitAuthorized = true
        return true
    }

    val canReplaceRemainingOutbound: Boolean
        get() = !recovering && remainingOutboundSteps > 0 && pendingOffset == null

    /**
     * Replanning is safe only between cadence waits and before the kinetic suffix is committed.
     * Once the suffix starts, changing its route would destroy the continuous MaxSpeed run-up.
     */
    val canReplaceRemainingApproach: Boolean
        get() = canReplaceRemainingOutbound &&
            remainingStepWaitTicks == 0 &&
            remainingOutboundSteps > configuredTerminalSuffixSteps

    /**
     * True only at a freshly confirmed attack endpoint, before any return edge enters the packet
     * pipeline. This is the only recovery phase where a defeated target may hand the session to a
     * new outbound route without invalidating the exact return history.
     */
    val canStartChainedOutbound: Boolean
        get() = recovering && chainedOutboundWindowOpen &&
            pendingOffset == null && committedOffset.lengthSqr() >= SPEAR_KILL_PACKET_SESSION_EPSILON

    val physicalReturnConfigured: Boolean
        get() = physicalReturnEnabled
}
