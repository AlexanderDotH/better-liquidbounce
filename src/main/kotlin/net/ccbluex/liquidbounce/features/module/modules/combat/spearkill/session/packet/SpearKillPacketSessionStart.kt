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



import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.REMOTE_KILL_ROUTE_MAX_STEP_WAIT_TICKS
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillRouteRequest
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS
import net.minecraft.world.phys.Vec3

internal fun SpearKillPacketSessionState.start(request: RemoteKillRouteRequest) {
    if (request.physicalReturn) {
        startPhysicalReturn(
            path = request.roundTripMovements,
            outboundSteps = request.outboundMovements.size,
            strikeHoldTicks = request.strikeHoldTicks,
            stepWaitTicks = request.stepWaitTicks,
            preStrikeHoldTicks = request.preStrikeHoldTicks,
            terminalSuffixSteps = request.terminalSuffixSteps,
            terminalBurstSteps = request.terminalBurstSteps,
            requireTerminalAuthorization = request.requireTerminalAuthorization,
        )
        return
    }

    start(
        path = request.roundTripMovements,
        outboundSteps = request.outboundMovements.size,
        strikeHoldTicks = request.strikeHoldTicks,
        stepWaitTicks = request.stepWaitTicks,
        preStrikeHoldTicks = request.preStrikeHoldTicks,
        terminalSuffixSteps = request.terminalSuffixSteps,
        terminalBurstSteps = request.terminalBurstSteps,
        requireTerminalAuthorization = request.requireTerminalAuthorization,
    )
}

internal fun SpearKillPacketSessionState.start(
    path: List<Vec3>,
    outboundSteps: Int = 0,
    strikeHoldTicks: Int = 0,
    stepWaitTicks: Int = 0,
    preStrikeHoldTicks: Int = 0,
    terminalSuffixSteps: Int = 1,
    terminalBurstSteps: Int = 0,
    requireTerminalAuthorization: Boolean = false,
) = startInternal(
    path = path,
    outboundSteps = outboundSteps,
    strikeHoldTicks = strikeHoldTicks,
    stepWaitTicks = stepWaitTicks,
    preStrikeHoldTicks = preStrikeHoldTicks,
    terminalSuffixSteps = terminalSuffixSteps,
    terminalBurstSteps = terminalBurstSteps,
    requireTerminalAuthorization = requireTerminalAuthorization,
    physicalReturn = false,
)

internal fun SpearKillPacketSessionState.startPhysicalReturn(
    path: List<Vec3>,
    outboundSteps: Int,
    strikeHoldTicks: Int = 0,
    stepWaitTicks: Int = 0,
    preStrikeHoldTicks: Int = 0,
    terminalSuffixSteps: Int = 1,
    terminalBurstSteps: Int = 0,
    requireTerminalAuthorization: Boolean = false,
) = startInternal(
    path = path,
    outboundSteps = outboundSteps,
    strikeHoldTicks = strikeHoldTicks,
    stepWaitTicks = stepWaitTicks,
    preStrikeHoldTicks = preStrikeHoldTicks,
    terminalSuffixSteps = terminalSuffixSteps,
    terminalBurstSteps = terminalBurstSteps,
    requireTerminalAuthorization = requireTerminalAuthorization,
    physicalReturn = true,
)


internal fun SpearKillPacketSessionState.startInternal(
    path: List<Vec3>,
    outboundSteps: Int,
    strikeHoldTicks: Int,
    stepWaitTicks: Int,
    preStrikeHoldTicks: Int,
    terminalSuffixSteps: Int,
    terminalBurstSteps: Int,
    requireTerminalAuthorization: Boolean,
    physicalReturn: Boolean,
) {
    val configuration = PacketSessionStartConfiguration(
        outboundSteps = outboundSteps,
        strikeHoldTicks = strikeHoldTicks,
        stepWaitTicks = stepWaitTicks,
        preStrikeHoldTicks = preStrikeHoldTicks,
        terminalSuffixSteps = terminalSuffixSteps,
        terminalBurstSteps = terminalBurstSteps,
        requireTerminalAuthorization = requireTerminalAuthorization,
        physicalReturn = physicalReturn,
    )
    validateStartStateAndHold(configuration)
    validateTerminalAndPath(path, configuration)
    movements.addAll(path)
    configureStartedSession(configuration)
}

