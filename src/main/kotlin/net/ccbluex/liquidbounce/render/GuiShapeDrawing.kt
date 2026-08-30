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


@file:JvmName("Render2DKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.math.ceilToInt
import net.ccbluex.liquidbounce.utils.math.floorToInt
import net.ccbluex.liquidbounce.render.gui.element.RoundedRectGuiElementRenderState
import net.ccbluex.liquidbounce.render.gui.element.TexQuadGuiElementRenderState
import net.ccbluex.liquidbounce.render.gui.element.TriangleGuiElementRenderState
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.world.phys.Vec2

fun GuiGraphicsExtractor.drawRoundedRect(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    radius: Float,
    fillColor: Color4b? = Color4b.TRANSPARENT,
    outlineColor: Color4b? = Color4b.TRANSPARENT,
    outlineWidth: Float = 1.0f,
) {
    val x11 = minOf(x1, x2)
    val y11 = minOf(y1, y2)
    val x21 = maxOf(x1, x2)
    val y21 = maxOf(y1, y2)

    val fill = fillColor ?: Color4b.TRANSPARENT
    val outline = outlineColor ?: Color4b.TRANSPARENT
    if (fill.isTransparent && (outline.isTransparent || outlineWidth <= 0.0f)) {
        return
    }

    this.guiRenderState.addGuiElement(
        RoundedRectGuiElementRenderState(
            x11,
            y11,
            x21,
            y21,
            radius.coerceAtLeast(0.0f),
            fill.argb,
            outline.argb,
            outlineWidth.coerceAtLeast(0.0f),
            copyPosePooled(),
            this.scissorStack.peek(),
            getBounds(x11, y11, x21, y21),
        )
    )
}

inline fun GuiGraphicsExtractor.drawQuadXYWH(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    fillColor: Color4b? = Color4b.TRANSPARENT,
    outlineColor: Color4b? = Color4b.TRANSPARENT,
) = drawQuad(x, y, x + w, y + h, fillColor, outlineColor)

/**
 * Float version of [GuiGraphicsExtractor.drawHorizontalLine]
 */
fun GuiGraphicsExtractor.drawHorizontalLine(x1: Float, x2: Float, y: Float, thickness: Float, color: Color4b) {
    this.drawQuad(x1, y, x2, y + thickness, color)
}

/**
 * Float version of [GuiGraphicsExtractor.drawVerticalLine]
 */
fun GuiGraphicsExtractor.drawVerticalLine(x: Float, y1: Float, y2: Float, thickness: Float, color: Color4b) {
    this.drawQuad(x, y1, x + thickness, y2, color)
}

@Suppress("LongParameterList")
fun GuiGraphicsExtractor.drawTriangle(
    x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float,
    fillColor: Color4b? = Color4b.TRANSPARENT,
    outlineColor: Color4b? = Color4b.TRANSPARENT,
    cull: Boolean = true,
) {
    val minX = minOf(x0, x1, x2)
    val minY = minOf(y0, y1, y2)
    val maxX = maxOf(x0, x1, x2)
    val maxY = maxOf(y0, y1, y2)
    val bounds = getBounds(minX, minY, maxX, maxY)

    if (fillColor != null && !fillColor.isTransparent) {
        this.guiRenderState.addGuiElement(
            TriangleGuiElementRenderState(
                x0, y0, x1, y1, x2, y2,
                fillColor.argb,
                ClientRenderPipelines.GUI.triangles(cull),
                copyPosePooled(),
                this.scissorStack.peek(),
                bounds,
            )
        )
    }

    if (outlineColor != null && !outlineColor.isTransparent) {
        drawLines(
            floatArrayOf(
                x0, y0,
                x1, y1,
                x1, y1,
                x2, y2,
                x2, y2,
                x0, y0,
            ),
            outlineColor.argb,
            bounds,
        )
    }
}

fun GuiGraphicsExtractor.drawTriangle(
    p1: Vec2, p2: Vec2, p3: Vec2,
    fillColor: Color4b? = Color4b.TRANSPARENT,
    outlineColor: Color4b? = Color4b.TRANSPARENT,
) = drawTriangle(
    p1.x, p1.y, p2.x, p2.y, p3.x, p3.y,
    fillColor, outlineColor,
)

@Suppress("LongParameterList")
inline fun GuiGraphicsExtractor.drawGlyphOnCurrentLayer(
    textureSetup: TextureSetup,
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    u1: Float = 0f,
    v1: Float = 0f,
    u2: Float = 1f,
    v2: Float = 1f,
    argb: Int = -1,
    pipeline: RenderPipeline = RenderPipelines.GUI_TEXTURED,
) {
    this.guiRenderState.addGlyphToCurrentLayer(
        TexQuadGuiElementRenderState(
            x0,
            y0,
            x1,
            y1,
            u1,
            v1,
            u2,
            v2,
            argb,
            pipeline,
            textureSetup,
            copyPosePooled(),
            this.scissorStack.peek(),
            null,
        )
    )
}
