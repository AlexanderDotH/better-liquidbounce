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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.policy

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.SPEAR_KILL_DAMAGE_EVIDENCE_WINDOW_TICKS
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillAStarSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillNetworkBudget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPacketSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.SpearKillPrimedInstantPacketType
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillPrimedInstantPriming
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.SpearKillRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.resolveSpearKillMovementTransport
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.SpearKillHighSpeedResearchFinalPacketType

internal data class SpearKillPacketSettingsPolicyInput(
    val configuredSpeed: Double,
    val configuredStepLimit: Double,
    val elytraActive: Boolean,
    val configuredStepWaitTicks: Int,
    val routingMode: SpearKillRoutingMode,
    val aStar: SpearKillAStarSessionSettings,
    val networkBudget: SpearKillNetworkBudget?,
    val configuredSetbackBackoffTicks: Int,
    val instantMaxPackets: Int,
    val primedStrategySelected: Boolean,
    val primingPacketType: SpearKillPrimedInstantPacketType,
    val primedResearchLog: Boolean,
)

internal fun resolveSpearKillPacketSessionSettings(
    input: SpearKillPacketSettingsPolicyInput,
): SpearKillPacketSessionSettings {
    val primedInstant = input.routingMode == SpearKillRoutingMode.INSTANT && input.primedStrategySelected

    return SpearKillPacketSessionSettings(
        transport = input.resolveMovementTransport(),
        stepWaitTicks = input.resolveStepWaitTicks(),
        routingMode = input.routingMode,
        aStar = input.aStar,
        damageEvidenceWindowTicks = input.networkBudget?.damageEvidenceWindowTicks
            ?: SPEAR_KILL_DAMAGE_EVIDENCE_WINDOW_TICKS,
        setbackBackoffTicks = input.networkBudget?.setbackBackoffTicks
            ?: input.configuredSetbackBackoffTicks,
        allowTerminalBurst = input.networkBudget?.allowTerminalBurst ?: true,
        instantMaxPackets = input.instantMaxPackets,
        primedInstant = primedInstant,
        priming = SpearKillPrimedInstantPriming.Auto,
        primingPacketType = input.primingPacketType,
        researchLog = primedInstant && input.primedResearchLog,
        finalPacketType = SpearKillHighSpeedResearchFinalPacketType.POSITION_ROTATION,
    )
}

private fun SpearKillPacketSettingsPolicyInput.resolveMovementTransport() = resolveSpearKillMovementTransport(
    configuredSpeed = minOf(configuredSpeed, networkBudget?.maxSpeed ?: Double.POSITIVE_INFINITY),
    configuredStepLimit = minOf(configuredStepLimit, networkBudget?.maxSpeed ?: Double.POSITIVE_INFINITY),
    elytraActive = elytraActive,
)

private fun SpearKillPacketSettingsPolicyInput.resolveStepWaitTicks(): Int =
    if (routingMode == SpearKillRoutingMode.INSTANT) 0 else networkBudget?.stepWaitTicks ?: configuredStepWaitTicks
