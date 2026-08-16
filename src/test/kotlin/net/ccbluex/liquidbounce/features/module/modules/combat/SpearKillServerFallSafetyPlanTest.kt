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

class SpearKillServerFallSafetyPlanTest {

    @Test
    fun `full speed descent stays airborne until a physically near-ground endpoint`() {
        val down = Vec3(6.0, -10.0, 0.0)
        val up = down.scale(-1.0)
        val result = SpearKillServerFallSafetyPlan.createForMovements(
            movements = listOf(down, up),
            outboundStepCount = 1,
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
            groundedSteps = listOf(false, true),
            expectedNetMovement = Vec3.ZERO,
        ) as SpearKillServerFallSafetyPlanResult.Ready

        assertFalse(result.plan.steps[0].groundExactPacket)
        assertTrue(result.plan.steps[1].groundExactPacket)
    }

    @Test
    fun `unsafe fall distance never creates an airborne ground spoof`() {
        val movement = Vec3(4.0, -20.0, 0.0)
        val result = SpearKillServerFallSafetyPlan.createForMovements(
            movements = listOf(movement),
            outboundStepCount = 1,
            initialFallDistance = 12.0,
            safeFallDistance = 3.0,
            groundedSteps = listOf(false),
        ) as SpearKillServerFallSafetyPlanResult.Ready

        assertFalse(result.plan.steps.single().groundExactPacket)
    }

    @Test
    fun `non finite fall inputs and movements are blocked`() {
        assertBlocked(
            initialFallDistance = Double.NaN,
            reason = SpearKillServerFallSafetyBlockReason.NON_FINITE_INPUT,
        )
        assertBlocked(
            safeFallDistance = Double.POSITIVE_INFINITY,
            reason = SpearKillServerFallSafetyBlockReason.NON_FINITE_INPUT,
        )
        assertBlocked(
            outboundMovements = listOf(Vec3(Double.NaN, 0.0, 0.0)),
            reason = SpearKillServerFallSafetyBlockReason.NON_FINITE_INPUT,
        )
        assertBlocked(
            outboundMovements = listOf(
                Vec3(Double.MAX_VALUE, 0.0, 0.0),
                Vec3(Double.MAX_VALUE, 0.0, 0.0),
            ),
            reason = SpearKillServerFallSafetyBlockReason.NON_FINITE_INPUT,
        )
    }

    @Test
    fun `initial fall distance never fabricates a ground packet or blocks movement`() {
        assertTrue(create(initialFallDistance = 2.95) is SpearKillServerFallSafetyPlanResult.Ready)
        val aboveMargin = create(initialFallDistance = 20.0) as SpearKillServerFallSafetyPlanResult.Ready
        assertFalse(aboveMargin.plan.steps.any(SpearKillServerFallSafetyStep::groundExactPacket))
    }

    @Test
    fun `negative fall attributes and zero movement are blocked`() {
        assertBlocked(
            initialFallDistance = -0.01,
            reason = SpearKillServerFallSafetyBlockReason.INVALID_DISTANCE,
        )
        assertBlocked(
            safeFallDistance = -0.01,
            reason = SpearKillServerFallSafetyBlockReason.INVALID_DISTANCE,
        )
        assertBlocked(
            outboundMovements = listOf(Vec3.ZERO),
            reason = SpearKillServerFallSafetyBlockReason.ZERO_MOVEMENT,
        )
    }

    @Test
    fun `full speed descent is airborne and only the near-ground return packet is grounded`() {
        val descent = Vec3(6.0, -10.0, 0.0)
        val plan = ready(
            outboundMovements = listOf(descent),
            groundedSteps = listOf(false, true),
        )

        assertEquals(listOf(descent, descent.scale(-1.0)), plan.steps.map(SpearKillServerFallSafetyStep::movement))
        assertFalse(plan.steps[0].groundExactPacket)
        assertTrue(plan.steps[1].groundExactPacket)
    }

