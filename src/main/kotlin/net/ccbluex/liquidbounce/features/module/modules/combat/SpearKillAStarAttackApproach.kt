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

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** A collision-safe point A* reaches before the spear performs its final forward movement. */
internal data class SpearKillAStarAttackApproach(
    val plannerGoal: Vec3,
    val terminalWaypoint: Vec3,
)

internal fun createSpearKillAStarAttackApproach(
    targetHitPoint: Vec3,
    playerEyeOffset: Vec3,
    lookDirection: Vec3,
    terminalLungeDistance: Double = SPEAR_KILL_A_STAR_DEFAULT_TERMINAL_LUNGE_DISTANCE,
): SpearKillAStarAttackApproach? {
    if (!targetHitPoint.isFinite() || !playerEyeOffset.isFinite() || !lookDirection.isFinite() ||
        !terminalLungeDistance.isFinite() || terminalLungeDistance <= 0.0
    ) {
        return null
    }
    val forward = lookDirection.horizontalDirection() ?: return null

    val terminalWaypoint = targetHitPoint
        .subtract(forward.scale(SPEAR_KILL_A_STAR_TARGET_STAND_OFF))
        .subtract(playerEyeOffset)
    return SpearKillAStarAttackApproach(
        plannerGoal = terminalWaypoint.subtract(forward.scale(terminalLungeDistance)),
        terminalWaypoint = terminalWaypoint,
    )
}

/** Places the player above the target for one straight, downward kinetic lunge. */
internal fun createSpearKillVerticalDiveAttackApproach(
    targetBox: AABB,
    targetEyePosition: Vec3,
    playerEyeOffset: Vec3,
    terminalLungeDistance: Double,
): SpearKillAStarAttackApproach? {
    if (!targetEyePosition.isFinite() || !playerEyeOffset.isFinite() ||
        !terminalLungeDistance.isFinite() || terminalLungeDistance <= 0.0
    ) {
        return null
    }

    val downward = Vec3(0.0, -1.0, 0.0)
    val terminalEyePosition = targetEyePosition.subtract(
        downward.scale(SPEAR_KILL_A_STAR_TARGET_STAND_OFF),
    )
    val hitPoint = targetBox.clip(
        terminalEyePosition,
        terminalEyePosition.add(downward.scale(SPEAR_KILL_A_STAR_CANDIDATE_RAY_RANGE)),
    ).orElse(null) ?: return null
    val terminalWaypoint = hitPoint
        .subtract(downward.scale(SPEAR_KILL_A_STAR_TARGET_STAND_OFF))
        .subtract(playerEyeOffset)
    return SpearKillAStarAttackApproach(
        plannerGoal = terminalWaypoint.subtract(downward.scale(terminalLungeDistance)),
        terminalWaypoint = terminalWaypoint,
    )
}

/** Builds horizontal alternatives so the final spear lunge never descends onto the target. */
internal fun createSpearKillAStarAttackApproachCandidates(
    targetBox: AABB,
    targetEyePosition: Vec3,
    playerEyeOffset: Vec3,
    preferredDirection: Vec3,
    terminalLungeDistance: Double = SPEAR_KILL_A_STAR_DEFAULT_TERMINAL_LUNGE_DISTANCE,
    bearingCount: Int = SPEAR_KILL_A_STAR_APPROACH_BEARING_COUNT,
): List<SpearKillAStarAttackApproach> {
    if (!targetEyePosition.isFinite() || !playerEyeOffset.isFinite()) return emptyList()

    return spearKillAStarLungeDirections(preferredDirection, bearingCount).mapNotNull { direction ->
        val terminalEyePosition = targetEyePosition.subtract(direction.scale(SPEAR_KILL_A_STAR_TARGET_STAND_OFF))
        val hitPoint = targetBox.clip(
            terminalEyePosition,
            terminalEyePosition.add(direction.scale(SPEAR_KILL_A_STAR_CANDIDATE_RAY_RANGE)),
        ).orElse(null) ?: return@mapNotNull null
        createSpearKillAStarAttackApproach(hitPoint, playerEyeOffset, direction, terminalLungeDistance)
    }
}

/** Keeps only attack sides whose complete long terminal lunge is collision-safe. */
internal fun filterSpearKillAStarApproachesByTerminalClearance(
    approaches: List<SpearKillAStarAttackApproach>,
    segmentValidator: SpearKillAStarSegmentValidator,
): List<SpearKillAStarAttackApproach> = approaches.filter { approach ->
    segmentValidator.isClear(approach.plannerGoal, approach.terminalWaypoint)
}

/**
 * Ensures the outbound route ends with the approach's terminal lunge.
 *
 * The lunge length is independent of [stepLimit]; Packet emission may split it into full steps plus
 * an exact remainder that lands on [SpearKillAStarAttackApproach.terminalWaypoint].
 */
