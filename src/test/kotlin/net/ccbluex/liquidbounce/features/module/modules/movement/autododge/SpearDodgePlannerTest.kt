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
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SpearDodgePlannerTest {

    @Test
    fun `evaluates all eight movement directions`() {
        val evaluated = mutableListOf<DirectionalInput>()

        planner().plan(ATTACKER, PLAYER, startedSafelyGrounded = true, SAFE_DISTANCE) { input ->
            evaluated += input
            safeSimulation(clearance = clearanceFor(input))
        }

        assertEquals(SpearDodgePlanner.CANDIDATE_INPUTS, evaluated)
    }

    @Test
    fun `prefers perpendicular clearance over distance from attacker`() {
        val result = planner().plan(ATTACKER, PLAYER, startedSafelyGrounded = true, SAFE_DISTANCE) { input ->
            when (input) {
                DirectionalInput.LEFT -> safeSimulation(clearance = 3.0, z = 10.0)
                DirectionalInput.RIGHT -> safeSimulation(clearance = 2.6, z = 30.0)
                else -> collidingSimulation()
            }
        }

        assertEquals(DirectionalInput.LEFT, result.directionalInput)
    }

    @Test
    fun `uses distance from attacker to break equal-clearance candidates`() {
        val result = planner().plan(ATTACKER, PLAYER, startedSafelyGrounded = true, SAFE_DISTANCE) { input ->
            when (input) {
                DirectionalInput.LEFT -> safeSimulation(clearance = 3.0, z = 10.0)
                DirectionalInput.RIGHT -> safeSimulation(clearance = 3.0, z = 20.0)
                else -> collidingSimulation()
            }
        }

        assertEquals(DirectionalInput.RIGHT, result.directionalInput)
    }

    @Test
    fun `scores the minimum clearance across the full simulation`() {
        val result = planner().plan(ATTACKER, PLAYER, startedSafelyGrounded = true, SAFE_DISTANCE) { input ->
            when (input) {
                DirectionalInput.LEFT -> simulationWithClearances(0.5, 3.0, 3.0)
                DirectionalInput.RIGHT -> simulationWithClearances(1.0, 1.0, 1.0)
                else -> collidingSimulation()
            }
        }

        assertEquals(DirectionalInput.RIGHT, result.directionalInput)
    }

    @Test
    fun `uses the remote look direction instead of the stale attacker to player axis`() {
        val result = planner().plan(
            attackerPosition = ATTACKER,
            playerPosition = PLAYER,
            attackDirection = SpearTeleportDirection(1.0, 0.0),
            startedSafelyGrounded = true,
            safeDistance = SAFE_DISTANCE,
        ) { input ->
            when (input) {
                DirectionalInput.LEFT -> safeSimulation(clearance = 3.0, z = 1.0)
                DirectionalInput.RIGHT -> safeSimulation(clearance = 1.0, z = 3.0)
                else -> collidingSimulation()
            }
        }

        assertEquals(DirectionalInput.RIGHT, result.directionalInput)
    }

    @Test
    fun `random choice among near-best candidates is reproducible with a seed`() {
        val firstPlanner = SpearDodgePlanner(Random(0x5EED))
        val secondPlanner = SpearDodgePlanner(Random(0x5EED))

        val firstSequence = List(20) { firstPlanner.planNearBestInput() }
        val secondSequence = List(20) { secondPlanner.planNearBestInput() }

        assertEquals(firstSequence, secondSequence)
        assertTrue(firstSequence.distinct().size > 1)
    }

    @Test
    fun `rejects collision edge void and negligible movement outcomes`() {
        val result = planner().plan(ATTACKER, PLAYER, startedSafelyGrounded = true, SAFE_DISTANCE) { input ->
            when (input) {
                DirectionalInput.FORWARDS -> collidingSimulation()
                DirectionalInput.BACKWARDS -> unsafeGroundSimulation(supported = false, overVoid = false)
                DirectionalInput.LEFT -> unsafeGroundSimulation(supported = true, overVoid = true)
                DirectionalInput.RIGHT -> negligibleSimulation()
                DirectionalInput.FORWARDS_LEFT -> safeSimulation(clearance = 2.0)
                else -> collidingSimulation()
            }
        }

        assertEquals(DirectionalInput.FORWARDS_LEFT, result.directionalInput)
    }

    @Test
    fun `unsupported samples remain eligible when player did not start safely grounded`() {
        val result = planner().plan(ATTACKER, PLAYER, startedSafelyGrounded = false, SAFE_DISTANCE) { input ->
            if (input == DirectionalInput.LEFT) {
                unsafeGroundSimulation(supported = false, overVoid = true)
            } else {
                collidingSimulation()
            }
        }

        assertEquals(DirectionalInput.LEFT, result.directionalInput)
    }

    @Test
    fun `returns none when every simulation is unsafe`() {
        val result = planner().plan(ATTACKER, PLAYER, startedSafelyGrounded = true, SAFE_DISTANCE) {
            collidingSimulation()
        }

        assertEquals(DirectionalInput.NONE, result.directionalInput)
        assertFalse(result.useTimer)
    }

    @Test
    fun `requests timer below supplied safe clearance`() {
        val result = planner().plan(ATTACKER, PLAYER, startedSafelyGrounded = true, SAFE_DISTANCE) { input ->
            if (input == DirectionalInput.LEFT) {
                safeSimulation(clearance = SAFE_DISTANCE - 0.1)
            } else {
                collidingSimulation()
            }
        }

        assertTrue(result.useTimer)
    }

    @Test
    fun `does not request timer at supplied safe clearance`() {
        val result = planner().plan(ATTACKER, PLAYER, startedSafelyGrounded = true, SAFE_DISTANCE) { input ->
            if (input == DirectionalInput.LEFT) {
                safeSimulation(clearance = SAFE_DISTANCE)
            } else {
                collidingSimulation()
            }
        }

        assertFalse(result.useTimer)
    }

    @Test
    fun `commitment holds input for configured ticks`() {
        val commitment = SpearJukeCommitment(Random(1))
        var replans = 0

        fun update() = commitment.update(
            durationTicks = 2..2,
            isCurrentInputSafe = { true },
        ) {
            replans++
            planFor(if (replans == 1) DirectionalInput.LEFT else DirectionalInput.RIGHT)
        }

        val first = update()
        val second = update()
        val third = update()

        assertEquals(DirectionalInput.LEFT, first.plan.directionalInput)
        assertEquals(2, first.ticksRemaining)
        assertTrue(first.replanned)
        assertEquals(DirectionalInput.LEFT, second.plan.directionalInput)
        assertEquals(1, second.ticksRemaining)
        assertFalse(second.replanned)
        assertEquals(DirectionalInput.RIGHT, third.plan.directionalInput)
        assertEquals(2, replans)
    }

    @Test
    fun `default commitment duration is seeded and stays between two and five ticks`() {
        val first = SpearJukeCommitment(Random(0xC0FFEE))
        val second = SpearJukeCommitment(Random(0xC0FFEE))

        fun durations(commitment: SpearJukeCommitment) = List(20) {
            commitment.reset()
            commitment.update(isCurrentInputSafe = { true }) { planFor(DirectionalInput.LEFT) }.ticksRemaining
        }

        val firstDurations = durations(first)
        val secondDurations = durations(second)

        assertEquals(firstDurations, secondDurations)
        assertTrue(firstDurations.all { it in 2..5 })
        assertTrue(firstDurations.distinct().size > 1)
    }

    @Test
    fun `unsafe committed input replans immediately`() {
        val commitment = SpearJukeCommitment(Random(2))
        var plannedInput = DirectionalInput.LEFT

        commitment.update(5..5, isCurrentInputSafe = { true }) { planFor(plannedInput) }
        plannedInput = DirectionalInput.RIGHT
        val result = commitment.update(5..5, isCurrentInputSafe = { false }) { planFor(plannedInput) }

        assertEquals(DirectionalInput.RIGHT, result.plan.directionalInput)
        assertTrue(result.replanned)
        assertEquals(5, result.ticksRemaining)
    }

    @Test
    fun `none result is never committed`() {
        val commitment = SpearJukeCommitment(Random(3))
        var replans = 0

        repeat(2) {
            commitment.update(5..5, isCurrentInputSafe = { true }) {
                replans++
                SpearDodgePlan.NONE
            }
        }

        assertEquals(2, replans)
    }

    @Test
    fun `projectile movement always wins arbitration`() {
        val projectile = DodgePlan(DirectionalInput.FORWARDS, shouldJump = true, yawChange = 45.0F, useTimer = false)
        val spear = planFor(DirectionalInput.LEFT)

        val result = AutoDodgeMovementArbitrator.choose(projectile, spear)

        assertSame(projectile, result)
    }

    @Test
    fun `spear movement is adapted without rotation or jump`() {
        val result = AutoDodgeMovementArbitrator.choose(
            projectilePlan = null,
            spearPlan = planFor(DirectionalInput.LEFT, useTimer = true),
        )

        assertEquals(
            DodgePlan(DirectionalInput.LEFT, shouldJump = false, yawChange = null, useTimer = true),
            result,
        )
    }

    private fun SpearDodgePlanner.planNearBestInput(): DirectionalInput {
        return plan(ATTACKER, PLAYER, startedSafelyGrounded = true, SAFE_DISTANCE) { input ->
            when (input) {
                DirectionalInput.LEFT -> safeSimulation(clearance = 3.0, z = 20.0)
                DirectionalInput.RIGHT -> safeSimulation(clearance = 2.8, z = 20.0)
                else -> collidingSimulation()
            }
        }.directionalInput
    }

    private fun planner() = SpearDodgePlanner(Random(1))

    private fun planFor(input: DirectionalInput, useTimer: Boolean = false) = SpearDodgePlan(
        input = input,
        minimumClearance = 2.0,
        distanceFromAttacker = 12.0,
        useTimer = useTimer,
    )

    private fun safeSimulation(
        clearance: Double,
        z: Double = 10.0,
    ) = SpearMovementSimulation(
        listOf(
            sample(clearance, z),
            sample(clearance, z + 0.5),
            sample(clearance, z + 1.0),
        ),
    )

    private fun collidingSimulation() = SpearMovementSimulation(
        listOf(
            sample(1.0, colliding = true),
            sample(2.0),
            sample(3.0),
        ),
    )

    private fun unsafeGroundSimulation(
        supported: Boolean,
        overVoid: Boolean,
    ) = SpearMovementSimulation(
        listOf(
            sample(1.0),
            sample(2.0, supported = supported, overVoid = overVoid),
            sample(3.0),
        ),
    )

    private fun negligibleSimulation() = SpearMovementSimulation(
        listOf(
            sample(0.01),
            sample(0.02),
            sample(0.03),
        ),
    )

    private fun simulationWithClearances(vararg clearances: Double) = SpearMovementSimulation(
        clearances.mapIndexed { tick, clearance -> sample(clearance, z = 10.0 + tick) },
    )

    private fun sample(
        x: Double,
        z: Double = 10.0,
        colliding: Boolean = false,
        supported: Boolean = true,
        overVoid: Boolean = false,
    ) = SpearMovementSample(HorizontalPosition(x, z), colliding, supported, overVoid)

    private fun clearanceFor(input: DirectionalInput): Double {
        return SpearDodgePlanner.CANDIDATE_INPUTS.indexOf(input).toDouble() + 1.0
    }

    companion object {
        private val ATTACKER = HorizontalPosition(0.0, 0.0)
        private val PLAYER = HorizontalPosition(0.0, 10.0)
        private const val SAFE_DISTANCE = 1.5
    }
}
