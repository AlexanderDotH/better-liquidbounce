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
package net.ccbluex.liquidbounce.render.engine.gui

import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.esp.EspShaderStyleResolver
import net.ccbluex.liquidbounce.render.engine.esp.EspTargetSize
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import org.joml.Matrix3x2fc
import org.joml.Vector2f
import kotlin.math.hypot

internal data class GuiGlowFrameRequest(
    val corners: FloatArray,
    val width: Float,
    val height: Float,
    val radius: Float,
    val color: Color4b,
    val style: EspGlowStyle,
    val backgroundBlurRadius: Float,
) {
    companion object {
        fun axisAligned(
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
            radius: Float,
            color: Color4b,
            style: EspGlowStyle,
            backgroundBlurRadius: Float,
        ) = GuiGlowFrameRequest(
            corners = floatArrayOf(x1, y1, x1, y2, x2, y2, x2, y1),
            width = x2 - x1,
            height = y2 - y1,
            radius = radius,
            color = color.with(a = 255),
            style = style,
            backgroundBlurRadius = backgroundBlurRadius,
        )

        fun transformed(
            pose: Matrix3x2fc,
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
            radius: Float,
            color: Color4b,
            style: EspGlowStyle,
            backgroundBlurRadius: Float,
        ): GuiGlowFrameRequest {
            val topLeft = pose.transformPosition(x1, y1, Vector2f())
            val bottomLeft = pose.transformPosition(x1, y2, Vector2f())
            val bottomRight = pose.transformPosition(x2, y2, Vector2f())
            val topRight = pose.transformPosition(x2, y1, Vector2f())
            val transformedWidth = distance(topLeft, topRight)
            val transformedHeight = distance(topLeft, bottomLeft)
            val widthScale = transformedWidth / (x2 - x1).coerceAtLeast(1e-4f)
            val heightScale = transformedHeight / (y2 - y1).coerceAtLeast(1e-4f)

            return GuiGlowFrameRequest(
                corners = floatArrayOf(
                    topLeft.x, topLeft.y,
                    bottomLeft.x, bottomLeft.y,
                    bottomRight.x, bottomRight.y,
                    topRight.x, topRight.y,
                ),
                width = transformedWidth,
                height = transformedHeight,
                radius = radius * minOf(widthScale, heightScale),
                color = color.with(a = 255),
                style = style,
                backgroundBlurRadius = backgroundBlurRadius,
            )
        }

        private fun distance(first: Vector2f, second: Vector2f): Float =
            hypot(second.x - first.x, second.y - first.y)
    }
}

internal data class GuiGlowFrameBatch(
    val requests: List<GuiGlowFrameRequest>,
    val style: EspGlowStyle,
    val backgroundBlurRadius: Float,
) {
    val hasVisibleGlow: Boolean
        get() = style.opacity > 0f && (style.intensity > 0f || style.coreSize > 0f)
}

/** Owns the CPU-side lifecycle of one GUI glow frame independently of GPU resources. */
internal class GuiGlowFrameState {

    private val requests = mutableListOf<GuiGlowFrameRequest>()
    private var maskPrepared = false

    var targetSize: EspTargetSize? = null
        private set

    val pendingCount: Int
        get() = requests.size

    fun beginFrame() {
        requests.clear()
        maskPrepared = false
    }

    fun append(request: GuiGlowFrameRequest) {
        requests += request
    }

    fun prepareMask(width: Int, height: Int): Boolean {
        val size = EspTargetSize(width.coerceAtLeast(1), height.coerceAtLeast(1))
        val shouldClear = !maskPrepared || targetSize != size
        targetSize = size
        maskPrepared = true
        return shouldClear
    }

    fun consume(): GuiGlowFrameBatch? {
        if (requests.isEmpty()) return null

        val batch = GuiGlowFrameBatch(
            requests = requests.toList(),
            style = EspShaderStyleResolver.resolveGlow(*requests.map { it.style }.toTypedArray()),
            backgroundBlurRadius = requests.maxOf { it.backgroundBlurRadius },
        )
        requests.clear()
        maskPrepared = false
        return batch
    }
}
