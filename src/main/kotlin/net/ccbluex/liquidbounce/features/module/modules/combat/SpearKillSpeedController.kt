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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.world.phys.Vec3
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Immutable speed policy used both for route projection and one confirmed outbound step. */
internal data class SpearKillSpeedLimits(
    val targetSpeed: Double,
    val acceleration: Double,
    val deceleration: Double,
    val stepDistance: Double,
    val vanillaBudget: Double,
) {
    init {
        require(targetSpeed.isPositiveFinite()) { "Target speed must be finite and positive" }
        require(acceleration.isPositiveFinite()) { "Acceleration must be finite and positive" }
        require(deceleration.isPositiveFinite()) { "Deceleration must be finite and positive" }
        require(stepDistance.isPositiveFinite()) { "Step distance must be finite and positive" }
        require(vanillaBudget.isPositiveFinite()) { "Vanilla budget must be finite and positive" }
    }
}

/** Requested speed and the independently bounded route step for one outbound movement tick. */
internal data class SpearKillSpeedStep(
    val requestedSpeed: Double,
    val stepLimit: Double,
)

/**
 * SpearKill-owned transient speed. Previewing never mutates state; only a delivered outbound
 * movement may call [confirmOutbound].
 */
internal class SpearKillSpeedController {

    private var sessionStartSpeed: Double = 0.0

    var currentSpeed: Double = 0.0
        private set

    var active: Boolean = false
        private set

    fun begin(observedSpeed: Double, targetSpeed: Double) {
        require(targetSpeed.isPositiveFinite()) { "Target speed must be finite and positive" }
        if (active) return
        currentSpeed = observedSpeed.takeIf(Double::isFinite)?.coerceIn(0.0, targetSpeed) ?: 0.0
        sessionStartSpeed = currentSpeed
        active = true
    }

    fun preview(limits: SpearKillSpeedLimits): SpearKillSpeedStep =
        SpearKillSpeedProfile(currentSpeed, limits).stepAt(0)

    fun confirmOutbound(limits: SpearKillSpeedLimits): SpearKillSpeedStep = preview(limits).also {
        currentSpeed = it.requestedSpeed
    }

    fun profile(limits: SpearKillSpeedLimits): SpearKillSpeedProfile =
        SpearKillSpeedProfile(currentSpeed, limits)

    fun rejectOutboundProgress() {
        if (active) currentSpeed = sessionStartSpeed
    }

    fun reset() {
        currentSpeed = 0.0
        sessionStartSpeed = 0.0
        active = false
    }
}

/** Pure future projection; it never advances the owning [SpearKillSpeedController]. */
internal data class SpearKillSpeedProfile(
    val currentSpeed: Double,
    val limits: SpearKillSpeedLimits,
) {
    init {
        require(currentSpeed.isFinite() && currentSpeed >= 0.0) {
            "Current speed must be finite and non-negative"
        }
    }

    val maximumStepLimit: Double
        get() = minOf(max(currentSpeed, limits.targetSpeed), limits.stepDistance, limits.vanillaBudget)

    fun stepAt(index: Int): SpearKillSpeedStep {
        require(index >= 0) { "Speed profile index must not be negative" }
        val requestedSpeed = projectedRequestedSpeed(index + 1)
        return SpearKillSpeedStep(
            requestedSpeed = requestedSpeed,
            stepLimit = minOf(requestedSpeed, limits.stepDistance, limits.vanillaBudget),
        )
    }

    private fun projectedRequestedSpeed(confirmedSteps: Int): Double = when {
        currentSpeed < limits.targetSpeed -> min(
            limits.targetSpeed,
            currentSpeed + limits.acceleration * confirmedSteps,
        )
        currentSpeed > limits.targetSpeed -> max(
            limits.targetSpeed,
            currentSpeed - limits.deceleration * confirmedSteps,
        )
        else -> currentSpeed
    }
}

internal data class SpearKillProfiledTravel(
    val distance: Double,
    val stepCount: Int,
)

/** Generalizes the former constant-step travel equation to a cumulative acceleration profile. */
internal fun calculateSpearKillProfiledTravel(
    distance: Double,
    profile: SpearKillSpeedProfile,
): SpearKillProfiledTravel {
    require(distance.isPositiveFinite()) { "Target distance must be finite and positive" }

    var capacity = 0.0
    for (stepCount in 1..SPEAR_KILL_MAX_PROFILE_STEPS) {
        capacity += profile.stepAt(stepCount - 1).stepLimit
        val travel = 2.0 * distance * stepCount / (2.0 * stepCount + 1.0)
        if (capacity >= travel) return SpearKillProfiledTravel(travel, stepCount)
    }
    error("SpearKill speed profile did not converge")
}

