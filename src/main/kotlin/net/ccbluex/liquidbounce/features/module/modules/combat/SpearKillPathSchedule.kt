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
import kotlin.math.ceil

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
        stepWaitTicks !in 0..SPEAR_KILL_MAX_WAIT_TICKS ||
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
        if (!step.isFinite() || step.length() > stepLimit + SPEAR_KILL_PATH_SCHEDULE_EPSILON) {
            return null
        }
        accumulated = step.add(accumulated)
        suffixCount++
        if (accumulated.distanceToSqr(expectedMovement) <= SPEAR_KILL_PATH_SCHEDULE_EPSILON_SQUARED) {
            return suffixCount
        }
        if (accumulated.length() > expectedMovement.length() + SPEAR_KILL_PATH_SCHEDULE_EPSILON) {
            return null
        }
    }
    return null
}

/** Lower-bound travel ticks for approach packets only (no pre-hold, no strike hold). */
internal fun spearKillApproachTravelLowerBound(
    approachStepCount: Int,
    stepWaitTicks: Int,
): Int {
    if (approachStepCount <= 0) return 0
    return spearKillPacketTravelTicks(approachStepCount, stepWaitTicks)
}

/**
 * Optimistic hit tick before pathfinding. Detours can only add approach packets, so candidates
 * above an existing best hit tick can be discarded without a world-backed search.
 */
internal fun spearKillAStarCandidateLowerBoundHitTick(
    routeOrigin: Vec3,
    plannerGoal: Vec3,
    stepLimit: Double,
    terminalLungeDistance: Double,
    stepWaitTicks: Int,
    strikeHoldTicks: Int,
): Int {
    if (!routeOrigin.isFinite() || !plannerGoal.isFinite()) {
        return Int.MAX_VALUE
    }
    if (!stepLimit.isFinite() || stepLimit <= 0.0 ||
        !terminalLungeDistance.isFinite() || terminalLungeDistance <= 0.0
    ) {
        return Int.MAX_VALUE
    }

    val approachSteps = ceil(routeOrigin.distanceTo(plannerGoal) / stepLimit).toInt()
    val terminalSteps = ceil(terminalLungeDistance / stepLimit).toInt().coerceAtLeast(1)
    val outboundSteps = approachSteps + terminalSteps
    return buildSpearKillAStarPathSchedule(
        outboundStepCount = outboundSteps,
        stepWaitTicks = stepWaitTicks,
        terminalSuffixCount = terminalSteps,
        strikeHoldTicks = strikeHoldTicks,
    )?.hitTick ?: Int.MAX_VALUE
}

internal fun shouldRefineSpearKillAStarApproach(seedPosition: Vec3, hitPosition: Vec3): Boolean =
    horizontalDistanceSquared(seedPosition, hitPosition) > SPEAR_KILL_A_STAR_REFINEMENT_DISTANCE_SQUARED

internal fun hasSpearKillScheduleDamageWindow(
    ticksUsingItem: Int,
    damageUseDuration: Int,
    hitTick: Int,
): Boolean {
    if (ticksUsingItem < 0 || damageUseDuration < 0 || hitTick < 0) return false
    return ticksUsingItem + hitTick <= damageUseDuration
}

/** Server tick at which a direct Packet lunge should deal kinetic damage. */
internal fun spearKillDirectPacketHitTicks(stepCount: Int, stepWaitTicks: Int): Int =
    checkNotNull(buildSpearKillPathSchedule(
        outboundStepCount = stepCount,
        stepWaitTicks = stepWaitTicks,
        terminalSuffixCount = 1,
        preStrikeHoldTicks = 0,
        strikeHoldTicks = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
    )).hitTick

/** Prefer earlier kinetic hits; break ties with fewer outbound packets. */
internal fun isBetterSpearKillTimedAStarPlan(
    candidateHitTick: Int,
    candidateOutboundSteps: Int,
    bestHitTick: Int,
    bestOutboundSteps: Int,
): Boolean = when {
    candidateHitTick < bestHitTick -> true
    candidateHitTick > bestHitTick -> false
    else -> candidateOutboundSteps < bestOutboundSteps
}

internal fun horizontalDistanceSquared(left: Vec3, right: Vec3): Double {
    val dx = left.x - right.x
    val dz = left.z - right.z
    return dx * dx + dz * dz
}

private fun Vec3.isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

internal const val SPEAR_KILL_A_STAR_REFINEMENT_DISTANCE_SQUARED = 0.25
private const val SPEAR_KILL_PATH_SCHEDULE_EPSILON = 1.0E-6
private const val SPEAR_KILL_PATH_SCHEDULE_EPSILON_SQUARED = 1.0E-12
