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
@file:JvmName("GlyphPageKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.render.engine.font

import net.ccbluex.liquidbounce.render.engine.type.BoundingBox2f
import net.ccbluex.liquidbounce.render.engine.type.BoundingBox2s
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import java.awt.Dimension
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Contains information about the placement of characters in a bitmap
 * and how they are rendered
 */
@JvmRecord
data class GlyphRenderInfo(
    /**
     * Which Unicode codepoint does this glyph represent?
     */
    val codepoint: Int,
    /**
     * The location of the Glyph on the sprite, may be null if the glyph is a whitespace
     */
    val atlasLocation: GlyphAtlasLocation?,
    /**
     * The bounds of the rendered glyph. Used for rendering.
     */
    val glyphBounds: BoundingBox2f,
    val layoutInfo: GlyphLayoutInfo
)

class GlyphAtlasLocation(val pixelBoundingBox: BoundingBox2f, atlasDimensions: Dimension) {
    val uvCoordinatesOnTexture: BoundingBox2s
    val atlasWidth: Float
    val atlasHeight: Float

    init {
        val atlasWidth = atlasDimensions.width.toFloat()
        val atlasHeight = atlasDimensions.height.toFloat()

        this.uvCoordinatesOnTexture = BoundingBox2s(
            pixelBoundingBox.xMin / atlasWidth,
            pixelBoundingBox.yMin / atlasHeight,
            pixelBoundingBox.xMax / atlasWidth,
            pixelBoundingBox.yMax / atlasHeight,
        )

        this.atlasWidth = pixelBoundingBox.xMax - pixelBoundingBox.xMin
        this.atlasHeight = pixelBoundingBox.yMax - pixelBoundingBox.yMin
    }
}

@JvmRecord
data class GlyphLayoutInfo(val useHorizontalBaseline: Boolean, val advanceX: Float, val advanceY: Float)
