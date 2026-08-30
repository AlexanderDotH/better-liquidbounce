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
import it.unimi.dsi.fastutil.floats.Float2IntFunction
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.gui.GuiCircleLutAtlas
import net.ccbluex.liquidbounce.utils.math.ceilToInt
import net.ccbluex.liquidbounce.utils.math.floorToInt
import net.ccbluex.liquidbounce.render.gui.element.CircleGuiElementRenderState
import net.ccbluex.liquidbounce.render.gui.element.TexQuadGuiElementRenderState
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.BlitRenderState

@Suppress("LongParameterList")
inline fun GuiGraphicsExtractor.drawTexQuad(
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
    this.guiRenderState.addGuiElement(
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
            getBounds(x0, y0, x1, y1),
        )
    )
}

@Suppress("LongParameterList")
inline fun GuiGraphicsExtractor.drawBlitOnCurrentLayer(
    textureSetup: TextureSetup,
    x0: Int,
    y0: Int,
    x1: Int,
    y1: Int,
    u1: Float = 0f,
    v1: Float = 0f,
    u2: Float = 1f,
    v2: Float = 1f,
    argb: Int = -1,
    pipeline: RenderPipeline = RenderPipelines.GUI_TEXTURED,
) {
    this.guiRenderState.addBlitToCurrentLayer(
        BlitRenderState(
            pipeline,
            textureSetup,
            copyPose(),
            x0,
            y0,
            x1,
            y1,
            u1,
            v1,
            u2,
            v2,
            argb,
            this.scissorStack.peek(),
            null,
        )
    )
}

fun GuiGraphicsExtractor.drawCircle(
    x: Float,
    y: Float,
    radius: Float,
    innerRadius: Float = 0f,
    colorGetter: Float2IntFunction = Float2IntFunction { Color4b.WHITE.argb },
) {
    if (radius <= 0f) {
        return
    }

    val lut = GuiCircleLutAtlas.allocate(colorGetter)
    val innerRatio = (innerRadius / radius).coerceIn(0f, 1f)
    val bounds = getBoundsXYWH(x - radius, y - radius, radius * 2, radius * 2)

    this.guiRenderState.addGuiElement(
        CircleGuiElementRenderState(
            x,
            y,
            radius,
            innerRatio,
            lut.row,
            ClientRenderPipelines.GUI.circleLut(),
            lut.textureSetup,
            copyPosePooled(),
            this.scissorStack.peek(),
            bounds
        )
    )
}
