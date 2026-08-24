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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VClipTargetPlannerTest {

    @Test
    fun `distance target applies the input direction to the configured blocks`() {
        assertEquals(69.5, VClipTargetPlanner.distanceTargetY(64.5, VClipDirection.UP, 5.0))
        assertEquals(59.5, VClipTargetPlanner.distanceTargetY(64.5, VClipDirection.DOWN, 5.0))
    }

    @Test
    fun `smart down starts at the exact player Y and selects the nearest free pocket`() {
        val inspectedSegments = mutableListOf<Pair<Double, Double>>()

        val targetY = VClipTargetPlanner.smartTargetY(
            scan = scan(
                currentY = 70.75,
                direction = VClipDirection.DOWN,
                scanStep = 0.25,
            ),
            hasBlockCollisionBetween = { fromY, toY ->
                inspectedSegments += fromY to toY
                fromY > 67.75 && toY < 70.0
            },
            hasAnyCollisionAt = { candidateY -> candidateY > 67.75 && candidateY < 70.0 },
        )

        assertEquals(70.75 to 70.5, inspectedSegments.first())
        assertEquals(67.75, targetY)
    }

    @Test
    fun `smart up starts at the exact player Y and selects the nearest free pocket`() {
        val inspectedSegments = mutableListOf<Pair<Double, Double>>()

        val targetY = VClipTargetPlanner.smartTargetY(
            scan = scan(
                currentY = 64.25,
                direction = VClipDirection.UP,
                scanStep = 0.25,
            ),
            hasBlockCollisionBetween = { fromY, toY ->
                inspectedSegments += fromY to toY
                fromY < 67.25 && toY > 65.0
            },
            hasAnyCollisionAt = { candidateY -> candidateY > 65.0 && candidateY < 67.25 },
        )

        assertEquals(64.25 to 64.5, inspectedSegments.first())
        assertEquals(67.25, targetY)
    }

    @Test
    fun `smart target does not move through open air before crossing a block`() {
        assertNull(
            VClipTargetPlanner.smartTargetY(
                scan = scan(maxDistance = 3),
                hasBlockCollisionBetween = { _, _ -> false },
                hasAnyCollisionAt = { false },
            ),
        )
    }

    @Test
    fun `enabled scan distance is measured outward from the player position`() {
        val inspectedCandidates = mutableListOf<Double>()

        assertNull(
            VClipTargetPlanner.smartTargetY(
                scan = scan(currentY = 64.5, maxDistance = 1, scanStep = 0.25),
                hasBlockCollisionBetween = { _, _ -> true },
                hasAnyCollisionAt = { candidateY ->
                    inspectedCandidates += candidateY
                    true
                },
            ),
        )

        assertEquals(listOf(64.75, 65.0, 65.25, 65.5), inspectedCandidates)
    }

    @Test
    fun `disabled scan distance still starts downward at the player instead of the world minimum`() {
        val inspectedSegments = mutableListOf<Pair<Double, Double>>()

        val targetY = VClipTargetPlanner.smartTargetY(
            scan = scan(
                currentY = 70.0,
                direction = VClipDirection.DOWN,
                maxDistance = null,
                scanStep = 1.0,
            ),
            hasBlockCollisionBetween = { fromY, toY ->
                inspectedSegments += fromY to toY
                fromY <= 69.0 && toY >= 67.0
            },
            hasAnyCollisionAt = { candidateY -> candidateY in 67.0..69.0 },
        )

        assertEquals(70.0 to 69.0, inspectedSegments.first())
        assertEquals(66.0, targetY)
    }

    @Test
    fun `smart refines after a floor collision so a fractional nearby pocket is not skipped`() {
        val targetY = VClipTargetPlanner.smartTargetY(
            scan = scan(
                currentY = 70.24,
                direction = VClipDirection.DOWN,
                scanStep = 0.25,
                collisionRefinementStep = 0.05,
            ),
            hasBlockCollisionBetween = { fromY, toY -> fromY > 67.19 && toY < 69.95 },
            hasAnyCollisionAt = { candidateY -> candidateY > 67.19 && candidateY < 69.95 },
        )

        assertEquals(67.19, targetY!!, 1.0E-9)
    }

    private fun scan(
        currentY: Double = 64.0,
        direction: VClipDirection = VClipDirection.UP,
        maxDistance: Int? = 10,
        scanStep: Double = 0.25,
        collisionRefinementStep: Double = scanStep,
    ) = VClipSmartScan(
        currentY = currentY,
        direction = direction,
        minBuildY = -64,
        maxBuildY = 319,
        maxDistance = maxDistance,
        scanStep = scanStep,
        collisionRefinementStep = collisionRefinementStep,
    )
}
