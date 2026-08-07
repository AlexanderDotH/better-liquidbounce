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

internal enum class SpearKillAttackStartResult {
    STARTED,
    RETRY_LATER,
    REJECTED,
}

/** Classifies whether a failed A* launch should hard-lock the target or wait for a fresh spear window. */
internal fun classifySpearKillAStarStartFailure(
    routeFound: Boolean,
    hasDamageWindow: Boolean,
): SpearKillAttackStartResult = when {
    !routeFound -> SpearKillAttackStartResult.REJECTED
    !hasDamageWindow -> SpearKillAttackStartResult.RETRY_LATER
    else -> SpearKillAttackStartResult.STARTED
}

/**
 * Through-terrain A* aims at distant entities behind cover. Prefer angular aim quality, and when
 * two candidates are equally aligned choose the farther one so near interceptors cannot steal the lock.
 */
internal fun compareSpearKillLookRayPriority(
    left: SpearKillLookRayPriority,
    right: SpearKillLookRayPriority,
    throughTerrain: Boolean,
): Int {
    if (!throughTerrain) return left.compareTo(right)

    val angularComparison = left.angularErrorSquared.compareTo(right.angularErrorSquared)
    return if (angularComparison != 0) {
        angularComparison
    } else {
        right.distanceAlongRaySquared.compareTo(left.distanceAlongRaySquared)
    }
}

/** Validates that the built A* route still fits inside the kinetic spear's remaining damage window. */
internal fun hasSpearKillAStarDamageWindow(
    ticksUsingItem: Int,
    damageUseDuration: Int,
    outboundStepCount: Int,
    stepWaitTicks: Int,
    confirmationTicks: Int,
): Boolean = hasSpearKillDamageWindow(
    ticksUsingItem = ticksUsingItem,
    damageUseDuration = damageUseDuration,
    arrivalTicks = spearKillAStarArrivalTicks(
        outboundStepCount = outboundStepCount,
        stepWaitTicks = stepWaitTicks,
        preStrikeHoldTicks = 0,
    ),
    confirmationTicks = confirmationTicks,
)
