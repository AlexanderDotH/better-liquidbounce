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
package net.ccbluex.liquidbounce.render.engine.type

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UV2fTest {

    @Test
    fun `components preserve their raw float bits`() {
        val u = Float.fromBits(0x7FC0_1234)
        val v = -0.0F
        val uv = UV2f(u, v)

        assertEquals(u.toRawBits(), uv.u.toRawBits())
        assertEquals(v.toRawBits(), uv.v.toRawBits())
        assertEquals(u.toRawBits(), uv.component1().toRawBits())
        assertEquals(v.toRawBits(), uv.component2().toRawBits())
    }
}
