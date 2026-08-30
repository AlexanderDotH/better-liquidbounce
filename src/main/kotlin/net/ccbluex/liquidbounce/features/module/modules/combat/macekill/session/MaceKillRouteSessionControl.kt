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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.session


import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.MaceKillRouteSession
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillRouteSession
import net.minecraft.world.phys.Vec3

internal interface MaceKillRouteSessionControl {
    val remoteSession: RemoteKillRouteSession
    val canReplaceRemainingOutbound: Boolean
    val canReplaceRemainingApproach: Boolean
    val canStartChainedOutbound: Boolean

    fun replaceRemainingOutbound(
        outboundMovements: List<Vec3>,
        strikeHoldTicks: Int,
        preStrikeHoldTicks: Int,
        terminalSuffixSteps: Int,
        terminalBurstSteps: Int,
        requireTerminalAuthorization: Boolean,
        completeReturnMovements: List<Vec3>?,
    ): Boolean

    fun startChainedOutbound(
        outboundMovements: List<Vec3>,
        strikeHoldTicks: Int,
        preStrikeHoldTicks: Int,
        terminalSuffixSteps: Int,
        terminalBurstSteps: Int,
        requireTerminalAuthorization: Boolean,
    ): Boolean
}

internal fun MaceKillRouteSessionControl.asMaceKillRouteSession(): MaceKillRouteSession =
    object : MaceKillRouteSession, RemoteKillRouteSession by remoteSession {
        override val canReplaceRemainingOutbound: Boolean
            get() = this@asMaceKillRouteSession.canReplaceRemainingOutbound

        override val canReplaceRemainingApproach: Boolean
            get() = this@asMaceKillRouteSession.canReplaceRemainingApproach

        override val canStartChainedOutbound: Boolean
            get() = this@asMaceKillRouteSession.canStartChainedOutbound

        override fun replaceRemainingOutbound(
            outboundMovements: List<Vec3>,
            strikeHoldTicks: Int,
            preStrikeHoldTicks: Int,
            terminalSuffixSteps: Int,
            terminalBurstSteps: Int,
            requireTerminalAuthorization: Boolean,
            completeReturnMovements: List<Vec3>?,
        ): Boolean = this@asMaceKillRouteSession.replaceRemainingOutbound(
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
        ): Boolean = this@asMaceKillRouteSession.startChainedOutbound(
            outboundMovements,
            strikeHoldTicks,
            preStrikeHoldTicks,
            terminalSuffixSteps,
            terminalBurstSteps,
            requireTerminalAuthorization,
        )
    }
