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

package net.ccbluex.liquidbounce.features.module.modules.render.hats.modes

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HatsOrbsGeometryTest {

    @Test
    fun `rhombus keeps the original top and bottom triangle order`() {
        val consumer = RecordingVertexConsumer()
        val color = Color4b(12, 34, 56, 78)

        consumer.drawOrbRhombus(PoseStack().last(), 10f, 20f, 30f, rotation = 0f, size = 2f, color)

        assertEquals(
            listOf(
                TOP, D, A,
                TOP, A, B,
                TOP, B, C,
                TOP, C, D,
                BOTTOM, D, A,
                BOTTOM, A, B,
                BOTTOM, B, C,
                BOTTOM, C, D,
            ),
            consumer.vertices,
        )
        assertEquals(List(24) { color.argb }, consumer.colors)
    }

    private class RecordingVertexConsumer : VertexConsumer {
        val vertices = mutableListOf<Triple<Float, Float, Float>>()
        val colors = mutableListOf<Int>()

        override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer = apply {
            vertices += Triple(x, y, z)
        }

        override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer = this
        override fun setColor(color: Int): VertexConsumer = apply { colors += color }
        override fun setUv(u: Float, v: Float): VertexConsumer = this
        override fun setUv1(u: Int, v: Int): VertexConsumer = this
        override fun setUv2(u: Int, v: Int): VertexConsumer = this
        override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = this
        override fun setLineWidth(width: Float): VertexConsumer = this
    }

    private companion object {
        val TOP = Triple(10f, 22f, 30f)
        val BOTTOM = Triple(10f, 18f, 30f)
        val A = Triple(10f, 20f, 32f)
        val B = Triple(12f, 20f, 30f)
        val C = Triple(10f, 20f, 28f)
        val D = Triple(8f, 20f, 30f)
    }
}
