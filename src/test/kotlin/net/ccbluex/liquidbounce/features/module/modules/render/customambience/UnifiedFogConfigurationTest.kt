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
package net.ccbluex.liquidbounce.features.module.modules.render.customambience

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnifiedFogConfigurationTest {

    @Test
    fun `missing engine config keeps the legacy engine as the serialized default`() {
        val module = Files.readString(MODULE_PATH)

        assertTrue(
            Regex("""enumChoice\(\s*"Engine"\s*,\s*FogEngine\.LEGACY\s*\)""")
                .containsMatchIn(module),
            "Engine must default to Legacy so older configs preserve their rendering behavior",
        )
        assertEquals("Legacy", FogEngine.LEGACY.tag)
        assertEquals("Unified", FogEngine.UNIFIED.tag)
    }

    @Test
    fun `unified settings expose the planned defaults without reordering legacy values`() {
        val fogGroup = declarationBody(Files.readString(MODULE_PATH), "object FogValueGroup")

        assertTrue(
            Regex(
                """floatRange\(\s*"Horizon"\s*,\s*70[fF]\s*\.\.\s*100[fF]\s*,""" +
                    """\s*0[fF]\s*\.\.\s*100[fF]\s*,\s*"%"\s*\)""",
            ).containsMatchIn(fogGroup),
        )
        assertTrue(
            Regex(
                """float\(\s*"SilhouetteFeather"\s*,\s*12[fF]\s*,""" +
                    """\s*0[fF]\s*\.\.\s*32[fF]\s*,\s*"px"\s*\)""",
            ).containsMatchIn(fogGroup),
        )
        assertInOrder(
            fogGroup,
            "\"FogDensity\"",
            "\"FogColorOverride\"",
            "\"BlurFog\"",
            "\"VolumetricFog\"",
            "\"BackgroundColor\"",
            "\"Environmental\"",
            "\"RenderDistance\"",
            "\"SkyEnd\"",
            "\"CloudEnd\"",
        )
    }

    @Test
    fun `valid distant horizons far clip defines the physical horizon`() {
        val result = resolveUnifiedFogHorizon(
            horizonPercent = 70f..100f,
            distantHorizonsFarClipBlocks = 2_048f,
            vanillaRenderDistanceChunks = 12,
        )

        assertEquals(FogHorizonSource.DISTANT_HORIZONS, result.source)
        assertEquals(2_048f, result.visibleDistanceBlocks)
        assertEquals(1_433.6f, result.startBlocks, absoluteTolerance = 0.001f)
        assertEquals(2_048f, result.endBlocks)
    }

    @Test
    fun `missing or invalid distant horizons far clip falls back to vanilla blocks`() {
        listOf(null, Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f).forEach { farClip ->
            val result = resolveUnifiedFogHorizon(
                horizonPercent = 70f..100f,
                distantHorizonsFarClipBlocks = farClip,
                vanillaRenderDistanceChunks = 10,
            )

            assertEquals(FogHorizonSource.VANILLA, result.source)
            assertEquals(160f, result.visibleDistanceBlocks)
            assertEquals(112f, result.startBlocks, absoluteTolerance = 0.001f)
            assertEquals(160f, result.endBlocks)
        }
    }

    @Test
    fun `horizon policy clamps percentages and never emits an inverted range`() {
        val result = resolveUnifiedFogHorizon(
            horizonPercent = 120f..-20f,
            distantHorizonsFarClipBlocks = null,
            vanillaRenderDistanceChunks = 8,
        )

        assertEquals(0f, result.startBlocks)
        assertEquals(128f, result.endBlocks)
    }

    @Test
    fun `unified fog gating requires both the fog group and unified engine`() {
        assertTrue(shouldApplyUnifiedFog(fogRunning = true, engine = FogEngine.UNIFIED))
        assertFalse(shouldApplyUnifiedFog(fogRunning = false, engine = FogEngine.UNIFIED))
        assertFalse(shouldApplyUnifiedFog(fogRunning = true, engine = FogEngine.LEGACY))
    }

    private fun declarationBody(source: String, marker: String): String {
        val declarationStart = source.indexOf(marker)
        require(declarationStart >= 0) { "Missing declaration: $marker" }
        val bodyStart = source.indexOf('{', declarationStart)
        require(bodyStart >= 0) { "Missing declaration body: $marker" }

        var depth = 0
        for (index in bodyStart until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return source.substring(bodyStart + 1, index)
                    }
                }
            }
        }

        error("Unterminated declaration: $marker")
    }

    private fun assertInOrder(source: String, vararg tokens: String) {
        var previousIndex = -1
        tokens.forEach { token ->
            val index = source.indexOf(token)
            assertTrue(index > previousIndex, "$token must retain its serialized relative order")
            previousIndex = index
        }
    }

    private companion object {
        val MODULE_PATH: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/customambience/" +
                "ModuleCustomAmbience.kt",
        )
    }
}
