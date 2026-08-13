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
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

data class HorizontalPosition(
    val x: Double,
    val z: Double,
) {
    init {
        require(x.isFinite() && z.isFinite()) { "A horizontal position must be finite" }
    }

    fun distanceTo(other: HorizontalPosition): Double = hypot(x - other.x, z - other.z)
}

/** One post-tick observation supplied by the Minecraft simulation adapter. */
data class SpearMovementSample(
    val position: HorizontalPosition,
    val colliding: Boolean = false,
    val supported: Boolean = true,
    val overVoid: Boolean = false,
)

data class SpearMovementSimulation(
    val samples: List<SpearMovementSample>,
) {
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

    fun asDodgePlan() = DodgePlan(
        directionalInput = input,
        shouldJump = false,
        yawChange = null,
        useTimer = useTimer,
    )

    companion object {
        val NONE = SpearDodgePlan(
            input = DirectionalInput.NONE,
            minimumClearance = 0.0,
            distanceFromAttacker = 0.0,
            useTimer = false,
        )
    }
}

/**
 * Pure spear-juke policy. Minecraft movement simulation is intentionally supplied by the caller.
 */
class SpearDodgePlanner(
    private val random: Random = Random.Default,
) {
    fun plan(
        attackerPosition: HorizontalPosition,
        playerPosition: HorizontalPosition,
        startedSafelyGrounded: Boolean,
        safeDistance: Double,
        simulate: (DirectionalInput) -> SpearMovementSimulation,
    ): SpearDodgePlan {
        require(safeDistance.isFinite() && safeDistance >= 0.0) { "Safe distance must be finite and non-negative" }

        val attackAxis = AttackAxis.between(attackerPosition, playerPosition) ?: return SpearDodgePlan.NONE
        val candidates = CANDIDATE_INPUTS.mapNotNull { input ->
            evaluate(
                input = input,
                simulation = simulate(input),
                playerPosition = playerPosition,
                attackerPosition = attackerPosition,
                attackAxis = attackAxis,
                startedSafelyGrounded = startedSafelyGrounded,
            )
        }
        val selected = selectNearBest(candidates) ?: return SpearDodgePlan.NONE

        return selected.toPlan(useTimer = selected.minimumClearance < safeDistance)
    }

    fun isSafeSimulation(
        simulation: SpearMovementSimulation,
        playerPosition: HorizontalPosition,
        startedSafelyGrounded: Boolean,
    ): Boolean {
        if (simulation.isUnsafe(startedSafelyGrounded)) {
            return false
        }

        return simulation.samples.last().position.distanceTo(playerPosition) >= MINIMUM_HORIZONTAL_DISPLACEMENT
    }

    private fun evaluate(
        input: DirectionalInput,
        simulation: SpearMovementSimulation,
        playerPosition: HorizontalPosition,
        attackerPosition: HorizontalPosition,
        attackAxis: AttackAxis,
        startedSafelyGrounded: Boolean,
    ): ScoredCandidate? {
        if (!isSafeSimulation(simulation, playerPosition, startedSafelyGrounded)) {
            return null
        }

        val finalPosition = simulation.samples.last().position
        return ScoredCandidate(
            input = input,
            minimumClearance = simulation.samples.minOf { attackAxis.clearanceOf(it.position) },
            distanceFromAttacker = finalPosition.distanceTo(attackerPosition),
        )
    }

    private fun selectNearBest(candidates: List<ScoredCandidate>): ScoredCandidate? {
        val bestClearance = candidates.maxOfOrNull(ScoredCandidate::minimumClearance) ?: return null
        val nearBestClearance = candidates.filter {
            it.minimumClearance >= bestClearance * NEAR_BEST_FACTOR
        }
        val bestDistance = nearBestClearance.maxOf(ScoredCandidate::distanceFromAttacker)
        val nearBest = nearBestClearance.filter {
            it.distanceFromAttacker >= bestDistance * NEAR_BEST_FACTOR
        }

        return nearBest[random.nextInt(nearBest.size)]
    }

    private data class ScoredCandidate(
        val input: DirectionalInput,
        val minimumClearance: Double,
        val distanceFromAttacker: Double,
    ) {
        fun toPlan(useTimer: Boolean) = SpearDodgePlan(
            input = input,
            minimumClearance = minimumClearance,
            distanceFromAttacker = distanceFromAttacker,
            useTimer = useTimer,
        )
    }

    private data class AttackAxis(
        val origin: HorizontalPosition,
        val x: Double,
        val z: Double,
        val length: Double,
    ) {
        fun clearanceOf(position: HorizontalPosition): Double {
            val relativeX = position.x - origin.x
            val relativeZ = position.z - origin.z
            return abs(x * relativeZ - z * relativeX) / length
        }

        companion object {
            fun between(attacker: HorizontalPosition, player: HorizontalPosition): AttackAxis? {
                val x = player.x - attacker.x
                val z = player.z - attacker.z
                val lengthSquared = x * x + z * z
                if (lengthSquared <= MINIMUM_AXIS_LENGTH_SQUARED) {
                    return null
                }

                return AttackAxis(attacker, x, z, sqrt(lengthSquared))
            }
        }
    }

    companion object {
        const val SIMULATION_TICKS = 3

        val CANDIDATE_INPUTS = listOf(
            DirectionalInput.FORWARDS,
            DirectionalInput.FORWARDS_RIGHT,
            DirectionalInput.RIGHT,
            DirectionalInput.BACKWARDS_RIGHT,
            DirectionalInput.BACKWARDS,
            DirectionalInput.BACKWARDS_LEFT,
            DirectionalInput.LEFT,
            DirectionalInput.FORWARDS_LEFT,
        )

        private const val NEAR_BEST_FACTOR = 0.9
        private const val MINIMUM_HORIZONTAL_DISPLACEMENT = 0.05
        private const val MINIMUM_AXIS_LENGTH_SQUARED = 1.0E-8
    }
}

