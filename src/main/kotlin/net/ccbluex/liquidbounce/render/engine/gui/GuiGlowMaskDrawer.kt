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

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.vertex.VertexConsumer
import net.ccbluex.liquidbounce.render.ClientRenderPipelines
import net.ccbluex.liquidbounce.render.engine.RenderDrawKey
import net.ccbluex.liquidbounce.render.getDynamicTransformsUniform
import net.ccbluex.liquidbounce.render.mesh.BatchCollector
import org.joml.Matrix4f
import kotlin.math.roundToInt

internal data class GuiGlowMaskEncoding(
    val width: Int,
    val height: Int,
    val radius: Int,
)

internal fun GuiGlowFrameRequest.maskEncoding(): GuiGlowMaskEncoding {
    val encodedWidth = width.roundToInt().coerceIn(1, MAX_ENCODED_VALUE)
    val encodedHeight = height.roundToInt().coerceIn(1, MAX_ENCODED_VALUE)
    val encodedRadius = radius.roundToInt().coerceIn(0, minOf(encodedWidth, encodedHeight) / 2)
    return GuiGlowMaskEncoding(encodedWidth, encodedHeight, encodedRadius)
}

internal object GuiGlowMaskDrawer {

    fun draw(target: RenderTarget, requests: List<GuiGlowFrameRequest>) {
        val collector = BatchCollector()
        val key = RenderDrawKey.of(ClientRenderPipelines.GUI.roundedRect())
        collector.start(key).use { scope ->
            for (request in requests) {
                writeRoundedRect(scope.consumer, request)
            }
        }

        collector.flush(
            target,
            getDynamicTransformsUniform(Matrix4f().setTranslation(0f, 0f, -11000f)),
        )
    }

    private fun writeRoundedRect(vertices: VertexConsumer, request: GuiGlowFrameRequest) {
        val encoding = request.maskEncoding()
        for (index in CORNER_ORDER) {
            val offset = index * 2
            vertices.addVertex(request.corners[offset], request.corners[offset + 1], 0f)
                .setUv(U_COORDINATES[index], V_COORDINATES[index])
                .setColor(request.color.argb)
                .setUv1(encoding.width, encoding.height)
                .setUv2(encoding.radius, 0)
                .setLineWidth(0f)
        }
    }
}

private const val MAX_ENCODED_VALUE = 32767
private val CORNER_ORDER = intArrayOf(0, 1, 2, 3)
private val U_COORDINATES = floatArrayOf(0f, 0f, 1f, 1f)
private val V_COORDINATES = floatArrayOf(0f, 1f, 1f, 0f)
