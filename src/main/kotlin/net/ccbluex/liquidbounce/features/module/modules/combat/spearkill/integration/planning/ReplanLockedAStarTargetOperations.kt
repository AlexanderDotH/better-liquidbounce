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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.terminal.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillRouteRequest
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.AStarAttackPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.PacketFollowTermination
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPacketSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillPacketRouteReplanResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.SpearKillServerFallSafetyPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.hasSpearKillScheduleDamageWindow
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.selectUsableSpearKillAStarReplan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.refreshReplannedPacketAttempt
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.createReplacementFallSafetyPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.replanVirtualFallSafety
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.terminatePacketFollow
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.physicalReturnConfigured
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.replaceRemainingOutbound
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.replanLockedAStarTarget(
    target: LivingEntity,
    routeOrigin: Vec3,
    sessionOrigin: Vec3,
): SpearKillPacketRouteReplanResult {
    val settings = packetSessionSettings ?: return SpearKillPacketRouteReplanResult.BLOCKED
    val plan = predictUsableLockedAStarReplan(target, routeOrigin, sessionOrigin, settings)
    if (plan == null) {
        // Transient prediction misses should not destroy an already safe route.
        aStarPlanTick = player.tickCount
        return SpearKillPacketRouteReplanResult.TRANSIENT_FAILURE
    }
    val fallSafetyPlan = createReplacementFallSafetyPlan(plan.packetRoute.outboundMovements)
        ?: return SpearKillPacketRouteReplanResult.BLOCKED
    if (!replaceRemainingLockedAStarOutbound(plan, settings)) {
        return SpearKillPacketRouteReplanResult.TRANSIENT_FAILURE
    }
    installLockedAStarTargetReplan(target, routeOrigin, plan, settings, fallSafetyPlan)
    return SpearKillPacketRouteReplanResult.INSTALLED
}

private fun SpearKillModuleState.predictUsableLockedAStarReplan(
    target: LivingEntity,
    routeOrigin: Vec3,
    sessionOrigin: Vec3,
    settings: SpearKillPacketSessionSettings,
): AStarAttackPlan? = selectUsableSpearKillAStarReplan(
    plan = calculateAStarAttackPlan(target, routeOrigin, sessionOrigin, settings),
    damageUseDuration = player.useItem.get(DataComponents.KINETIC_WEAPON)?.computeDamageUseDuration(),
) { candidate, damageUseDuration ->
    hasSpearKillScheduleDamageWindow(player.ticksUsingItem, damageUseDuration, candidate.schedule.hitTick)
}

private fun SpearKillModuleState.replaceRemainingLockedAStarOutbound(
    plan: AStarAttackPlan,
    settings: SpearKillPacketSessionSettings,
): Boolean = packetBootSession.replaceRemainingOutbound(
    outboundMovements = plan.packetRoute.outboundMovements,
    strikeHoldTicks = settings.strikeHoldTicks,
    preStrikeHoldTicks = plan.preStrikeHoldTicks,
    terminalSuffixSteps = plan.terminalSuffixCount,
    requireTerminalAuthorization = true,
)

private fun SpearKillModuleState.installLockedAStarTargetReplan(
    target: LivingEntity,
    routeOrigin: Vec3,
    plan: AStarAttackPlan,
    settings: SpearKillPacketSessionSettings,
    fallSafetyPlan: SpearKillServerFallSafetyPlan,
) {
    remoteKillRouteEngine.handoff(
        target,
        RemoteKillRouteRequest(
            origin = routeOrigin,
            outboundMovements = plan.packetRoute.outboundMovements,
            strikeHoldTicks = settings.strikeHoldTicks,
            stepWaitTicks = settings.stepWaitTicks,
            physicalReturn = packetBootSession.physicalReturnConfigured,
            preStrikeHoldTicks = plan.preStrikeHoldTicks,
            terminalSuffixSteps = plan.terminalSuffixCount,
            requireTerminalAuthorization = true,
        ),
    )
    replanVirtualFallSafety(fallSafetyPlan)
    plannedAStarRenderPath = plan.renderPath
    plannedAStarApproach = plan.approach
    plannedAStarTargetPosition = plan.targetPosition
    plannedAStarTargetVelocity = plan.targetVelocity
    aStarPlanTick = player.tickCount
    refreshReplannedPacketAttempt(
        target = target,
        outboundSteps = plan.packetRoute.outboundMovements.size,
        hitTicks = plan.schedule.hitTick,
        terminalAuthorizationRequired = true,
    )
}

internal fun SpearKillModuleState.replanLockedDirectPacketTarget(
    target: LivingEntity,
    routeOrigin: Vec3,
    sessionOrigin: Vec3,
) {
    when (installReplannedDirectPacketRoute(target, routeOrigin, sessionOrigin)) {
        SpearKillPacketRouteReplanResult.INSTALLED,
        SpearKillPacketRouteReplanResult.TRANSIENT_FAILURE,
        -> Unit
        SpearKillPacketRouteReplanResult.BLOCKED ->
            terminatePacketFollow(target, PacketFollowTermination.BLOCKED)
    }
}
