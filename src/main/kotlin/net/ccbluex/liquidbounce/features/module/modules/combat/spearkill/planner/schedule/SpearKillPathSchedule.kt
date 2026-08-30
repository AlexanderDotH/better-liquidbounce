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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SPEAR_KILL_PACKET_MAX_WAIT_TICKS

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarAttackApproach

import net.minecraft.world.phys.Vec3

/** Per-step outbound timing for Packet A* attacks, including terminal aim-lock and strike holds. */
internal data class SpearKillPathSchedule(
    val stepStartTicks: List<Int>,
    val terminalStartTick: Int,
    val hitTick: Int,
    val totalOutboundTicks: Int,
)

/**
 * Builds relative tick indices for outbound packet emission.
 *
 * The optional single pre-strike tick is inserted immediately before the terminal suffix. Strike
 * hold is counted after the last outbound step for [SpearKillPathSchedule.hitTick].
 */
internal fun buildSpearKillPathSchedule(
    outboundStepCount: Int,
    stepWaitTicks: Int,
    terminalSuffixCount: Int,
    preStrikeHoldTicks: Int,
    strikeHoldTicks: Int,
): SpearKillPathSchedule? {
    if (outboundStepCount <= 0 ||
        terminalSuffixCount !in 1..outboundStepCount ||
        stepWaitTicks !in 0..SPEAR_KILL_PACKET_MAX_WAIT_TICKS ||
        preStrikeHoldTicks !in 0..SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS ||
        strikeHoldTicks < 0
    ) {
        return null
    }

    var tick = 0
    val stepStartTicks = ArrayList<Int>(outboundStepCount)
    val terminalIndex = outboundStepCount - terminalSuffixCount
    for (index in 0 until outboundStepCount) {
        if (index == terminalIndex) {
            tick += preStrikeHoldTicks
        }
        stepStartTicks += tick
        tick += 1
        if (index < outboundStepCount - 1) {
            tick += stepWaitTicks
        }
    }

    val terminalStartTick = stepStartTicks[terminalIndex]
    val hitTick = stepStartTicks.last() + strikeHoldTicks
    return SpearKillPathSchedule(
        stepStartTicks = stepStartTicks,
        terminalStartTick = terminalStartTick,
        hitTick = hitTick,
        totalOutboundTicks = hitTick,
    )
}

/** Builds an A* schedule with its single terminal aim-lock tick and no predictive waiting. */
internal fun buildSpearKillAStarPathSchedule(
    outboundStepCount: Int,
    stepWaitTicks: Int,
    terminalSuffixCount: Int,
    strikeHoldTicks: Int,
): SpearKillPathSchedule? = buildSpearKillPathSchedule(
    outboundStepCount = outboundStepCount,
    stepWaitTicks = stepWaitTicks,
    terminalSuffixCount = terminalSuffixCount,
    preStrikeHoldTicks = SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS,
    strikeHoldTicks = strikeHoldTicks,
)

/** Counts trailing outbound steps that sum exactly to the approach terminal corridor. */
@Suppress("ReturnCount")
internal fun countSpearKillAStarTerminalSuffix(
    outboundMovements: List<Vec3>,
    approach: SpearKillAStarAttackApproach,
    stepLimit: Double,
): Int? {
    if (outboundMovements.isEmpty() || !stepLimit.isFinite() || stepLimit <= 0.0) return null

    val expectedMovement = approach.terminalWaypoint.subtract(approach.plannerGoal)
    if (!expectedMovement.isFinite() ||
        expectedMovement.lengthSqr() <= SPEAR_KILL_PATH_SCHEDULE_EPSILON_SQUARED
    ) {
        return null
    }

    var accumulated = Vec3.ZERO
    var suffixCount = 0
    for (step in outboundMovements.asReversed()) {
        if (!step.isValidSpearKillTerminalStep(stepLimit)) return null
        accumulated = step.add(accumulated)
        suffixCount++
        if (accumulated.hasReachedSpearKillTerminal(expectedMovement)) return suffixCount
        if (accumulated.hasPassedSpearKillTerminal(expectedMovement)) return null
    }
    return null
}

/** Lower-bound travel ticks for approach packets only (no pre-hold, no strike hold). */

private fun Vec3.isValidSpearKillTerminalStep(stepLimit: Double): Boolean =
    isFinite() && length() <= stepLimit + SPEAR_KILL_PATH_SCHEDULE_EPSILON

private fun Vec3.hasReachedSpearKillTerminal(expectedMovement: Vec3): Boolean =
    distanceToSqr(expectedMovement) <= SPEAR_KILL_PATH_SCHEDULE_EPSILON_SQUARED

private fun Vec3.hasPassedSpearKillTerminal(expectedMovement: Vec3): Boolean =
    length() > expectedMovement.length() + SPEAR_KILL_PATH_SCHEDULE_EPSILON

private fun Vec3.isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

internal const val SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS = 2
internal const val SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS = 1
