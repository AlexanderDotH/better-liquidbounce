/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.features.baritone.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BaritoneRouteSimplifierTest {

    @Test
    fun `straight runs and duplicates collapse while direction changes remain`() {
        val route = listOf(
            point(0, 0),
            point(1, 0),
            point(1, 0),
            point(2, 0),
            point(2, 1),
            point(2, 2),
        )

        val simplified = BaritoneRouteSimplifier().simplify(route)

        assertEquals(listOf(point(0, 0), point(2, 0), point(2, 2)), simplified)
    }

    @Test
    fun `a reversal on the same axis is retained as a direction change`() {
        val route = listOf(point(0, 0), point(1, 0), point(2, 0), point(1, 0), point(0, 0))

        assertEquals(
            listOf(point(0, 0), point(2, 0), point(0, 0)),
            BaritoneRouteSimplifier().simplify(route),
        )
    }

    @Test
    fun `default simplification never exceeds the websocket route cap`() {
        val route = (0 until 2_000).map { index ->
            BaritoneRoutePoint(index.toDouble(), 64.0, (index % 2).toDouble())
        }

        val simplified = BaritoneRouteSimplifier().simplify(route)

        assertEquals(BaritoneRouteSimplifier.MAX_POINTS, simplified.size)
        assertEquals(route.first(), simplified.first())
        assertEquals(route.last(), simplified.last())
    }

    @Test
    fun `bounded simplification keeps the strongest route deviation`() {
        val route = buildList {
            repeat(100) { index -> add(point(index, 0)) }
            add(point(100, 100))
            repeat(100) { index -> add(point(101 + index, 0)) }
        }

        val simplified = BaritoneRouteSimplifier(maxPoints = 5).simplify(route)

        assertTrue(point(100, 100) in simplified)
        assertTrue(simplified.size <= 5)
    }

    @Test
    fun `route points are finite and simplified lists are immutable`() {
        assertFailsWith<IllegalArgumentException> {
            BaritoneRoutePoint(Double.POSITIVE_INFINITY, 64.0, 0.0)
        }

        val simplified = BaritoneRouteSimplifier().simplify(listOf(point(0, 0), point(1, 0)))
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (simplified as MutableList<BaritoneRoutePoint>).clear()
        }
    }

    private fun point(x: Int, z: Int) = BaritoneRoutePoint(x.toDouble(), 64.0, z.toDouble())
}