private fun SpearMovementSimulation.isUnsafe(startedSafelyGrounded: Boolean): Boolean {
    if (samples.any(SpearMovementSample::colliding)) {
        return true
    }

    return startedSafelyGrounded && samples.any { !it.supported || it.overVoid }
}

data class SpearJukeDecision(
    val plan: SpearDodgePlan,
    val ticksRemaining: Int,
    val replanned: Boolean,
)

/** Keeps a safe juke long enough to prevent deterministic per-tick direction changes. */
class SpearJukeCommitment(
    private val random: Random = Random.Default,
) {
    private var active: ActiveCommitment? = null

    fun update(
        durationTicks: IntRange = DEFAULT_DURATION_TICKS,
        isCurrentInputSafe: (DirectionalInput) -> Boolean,
        replan: () -> SpearDodgePlan,
    ): SpearJukeDecision {
        require(
            !durationTicks.isEmpty() &&
                durationTicks.first >= MIN_DURATION_TICKS &&
                durationTicks.last <= MAX_DURATION_TICKS
        ) {
            "Juke duration must stay within $MIN_DURATION_TICKS..$MAX_DURATION_TICKS ticks"
        }

        val current = active
        if (current != null && isCurrentInputSafe(current.plan.input)) {
            return continueCommitment(current)
        }

        return beginCommitment(replan(), durationTicks)
    }

    fun reset() {
        active = null
    }

    private fun continueCommitment(current: ActiveCommitment): SpearJukeDecision {
        val remaining = current.ticksRemaining
        active = current.takeIf { remaining > 1 }?.copy(ticksRemaining = remaining - 1)
        return SpearJukeDecision(current.plan, remaining, replanned = false)
    }

    private fun beginCommitment(plan: SpearDodgePlan, durationTicks: IntRange): SpearJukeDecision {
        if (!plan.input.isMoving) {
            active = null
            return SpearJukeDecision(plan, ticksRemaining = 0, replanned = true)
        }

        val duration = random.nextInt(durationTicks.first, durationTicks.last + 1)
        active = ActiveCommitment(plan, ticksRemaining = duration - 1).takeIf { duration > 1 }
        return SpearJukeDecision(plan, duration, replanned = true)
    }

    private data class ActiveCommitment(
        val plan: SpearDodgePlan,
        val ticksRemaining: Int,
    )

    companion object {
        val DEFAULT_DURATION_TICKS = 2..5
        private const val MIN_DURATION_TICKS = 1
        private const val MAX_DURATION_TICKS = 10
    }
}

object AutoDodgeMovementArbitrator {
    fun choose(projectilePlan: DodgePlan?, spearPlan: SpearDodgePlan?): DodgePlan? {
        return projectilePlan ?: spearPlan?.asDodgePlan()
    }

    fun chooseAction(
        projectilePlan: DodgePlan?,
        spearTeleportPlan: SpearTeleportPlan?,
        spearPlan: SpearDodgePlan?,
    ): AutoDodgeMovementAction = when {
        projectilePlan != null -> AutoDodgeMovementAction.Dodge(projectilePlan)
        spearTeleportPlan != null -> AutoDodgeMovementAction.Teleport(spearTeleportPlan)
        spearPlan != null -> AutoDodgeMovementAction.Dodge(spearPlan.asDodgePlan())
        else -> AutoDodgeMovementAction.None
    }
}

sealed interface AutoDodgeMovementAction {
    data class Dodge(val plan: DodgePlan) : AutoDodgeMovementAction
    data class Teleport(val plan: SpearTeleportPlan) : AutoDodgeMovementAction
    data object None : AutoDodgeMovementAction
}
