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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_A_STAR_SNAPSHOT_HORIZONTAL_MARGIN
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_A_STAR_SNAPSHOT_VERTICAL_MARGIN
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_RECOVERY_POSITION_EPSILON_SQUARED
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_ROUTE_SNAPSHOT_MAX_CELLS
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillAStarSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.SpearKillCollisionSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.SpearKillCollisionSnapshotBuilder
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.SpearKillReturnRecoveryAction
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.activeSpeedStepDistance
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.buildSpearKillReturnRecoveryMovements
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.calculateSpearKillRouteSynchronously
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.isSpearKillPacketMovementSequenceServerAccepted
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.packetRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.safeVirtualFallStep
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarSegmentValidator
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.withVanillaSpearKillBlockShapes
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.currentSpeedProfile
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.policy.resolveSpearKillAStarSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.spearKillCollisionBoxesAt
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.exactRecoveryMovementsFrom
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

internal val SpearKillModuleState.recoveryPlanningStepLimit
    get() = currentSpeedProfile(activeSpeedStepDistance).maximumStepLimit

internal fun SpearKillModuleState.createCollisionSafeSetbackRecovery(
    @Suppress("UNUSED_PARAMETER") sessionOrigin: Vec3,
    authoritativeOffset: Vec3,
): List<Vec3>? = packetBootSession.exactRecoveryMovementsFrom(authoritativeOffset)

internal fun SpearKillModuleState.startPacketFirstReturnRecovery(
    authoritativePosition: Vec3,
    targetPlayer: Player = player,
    preferredFirstLeg: List<Vec3>? = null,
    initialFallDistance: Double = player.fallDistance.toDouble(),
): Boolean {
    var preferredLeg = preferredFirstLeg
    while (true) {
        when (val action = returnRecoveryTracker.nextAction(authoritativePosition)) {
            is SpearKillReturnRecoveryAction.PacketAttempt -> {
                val movements = calculatePacketFirstReturnMovements(action, preferredLeg)
                preferredLeg = null
                if (movements == null) continue
                if (beginPacketFirstReturnAttempt(action, movements, initialFallDistance)) return true
            }
            is SpearKillReturnRecoveryAction.PhysicalReset -> {
                applyPhysicalReturnFallback(action.position, targetPlayer)
                return false
            }
        }
    }
}

internal fun SpearKillModuleState.calculatePacketFirstReturnMovements(
    attempt: SpearKillReturnRecoveryAction.PacketAttempt,
    preferredFirstLeg: List<Vec3>?,
): List<Vec3>? {
    val aStar = packetSessionSettings?.aStar
        ?: resolveSpearKillAStarSessionSettings(packetRoutingMode)
    val stepLimit = recoveryPlanningStepLimit
    val verticalStep = safeVirtualFallStep
    val playerBoundingBox = spearKillServerCollisionBoxAt(attempt.destination)
    val points = listOf(attempt.authoritativePosition) + attempt.checkpoints
    val snapshotBuilder = SpearKillCollisionSnapshotBuilder.corridor(
        points = points,
        horizontalMargin = SPEAR_KILL_A_STAR_SNAPSHOT_HORIZONTAL_MARGIN,
        verticalMargin = SPEAR_KILL_A_STAR_SNAPSHOT_VERTICAL_MARGIN,
        maxCells = SPEAR_KILL_ROUTE_SNAPSHOT_MAX_CELLS,
    )
    return withVanillaSpearKillBlockShapes {
        calculateSpearKillRouteSynchronously(
            snapshotBuilder = snapshotBuilder,
            collisionBoxesAt = ::spearKillCollisionBoxesAt,
        ) { collisionSnapshot ->
            createPacketFirstReturnMovements(
                attempt = attempt,
                preferredFirstLeg = preferredFirstLeg,
                segmentValidator = collisionSnapshot.createSegmentValidator(
                    origin = attempt.destination,
                    playerBoundingBox = playerBoundingBox,
                ),
                collisionSnapshot = collisionSnapshot,
                aStar = aStar,
                stepLimit = stepLimit,
                verticalStep = verticalStep,
            )
        }
    }
}

internal fun SpearKillModuleState.createPacketFirstReturnMovements(
    attempt: SpearKillReturnRecoveryAction.PacketAttempt,
    preferredFirstLeg: List<Vec3>?,
    segmentValidator: SpearKillAStarSegmentValidator,
    collisionSnapshot: SpearKillCollisionSnapshot,
    aStar: SpearKillAStarSessionSettings,
    stepLimit: Double,
    verticalStep: Double,
): List<Vec3>? {
    var preferredLeg = preferredFirstLeg
    return buildSpearKillReturnRecoveryMovements(
        authoritativePosition = attempt.authoritativePosition,
        checkpoints = attempt.checkpoints,
    ) { from, to ->
        val candidate = preferredLeg
        preferredLeg = null
        validatedPreferredReturnLeg(from, to, candidate, segmentValidator)
            ?: planPacketReturnLeg(
                from = from,
                to = to,
                segmentValidator = segmentValidator,
                collisionSnapshot = collisionSnapshot,
                aStar = aStar,
                stepLimit = stepLimit,
                verticalStep = verticalStep,
            )
    }
}

internal fun SpearKillModuleState.validatedPreferredReturnLeg(
    from: Vec3,
    to: Vec3,
    movements: List<Vec3>?,
    segmentValidator: SpearKillAStarSegmentValidator,
): List<Vec3>? {
    movements ?: return null
    if (!isSpearKillPacketMovementSequenceServerAccepted(from, movements, segmentValidator)) return null
    return movements.takeIf {
        it.fold(from, Vec3::add).distanceToSqr(to) <= SPEAR_KILL_RECOVERY_POSITION_EPSILON_SQUARED
    }
}
