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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning.*
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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillRouteRequest
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.DirectPacketRoutePlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillPacketRouteReplanResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPacketSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.SpearKillServerFallSafetyPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.hasSpearKillScheduleDamageWindow
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.spearKillDirectRouteHitTicks
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.activePacketStepWaitTicks
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarPacketRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.hasSpearKillKineticDamageRequirements
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.refreshReplannedPacketAttempt
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.spearKillKineticDamageRequirements
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.createReplacementFallSafetyPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.replanVirtualFallSafety
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.physicalReturnConfigured
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.replaceRemainingOutbound
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

private sealed interface SpearKillDirectReplanPreparation {
    data class Ready(val context: SpearKillDirectReplanContext) : SpearKillDirectReplanPreparation
    data class Rejected(val result: SpearKillPacketRouteReplanResult) : SpearKillDirectReplanPreparation
}

private data class SpearKillDirectReplanContext(
    val settings: SpearKillPacketSessionSettings,
    val plan: DirectPacketRoutePlan,
    val hitTicks: Int,
    val fallSafetyPlan: SpearKillServerFallSafetyPlan,
) {
    val route: SpearKillAStarPacketRoute
        get() = plan.route

    val preStrikeHoldTicks: Int
        get() = if (settings.primedInstant) 0 else SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS

    val terminalSuffixSteps: Int
        get() = if (settings.primedInstant) 1 else route.terminalBurstSteps.coerceAtLeast(1)
}

internal fun SpearKillModuleState.installReplannedDirectPacketRoute(
    target: LivingEntity,
    routeOrigin: Vec3,
    sessionOrigin: Vec3,
): SpearKillPacketRouteReplanResult {
    val preparation = prepareReplannedDirectPacketRoute(target, routeOrigin, sessionOrigin)
    val context = when (preparation) {
        is SpearKillDirectReplanPreparation.Ready -> preparation.context
        is SpearKillDirectReplanPreparation.Rejected -> return preparation.result
    }
    if (!replaceReplannedDirectOutbound(context)) return SpearKillPacketRouteReplanResult.TRANSIENT_FAILURE
    handoffReplannedDirectRoute(target, routeOrigin, context)
    commitReplannedDirectRoute(target, context)
    return SpearKillPacketRouteReplanResult.INSTALLED
}

@Suppress("ReturnCount")
private fun SpearKillModuleState.prepareReplannedDirectPacketRoute(
    target: LivingEntity,
    routeOrigin: Vec3,
    sessionOrigin: Vec3,
): SpearKillDirectReplanPreparation {
    val settings = packetSessionSettings ?: return SpearKillPacketRouteReplanResult.BLOCKED
        .asRejectedSpearKillReplan()
    val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON)
        ?: return SpearKillPacketRouteReplanResult.TRANSIENT_FAILURE.asRejectedSpearKillReplan()
    val kineticRequirements = spearKillKineticDamageRequirements(kineticWeapon)
        ?: return SpearKillPacketRouteReplanResult.BLOCKED.asRejectedSpearKillReplan()
    val plan = createDirectPacketRouteForMovedTarget(
        target,
        routeOrigin,
        sessionOrigin,
    ) ?: return SpearKillPacketRouteReplanResult.BLOCKED.asRejectedSpearKillReplan()
    if (!hasSpearKillKineticDamageRequirements(plan, kineticRequirements)) {
        return SpearKillPacketRouteReplanResult.BLOCKED.asRejectedSpearKillReplan()
    }
    val route = plan.route
    val hitTicks = resolveReplannedDirectPacketHitTicks(
        route,
        settings,
        sessionOrigin,
        routeOrigin,
    )
        ?: return SpearKillPacketRouteReplanResult.BLOCKED.asRejectedSpearKillReplan()
    val damageUseDuration = kineticWeapon.computeDamageUseDuration()
    if (!hasSpearKillScheduleDamageWindow(player.ticksUsingItem, damageUseDuration, hitTicks)) {
        return SpearKillPacketRouteReplanResult.TRANSIENT_FAILURE.asRejectedSpearKillReplan()
    }
    val fallSafetyPlan = createReplacementFallSafetyPlan(route.outboundMovements)
        ?: return SpearKillPacketRouteReplanResult.BLOCKED.asRejectedSpearKillReplan()
    return SpearKillDirectReplanPreparation.Ready(
        SpearKillDirectReplanContext(settings, plan, hitTicks, fallSafetyPlan),
    )
}

private fun SpearKillModuleState.replaceReplannedDirectOutbound(context: SpearKillDirectReplanContext): Boolean =
    packetBootSession.replaceRemainingOutbound(
        context.route.outboundMovements,
        strikeHoldTicks = context.settings.strikeHoldTicks,
        preStrikeHoldTicks = context.preStrikeHoldTicks,
        terminalSuffixSteps = context.terminalSuffixSteps,
        terminalBurstSteps = context.route.terminalBurstSteps,
        requireTerminalAuthorization = !context.settings.primedInstant,
    )

private fun SpearKillModuleState.handoffReplannedDirectRoute(
    target: LivingEntity,
    routeOrigin: Vec3,
    context: SpearKillDirectReplanContext,
) {
    val settings = context.settings
    val route = context.route
    remoteKillRouteEngine.handoff(
        target,
        RemoteKillRouteRequest(
            origin = routeOrigin,
            outboundMovements = route.outboundMovements,
            strikeHoldTicks = settings.strikeHoldTicks,
            stepWaitTicks = settings.stepWaitTicks,
            physicalReturn = packetBootSession.physicalReturnConfigured,
            preStrikeHoldTicks = context.preStrikeHoldTicks,
            terminalSuffixSteps = context.terminalSuffixSteps,
            terminalBurstSteps = route.terminalBurstSteps,
            requireTerminalAuthorization = !settings.primedInstant,
        ),
    )
}

private fun SpearKillModuleState.commitReplannedDirectRoute(
    target: LivingEntity,
    context: SpearKillDirectReplanContext,
) {
    val route = context.route
    replanVirtualFallSafety(context.fallSafetyPlan)
    plannedAStarTargetPosition = context.plan.targetSnapshot.observedPosition
    plannedAStarTargetVelocity = context.plan.targetSnapshot.velocity
    aStarPlanTick = player.tickCount
    refreshReplannedPacketAttempt(
        target = target,
        outboundSteps = route.outboundMovements.size,
        hitTicks = context.hitTicks,
        terminalAuthorizationRequired = !context.settings.primedInstant,
    )
}

private fun SpearKillPacketRouteReplanResult.asRejectedSpearKillReplan() =
    SpearKillDirectReplanPreparation.Rejected(this)

internal fun SpearKillModuleState.resolveReplannedDirectPacketHitTicks(
    route: SpearKillAStarPacketRoute,
    settings: SpearKillPacketSessionSettings,
    sessionOrigin: Vec3,
    routeOrigin: Vec3,
): Int? {
    if (!isServerAcceptedSpearKillDirectRoute(sessionOrigin, routeOrigin, route, settings)) return null
    val outboundTickCount = route.outboundTickCount
    return spearKillDirectRouteHitTicks(
        routingMode = settings.routingMode,
        outboundTickCount = outboundTickCount,
        stepWaitTicks = activePacketStepWaitTicks,
        strikeHoldTicks = settings.strikeHoldTicks,
    )
}
