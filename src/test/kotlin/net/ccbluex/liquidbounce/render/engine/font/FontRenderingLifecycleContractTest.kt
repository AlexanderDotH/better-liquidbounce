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

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FontRenderingLifecycleContractTest {

    @Test
    fun `font facade preserves draw order recycling and public constructor surface`() {
        val source = read("FontRenderer.kt")
        val commonDraw = source.substringAfter("private fun commonDraw(").substringBefore("override fun getStringWidth")

        assertTrue(source.contains("class FontRenderer("))
        assertTrue(source.contains("val font: FontFace"))
        assertTrue(source.contains("val glyphManager: FontGlyphPageManager"))
        assertTrue(source.contains("override val size: Float = DEFAULT_FONT_SIZE"))
        assertFalse(source.contains("CognitiveComplexMethod"))
        assertOrdered(
            commonDraw,
            "if (parameters.shadow)",
            "runRenderer.draw(",
            "z = if (z.isNaN()) z else z + 0.001f",
            "MinecraftTextProcessor.TEXT_POOL.recycle(text)",
        )
    }

    @Test
    fun `glyph run keeps resolution draw advance and decoration order`() {
        val source = read("GlyphRunRenderer.kt")

        assertOrdered(
            source,
            "glyphManager.requestGlyph",
            "primitiveRenderer.drawGlyph",
            "state.advance",
            "closeDecorations",
        )
        assertTrue(source.contains("if (!processedChar.obfuscated)"))
        assertTrue(source.contains("fallbackGlyph.renderInfo.layoutInfo"))
        assertTrue(source.contains("loadUnderlines(text)"))
        assertTrue(source.contains("loadStrikethroughs(text)"))
    }

    @Test
    fun `glyph primitives keep gui and world pipelines and texture ownership`() {
        val source = read("GlyphPrimitiveRenderer.kt")

        assertTrue(source.contains("drawGlyphOnCurrentLayer"))
        assertTrue(source.contains("ClientRenderPipelines.GUI.FontMask"))
        assertTrue(source.contains("glyph.page.texture"))
        assertTrue(source.contains("quad.texture.textureSetup"))
        assertTrue(source.contains("drawCustomMeshTextured"))
        assertTrue(source.contains("ClientRenderPipelines.FontMaskQuads"))
        assertTrue(source.contains("drawHorizontalLine"))
        assertTrue(source.contains("ClientRenderPipelines.quads(noDepthTest = true)"))
    }

    @Test
    fun `coverage copy keeps gray argb fallback priority and scratch contract`() {
        val facade = read("GlyphAtlasTexture.kt")
        val copier = read("GlyphCoverageCopier.kt")

        assertTrue(facade.contains("GlyphCoverageCopier("))
        assertTrue(facade.contains(".copy(scratchBuffer)"))
        assertOrdered(copier, "copyGrayCoverage()", "copyArgbAlpha()", "copyFallbackAlpha(scratchBuffer)")
        assertTrue(copier.contains("return scratchBuffer"))
    }

    private fun assertOrdered(source: String, vararg markers: String) {
        val positions = markers.map(source::indexOf)
        assertTrue(positions.all { it >= 0 }, "Missing ordered marker in ${markers.toList()}")
        assertEquals(positions.sorted(), positions)
    }

    private fun read(file: String): String = Files.readString(Path.of(FONT_PATH, file))

    private companion object {
        const val FONT_PATH = "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/font"
    }
}
