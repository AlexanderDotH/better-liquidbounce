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
package net.ccbluex.liquidbounce.render.engine.esp

import com.mojang.blaze3d.vertex.VertexConsumer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class EspMaskVertexConsumerTest {

    @Test
    fun `custom geometry keeps the mask color throughout a chained vertex submission`() {
        val delegate = RecordingVertexConsumer()
        val maskColor = 0xFF78E678.toInt()
        val consumer = EspMaskVertexConsumer(delegate, maskColor)

        val result = consumer
            .addVertex(1.0f, 2.0f, 3.0f)
            .setColor(12, 34, 56, 78)
            .setUv(0.25f, 0.75f)
            .setUv1(4, 5)
            .setUv2(6, 7)
            .setNormal(0.0f, 1.0f, 0.0f)
            .setLineWidth(2.0f)

        assertSame(consumer, result)
        assertEquals(listOf(maskColor), delegate.packedColors)
        assertEquals(emptyList<List<Int>>(), delegate.componentColors)
        assertEquals(listOf(Vertex(1.0f, 2.0f, 3.0f)), delegate.vertices)
        assertEquals(listOf(Uv(0.25f, 0.75f)), delegate.uvs)
    }

    @Test
    fun `packed custom geometry colors are also replaced by the mask color`() {
        val delegate = RecordingVertexConsumer()
        val maskColor = 0xFFAABBCC.toInt()
        val consumer = EspMaskVertexConsumer(delegate, maskColor)

        val result = consumer.setColor(0x10203040)

        assertSame(consumer, result)
        assertEquals(listOf(maskColor), delegate.packedColors)
        assertEquals(emptyList<List<Int>>(), delegate.componentColors)
    }

    @Test
    fun `custom geometry submissions are duplicated into the dedicated ESP mask phases`() {
        val source = Files.readString(
            Path.of(
                "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/render/" +
                    "MixinSubmitNodeCollection.java"
            )
        )

        assertTrue(source.contains("method = \"submitCustomGeometry\""))
        assertTrue(source.contains("new CustomFeatureRenderer.Submit"))
        assertTrue(source.contains("new EspMaskVertexConsumer(vertexConsumer, maskColor)"))
    }

    private data class Vertex(val x: Float, val y: Float, val z: Float)

    private data class Uv(val u: Float, val v: Float)

    private class RecordingVertexConsumer : VertexConsumer {

        val vertices = mutableListOf<Vertex>()
        val packedColors = mutableListOf<Int>()
        val componentColors = mutableListOf<List<Int>>()
        val uvs = mutableListOf<Uv>()

        override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer = apply {
            vertices += Vertex(x, y, z)
        }

        override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer = apply {
            componentColors += listOf(red, green, blue, alpha)
        }

        override fun setColor(color: Int): VertexConsumer = apply {
            packedColors += color
        }

        override fun setUv(u: Float, v: Float): VertexConsumer = apply {
            uvs += Uv(u, v)
        }

        override fun setUv1(u: Int, v: Int): VertexConsumer = this

        override fun setUv2(u: Int, v: Int): VertexConsumer = this

        override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = this

        override fun setLineWidth(width: Float): VertexConsumer = this
    }
}
