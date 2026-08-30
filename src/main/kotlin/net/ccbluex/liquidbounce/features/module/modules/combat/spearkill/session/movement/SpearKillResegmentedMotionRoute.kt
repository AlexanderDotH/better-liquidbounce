/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement



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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarSegmentValidator
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

internal data class SpearKillResegmentedMotionRoute(
    val movements: List<Vec3>,
    val outboundStepCount: Int,
)

/** Drops untouched outbound movement and its not-yet-earned inverse while retaining confirmed history. */
internal fun spearKillConfirmedMotionRecoveryTail(
    queuedMovements: List<Vec3>,
    remainingOutboundSteps: Int,
): List<Vec3>? {
    if (remainingOutboundSteps < 1 || queuedMovements.any { !it.hasFiniteSpearKillSpeedCoordinates() }) return null
    val retainedRecoveryIndex = remainingOutboundSteps - 1 + remainingOutboundSteps
    if (retainedRecoveryIndex >= queuedMovements.size) return null
    return queuedMovements.drop(retainedRecoveryIndex)
}

/** Keeps only the inverse of already delivered Motion steps when an external owner stops the route. */
internal fun spearKillMotionReturnTailOnDisable(
    queuedMovements: List<Vec3>,
    plannedOutboundSteps: Int,
    confirmedOutboundSteps: Int,
): List<Vec3>? {
    if (plannedOutboundSteps < 0 || confirmedOutboundSteps !in 0..plannedOutboundSteps ||
        queuedMovements.any { !it.hasFiniteSpearKillSpeedCoordinates() }
    ) {
        return null
    }

    val remainingOutboundSteps = plannedOutboundSteps - confirmedOutboundSteps
    val retainedRecoveryIndex = remainingOutboundSteps * 2
    if (retainedRecoveryIndex >= queuedMovements.size) return null
    return queuedMovements.drop(retainedRecoveryIndex)
}

/**
 * Rebuilds only the untouched portion of a Motion route. The already confirmed inverse tail is
 * retained byte-for-byte so a smaller live budget cannot alter recovery back to the session origin.
 */
internal fun resegmentSpearKillUnconfirmedMotionRoute(
    origin: Vec3,
    pendingOutboundMovement: Vec3,
    queuedMovements: List<Vec3>,
    remainingOutboundSteps: Int,
    profile: SpearKillSpeedProfile,
    segmentValidator: SpearKillAStarSegmentValidator,
): SpearKillResegmentedMotionRoute? {
    if (!origin.hasFiniteSpearKillSpeedCoordinates() ||
        !pendingOutboundMovement.hasFiniteSpearKillSpeedCoordinates() ||
        pendingOutboundMovement.lengthSqr() <= SPEAR_KILL_PROFILE_EPSILON_SQUARED ||
        remainingOutboundSteps < 1
    ) {
        return null
    }

    val untouchedAfterPending = remainingOutboundSteps - 1
    val retainedRecovery = spearKillConfirmedMotionRecoveryTail(
        queuedMovements,
        remainingOutboundSteps,
    ) ?: return null

    val oldOutbound = buildList(remainingOutboundSteps) {
        add(pendingOutboundMovement)
        addAll(queuedMovements.take(untouchedAfterPending))
    }
    var waypoint = origin
    val outboundWaypoints = oldOutbound.map { movement ->
        waypoint = waypoint.add(movement)
        waypoint
    }
    val route = buildSpearKillProfiledAStarPacketRoute(
        origin = origin,
        outboundWaypoints = outboundWaypoints,
        profile = profile,
        segmentValidator = segmentValidator,
    ) ?: return null
    var recoveryPosition = origin
    for (movement in retainedRecovery) {
        val next = recoveryPosition.add(movement)
        if (movement.lengthSqr() > SPEAR_KILL_PROFILE_EPSILON_SQUARED &&
            !segmentValidator.isClear(recoveryPosition, next)
        ) {
            return null
        }
        recoveryPosition = next
    }
    val rebuiltMovements = buildList(route.roundTripMovements.size - 1 + retainedRecovery.size) {
        addAll(route.roundTripMovements.dropLast(1))
        addAll(retainedRecovery)
    }
    return SpearKillResegmentedMotionRoute(rebuiltMovements, route.outboundMovements.size)
}

/** Minecraft 26.2 one-packet moved-too-quickly boundary. */
internal fun calculateSpearKillVanillaMovementBudget(
    serverPhysicsVelocity: Vec3,
    fallFlying: Boolean,
): Double {
    val expectedVelocitySquared = serverPhysicsVelocity
        .takeIf(Vec3::hasFiniteSpearKillSpeedCoordinates)
        ?.lengthSqr()
        ?.takeIf(Double::isFinite)
        ?: 0.0
    val threshold = if (fallFlying) SPEAR_KILL_ELYTRA_MOVEMENT_THRESHOLD else SPEAR_KILL_NORMAL_MOVEMENT_THRESHOLD
    val roundedBoundary = sqrt(expectedVelocitySquared + threshold)
    return if (roundedBoundary * roundedBoundary - expectedVelocitySquared <= threshold) {
        roundedBoundary
    } else {
        Math.nextDown(roundedBoundary)
    }
}

internal fun isSpearKillWithinVanillaMovementBudget(
    movementFromFirstGood: Vec3,
    serverPhysicsVelocity: Vec3,
    fallFlying: Boolean,
): Boolean = movementFromFirstGood.hasFiniteSpearKillSpeedCoordinates() &&
    movementFromFirstGood.length() <= calculateSpearKillVanillaMovementBudget(serverPhysicsVelocity, fallFlying)

internal fun boundedSpearKillProfileStep(remaining: Vec3, cap: Double): Vec3 {
    var step = remaining.scale(cap / remaining.length())
    if (step.length() > cap) {
        step = step.scale(Math.nextDown(cap) / step.length())
    }
    return step
}

internal fun Vec3.hasFiniteSpearKillSpeedCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

internal fun Double.isPositiveSpearKillSpeed(): Boolean = isFinite() && this > 0.0

private const val SPEAR_KILL_NORMAL_MOVEMENT_THRESHOLD = 100.0
private const val SPEAR_KILL_ELYTRA_MOVEMENT_THRESHOLD = 300.0
internal const val SPEAR_KILL_MAX_PROFILE_STEPS = 100_000
internal const val SPEAR_KILL_PROFILE_EPSILON_SQUARED = 1.0E-12
