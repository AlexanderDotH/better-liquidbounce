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
import net.ccbluex.liquidbounce.render.engine.type.BoundingBox2f
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.math.ceilToInt
import net.ccbluex.liquidbounce.utils.math.floorToInt
import net.ccbluex.liquidbounce.utils.collection.Pools
import net.ccbluex.liquidbounce.render.gui.element.LambdaSimpleGuiElementRenderState
import net.ccbluex.liquidbounce.render.gui.element.LineGuiElementRenderState
import net.ccbluex.liquidbounce.render.gui.element.QuadGuiElementRenderState
import net.ccbluex.liquidbounce.utils.render.VerticesSetupHandler
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fStack
import org.joml.Matrix3x2fc
import org.joml.Vector2f

internal val LEFT_TOP = Vector2f()
internal val RIGHT_TOP = Vector2f()
internal val LEFT_BOTTOM = Vector2f()
internal val RIGHT_BOTTOM = Vector2f()

/**
 * Primitive version of [ScreenRectangle.transformMaxBounds]
 */
internal fun Matrix3x2fc.transformMaxBounds(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
): ScreenRectangle {
    val v1 = transformPosition(left, top, LEFT_TOP)
    val v2 = transformPosition(right, top, RIGHT_TOP)
    val v3 = transformPosition(left, bottom, LEFT_BOTTOM)
    val v4 = transformPosition(right, bottom, RIGHT_BOTTOM)
    val minX = minOf(v1.x, minOf(v3.x, v2.x, v4.x))
    val maxX = maxOf(v1.x, maxOf(v3.x, v2.x, v4.x))
    val minY = minOf(v1.y, minOf(v3.y, v2.y, v4.y))
    val maxY = maxOf(v1.y, maxOf(v3.y, v2.y, v4.y))
    return ScreenRectangle(
        minX.floorToInt(),
        minY.floorToInt(),
        (maxX - minX).ceilToInt(),
        (maxY - minY).ceilToInt(),
    )
}

/**
 * @see net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState.getBounds
 */
fun GuiGraphicsExtractor.getBounds(left: Float, top: Float, right: Float, bottom: Float): ScreenRectangle {
    val rect = this.pose().transformMaxBounds(left, top, right, bottom)
    return this.scissorStack.peek()?.intersection(rect) ?: rect
}

/**
 * @see net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState.getBounds
 */
fun GuiGraphicsExtractor.getBoundsXYWH(x: Float, y: Float, w: Float, h: Float): ScreenRectangle {
    return getBounds(x, y, x + w, y + h)
}

fun GuiGraphicsExtractor.getBounds(box: BoundingBox2f): ScreenRectangle =
    getBounds(box.xMin, box.yMin, box.xMax, box.yMax)

inline fun GuiGraphicsExtractor.copyPosePooled(): Matrix3x2f = Pools.Mat3x2f.borrow().set(this.pose())

inline fun GuiGraphicsExtractor.copyPose(): Matrix3x2f = Matrix3x2f(this.pose())

inline fun Matrix3x2fStack.withPush(block: Matrix3x2fStack.() -> Unit) {
    pushMatrix()
    try {
        block()
    } finally {
        popMatrix()
    }
}

inline fun GuiGraphicsExtractor.ScissorStack.withPush(
    rect: ScreenRectangle,
    block: GuiGraphicsExtractor.ScissorStack.() -> Unit,
) {
    if (rect.width <= 0 || rect.height <= 0) return
    push(rect)
    try {
        block()
    } finally {
        pop()
    }
}

inline fun GuiGraphicsExtractor.drawCustomElement(
    pipeline: RenderPipeline = RenderPipelines.GUI, // PosColor + QUADS
    textureSetup: TextureSetup = TextureSetup.noTexture(),
    scissorArea: ScreenRectangle? = this.scissorStack.peek(),
    bounds: ScreenRectangle? = null,
    verticesSetupHandler: VerticesSetupHandler,
) = this.guiRenderState.addGuiElement(
    LambdaSimpleGuiElementRenderState(
        pipeline,
        textureSetup,
        copyPosePooled(),
        scissorArea,
        bounds,
        verticesSetupHandler
    )
)

fun GuiGraphicsExtractor.drawLines(
    points: FloatArray,
    argb: Int,
    bounds: ScreenRectangle,
    cull: Boolean = true,
) {
    this.guiRenderState.addGuiElement(
        LineGuiElementRenderState(
            points,
            argb,
            ClientRenderPipelines.GUI.lines(cull),
            copyPosePooled(),
            this.scissorStack.peek(),
            bounds,
        )
    )
}

fun GuiGraphicsExtractor.drawQuad(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    fillColor: Color4b? = Color4b.TRANSPARENT,
    outlineColor: Color4b? = Color4b.TRANSPARENT,
) {
    val x11 = minOf(x1, x2)
    val y11 = minOf(y1, y2)
    val x21 = maxOf(x1, x2)
    val y21 = maxOf(y1, y2)

    val bounds = getBounds(x11, y11, x21, y21)

    if (fillColor != null && !fillColor.isTransparent) {
        this.guiRenderState.addGuiElement(
            QuadGuiElementRenderState(
                x11,
                y11,
                x21,
                y21,
                fillColor.argb,
                copyPosePooled(),
                this.scissorStack.peek(),
                bounds,
            )
        )
    }
    if (outlineColor != null && !outlineColor.isTransparent) {
        val argb = outlineColor.argb

        drawLines(
            quadOutlinePoints(x11, y11, x21, y21),
            argb,
            bounds,
        )
    }
}

internal fun quadOutlinePoints(minX: Float, minY: Float, maxX: Float, maxY: Float) = floatArrayOf(
    minX, minY,
    minX, maxY,
    minX, maxY,
    maxX, maxY,
    maxX, maxY,
    maxX, minY,
    maxX, minY,
    minX, minY,
)
