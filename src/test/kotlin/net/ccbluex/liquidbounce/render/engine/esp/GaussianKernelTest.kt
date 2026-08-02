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

package net.ccbluex.liquidbounce.render.engine.esp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GaussianKernelTest {

    @Test
    fun `classic fourteen pixel glow produces a normalized paired kernel`() {
        val kernel = GaussianKernel.forScreenRadius(14f)

        assertEquals(6, kernel.pairs.size)
        assertEquals(
            1.0,
            kernel.centerWeight.toDouble() + 2.0 * kernel.pairs.sumOf { it.weight.toDouble() },
            0.0001,
        )
        assertTrue(kernel.centerWeight.isFinite())
        assertTrue(kernel.pairs.all { it.offset.isFinite() && it.weight.isFinite() && it.weight >= 0f })
    }

    @Test
    fun `paired offsets increase while weights fade outward`() {
        val pairs = GaussianKernel.forScreenRadius(24f).pairs

        assertTrue(pairs.zipWithNext().all { (left, right) -> left.offset < right.offset })
        assertTrue(pairs.zipWithNext().all { (left, right) -> left.weight >= right.weight })
    }

    @Test
    fun `radius is clamped to the supported classic glow range`() {
        assertEquals(GaussianKernel.forScreenRadius(4f), GaussianKernel.forScreenRadius(-20f))
        assertEquals(GaussianKernel.forScreenRadius(24f), GaussianKernel.forScreenRadius(200f))
    }
}
