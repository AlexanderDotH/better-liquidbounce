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

import net.minecraft.core.Direction
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class FaceVertexGeometryTest {

    @Test
    fun `face vertices preserve direction-specific winding`() {
        val expected = mapOf(
            Direction.DOWN to points(0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1),
            Direction.UP to points(0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0),
            Direction.NORTH to points(0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0),
            Direction.EAST to points(1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1),
            Direction.SOUTH to points(0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1),
            Direction.WEST to points(0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0),
        )

        expected.forEach { (direction, vertices) ->
            assertArrayEquals(vertices, faceVertexCoordinates(direction, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0), 0.0)
        }
    }

    private fun points(vararg coordinates: Int) = coordinates.map(Int::toDouble).toDoubleArray()
}
