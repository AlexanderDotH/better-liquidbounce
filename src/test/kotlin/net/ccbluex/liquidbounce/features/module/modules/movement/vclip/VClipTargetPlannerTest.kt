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
    fun `smart up selects the first valid support surface above the player`() {
        val visitedSupports = mutableListOf<Int>()

        val targetY = VClipTargetPlanner.smartTargetY(
            scan = scan(
                startBlockY = 64,
                currentY = 64.25,
                direction = VClipDirection.UP,
                maxDistance = 10,
            ),
        ) { supportY ->
            visitedSupports += supportY
            if (supportY == 67) 0.5 else null
        }

        assertEquals(listOf(65, 66, 67), visitedSupports)
        assertEquals(67.5, targetY)
    }

    @Test
    fun `smart down skips the support directly beneath the current feet`() {
        val visitedSupports = mutableListOf<Int>()

        val targetY = VClipTargetPlanner.smartTargetY(
            scan = scan(
                startBlockY = 70,
                currentY = 70.0,
                direction = VClipDirection.DOWN,
                maxDistance = 10,
            ),
        ) { supportY ->
            visitedSupports += supportY
            if (supportY == 68) 1.0 else null
        }

        assertEquals(listOf(68), visitedSupports)
        assertEquals(69.0, targetY)
    }

    @Test
    fun `smart target returns no position when the scan finds no landing`() {
        assertNull(VClipTargetPlanner.smartTargetY(scan(maxDistance = 3)) { null })
    }

    @Test
    fun `disabled scan distance searches upward through the dimension build height`() {
        val visitedSupports = mutableListOf<Int>()

        val targetY = VClipTargetPlanner.smartTargetY(
            scan = scan(maxDistance = null),
        ) { supportY ->
            visitedSupports += supportY
            if (supportY == 318) 0.5 else null
        }

        assertEquals(65, visitedSupports.first())
        assertEquals(318, visitedSupports.last())
        assertEquals(318.5, targetY)
    }

    @Test
    fun `disabled scan distance searches downward through the dimension build height`() {
        val visitedSupports = mutableListOf<Int>()

        val targetY = VClipTargetPlanner.smartTargetY(
            scan = scan(
                startBlockY = 70,
                currentY = 70.0,
                direction = VClipDirection.DOWN,
                maxDistance = null,
            ),
        ) { supportY ->
            visitedSupports += supportY
            if (supportY == -64) 1.0 else null
        }

        assertEquals(68, visitedSupports.first())
        assertEquals(-64, visitedSupports.last())
        assertEquals(-63.0, targetY)
    }

    @Test
    fun `enabled scan distance still limits the number of inspected support blocks`() {
        val visitedSupports = mutableListOf<Int>()

        assertNull(
            VClipTargetPlanner.smartTargetY(scan(maxDistance = 3)) { supportY ->
                visitedSupports += supportY
                null
            },
        )

        assertEquals(listOf(65, 66, 67), visitedSupports)
    }

    @Test
    fun `bedrock barrier stops a full-height scan before positions beyond it`() {
        val inspectedSurfaces = mutableListOf<Int>()

        val targetY = VClipTargetPlanner.smartTargetY(
            scan = scan(startBlockY = 124, currentY = 124.0, maxDistance = null),
            isBarrierAt = { supportY -> supportY == 127 },
        ) { supportY ->
            inspectedSurfaces += supportY
            if (supportY == 129) 1.0 else null
        }

        assertNull(targetY)
        assertEquals(listOf(125, 126), inspectedSurfaces)
    }

    @Test
    fun `disabled bedrock protection allows the scan to find a surface beyond bedrock`() {
        val targetY = VClipTargetPlanner.smartTargetY(
            scan = scan(startBlockY = 124, currentY = 124.0, maxDistance = null),
            isBarrierAt = { false },
        ) { supportY ->
            if (supportY == 129) 1.0 else null
        }

        assertEquals(130.0, targetY)
    }

    private fun scan(
        startBlockY: Int = 64,
        currentY: Double = 64.0,
        direction: VClipDirection = VClipDirection.UP,
        maxDistance: Int? = 10,
    ) = VClipSmartScan(
        startBlockY = startBlockY,
        currentY = currentY,
        direction = direction,
        minBuildY = -64,
        maxBuildY = 319,
        maxDistance = maxDistance,
    )
}