    @Test
    fun `descent that crosses the fall margin remains airborne without physical support`() {
        val plan = ready(
            outboundMovements = listOf(Vec3(1.0, -0.01, 0.0)),
            initialFallDistance = 2.95,
            groundedSteps = listOf(false, true),
        )

        assertFalse(plan.steps[0].groundExactPacket)
        assertTrue(plan.steps[1].groundExactPacket)
    }

    @Test
    fun `long diagonal descent grounds only the endpoint reported near ground`() {
        val down = Vec3(2.0, -2.0, 1.0)
        val plan = ready(
            outboundMovements = listOf(down, down),
            groundedSteps = listOf(false, false, false, true),
        )

        assertEquals(listOf(3), plan.steps.indices.filter(plan::groundsExactPacket))
        assertEquals(
            listOf(down, down, down.scale(-1.0), down.scale(-1.0)),
            plan.steps.map(SpearKillServerFallSafetyStep::movement),
        )
    }

    @Test
    fun `descent to ascent transition stays airborne unless collision reports support`() {
        val down = Vec3(1.0, -2.0, 0.0)
        val up = Vec3(1.0, 1.0, 0.0)
        val plan = ready(
            outboundMovements = listOf(down, up),
            groundedSteps = listOf(false, false, false, true),
        )

        assertEquals(listOf(3), plan.steps.indices.filter(plan::groundsExactPacket))
    }

    @Test
    fun `direct style round trip is exact and requests a confirmed final grounding`() {
        val outbound = listOf(Vec3(2.0, 0.0, 1.0))
        val plan = ready(outbound, groundedSteps = listOf(false, true))

        assertEquals(1, plan.outboundStepCount)
        assertEquals(listOf(1), plan.steps.indices.filter(plan::groundsExactPacket))
        assertEquals(Vec3.ZERO, plan.steps.fold(Vec3.ZERO) { total, step -> total.add(step.movement) })
        assertFalse(plan.finalGroundingRequired)
    }

    @Test
    fun `network style paced diagonal round trip is exact and fully preflighted`() {
        val outbound = listOf(
            Vec3(1.0, -0.75, 0.5),
            Vec3(1.0, -0.75, 0.5),
            Vec3(1.0, -0.75, 0.5),
        )
        val plan = ready(
            outboundMovements = outbound,
            groundedSteps = listOf(false, false, false, false, false, true),
        )

        assertEquals(outbound.size * 2, plan.steps.size)
        assertEquals(outbound, plan.steps.take(outbound.size).map(SpearKillServerFallSafetyStep::movement))
        assertEquals(
            outbound.asReversed().map { it.scale(-1.0) },
            plan.steps.drop(outbound.size).map(SpearKillServerFallSafetyStep::movement),
        )
        assertEquals(Vec3.ZERO, plan.steps.fold(Vec3.ZERO) { total, step -> total.add(step.movement) })
        assertFalse(plan.steps[outbound.size].groundExactPacket)
        assertTrue(plan.steps.last().groundExactPacket)
    }

    @Test
    fun `explicit future queue is not expanded and may retain recovery displacement`() {
        val futureMovements = listOf(
            Vec3(-2.0, 1.0, 0.0),
            Vec3(-3.0, 0.0, 0.0),
        )
        val plan = readyFuture(
            movements = futureMovements,
            outboundStepCount = 0,
            initialFallDistance = 1.0,
            groundedSteps = listOf(false, true),
        )

        assertEquals(futureMovements, plan.steps.map(SpearKillServerFallSafetyStep::movement))
        assertEquals(0, plan.outboundStepCount)
        assertFalse(plan.steps.first().groundExactPacket)
        assertTrue(plan.steps.last().groundExactPacket)
        assertEquals(Vec3(-5.0, 1.0, 0.0), plan.netMovement)
    }

