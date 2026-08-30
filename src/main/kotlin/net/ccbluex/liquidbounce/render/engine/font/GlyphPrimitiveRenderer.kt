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
package net.ccbluex.liquidbounce.render.engine.font

import net.ccbluex.liquidbounce.render.ClientRenderPipelines
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.drawCustomMesh
import net.ccbluex.liquidbounce.render.drawCustomMeshTextured
import net.ccbluex.liquidbounce.render.drawGlyphOnCurrentLayer
import net.ccbluex.liquidbounce.render.drawHorizontalLine
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.setColor
import net.ccbluex.liquidbounce.utils.render.textureSetup
import net.minecraft.client.gui.GuiGraphicsExtractor

internal class GlyphPrimitiveRenderer(
    private val underlineOffset: Float,
    private val underlineThickness: Float,
    private val strikethroughOffset: Float,
    private val strikethroughThickness: Float,
) {
    context(ctx: Any)
    fun drawLine(
        x0: Float,
        x1: Float,
        y: Float,
        z: Float,
        scale: Float,
        color: Color4b,
        through: Boolean,
    ) {
        val thickness = if (through) strikethroughThickness else underlineThickness
        val offset = if (through) strikethroughOffset else underlineOffset
        val lineWidth = (thickness * scale).coerceAtLeast(0f)
        val lineY = y + offset * scale
        if (z.isNaN()) {
            (ctx as GuiGraphicsExtractor).drawHorizontalLine(x0, x1, lineY, lineWidth, color)
            return
        }
        (ctx as WorldRenderEnvironment).drawCustomMesh(ClientRenderPipelines.quads(noDepthTest = true)) { matrix ->
            val y1 = lineY + lineWidth
            addVertex(matrix, x0, lineY, z).setColor(color)
            addVertex(matrix, x0, y1, z).setColor(color)
            addVertex(matrix, x1, y1, z).setColor(color)
            addVertex(matrix, x1, lineY, z).setColor(color)
        }
    }

    context(ctx: Any)
    fun drawGlyph(glyph: GlyphDescriptor, x: Float, y: Float, z: Float, scale: Float, color: Color4b) {
        val atlasLocation = glyph.renderInfo.atlasLocation ?: return
        if (color.isTransparent) return

        val bounds = glyph.renderInfo.glyphBounds
        val (u1, v1) = atlasLocation.uvCoordinatesOnTexture.min
        val (u2, v2) = atlasLocation.uvCoordinatesOnTexture.max
        val quad = GlyphQuad(
            glyph.page.texture,
            x + bounds.xMin * scale,
            y + bounds.yMin * scale,
            x + (bounds.xMin + atlasLocation.atlasWidth) * scale,
            y + (bounds.yMin + atlasLocation.atlasHeight) * scale,
            u1, v1, u2, v2, color.argb,
        )
        if (z.isNaN()) drawGuiGlyph(quad) else drawWorldGlyph(quad, z)
    }

    context(ctx: Any)
    private fun drawGuiGlyph(quad: GlyphQuad) {
        (ctx as GuiGraphicsExtractor).drawGlyphOnCurrentLayer(
            quad.texture.textureSetup,
            x0 = quad.x0, y0 = quad.y0, x1 = quad.x1, y1 = quad.y1,
            u1 = quad.u1, v1 = quad.v1, u2 = quad.u2, v2 = quad.v2, argb = quad.argb,
            pipeline = ClientRenderPipelines.GUI.FontMask,
        )
    }

    context(ctx: Any)
    private fun drawWorldGlyph(quad: GlyphQuad, z: Float) {
        (ctx as WorldRenderEnvironment).drawCustomMeshTextured(
            quad.texture,
            pipeline = ClientRenderPipelines.FontMaskQuads,
        ) { matrix ->
            addVertex(matrix, quad.x0, quad.y0, z).setUv(quad.u1, quad.v1).setColor(quad.argb)
            addVertex(matrix, quad.x0, quad.y1, z).setUv(quad.u1, quad.v2).setColor(quad.argb)
            addVertex(matrix, quad.x1, quad.y1, z).setUv(quad.u2, quad.v2).setColor(quad.argb)
            addVertex(matrix, quad.x1, quad.y0, z).setUv(quad.u2, quad.v1).setColor(quad.argb)
        }
    }
}

private data class GlyphQuad(
    val texture: GlyphAtlasTexture,
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    val u1: Float,
    val v1: Float,
    val u2: Float,
    val v2: Float,
    val argb: Int,
)
