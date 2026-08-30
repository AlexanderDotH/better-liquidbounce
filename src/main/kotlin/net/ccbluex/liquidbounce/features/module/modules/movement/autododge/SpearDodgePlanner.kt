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

import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.SpearTeleportDirection

import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

class SpearDodgePlanner(private val random: Random = Random.Default) {
    fun plan(
        attackerPosition: HorizontalPosition,
        playerPosition: HorizontalPosition,
        startedSafelyGrounded: Boolean,
        safeDistance: Double,
        attackDirection: SpearTeleportDirection? = null,
        simulate: (DirectionalInput) -> SpearMovementSimulation,
    ): SpearDodgePlan {
        require(safeDistance.isFinite() && safeDistance >= 0.0) { "Safe distance must be finite and non-negative" }
        val attackAxis = AttackAxis.from(attackerPosition, playerPosition, attackDirection)
            ?: return SpearDodgePlan.NONE
        val candidates = CANDIDATE_INPUTS.mapNotNull { input ->
            evaluate(input, simulate(input), playerPosition, attackerPosition, attackAxis, startedSafelyGrounded)
        }
        val selected = selectNearBest(candidates) ?: return SpearDodgePlan.NONE
        return selected.toPlan(useTimer = selected.minimumClearance < safeDistance)
    }

    fun isSafeSimulation(
        simulation: SpearMovementSimulation,
        playerPosition: HorizontalPosition,
        startedSafelyGrounded: Boolean,
    ): Boolean {
        if (simulation.isUnsafeSpearMovement(startedSafelyGrounded)) return false
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
        if (!isSafeSimulation(simulation, playerPosition, startedSafelyGrounded)) return null
        val finalPosition = simulation.samples.last().position
        return ScoredCandidate(
            input,
            simulation.samples.minOf { attackAxis.clearanceOf(it.position) },
            finalPosition.distanceTo(attackerPosition),
        )
    }

    private fun selectNearBest(candidates: List<ScoredCandidate>): ScoredCandidate? {
        val bestClearance = candidates.maxOfOrNull(ScoredCandidate::minimumClearance) ?: return null
        val nearBestClearance = candidates.filter { it.minimumClearance >= bestClearance * NEAR_BEST_FACTOR }
        val bestDistance = nearBestClearance.maxOf(ScoredCandidate::distanceFromAttacker)
        val nearBest = nearBestClearance.filter { it.distanceFromAttacker >= bestDistance * NEAR_BEST_FACTOR }
        return nearBest[random.nextInt(nearBest.size)]
    }

    private data class ScoredCandidate(
        val input: DirectionalInput,
        val minimumClearance: Double,
        val distanceFromAttacker: Double,
    ) {
        fun toPlan(useTimer: Boolean) = SpearDodgePlan(input, minimumClearance, distanceFromAttacker, useTimer)
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
            fun from(
                attacker: HorizontalPosition,
                player: HorizontalPosition,
                attackDirection: SpearTeleportDirection?,
            ): AttackAxis? {
                val direction = attackDirection?.normalizedOrNull()
                return if (direction == null) {
                    between(attacker, player)
                } else {
                    AttackAxis(attacker, direction.x, direction.z, length = 1.0)
                }
            }

            private fun between(attacker: HorizontalPosition, player: HorizontalPosition): AttackAxis? {
                val x = player.x - attacker.x
                val z = player.z - attacker.z
                val lengthSquared = x * x + z * z
                if (lengthSquared <= MINIMUM_AXIS_LENGTH_SQUARED) return null
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
