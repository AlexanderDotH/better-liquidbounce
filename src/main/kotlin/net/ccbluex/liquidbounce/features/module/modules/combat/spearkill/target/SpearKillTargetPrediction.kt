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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*

import net.minecraft.world.phys.Vec3

/**
 * Detects a late target-course change before Direct commits its final kinetic movement.
 * Constant movement remains on the original extrapolated route; invalid input fails safe.
 */
internal fun shouldReplanSpearKillDirectTerminal(
    plannedPosition: Vec3,
    currentPosition: Vec3,
    ticksSincePlan: Int,
    plannedVelocity: Vec3 = Vec3.ZERO,
    terminalReplanInstalled: Boolean = false,
): Boolean {
    if (ticksSincePlan < 0 || !plannedPosition.hasFiniteCoordinates() ||
        !currentPosition.hasFiniteCoordinates() || !plannedVelocity.hasFiniteCoordinates()
    ) {
        return true
    }
    if (terminalReplanInstalled) return false

    val expectedPosition = plannedPosition.add(plannedVelocity.scale(ticksSincePlan.toDouble()))
    return !expectedPosition.hasFiniteCoordinates() ||
        expectedPosition.distanceToSqr(currentPosition) >= SPEAR_KILL_DIRECT_TERMINAL_REPLAN_DISTANCE_SQUARED
}

private fun Vec3.hasFiniteCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

private const val SPEAR_KILL_DIRECT_TERMINAL_REPLAN_DISTANCE = 0.15
private const val SPEAR_KILL_DIRECT_TERMINAL_REPLAN_DISTANCE_SQUARED =
    SPEAR_KILL_DIRECT_TERMINAL_REPLAN_DISTANCE * SPEAR_KILL_DIRECT_TERMINAL_REPLAN_DISTANCE
