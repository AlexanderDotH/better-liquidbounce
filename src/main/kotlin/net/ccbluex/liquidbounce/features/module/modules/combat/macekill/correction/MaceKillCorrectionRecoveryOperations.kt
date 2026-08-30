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

package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.*

import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

import net.minecraft.world.phys.Vec3

private data class MaceKillRecoveryAnchor(
    val position: Vec3,
    val movementsToOrigin: List<Vec3>,
)

/**
 * Rejoins the nearest immutable ClipReach checkpoint and then propagates the recorded route fully
 * backwards. No straight-line movement through previously unvalidated space is synthesized.
 */
internal fun maceKillFullInverseRecovery(
    plan: MaceClipReachPlan,
    authoritativePosition: Vec3,
): List<Vec3>? {
    if (!authoritativePosition.hasFiniteRecoveryCoordinates()) return null
    val anchor = plan.recoveryAnchors().minByOrNull { candidate ->
        candidate.position.distanceToSqr(authoritativePosition)
    } ?: return null
    if (anchor.position.distanceToSqr(authoritativePosition) > RECOVERY_ANCHOR_TOLERANCE_SQUARED) return null

    val rejoin = anchor.position.subtract(authoritativePosition)
        .takeIf { it.lengthSqr() > RECOVERY_EPSILON_SQUARED }
    val movements = buildList {
        if (rejoin != null) add(rejoin)
        addAll(anchor.movementsToOrigin)
    }
    val finalPosition = movements.fold(authoritativePosition, Vec3::add)
    return movements.takeIf {
        it.isNotEmpty() && finalPosition.distanceToSqr(plan.origin) <= RECOVERY_EPSILON_SQUARED
    }
}

internal fun maceKillForcedOriginRecovery(
    authoritativePosition: Vec3,
    origin: Vec3,
): List<Vec3> {
    require(authoritativePosition.hasFiniteRecoveryCoordinates() && origin.hasFiniteRecoveryCoordinates()) {
        "Recovery positions must be finite"
    }
    val displacement = origin.subtract(authoritativePosition)
    require(displacement.lengthSqr() > RECOVERY_EPSILON_SQUARED) {
        "Forced recovery requires a displaced authoritative position"
    }
    return buildMaceKillFixedStepMovements(
        direction = displacement,
        distance = displacement.length(),
        maxSpeed = FORCED_RECOVERY_STEP_DISTANCE,
    )
}

private fun buildMaceKillFixedStepMovements(
    direction: Vec3,
    distance: Double,
    maxSpeed: Double,
): List<Vec3> {
    require(distance.isFinite() && distance >= 0.0) { "Distance must be finite and non-negative" }
    require(maxSpeed.isFinite() && maxSpeed > 0.0) { "Maximum speed must be finite and positive" }

    var remaining = direction.normalize().scale(distance)
    return buildList {
        do {
            val remainingLength = remaining.length()
            if (remainingLength <= maxSpeed) {
                add(remaining)
                return@buildList
            }

            var step = remaining.scale(maxSpeed / remainingLength)
            val stepLength = step.length()
            if (stepLength > maxSpeed) {
                step = step.scale(Math.nextDown(maxSpeed) / stepLength)
            }
            add(step)
            remaining = remaining.subtract(step)
        } while (true)
    }
}

internal fun selectMaceKillCorrectionRecoveryMovements(
    action: MaceKillCorrectionRecoveryAction,
    inverseRecovery: List<Vec3>?,
    collisionRecovery: List<Vec3>?,
    forcedRecovery: List<Vec3>?,
): List<Vec3>? = when (action) {
    MaceKillCorrectionRecoveryAction.RECOVER_COLLISION_DERIVED ->
        inverseRecovery ?: collisionRecovery ?: forcedRecovery
    MaceKillCorrectionRecoveryAction.FORCE_ORIGIN_PACKET_RESET -> forcedRecovery
}

internal enum class MaceKillDisableRouteAction {
    RELEASE_COMPLETED,
    BEGIN_SAFE_ABORT,
}

internal fun maceKillDisableRouteAction(
    sessionActive: Boolean,
    awaitingStrike: Boolean,
): MaceKillDisableRouteAction = if (!sessionActive && !awaitingStrike) {
    MaceKillDisableRouteAction.RELEASE_COMPLETED
} else {
    MaceKillDisableRouteAction.BEGIN_SAFE_ABORT
}

private fun MaceClipReachPlan.recoveryAnchors(): List<MaceKillRecoveryAnchor> = buildList {
    var returnPosition = endpoint
    for (index in 0..returnMovements.size) {
        add(MaceKillRecoveryAnchor(returnPosition, returnMovements.drop(index)))
        if (index < returnMovements.size) returnPosition = returnPosition.add(returnMovements[index])
    }

    var outboundPosition = origin
    add(MaceKillRecoveryAnchor(origin, emptyList()))
    for (index in outboundMovements.indices) {
        outboundPosition = outboundPosition.add(outboundMovements[index])
        if (index + 1 == outboundMovements.size) continue
        val inversePrefix = outboundMovements.take(index + 1).asReversed().map { it.scale(-1.0) }
        add(MaceKillRecoveryAnchor(outboundPosition, inversePrefix))
    }
}

private const val RECOVERY_ANCHOR_TOLERANCE_SQUARED = 0.25
private const val RECOVERY_EPSILON_SQUARED = 1.0E-8
private const val FORCED_RECOVERY_STEP_DISTANCE = 3.0

private fun Vec3.hasFiniteRecoveryCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
