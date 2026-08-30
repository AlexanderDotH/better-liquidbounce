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
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillRouteSession
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.world.phys.Vec3

/** Root-owned view of the delivery-confirmed packet session implemented below this package. */
internal interface SpearKillPacketSessionPort : RemoteKillRouteSession {
    val pendingFinalOutboundStep: Boolean
    val pendingTerminalBurstStart: Boolean
    val pendingLogicalOutboundCompletion: Boolean
    val pendingTerminalBurstMovement: Vec3?
    val pathHeading: Rotation?
    val holdingStrike: Boolean
    val holdingPreStrike: Boolean
    val holdingKineticBarrier: Boolean
    val terminalSuffixSteps: Int
    val awaitingTerminalCommitAuthorization: Boolean
    val terminalAimLockComplete: Boolean
    val canReplaceRemainingOutbound: Boolean
    val canReplaceRemainingApproach: Boolean
    val canStartChainedOutbound: Boolean
    val physicalReturnConfigured: Boolean

    fun authorizeTerminalCommit(): Boolean

    fun start(
        path: List<Vec3>,
        outboundSteps: Int = 0,
        strikeHoldTicks: Int = 0,
        stepWaitTicks: Int = 0,
        preStrikeHoldTicks: Int = 0,
        terminalSuffixSteps: Int = 1,
        terminalBurstSteps: Int = 0,
        requireTerminalAuthorization: Boolean = false,
    )

    fun startPhysicalReturn(
        path: List<Vec3>,
        outboundSteps: Int,
        strikeHoldTicks: Int = 0,
        stepWaitTicks: Int = 0,
        preStrikeHoldTicks: Int = 0,
        terminalSuffixSteps: Int = 1,
        terminalBurstSteps: Int = 0,
        requireTerminalAuthorization: Boolean = false,
    )

    fun replaceRemainingOutbound(
        outboundMovements: List<Vec3>,
        strikeHoldTicks: Int,
        preStrikeHoldTicks: Int = 0,
        terminalSuffixSteps: Int = 1,
        terminalBurstSteps: Int = 0,
        requireTerminalAuthorization: Boolean = false,
        completeReturnMovements: List<Vec3>? = null,
    ): Boolean

    fun startChainedOutbound(
        outboundMovements: List<Vec3>,
        strikeHoldTicks: Int,
        preStrikeHoldTicks: Int = 0,
        terminalSuffixSteps: Int = 1,
        terminalBurstSteps: Int = 0,
        requireTerminalAuthorization: Boolean = false,
    ): Boolean

    fun consumePhysicalPositionOffset(): Vec3?

    fun beginRecovery(maxSpeed: Double)

    fun beginPhysicalExactRecoveryFrom(
        authoritativeOffset: Vec3,
        recoveryMovements: List<Vec3>,
        stepWaitTicks: Int = 0,
    )

    fun beginRecoveryFrom(authoritativeOffset: Vec3, maxSpeed: Double)

    fun beginPhysicalRecoveryFrom(authoritativeOffset: Vec3, maxSpeed: Double)
}