@Suppress("ReturnCount")
internal fun isSpearKillAStarTerminalStepValid(
    outboundMovements: List<Vec3>,
    approach: SpearKillAStarAttackApproach,
    stepLimit: Double,
): Boolean {
    if (outboundMovements.isEmpty() || !stepLimit.isFinite() || stepLimit <= 0.0) return false

    val expectedMovement = approach.terminalWaypoint.subtract(approach.plannerGoal)
    if (!expectedMovement.isFinite() ||
        expectedMovement.lengthSqr() <= SPEAR_KILL_A_STAR_TERMINAL_SPEED_EPSILON_SQUARED
    ) {
        return false
    }

    var accumulated = Vec3.ZERO
    for (step in outboundMovements.asReversed()) {
        if (!step.isFinite() || step.length() > stepLimit + SPEAR_KILL_A_STAR_TERMINAL_SPEED_EPSILON) {
            return false
        }

        accumulated = step.add(accumulated)
        if (accumulated.distanceToSqr(expectedMovement) <= SPEAR_KILL_A_STAR_TERMINAL_SPEED_EPSILON_SQUARED) {
            return true
        }
        if (accumulated.length() > expectedMovement.length() + SPEAR_KILL_A_STAR_TERMINAL_SPEED_EPSILON) {
            return false
        }
    }
    return false
}

/** Estimates target travel time using the Packet mode's one shared inter-step wait. */
internal fun spearKillAStarPredictionTicks(
    distance: Double,
    maxSpeed: Double,
    stepWaitTicks: Int,
): Int {
    require(distance.isFinite() && distance >= 0.0) { "Target distance must be finite and non-negative" }
    require(maxSpeed.isFinite() && maxSpeed > 0.0) { "Maximum speed must be finite and positive" }

    val stepCount = ceil(distance / maxSpeed).toInt().coerceAtLeast(1)
    return spearKillPacketTravelTicks(stepCount, stepWaitTicks)
}

/** Uses the built route, not straight-line distance, to predict when the terminal lunge is sent. */
internal fun spearKillAStarArrivalTicks(
    outboundStepCount: Int,
    stepWaitTicks: Int,
    preStrikeHoldTicks: Int,
): Int {
    require(preStrikeHoldTicks >= 0) { "Pre-strike hold duration must not be negative" }
    return spearKillPacketTravelTicks(outboundStepCount, stepWaitTicks) + preStrikeHoldTicks
}

/** Prevents a route from starting when the kinetic weapon's finite damage window would expire. */
internal fun hasSpearKillDamageWindow(
    ticksUsingItem: Int,
    damageUseDuration: Int,
    arrivalTicks: Int,
    confirmationTicks: Int,
): Boolean {
    if (ticksUsingItem < 0 || damageUseDuration < 0 || arrivalTicks < 0 || confirmationTicks < 0) return false
    return ticksUsingItem + arrivalTicks + confirmationTicks <= damageUseDuration
}

/** Evenly spaced horizontal bearings with [preferredDirection] first. */
internal fun spearKillAStarLungeDirections(
    preferredDirection: Vec3,
    bearingCount: Int = SPEAR_KILL_A_STAR_APPROACH_BEARING_COUNT,
): List<Vec3> {
    if (bearingCount <= 0 ||
        !preferredDirection.isFinite() ||
        preferredDirection.lengthSqr() <= SPEAR_KILL_A_STAR_APPROACH_EPSILON_SQUARED
    ) {
        return emptyList()
    }

    val forward = preferredDirection.horizontalDirection() ?: Vec3(1.0, 0.0, 0.0)
    val baseAngle = atan2(forward.z, forward.x)
    return buildList(bearingCount) {
        add(forward)
        for (step in 1..bearingCount / 2) {
            val offset = 2.0 * PI * step / bearingCount
            add(Vec3(cos(baseAngle + offset), 0.0, sin(baseAngle + offset)))
            if (size < bearingCount && step * 2 != bearingCount) {
                add(Vec3(cos(baseAngle - offset), 0.0, sin(baseAngle - offset)))
            }
        }
    }
}

private fun Vec3.horizontalDirection(): Vec3? = Vec3(x, 0.0, z)
    .takeIf { it.isFinite() && it.lengthSqr() > SPEAR_KILL_A_STAR_APPROACH_EPSILON_SQUARED }
    ?.normalize()

private fun Vec3.isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

private const val SPEAR_KILL_A_STAR_DEFAULT_TERMINAL_LUNGE_DISTANCE = 7.0
internal const val SPEAR_KILL_A_STAR_APPROACH_BEARING_COUNT = 12
// Vanilla spears ignore the first two blocks of their attack ray. Stop just outside that inner
// dead-zone so the terminal pose lands close enough for reliable kinetic hits.
private const val SPEAR_KILL_A_STAR_TARGET_STAND_OFF = 2.25
private const val SPEAR_KILL_A_STAR_CANDIDATE_RAY_RANGE = 4.5
private const val SPEAR_KILL_A_STAR_APPROACH_EPSILON_SQUARED = 1.0E-18
private const val SPEAR_KILL_A_STAR_TERMINAL_SPEED_EPSILON = 1.0E-6
private const val SPEAR_KILL_A_STAR_TERMINAL_SPEED_EPSILON_SQUARED = 1.0E-12
