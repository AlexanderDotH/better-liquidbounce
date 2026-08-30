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
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import kotlin.math.hypot

data class HorizontalPosition(val x: Double, val z: Double) {
    init {
        require(x.isFinite() && z.isFinite()) { "A horizontal position must be finite" }
    }

    fun distanceTo(other: HorizontalPosition): Double = hypot(x - other.x, z - other.z)
}

data class SpearMovementSample(
    val position: HorizontalPosition,
    val colliding: Boolean = false,
    val supported: Boolean = true,
    val overVoid: Boolean = false,
)

data class SpearMovementSimulation(val samples: List<SpearMovementSample>) {
    init {
        require(samples.size == SpearDodgePlanner.SIMULATION_TICKS) {
            "A spear dodge simulation must contain exactly ${SpearDodgePlanner.SIMULATION_TICKS} post-tick samples"
        }
    }
}

data class SpearDodgePlan(
    val input: DirectionalInput,
    val minimumClearance: Double,
    val distanceFromAttacker: Double,
    val useTimer: Boolean,
) {
    val directionalInput: DirectionalInput
        get() = input

    fun asDodgePlan() = DodgePlan(input, shouldJump = false, yawChange = null, useTimer = useTimer)

    companion object {
        val NONE = SpearDodgePlan(DirectionalInput.NONE, 0.0, 0.0, useTimer = false)
    }
}

internal fun SpearMovementSimulation.isUnsafeSpearMovement(startedSafelyGrounded: Boolean): Boolean {
    if (samples.any(SpearMovementSample::colliding)) return true
    return startedSafelyGrounded && samples.any { !it.supported || it.overVoid }
}
