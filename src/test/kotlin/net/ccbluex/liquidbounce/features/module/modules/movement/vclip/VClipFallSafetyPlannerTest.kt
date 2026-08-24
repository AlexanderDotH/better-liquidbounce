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
package net.ccbluex.liquidbounce.features.module.modules.movement.vclip

import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.ThrowingSupplier

class VClipFallSafetyPlannerTest {

    @Test
    fun `six point three seven block descent uses three safe grounded checkpoints`() {
        val origin = VClipPosition(2.0, 64.0, -3.0)
        val target = origin.copy(y = 57.63)

        val plan = VClipFallSafetyPlanner.plan(
            origin = origin,
            target = target,
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
        ) as VClipFallSafetyPlan.GroundedSegmentation

        assertEquals(
            listOf(
                origin.copy(y = 61.25),
                origin.copy(y = 58.5),
                target,
            ),
            plan.checkpoints,
        )
    }

    @Test
    fun `every descending segment stays inside the dynamic server fall budget`() {
        val origin = VClipPosition(12.0, 100.0, -4.0)
        val maximumSafeFallDistance = 2.75

        listOf(0.01, 1.5, 2.75, 2.7501, 6.37, 30.0).forEach { descent ->
            val target = origin.copy(y = origin.y - descent)
            val plan = VClipFallSafetyPlanner.plan(
                origin = origin,
                target = target,
                initialFallDistance = 1.25,
                safeFallDistance = 3.0,
            ) as VClipFallSafetyPlan.GroundedSegmentation

            var previous = origin
            var accumulatedFallDistance = 1.25
            plan.checkpoints.forEach { checkpoint ->
                val segmentDescent = (previous.y - checkpoint.y).coerceAtLeast(0.0)
                assertTrue(
                    accumulatedFallDistance + segmentDescent <= maximumSafeFallDistance + 1.0E-9,
                    "Unsafe checkpoint $checkpoint for descent $descent",
                )
                accumulatedFallDistance = 0.0
                previous = checkpoint
            }
            assertEquals(target, plan.checkpoints.last())
        }
    }

    @Test
    fun `existing fall distance shortens only the first descending segment`() {
        val origin = VClipPosition(2.0, 64.0, -3.0)
        val target = origin.copy(y = 62.0)

        val plan = VClipFallSafetyPlanner.plan(
            origin = origin,
            target = target,
            initialFallDistance = 2.0,
            safeFallDistance = 3.0,
        ) as VClipFallSafetyPlan.GroundedSegmentation

        assertEquals(listOf(origin.copy(y = 63.25), target), plan.checkpoints)
    }

    @Test
    fun `upward clip needs only the exact grounded target`() {
        val origin = VClipPosition(2.0, 64.0, -3.0)
        val target = origin.copy(y = 128.0)

        val plan = VClipFallSafetyPlanner.plan(
            origin = origin,
            target = target,
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
        ) as VClipFallSafetyPlan.GroundedSegmentation

        assertEquals(listOf(target), plan.checkpoints)
    }

    @Test
    fun `already unsafe fall state cannot be grounded without damage`() {
        val plan = VClipFallSafetyPlanner.plan(
            origin = VClipPosition(2.0, 64.0, -3.0),
            target = VClipPosition(2.0, 70.0, -3.0),
            initialFallDistance = 2.76,
            safeFallDistance = 3.0,
        )

        assertSame(VClipFallSafetyPlan.Unsafe, plan)
    }

    @Test
    fun `exactly exhausted first fall budget grounds at the origin before descending`() {
        val origin = VClipPosition(2.0, 64.0, -3.0)
        val target = origin.copy(y = 60.0)

        val plan = assertTimeoutPreemptively(Duration.ofSeconds(1), ThrowingSupplier {
            VClipFallSafetyPlanner.plan(
                origin = origin,
                target = target,
                initialFallDistance = 2.75,
                safeFallDistance = 3.0,
            )
        }) as VClipFallSafetyPlan.GroundedSegmentation

        assertEquals(listOf(origin, origin.copy(y = 61.25), target), plan.checkpoints)
    }

    @Test
    fun `invalid fall inputs are rejected instead of crashing packet planning`() {
        val origin = VClipPosition(2.0, 64.0, -3.0)

        assertSame(
            VClipFallSafetyPlan.Unsafe,
            VClipFallSafetyPlanner.plan(origin, origin.copy(y = 60.0), Double.NaN, 3.0),
        )
        assertSame(
            VClipFallSafetyPlan.Unsafe,
            VClipFallSafetyPlanner.plan(origin, origin.copy(y = 60.0), 0.0, Double.POSITIVE_INFINITY),
        )
    }
}
