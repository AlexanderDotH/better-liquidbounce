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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.DirectPacketRoutePlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.PacketChainPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.SpearKillKineticDamageRequirements
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPacketSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.SpearKillRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.directRouteLabel
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.hasSpearKillScheduleDamageWindow
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.spearKillDirectRouteHitTicks
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarPacketRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.hasSpearKillKineticDamageRequirements
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.spearKillKineticDamageRequirements
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.createReplacementFallSafetyPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.replanVirtualFallSafety
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.canStartChainedOutbound
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.replaceRemainingOutbound
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.startChainedOutbound
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.installPacketChainPlan(plan: PacketChainPlan): Boolean {
    val fallSafetyPlan = createReplacementFallSafetyPlan(plan.outboundMovements) ?: return false
    val aStarPlan = plan.aStarPlan
    val terminalSuffixSteps = aStarPlan?.terminalSuffixCount
        ?: plan.terminalBurstSteps.coerceAtLeast(1)
    val install: (List<Vec3>, Int, Int, Int, Int, Boolean) -> Boolean =
        if (packetBootSession.canStartChainedOutbound) {
            packetBootSession::startChainedOutbound
        } else {
            packetBootSession::replaceRemainingOutbound
        }
    val installed = install(
        plan.outboundMovements,
        plan.strikeHoldTicks,
        plan.preStrikeHoldTicks,
        terminalSuffixSteps,
        plan.terminalBurstSteps,
        plan.terminalAuthorizationRequired,
    )
    if (installed) replanVirtualFallSafety(fallSafetyPlan)
    return installed
}

@Suppress("ReturnCount")
internal fun SpearKillModuleState.createPacketChainPlan(
    target: LivingEntity,
    routeOrigin: Vec3,
    sessionOrigin: Vec3,
    settings: SpearKillPacketSessionSettings,
): PacketChainPlan? {
    if (settings.routingMode == SpearKillRoutingMode.INSTANT && !settings.primedInstant) return null

    val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON)
        ?: return null
    val kineticRequirements = spearKillKineticDamageRequirements(kineticWeapon)
        ?: return null
    val damageUseDuration = kineticWeapon.computeDamageUseDuration()
    val directPlan = createDirectPacketRouteForMovedTarget(
        target,
        routeOrigin,
        sessionOrigin,
    )
    if (directPlan != null) {
        return createValidatedDirectPacketChainPlan(
            directPlan,
            kineticRequirements,
            settings,
            sessionOrigin,
            routeOrigin,
            damageUseDuration,
        )
    }
    return createFallbackAStarPacketChainPlan(target, routeOrigin, sessionOrigin, settings, damageUseDuration)
}

@Suppress("LongParameterList", "ReturnCount")
private fun SpearKillModuleState.createValidatedDirectPacketChainPlan(
    directPlan: DirectPacketRoutePlan,
    kineticRequirements: SpearKillKineticDamageRequirements,
    settings: SpearKillPacketSessionSettings,
    sessionOrigin: Vec3,
    routeOrigin: Vec3,
    damageUseDuration: Int,
): PacketChainPlan? {
    if (!hasSpearKillKineticDamageRequirements(directPlan, kineticRequirements)) return null
    if (!isServerAcceptedSpearKillDirectRoute(sessionOrigin, routeOrigin, directPlan.route, settings)) return null
    if (settings.primedInstant && !canAdmitPrimedChain(directPlan.route, settings)) return null
    return createDirectPacketChainPlan(directPlan.route, settings, damageUseDuration)
}

@Suppress("LongParameterList")
private fun SpearKillModuleState.createFallbackAStarPacketChainPlan(
    target: LivingEntity,
    routeOrigin: Vec3,
    sessionOrigin: Vec3,
    settings: SpearKillPacketSessionSettings,
    damageUseDuration: Int,
): PacketChainPlan? {
    val supportsAStar = settings.routingMode == SpearKillRoutingMode.A_STAR ||
        settings.routingMode == SpearKillRoutingMode.NETWORK_OPTIMIZED
    if (!supportsAStar) return null
    return createAStarPacketChainPlan(
        target,
        routeOrigin,
        sessionOrigin,
        settings,
        damageUseDuration,
    )
}

internal fun SpearKillModuleState.createDirectPacketChainPlan(
    route: SpearKillAStarPacketRoute,
    settings: SpearKillPacketSessionSettings,
    damageUseDuration: Int,
): PacketChainPlan? {
    val outboundTickCount = route.outboundTickCount
    val hitTicks = spearKillDirectRouteHitTicks(
        routingMode = settings.routingMode,
        outboundTickCount = outboundTickCount,
        stepWaitTicks = settings.stepWaitTicks,
        strikeHoldTicks = settings.strikeHoldTicks,
    )
    return PacketChainPlan(
        outboundMovements = route.outboundMovements,
        routeMode = settings.routingMode.directRouteLabel(),
        hitTicks = hitTicks,
        strikeHoldTicks = settings.strikeHoldTicks,
        terminalBurstSteps = route.terminalBurstSteps,
        preStrikeHoldTicks = if (settings.primedInstant) 0 else SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS,
        terminalAuthorizationRequired = !settings.primedInstant,
    ).takeIf {
        hasSpearKillScheduleDamageWindow(
            ticksUsingItem = player.ticksUsingItem,
            damageUseDuration = damageUseDuration,
            hitTick = hitTicks,
        )
    }
}
