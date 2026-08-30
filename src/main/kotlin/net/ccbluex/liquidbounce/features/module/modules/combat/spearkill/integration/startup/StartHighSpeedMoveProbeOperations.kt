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
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillMovementOwnership
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_FALL_SAFETY_OFFSET_EPSILON_SQUARED
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_HIGH_SPEED_MAX_DISTANCE
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_HIGH_SPEED_MIN_DISTANCE
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillAttackStartResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillInstantPacketBurst
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPacketSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillPrimedInstantMovementProfile
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.buildSpearKillInstantDirectPacketRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.buildSpearKillInstantPacketBurst
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.calculateSpearKillPrimedInstantSessionBudget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.startSpearKillInstantPacketSession
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.holdingSpear
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.isUsingSpear
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarPacketRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.SpearKillHighSpeedResearchProbeRequest
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.SpearKillHighSpeedResearchProbeStartResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.beginSpearKillSpeedSession
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.policy.conservativePrimedBudgetMovementProfile
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.isSpearKillPrimedEndpointFree
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.clearVirtualMovementState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.resetSpearKillSpeedSession
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.core.component.DataComponents
import net.minecraft.world.phys.Vec3

private data class SpearKillHighSpeedMovePreparation(
    val origin: Vec3,
    val route: SpearKillAStarPacketRoute,
    val burst: SpearKillInstantPacketBurst,
)

@Suppress("ReturnCount")
internal fun SpearKillModuleState.startHighSpeedMoveProbe(
    request: SpearKillHighSpeedResearchProbeRequest.Move,
    settings: SpearKillPacketSessionSettings,
): SpearKillHighSpeedResearchProbeStartResult {
    val direction = validHighSpeedMoveDirection(request)
        ?: return SpearKillHighSpeedResearchProbeStartResult.INVALID_CONTEXT
    val preparation = prepareHighSpeedMoveProbe(request, settings, direction)
        ?: return SpearKillHighSpeedResearchProbeStartResult.ROUTE_REJECTED
    val movementLease = RemoteKillMovementOwnership.tryAcquire("SpearKillResearch")
        ?: return SpearKillHighSpeedResearchProbeStartResult.ACTIVE_SESSION
    if (!beginVirtualFallSafety(preparation.route.outboundMovements, preparation.origin)) {
        movementLease.close()
        return SpearKillHighSpeedResearchProbeStartResult.ROUTE_REJECTED
    }
    try {
        activateHighSpeedMoveProbe(preparation, settings, movementLease)
        return SpearKillHighSpeedResearchProbeStartResult.STARTED
    } catch (throwable: Throwable) {
        movementLease.close()
        clearVirtualMovementState()
        throw throwable
    }
}

private fun SpearKillModuleState.validHighSpeedMoveDirection(
    request: SpearKillHighSpeedResearchProbeRequest.Move,
): Vec3? {
    if (!request.distance.isFinite() || request.distance !in
        SPEAR_KILL_HIGH_SPEED_MIN_DISTANCE..SPEAR_KILL_HIGH_SPEED_MAX_DISTANCE
    ) {
        return null
    }
    return player.lookAngle.normalize().takeIf {
        it.lengthSqr() >= SPEAR_KILL_FALL_SAFETY_OFFSET_EPSILON_SQUARED
    }
}

private fun SpearKillModuleState.prepareHighSpeedMoveProbe(
    request: SpearKillHighSpeedResearchProbeRequest.Move,
    settings: SpearKillPacketSessionSettings,
    direction: Vec3,
): SpearKillHighSpeedMovePreparation? {
    val origin = player.position()
    val destination = origin.add(direction.scale(request.distance))
    val route = buildSpearKillInstantDirectPacketRoute(
        origin,
        destination,
        isEndpointFree = { position -> isSpearKillPrimedEndpointFree(origin, position) },
    ) ?: return null
    val burst = buildSpearKillInstantPacketBurst(route, settings.instantMaxPackets) ?: return null
    if (calculateSpearKillPrimedInstantSessionBudget(
        route,
        settings.priming,
        conservativePrimedBudgetMovementProfile(),
        settings.instantMaxPackets,
    ) == null) {
        return null
    }
    return SpearKillHighSpeedMovePreparation(origin, route, burst)
}

private fun SpearKillModuleState.activateHighSpeedMoveProbe(
    preparation: SpearKillHighSpeedMovePreparation,
    settings: SpearKillPacketSessionSettings,
    movementLease: RemoteKillMovementOwnership.Lease,
) {
    activeMovementTransport = settings.transport
    beginSpearKillSpeedSession()
    packetSessionSettings = settings
    packetAStarAttackActive = false
    clearAStarRenderPath()
    clearAStarTargetLock()
    packetSessionOrigin = preparation.origin
    physicalReturnPositioner.clear()
    returnRecoveryTracker.begin(preparation.origin)
    startSpearKillInstantPacketSession(packetBootSession, preparation.burst)
    standaloneRemoteMovementLease = movementLease
    primedSessionPacketsDelivered = 0
    highSpeedMoveProbeActive = true
}

internal fun SpearKillModuleState.startHighSpeedAttackProbe(
    settings: SpearKillPacketSessionSettings,
): SpearKillHighSpeedResearchProbeStartResult {
    if (!holdingSpear || !isUsingSpear ||
        player.useItem.get(DataComponents.KINETIC_WEAPON) == null
    ) {
        return SpearKillHighSpeedResearchProbeStartResult.INVALID_CONTEXT
    }
    val (target, distance) = findLookRayTarget(throughTerrain = true)
        ?: return SpearKillHighSpeedResearchProbeStartResult.NO_TARGET
    activeMovementTransport = settings.transport
    beginSpearKillSpeedSession()
    lockedAStarTarget = target
    val result = startDirectPacketAttack(
        target = target,
        distance = distance,
        settings = settings,
        routeMode = "Instant Primed Probe",
    )
    if (result == SpearKillAttackStartResult.STARTED) {
        return SpearKillHighSpeedResearchProbeStartResult.STARTED
    }
    clearAStarTargetLock()
    packetSessionSettings = null
    activeMovementTransport = null
    resetSpearKillSpeedSession()
    return when (result) {
        SpearKillAttackStartResult.RETRY_LATER -> SpearKillHighSpeedResearchProbeStartResult.INVALID_CONTEXT
        SpearKillAttackStartResult.BLOCKED,
        SpearKillAttackStartResult.REJECTED,
        -> SpearKillHighSpeedResearchProbeStartResult.ROUTE_REJECTED
        SpearKillAttackStartResult.STARTED -> error("Handled above")
    }
}

internal fun SpearKillModuleState.currentPrimedMovementProfile(): SpearKillPrimedInstantMovementProfile =
    if (player.isFallFlying) {
        SpearKillPrimedInstantMovementProfile.ELYTRA
    } else {
        SpearKillPrimedInstantMovementProfile.NORMAL
    }
