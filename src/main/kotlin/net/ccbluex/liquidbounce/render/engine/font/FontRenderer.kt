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

import net.ccbluex.liquidbounce.render.AbstractFontRenderer
import net.ccbluex.liquidbounce.render.FontFace
import net.ccbluex.liquidbounce.render.FontManager.DEFAULT_FONT_SIZE
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.engine.font.processor.MinecraftTextProcessor
import net.ccbluex.liquidbounce.render.engine.font.processor.ProcessedText
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import java.awt.Font

class FontRenderer(
    /**
     * Glyph pages for the style of the font. If an element is null, fall back to `[0]`
     *
     * [Font.PLAIN] -> 0 (Must not be null)
     *
     * [Font.BOLD] -> 1 (Can be null)
     *
     * [Font.ITALIC] -> 2 (Can be null)
     *
     * [Font.BOLD] | [Font.ITALIC] -> 3 (Can be null)
     */
    val font: FontFace,
    val glyphManager: FontGlyphPageManager,
    override val size: Float = DEFAULT_FONT_SIZE
) : AbstractFontRenderer<MinecraftTextProcessor.RecyclingProcessedText>() {

    override val height: Float = font.plainStyle.height

    private val shadowColor = Color4b(0, 0, 0, 150)
    private val runRenderer = GlyphRunRenderer(
        font,
        glyphManager,
        GlyphPrimitiveRenderer(
            underlineOffset = font.plainStyle.underlineOffset,
            underlineThickness = font.plainStyle.underlineThickness,
            strikethroughOffset = font.plainStyle.strikethroughOffset,
            strikethroughThickness = font.plainStyle.strikethroughThickness,
        ),
    )

    override fun process(text: Component, defaultColor: Color4b): MinecraftTextProcessor.RecyclingProcessedText {
        return MinecraftTextProcessor.process(ForeignTextSanitizer.sanitize(text), defaultColor)
    }

    context(ctx: GuiGraphicsExtractor)
    override fun draw(
        text: MinecraftTextProcessor.RecyclingProcessedText,
        parameters: DrawParameters,
    ): Float = commonDraw(text, parameters)

    context(ctx: WorldRenderEnvironment)
    override fun draw(
        text: MinecraftTextProcessor.RecyclingProcessedText,
        parameters: DrawParameters,
    ): Float = commonDraw(text, parameters)

    context(ctx: Any)
    private fun commonDraw(
        text: MinecraftTextProcessor.RecyclingProcessedText,
        parameters: DrawParameters,
    ): Float {
        val scale = parameters.scale
        val width = getStringWidth(text, parameters.shadow)

        val x = parameters.horizontalAnchor?.anchorToDrawX(
            x = parameters.x,
            width = width,
            scale,
        ) ?: parameters.x

        val y = parameters.verticalAnchor?.anchorToDrawY(
            y = parameters.y,
            height,
            scale,
        ) ?: parameters.y

        val z = parameters.z

        if (parameters.shadow) {
            runRenderer.draw(
                text,
                posX = x + 2.0f * scale,
                posY = y + 2.0f * scale,
                z = z,
                scale,
                overrideColor = shadowColor
            )
        }

        runRenderer.draw(
            text,
            posX = x,
            posY = y,
            z = if (z.isNaN()) z else z + 0.001f,
            scale,
            overrideColor = null,
        )

        MinecraftTextProcessor.TEXT_POOL.recycle(text)

        return width
    }

    override fun getStringWidth(
        text: ProcessedText,
        shadow: Boolean
    ): Float = runRenderer.width(text, shadow)

}