    @Test
    fun `explicit future queue validates its outbound boundary`() {
        val result = SpearKillServerFallSafetyPlan.createForMovements(
            movements = listOf(Vec3(1.0, 0.0, 0.0)),
            outboundStepCount = 2,
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
        ) as SpearKillServerFallSafetyPlanResult.Blocked

        assertEquals(SpearKillServerFallSafetyBlockReason.INVALID_OUTBOUND_STEP_COUNT, result.reason)
    }

    @Test
    fun `ground profile must describe every future packet`() {
        val result = SpearKillServerFallSafetyPlan.createForMovements(
            movements = listOf(Vec3(1.0, 0.0, 0.0)),
            outboundStepCount = 1,
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
            groundedSteps = emptyList(),
        ) as SpearKillServerFallSafetyPlanResult.Blocked

        assertEquals(SpearKillServerFallSafetyBlockReason.INVALID_GROUND_PROFILE, result.reason)
    }

    @Test
    fun `explicit future queue can validate the expected recovery displacement`() {
        val movements = listOf(Vec3(-2.0, 0.0, 0.0), Vec3(-3.0, 0.0, 0.0))
        val ready = SpearKillServerFallSafetyPlan.createForMovements(
            movements = movements,
            outboundStepCount = 0,
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
            expectedNetMovement = Vec3(-5.0, 0.0, 0.0),
        )
        val blocked = SpearKillServerFallSafetyPlan.createForMovements(
            movements = movements,
            outboundStepCount = 0,
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
            expectedNetMovement = Vec3.ZERO,
        ) as SpearKillServerFallSafetyPlanResult.Blocked

        assertTrue(ready is SpearKillServerFallSafetyPlanResult.Ready)
        assertEquals(SpearKillServerFallSafetyBlockReason.UNEXPECTED_NET_MOVEMENT, blocked.reason)
    }

    private fun assertBlocked(
        outboundMovements: List<Vec3> = emptyList(),
        initialFallDistance: Double = 0.0,
        safeFallDistance: Double = 3.0,
        reason: SpearKillServerFallSafetyBlockReason,
    ) {
        val blocked = create(outboundMovements, initialFallDistance, safeFallDistance)
            as SpearKillServerFallSafetyPlanResult.Blocked
        assertEquals(reason, blocked.reason)
    }

    private fun ready(
        outboundMovements: List<Vec3>,
        initialFallDistance: Double = 0.0,
        safeFallDistance: Double = 3.0,
        groundedSteps: List<Boolean>? = null,
    ): SpearKillServerFallSafetyPlan = when (
        val result = create(outboundMovements, initialFallDistance, safeFallDistance, groundedSteps)
    ) {
        is SpearKillServerFallSafetyPlanResult.Ready -> result.plan
        is SpearKillServerFallSafetyPlanResult.Blocked -> error("Expected a safe plan, got ${result.reason}")
    }

    private fun readyFuture(
        movements: List<Vec3>,
        outboundStepCount: Int,
        initialFallDistance: Double,
        safeFallDistance: Double = 3.0,
        groundedSteps: List<Boolean> = List(movements.size) { false },
    ): SpearKillServerFallSafetyPlan = when (val result = SpearKillServerFallSafetyPlan.createForMovements(
        movements = movements,
        outboundStepCount = outboundStepCount,
        initialFallDistance = initialFallDistance,
        safeFallDistance = safeFallDistance,
        groundedSteps = groundedSteps,
    )) {
        is SpearKillServerFallSafetyPlanResult.Ready -> result.plan
        is SpearKillServerFallSafetyPlanResult.Blocked -> error("Expected a safe plan, got ${result.reason}")
    }

    private fun create(
        outboundMovements: List<Vec3> = emptyList(),
        initialFallDistance: Double = 0.0,
        safeFallDistance: Double = 3.0,
        groundedSteps: List<Boolean>? = null,
    ) = SpearKillServerFallSafetyPlan.create(
        outboundMovements = outboundMovements,
        initialFallDistance = initialFallDistance,
        safeFallDistance = safeFallDistance,
        groundedSteps = groundedSteps,
    )
}
