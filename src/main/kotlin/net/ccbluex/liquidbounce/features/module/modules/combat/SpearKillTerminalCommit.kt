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
@file:Suppress("MatchingDeclarationName")

package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.world.phys.Vec3

internal enum class SpearKillPacketRouteReplanResult {
    INSTALLED,
    TRANSIENT_FAILURE,
    BLOCKED,
}

internal fun shouldKeepSpearKillTerminalPending(result: SpearKillPacketRouteReplanResult): Boolean =
    result != SpearKillPacketRouteReplanResult.BLOCKED

internal enum class SpearKillTerminalChargeAction {
    WAIT,
    REFRESH,
    READY,
    INVALID,
}

/** Timing that remains after the route has parked immediately before its terminal suffix. */
internal fun buildSpearKillTerminalSchedule(
    terminalStepCount: Int,
    stepWaitTicks: Int,
    strikeHoldTicks: Int = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
): SpearKillPathSchedule? = buildSpearKillPathSchedule(
    outboundStepCount = terminalStepCount,
    stepWaitTicks = stepWaitTicks,
    terminalSuffixCount = terminalStepCount,
    preStrikeHoldTicks = 0,
    strikeHoldTicks = strikeHoldTicks,
)

/**
 * A long paced route may outlive the charge it started with. It remains launchable when the
 * terminal suffix can still fit after the endpoint refresh performed by the runtime.
 */
internal fun hasSpearKillRefreshableTerminalDamageWindow(
    delayTicks: Int,
    damageUseDuration: Int,
    terminalStepCount: Int,
    stepWaitTicks: Int,
    strikeHoldTicks: Int = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
): Boolean {
    val schedule = buildSpearKillTerminalSchedule(
        terminalStepCount,
        stepWaitTicks,
        strikeHoldTicks,
    ) ?: return false
    return hasSpearKillFreshTerminalDamageWindow(
        delayTicks = delayTicks,
        damageUseDuration = damageUseDuration,
        remainingHitTicks = schedule.hitTick,
    )
}

private fun hasSpearKillFreshTerminalDamageWindow(
    delayTicks: Int,
    damageUseDuration: Int,
    remainingHitTicks: Int,
): Boolean = delayTicks >= 0 && damageUseDuration >= 0 && remainingHitTicks >= 0 &&
    delayTicks.toLong() + 1L + remainingHitTicks.toLong() <= damageUseDuration.toLong()

/**
 * Keeps a terminal packet route parked while a fresh spear charge is building, and explicitly
 * refreshes an otherwise valid lunge whose original damage window expired in transit.
 */
internal fun resolveSpearKillTerminalChargeAction(
    isUsingSpear: Boolean,
    ticksUsingItem: Int,
    delayTicks: Int,
    damageUseDuration: Int,
    remainingHitTicks: Int,
): SpearKillTerminalChargeAction {
    if (!isUsingSpear || ticksUsingItem < 0 || delayTicks < 0 ||
        damageUseDuration < 0 || remainingHitTicks < 0
    ) {
        return SpearKillTerminalChargeAction.INVALID
    }

    if (!hasSpearKillFreshTerminalDamageWindow(delayTicks, damageUseDuration, remainingHitTicks)) {
        return SpearKillTerminalChargeAction.INVALID
    }
    if (ticksUsingItem <= delayTicks) return SpearKillTerminalChargeAction.WAIT

    return if (ticksUsingItem.toLong() + remainingHitTicks.toLong() <= damageUseDuration.toLong()) {
        SpearKillTerminalChargeAction.READY
    } else {
        SpearKillTerminalChargeAction.REFRESH
    }
}

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
    if (resolveSpearKillTerminalChargeAction(
            isUsingSpear = isUsingSpear,
            ticksUsingItem = ticksUsingItem,
            delayTicks = delayTicks,
            damageUseDuration = damageUseDuration,
            remainingHitTicks = remainingHitTicks,
        ) != SpearKillTerminalChargeAction.READY
    ) {
        return false
    }
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
