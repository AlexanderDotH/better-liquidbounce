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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.PacketChainPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_PRIMED_MAX_PACKETS_PER_MOVEMENT
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPacketSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.aStarRouteLabel
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.planning.calculateSpearKillPrimedInstantSessionBudget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.hasSpearKillScheduleDamageWindow
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarPacketRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.policy.conservativePrimedBudgetMovementProfile
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.exactRecoveryMovementsFrom
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.canAdmitPrimedChain(
    route: SpearKillAStarPacketRoute,
    settings: SpearKillPacketSessionSettings,
): Boolean {
    val chainBudget = calculateSpearKillPrimedInstantSessionBudget(
        route = route,
        priming = settings.priming,
        movementProfile = conservativePrimedBudgetMovementProfile(),
        maxPackets = settings.instantMaxPackets,
    ) ?: return false
    val existingReturnSteps = packetBootSession.exactRecoveryMovementsFrom(
        packetBootSession.committedOffset,
    )?.size ?: return false
    val conservativeExistingReturnBudget = existingReturnSteps * SPEAR_KILL_PRIMED_MAX_PACKETS_PER_MOVEMENT
    return primedSessionPacketsDelivered + chainBudget.totalPackets + conservativeExistingReturnBudget <=
        settings.instantMaxPackets
}

@Suppress("ReturnCount")
internal fun SpearKillModuleState.createAStarPacketChainPlan(
    target: LivingEntity,
    routeOrigin: Vec3,
    sessionOrigin: Vec3,
    settings: SpearKillPacketSessionSettings,
    damageUseDuration: Int,
): PacketChainPlan? {
    val aStarPlan = calculateAStarAttackPlan(
        target = target,
        routeOrigin = routeOrigin,
        sessionOrigin = sessionOrigin,
        settings = settings,
    ) ?: return null
    if (!hasSpearKillScheduleDamageWindow(
            ticksUsingItem = player.ticksUsingItem,
            damageUseDuration = damageUseDuration,
            hitTick = aStarPlan.schedule.hitTick,
        )
    ) {
        return null
    }
    return PacketChainPlan(
        outboundMovements = aStarPlan.packetRoute.outboundMovements,
        routeMode = settings.routingMode.aStarRouteLabel(),
        hitTicks = aStarPlan.schedule.hitTick,
        strikeHoldTicks = settings.strikeHoldTicks,
        preStrikeHoldTicks = aStarPlan.preStrikeHoldTicks,
        terminalAuthorizationRequired = true,
        aStarPlan = aStarPlan,
    )
}
