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


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.spearKillPacketTravelTicks
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

/** Per-step outbound timing for Packet A* attacks, including terminal aim-lock and strike holds. */
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
internal fun spearKillDirectPacketHitTicks(
    stepCount: Int,
    stepWaitTicks: Int,
    strikeHoldTicks: Int = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
): Int =
    checkNotNull(buildSpearKillPathSchedule(
        outboundStepCount = stepCount,
        stepWaitTicks = stepWaitTicks,
        terminalSuffixCount = 1,
        preStrikeHoldTicks = SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS,
        strikeHoldTicks = strikeHoldTicks,
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
internal const val SPEAR_KILL_PATH_SCHEDULE_EPSILON = 1.0E-6
internal const val SPEAR_KILL_PATH_SCHEDULE_EPSILON_SQUARED = 1.0E-12
