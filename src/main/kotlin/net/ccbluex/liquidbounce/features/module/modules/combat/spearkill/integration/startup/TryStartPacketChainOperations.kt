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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.tick.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.PacketChainPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.PacketChainStartResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPacketSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.SpearKillTargetChainSelection
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.beginSpearKillAttempt
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.selectNearestReachableSpearKillChainTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.synchronizeSpearKillServerSneak
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.canReplaceRemainingOutbound
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.canStartChainedOutbound
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.physicalReturnConfigured
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.entity.lastPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.tryStartPacketChain(defeatedTarget: LivingEntity): PacketChainStartResult {
    if (!packetBootSession.canReplaceRemainingOutbound && !packetBootSession.canStartChainedOutbound) {
        return PacketChainStartResult.FAILED
    }

    val sessionOrigin = packetSessionOrigin ?: return PacketChainStartResult.FAILED
    val settings = packetSessionSettings ?: return PacketChainStartResult.FAILED
    val routeOrigin = sessionOrigin.add(packetBootSession.committedOffset)
    val chainAnchor = defeatedTarget.position()
    val inheritedTargetSource = attemptTracker.current?.targetSource
    val selection = createPacketChainSelection(defeatedTarget, routeOrigin, sessionOrigin, chainAnchor, settings)
        ?: return PacketChainStartResult.FAILED
    val plan = selection.route
    if (!installPacketChainPlan(plan)) return PacketChainStartResult.FAILED
    handoffPacketChainRoute(selection, routeOrigin, settings)
    commitPacketChainState(defeatedTarget, selection, routeOrigin, inheritedTargetSource)
    synchronizeSpearKillServerSneak()
    return PacketChainStartResult.STARTED
}

@Suppress("LongParameterList")
private fun SpearKillModuleState.createPacketChainSelection(
    defeatedTarget: LivingEntity,
    routeOrigin: Vec3,
    sessionOrigin: Vec3,
    chainAnchor: Vec3,
    settings: SpearKillPacketSessionSettings,
): SpearKillTargetChainSelection<LivingEntity, PacketChainPlan>? = selectNearestReachableSpearKillChainTarget(
        candidates = findSpearKillChainCandidates(defeatedTarget, chainAnchor),
        distanceSquared = { candidate -> chainAnchor.distanceToSqr(candidate.position()) },
        createRoute = { candidate ->
            createPacketChainPlan(candidate, routeOrigin, sessionOrigin, settings)
        },
    )

private fun SpearKillModuleState.handoffPacketChainRoute(
    selection: SpearKillTargetChainSelection<LivingEntity, PacketChainPlan>,
    routeOrigin: Vec3,
    settings: SpearKillPacketSessionSettings,
) {
    val plan = selection.route
    remoteKillRouteEngine.handoff(
        selection.target,
        RemoteKillRouteRequest(
            origin = routeOrigin,
            outboundMovements = plan.outboundMovements,
            strikeHoldTicks = plan.strikeHoldTicks,
            stepWaitTicks = settings.stepWaitTicks,
            physicalReturn = packetBootSession.physicalReturnConfigured,
            preStrikeHoldTicks = plan.preStrikeHoldTicks,
            terminalSuffixSteps = plan.aStarPlan?.terminalSuffixCount
                ?: plan.terminalBurstSteps.coerceAtLeast(1),
            terminalBurstSteps = plan.terminalBurstSteps,
            requireTerminalAuthorization = plan.terminalAuthorizationRequired,
        ),
    )
}

private fun SpearKillModuleState.commitPacketChainState(
    defeatedTarget: LivingEntity,
    selection: SpearKillTargetChainSelection<LivingEntity, PacketChainPlan>,
    routeOrigin: Vec3,
    inheritedTargetSource: String?,
) {
    val plan = selection.route
    val aStarPlan = plan.aStarPlan
    packetAStarAttackActive = aStarPlan != null
    directTerminalReplanInstalled = false
    plannedAStarApproach = aStarPlan?.approach
    plannedAStarRenderPath = aStarPlan?.renderPath.orEmpty()
    plannedAStarTargetPosition = aStarPlan?.targetPosition ?: selection.target.position()
    plannedAStarTargetVelocity = aStarPlan?.targetVelocity
        ?: selection.target.position().subtract(selection.target.lastPos)
    aStarPlanTick = player.tickCount
    packetRecoveryStallTicks = 0
    physicalReturnPositioner.clear()
    returnRecoveryTracker.observeCombatPosition(routeOrigin)
    handoffSpearKillRouteTarget(defeatedTarget, selection.target)
    beginSpearKillAttempt(
        target = selection.target,
        routeMode = "${plan.routeMode} Chain",
        outboundSteps = plan.outboundMovements.size,
        hitTicks = plan.hitTicks,
        terminalAuthorizationRequired = plan.terminalAuthorizationRequired,
        targetSourceOverride = inheritedTargetSource,
    )
}
