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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.runtime


import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.minecraft.world.phys.Vec3
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal const val MACE_KILL_MIN_SPEED = 2f
internal const val MACE_KILL_MIN_TARGET_SPEED = 1f
internal const val MACE_KILL_MIN_SPEED_CHANGE = 0.1f
internal const val MACE_KILL_NORMAL_MAX_SPEED = 10f
internal const val MACE_KILL_ELYTRA_MAX_SPEED = 17.32f
internal const val MACE_KILL_EXPERIMENTAL_MAX_SPEED = 500f
internal const val MACE_KILL_MAX_WAIT_TICKS = 4

internal data class MaceKillSpeedLimits(
    val targetSpeed: Double,
    val acceleration: Double,
    val deceleration: Double,
    val stepDistance: Double,
    val vanillaBudget: Double,
) {
    init {
        require(targetSpeed.isPositiveMaceKillSpeed())
        require(acceleration.isPositiveMaceKillSpeed())
        require(deceleration.isPositiveMaceKillSpeed())
        require(stepDistance.isPositiveMaceKillSpeed())
        require(vanillaBudget.isPositiveMaceKillSpeed())
    }
}

internal data class MaceKillSpeedStep(
    val requestedSpeed: Double,
    val stepLimit: Double,
)

internal data class MaceKillSpeedProfile(
    val currentSpeed: Double,
    val limits: MaceKillSpeedLimits,
) {
    init {
        require(currentSpeed.isFinite() && currentSpeed >= 0.0)
    }

    val maximumStepLimit: Double
        get() = minOf(max(currentSpeed, limits.targetSpeed), limits.stepDistance, limits.vanillaBudget)

    fun stepAt(index: Int): MaceKillSpeedStep {
        require(index >= 0)
        val requestedSpeed = when {
            currentSpeed < limits.targetSpeed -> min(
                limits.targetSpeed,
                currentSpeed + limits.acceleration * (index + 1),
            )
            currentSpeed > limits.targetSpeed -> max(
                limits.targetSpeed,
                currentSpeed - limits.deceleration * (index + 1),
            )
            else -> currentSpeed
        }
        return MaceKillSpeedStep(
            requestedSpeed = requestedSpeed,
            stepLimit = minOf(requestedSpeed, limits.stepDistance, limits.vanillaBudget),
        )
    }
}

internal class MaceKillSpeedController {
    private var sessionStartSpeed = 0.0

    var currentSpeed = 0.0
        private set

    var active = false
        private set

    fun begin(observedSpeed: Double, targetSpeed: Double) {
        require(targetSpeed.isPositiveMaceKillSpeed())
        if (active) return
        currentSpeed = observedSpeed.takeIf(Double::isFinite)?.coerceIn(0.0, targetSpeed) ?: 0.0
        sessionStartSpeed = currentSpeed
        active = true
    }

    fun confirmOutbound(limits: MaceKillSpeedLimits): MaceKillSpeedStep =
        MaceKillSpeedProfile(currentSpeed, limits).stepAt(0).also { currentSpeed = it.requestedSpeed }

    fun reset() {
        currentSpeed = 0.0
        sessionStartSpeed = 0.0
        active = false
    }
}

internal fun calculateMaceKillVanillaMovementBudget(
    serverPhysicsVelocity: Vec3,
    fallFlying: Boolean,
): Double {
    val expectedVelocitySquared = serverPhysicsVelocity
        .takeIf(Vec3::hasFiniteMaceKillCoordinates)
        ?.lengthSqr()
        ?.takeIf(Double::isFinite)
        ?: 0.0
    val threshold = if (fallFlying) 300.0 else 100.0
    val roundedBoundary = sqrt(expectedVelocitySquared + threshold)
    return if (roundedBoundary * roundedBoundary - expectedVelocitySquared <= threshold) {
        roundedBoundary
    } else {
        Math.nextDown(roundedBoundary)
    }
}

private fun Double.isPositiveMaceKillSpeed(): Boolean = isFinite() && this > 0.0
private fun Vec3.hasFiniteMaceKillCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
