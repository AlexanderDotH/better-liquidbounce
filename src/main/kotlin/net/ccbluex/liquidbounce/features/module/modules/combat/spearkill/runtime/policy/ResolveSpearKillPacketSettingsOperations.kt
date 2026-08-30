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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.control.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillNetworkBudget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillNetworkObservation
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillNetworkSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPacketSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.SpearKillRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.isSpearKillElytraActive
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.packetRoutingMode
import net.ccbluex.liquidbounce.features.server.ServerObserver
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.entity.ping

internal fun SpearKillModuleState.resolveSpearKillPacketSettings(
    prepareElytra: Boolean = false,
): SpearKillPacketSessionSettings {
    val packet = movementConfiguration.packet
    val routingMode = packetRoutingMode
    val aStarSettings = resolveSpearKillAStarSessionSettings(routingMode)
    val networkConfiguration = packet.networkOptimized
    val networkBudget = if (routingMode == SpearKillRoutingMode.NETWORK_OPTIMIZED) {
        resolveSpearKillNetworkBudget(
            configuredMaxSpeed = networkConfiguration.maxSpeed.toDouble(),
            configuredMinimumStepWaitTicks = maxOf(packet.stepDelay, networkConfiguration.minimumStepDelay),
            configuredSetbackBackoffTicks = networkConfiguration.setbackBackoff,
        )
    } else {
        null
    }
    val configuredSpeed = movementConfiguration.targetSpeed.toDouble()
    val configuredStepLimit = packet.stepDistance.toDouble()
    if (prepareElytra) {
        requestSpearKillPacketFallFlight()
    }
    val primedStrategySelected = routingMode == SpearKillRoutingMode.INSTANT &&
        packet.instant.strategy.activeMode === packet.instant.primed
    return resolveSpearKillPacketSessionSettings(
        SpearKillPacketSettingsPolicyInput(
            configuredSpeed = configuredSpeed,
            configuredStepLimit = configuredStepLimit,
            elytraActive = isSpearKillElytraActive,
            configuredStepWaitTicks = packet.stepDelay,
            routingMode = routingMode,
            aStar = aStarSettings,
            networkBudget = networkBudget,
            configuredSetbackBackoffTicks = networkConfiguration.setbackBackoff,
            instantMaxPackets = packet.instant.maxPackets,
            primedStrategySelected = primedStrategySelected,
            primingPacketType = packet.instant.primed.primingPacketType,
            primedResearchLog = primedStrategySelected && packet.instant.primed.researchLog,
        ),
    )
}

private fun SpearKillModuleState.resolveSpearKillNetworkBudget(
    configuredMaxSpeed: Double,
    configuredMinimumStepWaitTicks: Int,
    configuredSetbackBackoffTicks: Int,
): SpearKillNetworkBudget = networkOptimizer.resolve(
    observation = SpearKillNetworkObservation(
        serverTps = ServerObserver.tps,
        pingMillis = player.ping,
    ),
    settings = SpearKillNetworkSettings(
        maxSpeed = configuredMaxSpeed,
        minimumStepWaitTicks = configuredMinimumStepWaitTicks,
        setbackBackoffTicks = configuredSetbackBackoffTicks,
    ),
)
