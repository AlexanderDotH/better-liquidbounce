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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.world.phys.Vec3

/** Final server-side conditions that must all remain true when the terminal suffix is committed. */
@Suppress("LongParameterList")
internal fun canCommitSpearKillTerminalLunge(
    isUsingSpear: Boolean,
    ticksUsingItem: Int,
    delayTicks: Int,
    damageUseDuration: Int,
    remainingHitTicks: Int,
    hasLiveAttackRay: Boolean,
    aimAligned: Boolean,
): Boolean {
    if (!isUsingSpear || delayTicks < 0 || ticksUsingItem <= delayTicks) return false
    if (!hasSpearKillScheduleDamageWindow(ticksUsingItem, damageUseDuration, remainingHitTicks)) return false
    return hasLiveAttackRay && aimAligned
}

/**
 * Requires the kinetic movement to face the predicted target center, not merely graze its box.
 */
internal fun isSpearKillTerminalAimAligned(
    eye: Vec3,
    terminalMovement: Vec3,
    targetPoint: Vec3,
    maxAngleDegrees: Float = SPEAR_KILL_TERMINAL_AIM_TOLERANCE_DEGREES,
): Boolean {
    if (!maxAngleDegrees.isFinite() || maxAngleDegrees < 0f) return false

    val movementHeading = spearKillKineticHeading(terminalMovement) ?: return false
    val targetHeading = spearKillKineticHeading(targetPoint.subtract(eye)) ?: return false
    return movementHeading.directionAngleTo(targetHeading) <= maxAngleDegrees
}

private const val SPEAR_KILL_TERMINAL_AIM_TOLERANCE_DEGREES = 2f
