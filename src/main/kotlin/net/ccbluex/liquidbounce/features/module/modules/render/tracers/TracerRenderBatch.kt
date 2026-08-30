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

package net.ccbluex.liquidbounce.features.module.modules.render.tracers

import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.drawLines
import net.ccbluex.liquidbounce.render.drawLinesWithWidth
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.engine.type.Vec3f

internal data class TracerSegment(
    val color: Color4b,
    val eyePosition: Vec3f,
    val targetPosition: Vec3f,
) {
    val glowMaskColor: Color4b
        get() = color.with(a = 255)
}

internal data class TracerRenderBatch(
    val segments: List<TracerSegment>,
    val lineWidth: Float,
) {
    val glowMaskLineWidth: Float
        get() = maxOf(lineWidth, MIN_GLOW_MASK_LINE_WIDTH)

    fun contributeGlowIfPresent(contribute: (TracerRenderBatch) -> Unit): Boolean {
        if (segments.isEmpty()) return false

        contribute(this)
        return true
    }
}

internal data class TracerLineDraw(
    val color: Color4b,
    val width: Float,
    val start: Vec3f,
    val end: Vec3f,
    val depthTested: Boolean = false,
)

internal inline fun TracerRenderBatch.forEachLine(
    glowMask: Boolean,
    depthTested: Boolean = glowMask,
    draw: (TracerLineDraw) -> Unit,
) {
    val renderLineWidth = if (glowMask) glowMaskLineWidth else lineWidth
    for (segment in segments) {
        draw(
            TracerLineDraw(
                color = if (glowMask) segment.glowMaskColor else segment.color,
                width = renderLineWidth,
                start = segment.eyePosition,
                end = segment.targetPosition,
                depthTested = depthTested,
            )
        )
    }
}

internal fun WorldRenderEnvironment.drawTracerBatch(
    batch: TracerRenderBatch,
    glowMask: Boolean,
    depthTested: Boolean = glowMask,
) {
    batch.forEachLine(glowMask, depthTested) { line ->
        if (line.width == 1f && !line.depthTested) {
            drawLines(line.color.argb, line.start, line.end)
        } else {
            drawLinesWithWidth(line.color.argb, line.width, line.start, line.end)
        }
    }
}

private const val MIN_GLOW_MASK_LINE_WIDTH = 2f
