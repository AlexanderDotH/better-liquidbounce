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

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntStack
import net.ccbluex.liquidbounce.render.FontFace
import net.ccbluex.liquidbounce.render.engine.font.processor.ProcessedText
import net.ccbluex.liquidbounce.render.engine.font.processor.ProcessedText.ProcessedChar
import net.ccbluex.liquidbounce.render.engine.type.Color4b

internal class GlyphRunRenderer(
    private val font: FontFace,
    private val glyphManager: FontGlyphPageManager,
    private val primitiveRenderer: GlyphPrimitiveRenderer,
) {
    private val underlinesIdxStack = IntArrayList()
    private val strikethroughIdxStack = IntArrayList()

    fun width(text: ProcessedText, shadow: Boolean): Float {
        if (text.chars.isEmpty()) return 0f

        val fallbackGlyph = glyphManager.getFallbackGlyph(font)
        var width = 0f
        for (processedChar in text.chars) {
            val glyph = resolveGlyph(processedChar, fallbackGlyph)
            width += layoutInfo(processedChar, glyph, fallbackGlyph).advanceX
        }
        return width + if (shadow) 2f else 0f
    }

    context(ctx: Any)
    fun draw(
        text: ProcessedText,
        posX: Float,
        posY: Float,
        z: Float,
        scale: Float,
        overrideColor: Color4b?,
    ) {
        if (text.chars.isEmpty()) return

        val underlines = loadUnderlines(text)
        val strikethroughs = loadStrikethroughs(text)
        val state = GlyphRunState(posX, posY + font.plainStyle.ascent * scale)
        val fallbackGlyph = glyphManager.getFallbackGlyph(font)
        text.chars.forEachIndexed { index, processedChar ->
            drawProcessedCharacter(
                index, processedChar, fallbackGlyph, state, underlines, strikethroughs, z, scale, overrideColor
            )
        }
        closeRemainingDecorations(state, underlines, strikethroughs, z, scale)
    }

    context(ctx: Any)
    private fun drawProcessedCharacter(
        index: Int,
        processedChar: ProcessedChar,
        fallbackGlyph: GlyphDescriptor,
        state: GlyphRunState,
        underlines: IntStack,
        strikethroughs: IntStack,
        z: Float,
        scale: Float,
        overrideColor: Color4b?,
    ) {
        val glyph = glyphManager.requestGlyph(font, processedChar.font, processedChar.codepoint) ?: fallbackGlyph
        state.color = overrideColor ?: processedChar.color
        openDecorations(index, state, underlines, strikethroughs)
        primitiveRenderer.drawGlyph(glyph, state.x, state.y, z, scale, state.color)
        state.advance(layoutInfo(processedChar, glyph, fallbackGlyph), scale)
        closeDecorations(index + 1, state, underlines, strikethroughs, z, scale)
    }

    private fun openDecorations(
        index: Int,
        state: GlyphRunState,
        underlines: IntStack,
        strikethroughs: IntStack,
    ) {
        if (underlines.startsAt(index)) {
            underlines.popInt()
            state.underlineStartX = state.x
        }
        if (strikethroughs.startsAt(index)) {
            strikethroughs.popInt()
            state.strikethroughStartX = state.x
        }
    }

    context(ctx: Any)
    private fun closeDecorations(
        index: Int,
        state: GlyphRunState,
        underlines: IntStack,
        strikethroughs: IntStack,
        z: Float,
        scale: Float,
    ) {
        if (underlines.startsAt(index)) {
            underlines.popInt()
            primitiveRenderer.drawLine(state.underlineStartX, state.x, state.y, z, scale, state.color, false)
        }
        if (strikethroughs.startsAt(index)) {
            strikethroughs.popInt()
            primitiveRenderer.drawLine(state.strikethroughStartX, state.x, state.y, z, scale, state.color, true)
        }
    }

    context(ctx: Any)
    private fun closeRemainingDecorations(
        state: GlyphRunState,
        underlines: IntStack,
        strikethroughs: IntStack,
        z: Float,
        scale: Float,
    ) {
        if (!underlines.isEmpty && !state.underlineStartX.isNaN()) {
            underlines.popInt()
            primitiveRenderer.drawLine(state.underlineStartX, state.x, state.y, z, scale, state.color, false)
        }
        if (!strikethroughs.isEmpty && !state.strikethroughStartX.isNaN()) {
            strikethroughs.popInt()
            primitiveRenderer.drawLine(state.strikethroughStartX, state.x, state.y, z, scale, state.color, true)
        }
    }

    private fun resolveGlyph(processedChar: ProcessedChar, fallbackGlyph: GlyphDescriptor): GlyphDescriptor =
        glyphManager.requestGlyph(font, processedChar.font, processedChar.codepoint) ?: fallbackGlyph

    private fun layoutInfo(
        processedChar: ProcessedChar,
        glyph: GlyphDescriptor,
        fallbackGlyph: GlyphDescriptor,
    ) = if (!processedChar.obfuscated) glyph.renderInfo.layoutInfo else fallbackGlyph.renderInfo.layoutInfo

    private fun loadUnderlines(text: ProcessedText): IntStack = underlinesIdxStack.load(text.underlines)

    private fun loadStrikethroughs(text: ProcessedText): IntStack = strikethroughIdxStack.load(text.strikeThroughs)

    private fun IntArrayList.load(indices: it.unimi.dsi.fastutil.ints.IntList): IntStack = apply {
        clear()
        addAll(indices)
        elements().reverse(0, size)
    }

    private fun IntStack.startsAt(index: Int) = !isEmpty && topInt() == index
}

private class GlyphRunState(var x: Float, var y: Float) {
    var color: Color4b = Color4b.WHITE
    var underlineStartX = Float.NaN
    var strikethroughStartX = Float.NaN

    fun advance(layoutInfo: GlyphLayoutInfo, scale: Float) {
        x += layoutInfo.advanceX * scale
        y += layoutInfo.advanceY * scale
    }
}
