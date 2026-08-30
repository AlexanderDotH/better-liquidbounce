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
package net.ccbluex.liquidbounce.features.block.planner

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YawTargetSelectionTest {

    private val high = Vec3(1.0, 0.0, 0.0)
    private val low = Vec3(-1.0, 0.0, 0.0)

    @Test
    fun `selection keeps tolerance preference and low tie break`() {
        assertEquals(high, selectYawTarget(candidate(high, 1f), candidate(low, 2f), 5f))
        assertEquals(low, selectYawTarget(candidate(high, 2f), candidate(low, 1f), 5f))
        assertEquals(low, selectYawTarget(candidate(high, 1f), candidate(low, 1f), 5f))
    }

    @Test
    fun `selection keeps single valid candidate and rejects both invalid`() {
        assertEquals(high, selectYawTarget(candidate(high, 1f), candidate(low, 6f), 5f))
        assertEquals(low, selectYawTarget(candidate(high, 6f), candidate(low, 1f), 5f))
        assertNull(selectYawTarget(candidate(high, 6f), candidate(low, 6f), 5f))
    }

    private fun candidate(point: Vec3, tolerance: Float) = YawTargetCandidate(point, tolerance)
}
