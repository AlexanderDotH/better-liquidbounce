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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config

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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*

import net.minecraft.world.phys.Vec3
import kotlin.math.max
import kotlin.math.min

/** Immutable speed policy used both for route projection and one confirmed outbound step. */
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
    require(distance.isPositiveSpearKillSpeed()) { "Target distance must be finite and positive" }

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
    require(direction.hasFiniteSpearKillSpeedCoordinates()) { "Direction must be finite" }
    if (distance == 0.0) return listOf(Vec3.ZERO)

    val directionLength = direction.length()
    require(directionLength.isPositiveSpearKillSpeed()) { "Direction must be non-zero" }
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
