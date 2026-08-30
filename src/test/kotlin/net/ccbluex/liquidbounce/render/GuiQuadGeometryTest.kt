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
package net.ccbluex.liquidbounce.render

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class GuiQuadGeometryTest {

    @Test
    fun `outline keeps clockwise line-pair vertex order`() {
        assertArrayEquals(
            floatArrayOf(1f, 2f, 1f, 4f, 1f, 4f, 3f, 4f, 3f, 4f, 3f, 2f, 3f, 2f, 1f, 2f),
            quadOutlinePoints(1f, 2f, 3f, 4f),
        )
    }
}