/** Splits one straight movement with the cap projected for each future confirmed step. */
internal fun buildSpearKillProfiledMovements(
    direction: Vec3,
    distance: Double,
    profile: SpearKillSpeedProfile,
): List<Vec3> {
    require(distance.isFinite() && distance >= 0.0) { "Distance must be finite and non-negative" }
    require(direction.hasFiniteCoordinates()) { "Direction must be finite" }
    if (distance == 0.0) return listOf(Vec3.ZERO)

    val directionLength = direction.length()
    require(directionLength.isPositiveFinite()) { "Direction must be non-zero" }
    var remaining = direction.scale(distance / directionLength)
    return buildList {
        while (remaining.lengthSqr() > SPEAR_KILL_PROFILE_EPSILON_SQUARED) {
            check(size < SPEAR_KILL_MAX_PROFILE_STEPS) { "SpearKill route exceeds the profile step limit" }
            val cap = profile.stepAt(size).stepLimit
            val remainingLength = remaining.length()
            if (remainingLength <= cap) {
                add(remaining)
                break
            }
            val step = boundedSpearKillProfileStep(remaining, cap)
            add(step)
            remaining = remaining.subtract(step)
        }
    }
}

internal fun buildSpearKillProfiledAttackMovements(
    direction: Vec3,
    distance: Double,
    profile: SpearKillSpeedProfile,
): List<Vec3> {
    val outbound = buildSpearKillProfiledMovements(direction, distance, profile)
    return buildList(outbound.size * 2 + 1) {
        addAll(outbound)
        outbound.asReversed().forEach { add(it.scale(-1.0)) }
        add(Vec3.ZERO)
    }
}

internal data class SpearKillResegmentedMotionRoute(
    val movements: List<Vec3>,
    val outboundStepCount: Int,
)

/** Drops untouched outbound movement and its not-yet-earned inverse while retaining confirmed history. */
internal fun spearKillConfirmedMotionRecoveryTail(
    queuedMovements: List<Vec3>,
    remainingOutboundSteps: Int,
): List<Vec3>? {
    if (remainingOutboundSteps < 1 || queuedMovements.any { !it.hasFiniteCoordinates() }) return null
    val retainedRecoveryIndex = remainingOutboundSteps - 1 + remainingOutboundSteps
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
    if (!origin.hasFiniteCoordinates() || !pendingOutboundMovement.hasFiniteCoordinates() ||
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
        .takeIf(Vec3::hasFiniteCoordinates)
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
): Boolean = movementFromFirstGood.hasFiniteCoordinates() &&
    movementFromFirstGood.length() <= calculateSpearKillVanillaMovementBudget(serverPhysicsVelocity, fallFlying)

internal data class SpearKillKineticSpeedEstimate(
    val attackerSpeed: Double,
    val targetSpeed: Double,
    val relativeSpeed: Double,
)

/** Mirrors KineticWeapon's known-speed projection, using delivered displacement rather than intent. */
internal fun estimateSpearKillKineticSpeed(
    deliveredMovement: Vec3,
    targetMovement: Vec3,
    lookDirection: Vec3,
): SpearKillKineticSpeedEstimate {
    if (!deliveredMovement.hasFiniteCoordinates() || !targetMovement.hasFiniteCoordinates() ||
        !lookDirection.hasFiniteCoordinates() || lookDirection.lengthSqr() <= SPEAR_KILL_PROFILE_EPSILON_SQUARED
    ) {
        return SpearKillKineticSpeedEstimate(0.0, 0.0, 0.0)
    }
    val look = lookDirection.normalize()
    val attacker = look.dot(deliveredMovement)
    val target = look.dot(targetMovement)
    return SpearKillKineticSpeedEstimate(attacker, target, max(0.0, attacker - target))
}

internal fun boundedSpearKillProfileStep(remaining: Vec3, cap: Double): Vec3 {
    var step = remaining.scale(cap / remaining.length())
    if (step.length() > cap) {
        step = step.scale(Math.nextDown(cap) / step.length())
    }
    return step
}

private fun Vec3.hasFiniteCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

private fun Double.isPositiveFinite(): Boolean = isFinite() && this > 0.0

private const val SPEAR_KILL_NORMAL_MOVEMENT_THRESHOLD = 100.0
private const val SPEAR_KILL_ELYTRA_MOVEMENT_THRESHOLD = 300.0
internal const val SPEAR_KILL_MAX_PROFILE_STEPS = 100_000
internal const val SPEAR_KILL_PROFILE_EPSILON_SQUARED = 1.0E-12
