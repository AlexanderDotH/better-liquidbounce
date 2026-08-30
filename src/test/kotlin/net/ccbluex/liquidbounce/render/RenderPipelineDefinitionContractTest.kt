/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.ccbluex.liquidbounce.render

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RenderPipelineDefinitionContractTest {

    @Test
    fun `pipeline definitions preserve identifiers and registration order`() {
        val definitions = DEFINITION_PATHS.map(::read)
        val identifiers = definitions.flatMap { source ->
            PIPELINE_IDENTIFIER.findAll(source).map { it.groupValues[1] }.toList()
        }

        assertEquals(EXPECTED_IDENTIFIERS, identifiers)
        definitions.forEach { source ->
            assertFalse(source.contains("Object2ObjectOpenHashMap"))
            assertFalse(source.contains("precompilePipeline"))
        }

        val facade = read(FACADE)
        val precompile = facade.substringAfter("fun precompile()")
        assertOrdered(precompile, "JCEF", "GUI", "renderPipelines.fastIterator()")
        assertTrue(facade.contains("renderPipelines.put(id, pipeline)?.let"))
    }

    @Test
    fun `pipeline families preserve formats shaders and phase data`() {
        val browserAndGui = read(BROWSER_AND_GUI)
        val world = read(WORLD_PRIMITIVES) + read(WORLD_EFFECTS)
        val screenEffects = read(SCREEN_EFFECTS)
        val fog = read(FOG)
        val composites = read(COMPOSITES)

        assertTrue(browserAndGui.contains("BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA"))
        assertTrue(browserAndGui.contains("ClientShaders.Fragment.BgraPosTex"))
        assertTrue(world.contains("ClientUniformDefine.MESH_BASE_BLOCK_POS"))
        assertTrue(world.contains("ClientUniformDefine.DISTANCE_FADE"))
        assertTrue(screenEffects.contains("GpuFormat.RGBA16_FLOAT"))
        assertTrue(screenEffects.contains("ClientShaders.Fragment.EntityOutline"))
        assertTrue(fog.contains("ClientShaders.Fragment.UnifiedFogComposite"))
        assertTrue(fog.contains("ClientUniformDefine.UNIFIED_FOG_KERNEL"))
        assertTrue(composites.contains("ClientShaders.Fragment.EspMaskUnion"))
        assertTrue(composites.contains("ColorTargetState.WRITE_ALL"))
        assertTrue(composites.contains("ClientUniformDefine.GUI_BLUR_KERNEL"))
    }

    @Test
    fun `facade preserves Java and Kotlin pipeline access`() {
        val facade = read(FACADE)

        JVM_FIELDS.forEach { field ->
            assertTrue(
                Regex("""@JvmField\s+val\s+$field\b""").containsMatchIn(facade),
                "Missing public JVM field $field",
            )
        }
        JVM_STATIC_FUNCTIONS.forEach { function ->
            assertTrue(
                Regex("""@JvmStatic\s+fun\s+$function\b""").containsMatchIn(facade),
                "Missing public JVM static function $function",
            )
        }
        listOf("roundedRect", "gradientCircle", "heart").forEach { function ->
            assertTrue(Regex("""fun\s+$function\b""").containsMatchIn(facade))
        }
        assertTrue(facade.contains("internal inline fun newPipeline("))
        assertTrue(facade.contains("inline fun RenderPipeline.Builder.screenQuadSnippet()"))
        assertTrue(facade.contains("inline fun RenderPipeline.Builder.withBindGroupLayout("))
        assertFalse(facade.contains("TooManyFunctions"))
    }

    private fun assertOrdered(source: String, vararg markers: String) {
        val positions = markers.map(source::indexOf)
        assertTrue(positions.all { it >= 0 }, "Missing ordered marker in $markers")
        assertEquals(positions.sorted(), positions)
    }

    private fun read(path: String): String = Files.readString(Path.of(path))

    private companion object {
        const val FACADE = "src/main/kotlin/net/ccbluex/liquidbounce/render/ClientRenderPipelines.kt"
        const val BROWSER_AND_GUI = "src/main/kotlin/net/ccbluex/liquidbounce/render/BrowserGuiPipelineDefinitions.kt"
        const val WORLD_PRIMITIVES = "src/main/kotlin/net/ccbluex/liquidbounce/render/WorldPrimitivePipelineDefinitions.kt"
        const val WORLD_EFFECTS = "src/main/kotlin/net/ccbluex/liquidbounce/render/WorldEffectPipelineDefinitions.kt"
        const val SCREEN_EFFECTS = "src/main/kotlin/net/ccbluex/liquidbounce/render/ScreenEffectPipelineDefinitions.kt"
        const val FOG = "src/main/kotlin/net/ccbluex/liquidbounce/render/FogPipelineDefinitions.kt"
        const val COMPOSITES = "src/main/kotlin/net/ccbluex/liquidbounce/render/CompositePipelineDefinitions.kt"

        val DEFINITION_PATHS = listOf(
            BROWSER_AND_GUI,
            WORLD_PRIMITIVES,
            WORLD_EFFECTS,
            SCREEN_EFFECTS,
            FOG,
            COMPOSITES,
        )
        val PIPELINE_IDENTIFIER = Regex("""newPipeline\("([^"]+)"\)""")

        val JVM_FIELDS = listOf(
            "SMOOTH_TEXTURE", "BLURRED_TEXTURE", "BGRA_TEXTURE", "BGRA_BLURRED_TEXTURE", "Blit",
            "TexQuadNoCull", "FontMask", "LinesWithWidth", "LineStrip", "FontMaskQuads", "Outline",
            "EspDownsample", "GuiBackdropDownsample", "GuiBackdropBlurComposite", "FogVolume",
            "FogBlurHorizontal", "FogBlurComposite", "UnifiedFogTerrainMask", "UnifiedFogGenerate",
            "UnifiedFogBlurHorizontal", "UnifiedFogBlurVertical", "UnifiedFogComposite", "EspGaussianBlur",
            "EspGlowComposite", "EspMaskUnion", "EspOutlineComposite", "EspChamsComposite", "ChamsImage",
            "ItemChams", "GuiBlurH", "GuiBlurV", "Blend",
        )
        val JVM_STATIC_FUNCTIONS = listOf(
            "lines", "triangles", "circleLut", "roundedRect", "relativeLines", "triangleStrip", "quads",
            "relativeQuads", "outlineQuads", "texQuads",
        )
        val EXPECTED_IDENTIFIERS = listOf(
            "jcef/smooth_texture", "jcef/blurred_texture", "jcef/bgra_texture", "jcef/bgra_blurred_texture",
            "jcef_blit", "gui/circle_lut", "gui/rounded_rect", "gui/lines", "gui/triangles",
            "gui/lines_no_cull", "gui/triangles_no_cull", "gui/tex_quad_no_cull", "gui/font_mask",
            "lines_with_width", "lines", "lines_depth_tested", "lines_relative_to_camera",
            "lines_relative_to_camera_no_color", "line_strip", "triangles", "triangles_depth_tested",
            "triangle_strip", "triangle_strip_no_depth_test", "quads", "quads_depth_tested",
            "quads_relative_to_camera", "quads_relative_to_camera_no_color", "outline_quads",
            "outline_quads_no_color", "tex_quads", "tex_quads_depth_tested", "font_mask_quads",
            "rounded_rect", "rounded_rect_no_depth_test", "gradient_circle", "gradient_circle_no_depth_test",
            "heart", "heart_no_depth_test", "outline", "esp/downsample", "gui/backdrop_downsample",
            "gui/backdrop_blur_composite", "fog/volume", "fog/blur_horizontal", "fog/blur_composite",
            "fog/unified/terrain_mask", "fog/unified/generate", "fog/unified/blur_horizontal",
            "fog/unified/blur_vertical", "fog/unified/composite", "esp/gaussian_blur", "esp/glow_composite",
            "esp/mask_union", "esp/outline_composite", "esp/chams_composite", "chams/image_blit", "item_chams",
            "blur_h", "blur_v", "blend",
        )
    }
}
