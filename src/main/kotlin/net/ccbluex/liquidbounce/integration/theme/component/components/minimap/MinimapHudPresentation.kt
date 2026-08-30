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

package net.ccbluex.liquidbounce.integration.theme.component.components.minimap

import net.ccbluex.liquidbounce.render.drawCustomElement
import net.ccbluex.liquidbounce.render.drawLines
import net.ccbluex.liquidbounce.render.engine.type.BoundingBox2f
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.renderer.RenderPipelines

internal fun GuiGraphicsExtractor.drawMinimapHudChrome(
    boundingBox: BoundingBox2f,
    bounds: ScreenRectangle,
    chrome: MinimapHudChrome,
) {
    drawMinimapShadow(boundingBox, bounds, chrome)
    val center = boundingBox.centerVec
    val crosshairLines = floatArrayOf(
        boundingBox.xMin, center.y,
        boundingBox.xMax, center.y,
        center.x, boundingBox.yMin,
        center.x, boundingBox.yMax,
    )
    val borderLines = floatArrayOf(
        boundingBox.xMin, boundingBox.yMin,
        boundingBox.xMax, boundingBox.yMin,
        boundingBox.xMin, boundingBox.yMax,
        boundingBox.xMax, boundingBox.yMax,
        boundingBox.xMin, boundingBox.yMin,
        boundingBox.xMin, boundingBox.yMax,
        boundingBox.xMax, boundingBox.yMin,
        boundingBox.xMax, boundingBox.yMax,
    )
    drawLines(crosshairLines, chrome.crosshairColor.argb, bounds)
    drawLines(borderLines, chrome.borderColor.argb, bounds)
}

private fun GuiGraphicsExtractor.drawMinimapShadow(
    boundingBox: BoundingBox2f,
    bounds: ScreenRectangle,
    chrome: MinimapHudChrome,
) {
    val from = chrome.shadowColor.argb
    val to = Color4b.TRANSPARENT.argb
    val offset = chrome.shadowOffset
    val width = chrome.shadowWidth
    drawCustomElement(pipeline = RenderPipelines.GUI, bounds = bounds) { pose ->
        addVertexWith2DPose(pose, boundingBox.xMin + offset, boundingBox.yMax).setColor(from)
        addVertexWith2DPose(pose, boundingBox.xMin + offset, boundingBox.yMax + width).setColor(to)
        addVertexWith2DPose(pose, boundingBox.xMax, boundingBox.yMax + width).setColor(to)
        addVertexWith2DPose(pose, boundingBox.xMax, boundingBox.yMax).setColor(from)

        addVertexWith2DPose(pose, boundingBox.xMax, boundingBox.yMin + offset).setColor(from)
        addVertexWith2DPose(pose, boundingBox.xMax, boundingBox.yMax).setColor(from)
        addVertexWith2DPose(pose, boundingBox.xMax + width, boundingBox.yMax).setColor(to)
        addVertexWith2DPose(pose, boundingBox.xMax + width, boundingBox.yMin + offset).setColor(to)

        addVertexWith2DPose(pose, boundingBox.xMax, boundingBox.yMax).setColor(from)
        addVertexWith2DPose(pose, boundingBox.xMax, boundingBox.yMax + width).setColor(to)
        addVertexWith2DPose(pose, boundingBox.xMax + width, boundingBox.yMax + width).setColor(to)
        addVertexWith2DPose(pose, boundingBox.xMax + width, boundingBox.yMax).setColor(to)

        addVertexWith2DPose(pose, boundingBox.xMin + offset - width, boundingBox.yMax).setColor(to)
        addVertexWith2DPose(pose, boundingBox.xMin + offset - width, boundingBox.yMax + width).setColor(to)
        addVertexWith2DPose(pose, boundingBox.xMin + offset, boundingBox.yMax + width).setColor(to)
        addVertexWith2DPose(pose, boundingBox.xMin + offset, boundingBox.yMax).setColor(from)

        addVertexWith2DPose(pose, boundingBox.xMax, boundingBox.yMin + offset - width).setColor(to)
        addVertexWith2DPose(pose, boundingBox.xMax, boundingBox.yMin + offset).setColor(from)
        addVertexWith2DPose(pose, boundingBox.xMax + width, boundingBox.yMin + offset).setColor(to)
        addVertexWith2DPose(pose, boundingBox.xMax + width, boundingBox.yMin + offset - width).setColor(to)
    }
}