private data class PacketSessionStartConfiguration(
    val outboundSteps: Int,
    val strikeHoldTicks: Int,
    val stepWaitTicks: Int,
    val preStrikeHoldTicks: Int,
    val terminalSuffixSteps: Int,
    val terminalBurstSteps: Int,
    val requireTerminalAuthorization: Boolean,
    val physicalReturn: Boolean,
)

private fun SpearKillPacketSessionState.validateStartStateAndHold(
    configuration: PacketSessionStartConfiguration,
) {
    check(!active && committedOffset.lengthSqr() < SPEAR_KILL_PACKET_SESSION_EPSILON) { "A PacketBoot session is already active" }
    require(configuration.outboundSteps >= 0) { "Outbound step count must not be negative" }
    require(configuration.strikeHoldTicks >= 0) { "Strike hold duration must not be negative" }
    require(configuration.preStrikeHoldTicks in 0..SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS) {
        "Pre-strike hold may contain only the one terminal aim-lock tick"
    }
    require(configuration.terminalSuffixSteps >= 1) { "Terminal suffix step count must be positive" }
}

private fun SpearKillPacketSessionState.validateTerminalAndPath(
    path: List<Vec3>,
    configuration: PacketSessionStartConfiguration,
) {
    require(!configuration.requireTerminalAuthorization || configuration.preStrikeHoldTicks >= 1) {
        "Terminal authorization requires at least one pre-strike aim-lock tick"
    }
    require(configuration.outboundSteps == 0 || configuration.terminalSuffixSteps <= configuration.outboundSteps) {
        "Terminal suffix must not exceed outbound steps"
    }
    require(hasValidTerminalBurst(configuration.terminalBurstSteps, configuration.terminalSuffixSteps)) {
        "Terminal burst must be disabled or contain at least two terminal suffix steps"
    }
    require(configuration.terminalBurstSteps == 0 || configuration.outboundSteps > 0) {
        "Terminal burst requires outbound movement"
    }
    require(configuration.stepWaitTicks in 0..REMOTE_KILL_ROUTE_MAX_STEP_WAIT_TICKS) {
        "Step wait duration is outside the shared route range"
    }
    require(configuration.outboundSteps <= path.count { it.lengthSqr() >= SPEAR_KILL_PACKET_SESSION_EPSILON }) {
        "Outbound step count must not exceed movement count"
    }
}

private fun SpearKillPacketSessionState.configureStartedSession(
    configuration: PacketSessionStartConfiguration,
) {
    committedMovements.clear()
    remainingOutboundSteps = configuration.outboundSteps
    remainingPreStrikeHoldTicks = 0
    remainingStepWaitTicks = 0
    holdingStrikeThisTick = false
    holdingPreStrikeThisTick = false
    configuredStrikeHoldTicks = configuration.strikeHoldTicks
    configuredPreStrikeHoldTicks = configuration.preStrikeHoldTicks
    configuredTerminalSuffixSteps = configuration.terminalSuffixSteps
    configuredTerminalBurstSteps = configuration.terminalBurstSteps
    configuredTerminalAuthorizationRequired = configuration.requireTerminalAuthorization
    preStrikeHoldPending = configuration.outboundSteps > 0 && configuration.preStrikeHoldTicks > 0
    terminalAimLockComplete = !configuration.requireTerminalAuthorization
    terminalCommitAuthorized = !configuration.requireTerminalAuthorization
    configuredStepWaitTicks = configuration.stepWaitTicks
    physicalReturnEnabled = configuration.physicalReturn
    physicalReturnStarted = false
    chainedOutboundWindowOpen = false
    lastDeliveredMovement = null
    recovering = false
}

private fun Vec3.isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
