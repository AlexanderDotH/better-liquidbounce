/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillFallSafetyLifecycleTest {

    @Test
    fun `airborne movement cannot satisfy a near-ground planned packet`() {
        val movement = Vec3(6.0, -10.0, 0.0)
        val plan = readyPlan(
            outboundMovements = listOf(movement),
            initialFallDistance = 0.0,
            groundedSteps = listOf(true, true),
        )
        val lifecycle = SpearKillFallSafetyLifecycle().apply { begin(plan) }

        assertEquals(
            SpearKillFallSafetyPendingStepGate.BLOCKED,
            lifecycle.gatePendingMovement(movement, physicallyNearGround = false),
        )
    }

    @Test
    fun `cancelled full speed grounded movement stays pending`() {
        val movement = Vec3(6.0, -10.0, 0.0)
        val lifecycle = lifecycleFor(
            outboundMovements = listOf(movement),
            initialFallDistance = 0.0,
            groundedSteps = listOf(true, true),
        )

        assertEquals(SpearKillFallSafetyPendingStepGate.CLEAR, lifecycle.gatePendingMovement(movement))
        assertTrue(lifecycle.shouldGroundPendingMovement(movement))
        assertFalse(lifecycle.confirmMovement(movement, delivered = false, exactPacketGrounded = false))
        assertEquals(0, lifecycle.confirmedMovementCount)
    }

    @Test
    fun `full speed descent advances only when its exact delivered packet was grounded`() {
        val movement = Vec3(6.0, -10.0, 0.0)
        val lifecycle = lifecycleFor(
            outboundMovements = listOf(movement),
            initialFallDistance = 0.0,
            groundedSteps = listOf(true, true),
        )

        assertEquals(SpearKillFallSafetyPendingStepGate.CLEAR, lifecycle.gatePendingMovement(movement))
        assertTrue(lifecycle.shouldGroundPendingMovement(movement))
        assertFalse(lifecycle.confirmMovement(movement, delivered = true, exactPacketGrounded = false))
        assertEquals(0, lifecycle.confirmedMovementCount)

        assertTrue(lifecycle.confirmMovement(movement, delivered = true, exactPacketGrounded = true))
        assertEquals(1, lifecycle.confirmedMovementCount)
        assertEquals(0.0, lifecycle.confirmedFallDistance, 1.0E-9)
    }

    @Test
    fun `different pending movement is rejected without advancing`() {
        val expected = Vec3(1.0, 0.0, 0.0)
        val lifecycle = lifecycleFor(listOf(expected))

        assertEquals(
            SpearKillFallSafetyPendingStepGate.BLOCKED,
            lifecycle.gatePendingMovement(Vec3(2.0, 0.0, 0.0)),
        )
        assertFalse(lifecycle.confirmMovement(Vec3(2.0, 0.0, 0.0), delivered = true))
        assertEquals(0, lifecycle.confirmedMovementCount)
    }

    @Test
    fun `finish first requests grounding and resets locally only after confirmation`() {
        val lifecycle = lifecycleFor(emptyList())

        val action = lifecycle.finish(
            finalPositionKnown = true,
            connectionOpen = true,
            physicallyNearGround = true,
        )

        assertFalse(action.resetLocalFallDistance)
        assertTrue(action.sendGroundedPacket)
        assertTrue(lifecycle.active)

        assertTrue(lifecycle.confirmGrounding(delivered = true))

        val confirmedAction = lifecycle.finish(
            finalPositionKnown = true,
            connectionOpen = true,
            physicallyNearGround = true,
        )
        assertTrue(confirmedAction.resetLocalFallDistance)
        assertFalse(confirmedAction.sendGroundedPacket)
        assertFalse(lifecycle.active)
    }

    @Test
    fun `cancelled final grounding can be retried but never resets local fall distance`() {
        val lifecycle = lifecycleFor(emptyList())

        assertTrue(lifecycle.finish(
            finalPositionKnown = true,
            connectionOpen = true,
            physicallyNearGround = true,
        ).sendGroundedPacket)
        assertFalse(lifecycle.confirmGrounding(delivered = false))

        val retry = lifecycle.finish(
            finalPositionKnown = true,
            connectionOpen = true,
            physicallyNearGround = true,
        )
        assertFalse(retry.resetLocalFallDistance)
        assertTrue(retry.sendGroundedPacket)
    }

    @Test
    fun `disconnect invalidates lifecycle without claiming a reset`() {
        val lifecycle = lifecycleFor(emptyList())

        val action = lifecycle.finish(finalPositionKnown = false, connectionOpen = false)

        assertFalse(action.resetLocalFallDistance)
        assertFalse(action.sendGroundedPacket)
        assertFalse(lifecycle.active)
    }

    @Test
    fun `airborne finish never sends a blind ground packet`() {
        val lifecycle = lifecycleFor(emptyList())

        val action = lifecycle.finish(
            finalPositionKnown = true,
            connectionOpen = true,
            physicallyNearGround = false,
        )

        assertEquals(SpearKillFallSafetyFinishAction.NONE, action)
        assertFalse(lifecycle.active)
    }

    @Test
    fun `finish cannot skip undelivered route movements`() {
        val lifecycle = lifecycleFor(listOf(Vec3(1.0, 0.0, 0.0)))

        assertEquals(
            SpearKillFallSafetyFinishAction.NONE,
            lifecycle.finish(finalPositionKnown = true, connectionOpen = true),
        )
        assertTrue(lifecycle.active)
    }

    @Test
    fun `invalidation and replan restart from a new confirmed state`() {
        val first = readyPlan(
            outboundMovements = listOf(Vec3(1.0, -1.0, 0.0)),
            initialFallDistance = 0.0,
        )
        val second = readyPlan(
            outboundMovements = listOf(Vec3(2.0, 0.0, 0.0)),
            initialFallDistance = 1.0,
        )
        val lifecycle = SpearKillFallSafetyLifecycle()
        lifecycle.begin(first)

        assertTrue(lifecycle.confirmMovement(first.steps.first().movement, delivered = true))
        lifecycle.invalidate()
        assertFalse(lifecycle.active)

        lifecycle.replan(second)
        assertTrue(lifecycle.active)
        assertEquals(0, lifecycle.confirmedMovementCount)
        assertEquals(1.0, lifecycle.confirmedFallDistance, 1.0E-9)
    }

    @Test
    fun `direct and network style round trips finish only after every delivery`() {
        val routes = listOf(
            listOf(Vec3(2.0, -1.0, 0.0)),
            listOf(
                Vec3(1.0, -0.75, 0.5),
                Vec3(1.0, -0.75, 0.5),
                Vec3(1.0, -0.75, 0.5),
            ),
        )

        for (outbound in routes) {
            val plan = readyPlan(outbound, initialFallDistance = 0.0)
            val lifecycle = SpearKillFallSafetyLifecycle().apply { begin(plan) }
            for (step in plan.steps) {
                assertEquals(
                    SpearKillFallSafetyPendingStepGate.CLEAR,
                    lifecycle.gatePendingMovement(step.movement),
                )
                assertTrue(lifecycle.confirmMovement(
                    movement = step.movement,
                    delivered = true,
                    exactPacketGrounded = lifecycle.shouldGroundPendingMovement(step.movement),
                ))
            }

            assertTrue(lifecycle.finish(
                finalPositionKnown = true,
                connectionOpen = true,
                physicallyNearGround = true,
            ).sendGroundedPacket)
            assertTrue(lifecycle.confirmGrounding(delivered = true))
            assertTrue(
                lifecycle.finish(
                    finalPositionKnown = true,
                    connectionOpen = true,
                    physicallyNearGround = true,
                ).resetLocalFallDistance,
            )
            assertFalse(lifecycle.active)
        }
    }

    private fun lifecycleFor(
        outboundMovements: List<Vec3>,
        initialFallDistance: Double = 0.0,
        groundedSteps: List<Boolean>? = null,
    ): SpearKillFallSafetyLifecycle = SpearKillFallSafetyLifecycle().apply {
        begin(readyPlan(outboundMovements, initialFallDistance, groundedSteps))
    }

    private fun readyPlan(
        outboundMovements: List<Vec3>,
        initialFallDistance: Double,
        groundedSteps: List<Boolean>? = null,
    ): SpearKillServerFallSafetyPlan = when (val result = SpearKillServerFallSafetyPlan.create(
        outboundMovements = outboundMovements,
        initialFallDistance = initialFallDistance,
        safeFallDistance = 3.0,
        groundedSteps = groundedSteps,
    )) {
        is SpearKillServerFallSafetyPlanResult.Ready -> result.plan
        is SpearKillServerFallSafetyPlanResult.Blocked -> error("Expected a safe plan, got ${result.reason}")
    }
}
