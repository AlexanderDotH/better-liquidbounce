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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage

import net.minecraft.world.phys.Vec3
import kotlin.math.floor
import kotlin.math.max

internal data class SpearKillKineticSpeedEstimate(
    val attackerSpeed: Double,
    val targetSpeed: Double,
    val relativeSpeed: Double,
)

internal data class SpearKillKineticDamageRequirements(
    val minimumAttackerSpeed: Double,
    val minimumRelativeSpeed: Double,
    val damageMultiplier: Double,
) {
    init {
        require(minimumAttackerSpeed.isNonNegativeFinite()) {
            "Minimum attacker speed must be finite and non-negative"
        }
        require(minimumRelativeSpeed.isNonNegativeFinite()) {
            "Minimum relative speed must be finite and non-negative"
        }
        require(damageMultiplier.isNonNegativeFinite()) {
            "Damage multiplier must be finite and non-negative"
        }
    }
}

internal data class SpearKillKineticDamageEstimate(
    val speed: SpearKillKineticSpeedEstimate,
    val meetsRequirements: Boolean,
    val bonusDamage: Int,
)

/** Mirrors KineticWeapon's known-speed projection, using delivered per-tick displacement. */
internal fun estimateSpearKillKineticSpeed(
    deliveredMovement: Vec3,
    targetMovement: Vec3,
    lookDirection: Vec3,
): SpearKillKineticSpeedEstimate {
    if (!deliveredMovement.hasFiniteCoordinates() || !targetMovement.hasFiniteCoordinates() ||
        !lookDirection.hasFiniteCoordinates() || lookDirection.lengthSqr() <= SPEAR_KILL_KINETIC_EPSILON_SQUARED
    ) {
        return SpearKillKineticSpeedEstimate(0.0, 0.0, 0.0)
    }
    val look = lookDirection.normalize()
    val attacker = look.dot(deliveredMovement.scale(SPEAR_KILL_KNOWN_SPEED_TICKS_PER_SECOND))
    val target = look.dot(targetMovement.scale(SPEAR_KILL_KNOWN_SPEED_TICKS_PER_SECOND))
    return SpearKillKineticSpeedEstimate(attacker, target, max(0.0, attacker - target))
}

internal fun estimateSpearKillKineticDamage(
    deliveredMovement: Vec3,
    targetMovement: Vec3,
    lookDirection: Vec3,
    requirements: SpearKillKineticDamageRequirements,
): SpearKillKineticDamageEstimate {
    val validInputs = deliveredMovement.hasFiniteCoordinates() && targetMovement.hasFiniteCoordinates() &&
        lookDirection.hasFiniteCoordinates() &&
        lookDirection.lengthSqr() > SPEAR_KILL_KINETIC_EPSILON_SQUARED
    val speed = estimateSpearKillKineticSpeed(deliveredMovement, targetMovement, lookDirection)
    val meetsRequirements = validInputs &&
        speed.attackerSpeed >= requirements.minimumAttackerSpeed &&
        speed.relativeSpeed >= requirements.minimumRelativeSpeed
    val bonusDamage = if (meetsRequirements) {
        floor(speed.relativeSpeed * requirements.damageMultiplier).toInt()
    } else {
        0
    }
    return SpearKillKineticDamageEstimate(speed, meetsRequirements, bonusDamage)
}

private fun Vec3.hasFiniteCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

private const val SPEAR_KILL_KINETIC_EPSILON_SQUARED = 1.0E-12

private fun Double.isNonNegativeFinite(): Boolean = isFinite() && this >= 0.0

private const val SPEAR_KILL_KNOWN_SPEED_TICKS_PER_SECOND = 20.0
