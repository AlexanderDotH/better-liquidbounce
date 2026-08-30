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
import kotlin.math.sqrt

internal fun interface SquareTextureGradientVertexConsumer {
    fun accept(x: Float, y: Float, u: Float, v: Float, argb: Int)
}

internal class SquareTextureGradientSpec(
    private val outerRadius: Float,
    innerRadius: Float,
    private val outerColor: Color4b,
    private val innerColor: Color4b,
    anchor: AnchorPoint,
    private val subdivisions: Int,
    startOffset: Float,
) {
    private val size = outerRadius * 2f
    private val minX = size * anchor.xFactor
    private val minY = size * anchor.yFactor
    private val step = size / subdivisions
    private val centerX = minX + outerRadius
    private val centerY = minY + outerRadius
    private val effectiveRatio = maxOf(
        (innerRadius / outerRadius).coerceIn(0f, 1f),
        startOffset.coerceIn(0f, 0.99f),
    )

    fun forEachVertex(consumer: SquareTextureGradientVertexConsumer) {
        for (row in 0 until subdivisions) {
            for (column in 0 until subdivisions) {
                emitCell(row, column, consumer)
            }
        }
    }

    private fun emitCell(row: Int, column: Int, consumer: SquareTextureGradientVertexConsumer) {
        val x1 = minX + column * step
        val x2 = x1 + step
        val y1 = minY + row * step
        val y2 = y1 + step
        val u1 = column.toFloat() / subdivisions
        val u2 = (column + 1).toFloat() / subdivisions
        val v1 = row.toFloat() / subdivisions
        val v2 = (row + 1).toFloat() / subdivisions
        val c11 = colorAt(x1, y1)
        val c12 = colorAt(x1, y2)
        val c22 = colorAt(x2, y2)
        val c21 = colorAt(x2, y1)

        consumer.accept(x1, y2, u1, v2, c12)
        consumer.accept(x1, y1, u1, v1, c11)
        consumer.accept(x2, y1, u2, v1, c21)
        consumer.accept(x2, y2, u2, v2, c22)
    }

    private fun colorAt(x: Float, y: Float): Int {
        val dx = x - centerX
        val dy = y - centerY
        val distanceRatio = (sqrt(dx * dx + dy * dy) / outerRadius).coerceIn(0f, 1f)
        val interpolation = when (distanceRatio <= effectiveRatio) {
            true -> 0.0
            else -> ((distanceRatio - effectiveRatio) / (1.0 - effectiveRatio)).coerceIn(0.0, 1.0)
        }
        return innerColor.interpolateTo(outerColor, interpolation).argb
    }
}
