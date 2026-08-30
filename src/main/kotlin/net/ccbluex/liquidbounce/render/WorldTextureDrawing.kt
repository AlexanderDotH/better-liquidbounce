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
import net.minecraft.client.renderer.texture.AbstractTexture

fun WorldRenderEnvironment.drawTexQuad(
    sampler0: AbstractTexture,
    argb: Int,
) {
    drawCustomMeshTextured(sampler0) { pose ->
        addVertex(pose, -0.5f, -0.5f, 0f).setUv(0f, 0f).setColor(argb)
        addVertex(pose, -0.5f, 0.5f, 0f).setUv(0f, 1f).setColor(argb)
        addVertex(pose, 0.5f, 0.5f, 0f).setUv(1f, 1f).setColor(argb)
        addVertex(pose, 0.5f, -0.5f, 0f).setUv(1f, 0f).setColor(argb)
    }
}


fun WorldRenderEnvironment.drawSquareTexture(
    sampler0: AbstractTexture,
    size: Float,
    argb: Int,
    anchor: AnchorPoint = AnchorPoint.TOP_LEFT,
    noDepthTest: Boolean = false,
) = drawCustomMeshTextured(sampler0, pipeline = ClientRenderPipelines.texQuads(noDepthTest) ) { matrix ->
    val minX = size * anchor.xFactor
    val maxX = minX + size
    val minY = size * anchor.yFactor
    val maxY = minY + size

    addVertex(matrix, minX, maxY, 0.0f)
        .setUv(0.0f, 0.0f)
        .setColor(argb)

    addVertex(matrix, minX, minY, 0.0f)
        .setUv(0.0f, 1.0f)
        .setColor(argb)

    addVertex(matrix, maxX, minY, 0.0f)
        .setUv(1.0f, 1.0f)
        .setColor(argb)

    addVertex(matrix, maxX, maxY, 0.0f)
        .setUv(1.0f, 0.0f)
        .setColor(argb)

}

fun WorldRenderEnvironment.drawSquareTextureGradient(
    sampler0: AbstractTexture,
    outerRadius: Float,
    innerRadius: Float,
    outerColor: Color4b,
    innerColor: Color4b,
    anchor: AnchorPoint = AnchorPoint.TOP_LEFT,
    subdivisions: Int = 16,
    startOffset: Float = 0.5f,
    noDepthTest: Boolean = true,
) {
    if (outerRadius <= 0f || (outerColor.isTransparent && innerColor.isTransparent)) {
        return
    }

    val gradient = SquareTextureGradientSpec(
        outerRadius,
        innerRadius,
        outerColor,
        innerColor,
        anchor,
        subdivisions,
        startOffset,
    )

    drawCustomMeshTextured(sampler0, ClientRenderPipelines.texQuads(noDepthTest)) { matrix ->
        gradient.forEachVertex { x, y, u, v, color ->
            addVertex(matrix, x, y, 0.0f).setUv(u, v).setColor(color)
        }
    }
}
