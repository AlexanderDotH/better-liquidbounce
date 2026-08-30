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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_FALL_SAFETY_OFFSET_EPSILON_SQUARED
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillAStarSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.SpearKillCollisionSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPacketSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.SpearKillReturnRecoveryAction
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.SpearKillServerFallSafetyPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.activePacketStepWaitTicks
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.calculateSpearKillPrimedInstantMovementBudget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarRoutePlanner
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarSegmentValidator
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.buildSpearKillAStarOutboundMovements
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.compactSpearKillAStarWaypoints
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.policy.conservativePrimedBudgetMovementProfile
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.planPacketReturnLeg(
    from: Vec3,
    to: Vec3,
    segmentValidator: SpearKillAStarSegmentValidator,
    collisionSnapshot: SpearKillCollisionSnapshot,
    aStar: SpearKillAStarSessionSettings,
    stepLimit: Double,
    verticalStep: Double,
): List<Vec3>? {
    buildSpearKillAStarOutboundMovements(
        origin = from,
        waypoints = listOf(to),
        maxSpeed = stepLimit,
        segmentValidator = segmentValidator,
        maxVerticalStep = verticalStep,
    )?.let { return it }

    val route = SpearKillAStarRoutePlanner(
        allowDiagonal = aStar.diagonal,
        maxCost = aStar.maxCost,
        isPassable = collisionSnapshot::isPassable,
        canTraverse = segmentValidator::isClear,
    ).plan(from, to) ?: return null
    val compactedRoute = compactSpearKillAStarWaypoints(
        origin = from,
        waypoints = route,
        maxSpeed = stepLimit,
        segmentValidator = segmentValidator,
        lineOfSightShortcuts = aStar.lineOfSightShortcuts,
    )
    return buildSpearKillAStarOutboundMovements(
        origin = from,
        waypoints = compactedRoute + to,
        maxSpeed = stepLimit,
        segmentValidator = segmentValidator,
        maxVerticalStep = verticalStep,
    )
}

internal fun SpearKillModuleState.beginPacketFirstReturnAttempt(
    attempt: SpearKillReturnRecoveryAction.PacketAttempt,
    movements: List<Vec3>,
    initialFallDistance: Double,
): Boolean {
    if (movements.isEmpty()) return finishEmptyPacketFirstReturnAttempt(attempt)
    val primedSettings = packetSessionSettings?.takeIf { it.primedInstant }
    if (!canAffordPacketFirstReturn(movements, attempt, primedSettings)) return false
    val fallSafetyPlan = createFutureFallSafetyPlan(
        routeOrigin = attempt.authoritativePosition,
        movements = movements,
        outboundStepCount = 0,
        expectedNetMovement = attempt.authoritativeOffset.scale(-1.0),
        initialFallDistance = initialFallDistance,
    ) ?: return false
    installPacketFirstReturn(attempt, movements, fallSafetyPlan, primedSettings != null)
    return true
}

private fun SpearKillModuleState.canAffordPacketFirstReturn(
    movements: List<Vec3>,
    attempt: SpearKillReturnRecoveryAction.PacketAttempt,
    settings: SpearKillPacketSessionSettings?,
): Boolean = settings == null || calculateSpearKillPrimedInstantMovementBudget(
    movements = movements,
    priming = settings.priming,
    movementProfile = conservativePrimedBudgetMovementProfile(),
    maxPackets = settings.instantMaxPackets - primedSessionPacketsDelivered,
    recoveryConfirmationPackets = attempt.checkpoints.size,
) != null

private fun SpearKillModuleState.installPacketFirstReturn(
    attempt: SpearKillReturnRecoveryAction.PacketAttempt,
    movements: List<Vec3>,
    fallSafetyPlan: SpearKillServerFallSafetyPlan,
    retainPrimedPacketBudget: Boolean,
) {
    clearVirtualMovementState(
        retainPrimedPacketBudget = retainPrimedPacketBudget,
        retainRemoteKillOwnership = true,
    )
    packetSessionOrigin = attempt.destination
    physicalReturnPositioner.clear()
    packetRecoveryStallTicks = 0
    packetSetbackRecoveryAttempted = true
    attemptTracker.markRecovery()
    beginVirtualFallSafety(fallSafetyPlan)
    beginCoordinatedPacketExactRecoveryFrom(
        attempt.authoritativeOffset,
        movements,
        activePacketStepWaitTicks,
    )
    sendReturnArrivalConfirmations(attempt.authoritativePosition)
    synchronizeSpearKillServerSneak()
}

internal fun SpearKillModuleState.finishEmptyPacketFirstReturnAttempt(
    attempt: SpearKillReturnRecoveryAction.PacketAttempt,
): Boolean {
    if (attempt.authoritativeOffset.lengthSqr() >= SPEAR_KILL_FALL_SAFETY_OFFSET_EPSILON_SQUARED) {
        return false
    }
    clearVirtualMovementState()
    sendReturnArrivalConfirmations(attempt.authoritativePosition)
    finishPacketFirstReturnAttempt()
    return true
}
