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

package net.ccbluex.liquidbounce.render

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import kotlin.test.Test
import kotlin.test.assertEquals

class SquareTextureGradientMeshTest {

    @Test
    fun `single cell keeps quad position texture coordinates and draw order`() {
        val outerColor = Color4b(10, 20, 30, 40)
        val vertices = collectVertices(
            SquareTextureGradientSpec(
                outerRadius = 1f,
                innerRadius = 0f,
                outerColor = outerColor,
                innerColor = Color4b.WHITE,
                anchor = AnchorPoint.TOP_LEFT,
                subdivisions = 1,
                startOffset = 0.5f,
            )
        )

        assertEquals(
            listOf(
                GradientVertex(-2f, 2f, 0f, 1f, outerColor.argb),
                GradientVertex(-2f, 0f, 0f, 0f, outerColor.argb),
                GradientVertex(0f, 0f, 1f, 0f, outerColor.argb),
                GradientVertex(0f, 2f, 1f, 1f, outerColor.argb),
            ),
            vertices,
        )
    }

    @Test
    fun `two by two grid keeps center at inner color and emits row major quads`() {
        val innerColor = Color4b(70, 80, 90, 100)
        val vertices = collectVertices(
            SquareTextureGradientSpec(
                outerRadius = 1f,
                innerRadius = 0f,
                outerColor = Color4b.TRANSPARENT,
                innerColor = innerColor,
                anchor = AnchorPoint.TOP_LEFT,
                subdivisions = 2,
                startOffset = 0.5f,
            )
        )

        assertEquals(16, vertices.size)
        assertEquals(GradientVertex(-2f, 1f, 0f, 0.5f, Color4b.TRANSPARENT.argb), vertices[0])
        assertEquals(GradientVertex(-1f, 1f, 0.5f, 0.5f, innerColor.argb), vertices[3])
        assertEquals(GradientVertex(-1f, 1f, 0.5f, 0.5f, innerColor.argb), vertices[4])
    }

    private fun collectVertices(spec: SquareTextureGradientSpec): List<GradientVertex> = buildList {
        spec.forEachVertex { x, y, u, v, argb -> add(GradientVertex(x, y, u, v, argb)) }
    }

    private data class GradientVertex(val x: Float, val y: Float, val u: Float, val v: Float, val argb: Int)
}
