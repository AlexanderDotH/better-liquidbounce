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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillPacketBootSession
import net.minecraft.world.phys.Vec3

internal val SpearKillPacketBootSession.pendingFinalOutboundStep: Boolean
    get() = state.pendingFinalOutboundStep
internal val SpearKillPacketBootSession.pendingTerminalBurstStart: Boolean
    get() = state.pendingTerminalBurstStart
internal val SpearKillPacketBootSession.pendingLogicalOutboundCompletion: Boolean
    get() = state.pendingLogicalOutboundCompletion
internal val SpearKillPacketBootSession.pendingTerminalBurstMovement: Vec3?
    get() = state.pendingTerminalBurstMovement
internal val SpearKillPacketBootSession.pathHeading
    get() = state.pathHeading
internal val SpearKillPacketBootSession.holdingStrike: Boolean
    get() = state.holdingStrike
internal val SpearKillPacketBootSession.holdingPreStrike: Boolean
    get() = state.holdingPreStrike
internal val SpearKillPacketBootSession.holdingKineticBarrier: Boolean
    get() = state.holdingKineticBarrier
internal val SpearKillPacketBootSession.terminalSuffixSteps: Int
    get() = state.terminalSuffixSteps
internal val SpearKillPacketBootSession.awaitingTerminalCommitAuthorization: Boolean
    get() = state.awaitingTerminalCommitAuthorization
internal val SpearKillPacketBootSession.terminalAimLockComplete: Boolean
    get() = state.terminalAimLockComplete
internal val SpearKillPacketBootSession.canReplaceRemainingOutbound: Boolean
    get() = state.canReplaceRemainingOutbound
internal val SpearKillPacketBootSession.canReplaceRemainingApproach: Boolean
    get() = state.canReplaceRemainingApproach
internal val SpearKillPacketBootSession.canStartChainedOutbound: Boolean
    get() = state.canStartChainedOutbound
internal val SpearKillPacketBootSession.physicalReturnConfigured: Boolean
    get() = state.physicalReturnConfigured

internal fun SpearKillPacketBootSession.authorizeTerminalCommit(): Boolean =
    state.authorizeTerminalCommit()

internal fun SpearKillPacketBootSession.start(
    path: List<Vec3>,
    outboundSteps: Int = 0,
    strikeHoldTicks: Int = 0,
    stepWaitTicks: Int = 0,
    preStrikeHoldTicks: Int = 0,
    terminalSuffixSteps: Int = 1,
    terminalBurstSteps: Int = 0,
    requireTerminalAuthorization: Boolean = false,
) = state.start(
    path,
    outboundSteps,
    strikeHoldTicks,
    stepWaitTicks,
    preStrikeHoldTicks,
    terminalSuffixSteps,
    terminalBurstSteps,
    requireTerminalAuthorization,
)

internal fun SpearKillPacketBootSession.startPhysicalReturn(
    path: List<Vec3>,
    outboundSteps: Int,
    strikeHoldTicks: Int = 0,
    stepWaitTicks: Int = 0,
    preStrikeHoldTicks: Int = 0,
    terminalSuffixSteps: Int = 1,
    terminalBurstSteps: Int = 0,
    requireTerminalAuthorization: Boolean = false,
) = state.startPhysicalReturn(
    path,
    outboundSteps,
    strikeHoldTicks,
    stepWaitTicks,
    preStrikeHoldTicks,
    terminalSuffixSteps,
    terminalBurstSteps,
    requireTerminalAuthorization,
)

internal fun SpearKillPacketBootSession.replaceRemainingOutbound(
    outboundMovements: List<Vec3>,
    strikeHoldTicks: Int,
    preStrikeHoldTicks: Int = 0,
    terminalSuffixSteps: Int = 1,
    terminalBurstSteps: Int = 0,
    requireTerminalAuthorization: Boolean = false,
    completeReturnMovements: List<Vec3>? = null,
): Boolean = state.replaceRemainingOutbound(
    outboundMovements,
    strikeHoldTicks,
    preStrikeHoldTicks,
    terminalSuffixSteps,
    terminalBurstSteps,
    requireTerminalAuthorization,
    completeReturnMovements,
)

internal fun SpearKillPacketBootSession.startChainedOutbound(
    outboundMovements: List<Vec3>,
    strikeHoldTicks: Int,
    preStrikeHoldTicks: Int = 0,
    terminalSuffixSteps: Int = 1,
    terminalBurstSteps: Int = 0,
    requireTerminalAuthorization: Boolean = false,
): Boolean = state.startChainedOutbound(
    outboundMovements,
    strikeHoldTicks,
    preStrikeHoldTicks,
    terminalSuffixSteps,
    terminalBurstSteps,
    requireTerminalAuthorization,
)

internal fun SpearKillPacketBootSession.consumePhysicalPositionOffset(): Vec3? =
    state.consumePhysicalPositionOffset()

internal fun SpearKillPacketBootSession.beginRecovery(maxSpeed: Double) = state.beginRecovery(maxSpeed)

internal fun SpearKillPacketBootSession.beginPhysicalExactRecoveryFrom(
    authoritativeOffset: Vec3,
    recoveryMovements: List<Vec3>,
    stepWaitTicks: Int = 0,
) = state.beginPhysicalExactRecoveryFrom(authoritativeOffset, recoveryMovements, stepWaitTicks)

internal fun SpearKillPacketBootSession.beginRecoveryFrom(authoritativeOffset: Vec3, maxSpeed: Double) =
    state.beginRecoveryFrom(authoritativeOffset, maxSpeed)

internal fun SpearKillPacketBootSession.beginPhysicalRecoveryFrom(authoritativeOffset: Vec3, maxSpeed: Double) =
    state.beginPhysicalRecoveryFrom(authoritativeOffset, maxSpeed)
