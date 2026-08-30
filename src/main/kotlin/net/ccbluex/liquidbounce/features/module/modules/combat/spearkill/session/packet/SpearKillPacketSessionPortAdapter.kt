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
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillRouteRequest
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillPacketSessionPort
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.world.phys.Vec3

internal class SpearKillPacketSessionPortAdapter(
    private val state: SpearKillPacketSessionState = SpearKillPacketSessionState(),
) : SpearKillPacketSessionPort {

    override val active: Boolean get() = state.active
    override val recovering: Boolean get() = state.recovering
    override val committedOffset: Vec3 get() = state.committedOffset
    override val virtualOffset: Vec3 get() = state.virtualOffset
    override val requiresDelivery: Boolean get() = state.requiresDelivery
    override val pendingOutboundStep: Boolean get() = state.pendingOutboundStep
    override val pendingMovement: Vec3? get() = state.pendingMovement
    override val pendingFinalOutboundStep: Boolean get() = state.pendingFinalOutboundStep
    override val pendingTerminalBurstStart: Boolean get() = state.pendingTerminalBurstStart
    override val pendingLogicalOutboundCompletion: Boolean get() = state.pendingLogicalOutboundCompletion
    override val pendingTerminalBurstMovement: Vec3? get() = state.pendingTerminalBurstMovement
    override val pathHeading: Rotation? get() = state.pathHeading
    override val holdingStrike: Boolean get() = state.holdingStrike
    override val holdingPreStrike: Boolean get() = state.holdingPreStrike
    override val holdingKineticBarrier: Boolean get() = state.holdingKineticBarrier
    override val terminalSuffixSteps: Int get() = state.terminalSuffixSteps
    override val awaitingTerminalCommitAuthorization: Boolean
        get() = state.awaitingTerminalCommitAuthorization
    override val terminalAimLockComplete: Boolean get() = state.terminalAimLockComplete
    override val canReplaceRemainingOutbound: Boolean get() = state.canReplaceRemainingOutbound
    override val canReplaceRemainingApproach: Boolean get() = state.canReplaceRemainingApproach
    override val canStartChainedOutbound: Boolean get() = state.canStartChainedOutbound
    override val physicalReturnConfigured: Boolean get() = state.physicalReturnConfigured

    override fun start(request: RemoteKillRouteRequest) = state.start(request)

    override fun prepareNextStep(): Vec3? = state.prepareNextStep()

    override fun confirmStep(delivered: Boolean) = state.confirmStep(delivered)

    override fun beginExactReturn() = state.beginExactReturn()

    override fun exactRecoveryMovementsFrom(authoritativeOffset: Vec3): List<Vec3>? =
        state.exactRecoveryMovementsFrom(authoritativeOffset)

    override fun beginPacketExactRecoveryFrom(
        authoritativeOffset: Vec3,
        recoveryMovements: List<Vec3>,
        stepWaitTicks: Int,
    ) = state.beginPacketExactRecoveryFrom(authoritativeOffset, recoveryMovements, stepWaitTicks)

    override fun clear() = state.clear()

    override fun authorizeTerminalCommit(): Boolean = state.authorizeTerminalCommit()

    override fun start(
        path: List<Vec3>,
        outboundSteps: Int,
        strikeHoldTicks: Int,
        stepWaitTicks: Int,
        preStrikeHoldTicks: Int,
        terminalSuffixSteps: Int,
        terminalBurstSteps: Int,
        requireTerminalAuthorization: Boolean,
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

    override fun startPhysicalReturn(
        path: List<Vec3>,
        outboundSteps: Int,
        strikeHoldTicks: Int,
        stepWaitTicks: Int,
        preStrikeHoldTicks: Int,
        terminalSuffixSteps: Int,
        terminalBurstSteps: Int,
        requireTerminalAuthorization: Boolean,
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

    override fun replaceRemainingOutbound(
        outboundMovements: List<Vec3>,
        strikeHoldTicks: Int,
        preStrikeHoldTicks: Int,
        terminalSuffixSteps: Int,
        terminalBurstSteps: Int,
        requireTerminalAuthorization: Boolean,
        completeReturnMovements: List<Vec3>?,
    ): Boolean = state.replaceRemainingOutbound(
        outboundMovements,
        strikeHoldTicks,
        preStrikeHoldTicks,
        terminalSuffixSteps,
        terminalBurstSteps,
        requireTerminalAuthorization,
        completeReturnMovements,
    )

    override fun startChainedOutbound(
        outboundMovements: List<Vec3>,
        strikeHoldTicks: Int,
        preStrikeHoldTicks: Int,
        terminalSuffixSteps: Int,
        terminalBurstSteps: Int,
        requireTerminalAuthorization: Boolean,
    ): Boolean = state.startChainedOutbound(
        outboundMovements,
        strikeHoldTicks,
        preStrikeHoldTicks,
        terminalSuffixSteps,
        terminalBurstSteps,
        requireTerminalAuthorization,
    )

    override fun consumePhysicalPositionOffset(): Vec3? = state.consumePhysicalPositionOffset()

    override fun beginRecovery(maxSpeed: Double) = state.beginRecovery(maxSpeed)

    override fun beginPhysicalExactRecoveryFrom(
        authoritativeOffset: Vec3,
        recoveryMovements: List<Vec3>,
        stepWaitTicks: Int,
    ) = state.beginPhysicalExactRecoveryFrom(authoritativeOffset, recoveryMovements, stepWaitTicks)

    override fun beginRecoveryFrom(authoritativeOffset: Vec3, maxSpeed: Double) =
        state.beginRecoveryFrom(authoritativeOffset, maxSpeed)

    override fun beginPhysicalRecoveryFrom(authoritativeOffset: Vec3, maxSpeed: Double) =
        state.beginPhysicalRecoveryFrom(authoritativeOffset, maxSpeed)
}
