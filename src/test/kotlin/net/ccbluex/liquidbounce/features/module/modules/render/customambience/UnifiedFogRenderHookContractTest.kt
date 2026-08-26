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
import kotlin.test.assertTrue

class UnifiedFogRenderHookContractTest {

    @Test
    fun `unified frame token starts once before frame events and terrain rendering`() {
        val source = readSource(GAME_RENDERER_MIXIN_PATH)
        val method = bracedDeclaration(source, "public void hookGameRender")
        val hookStart = source.lastIndexOf("@Inject(", source.indexOf(method))
        val hook = source.substring(hookStart, source.indexOf(method))

        assertTrue(hook.contains("method = \"render\""))
        assertTrue(hook.contains("at = @At(\"HEAD\")"))
        assertEquals(1, source.countOccurrences("UnifiedFogRenderer.beginFrame()"))
        assertInOrder(
            method,
            "UnifiedFogRenderer.beginFrame();",
            "EventManager.INSTANCE.callEvent(GameRenderEvent.INSTANCE);",
        )
    }

    @Test
    fun `unified engine replaces legacy post processing at the terrain boundary`() {
        val source = readSource(GAME_RENDERER_MIXIN_PATH)
        val method = bracedDeclaration(source, "private void renderCustomFogPostProcessing")
        val hookStart = source.lastIndexOf("@Inject(", source.indexOf(method))
        val hook = source.substring(hookStart, source.indexOf(method))

        assertTrue(hook.contains("method = \"renderLevel\""))
        assertTrue(hook.contains("Lnet/minecraft/client/renderer/LevelRenderer;render("))
        assertTrue(hook.contains("shift = At.Shift.AFTER"))
        assertTrue(
            Regex(
                """if\s*\(\s*ModuleCustomAmbience\.FogValueGroup\.INSTANCE\.isUnified\(\)\s*\)\s*\{""" +
                    """\s*UnifiedFogRenderer\.render\(\s*cameraState\s*,\s*projectionMatrix\s*\)\s*;""" +
                    """\s*return\s*;\s*}""",
            ).containsMatchIn(method),
            "Unified must use exactly one dedicated pass and must not fall through to Legacy postprocessing",
        )
        assertEquals(1, source.countOccurrences("UnifiedFogRenderer.render("))

        val unifiedCall = method.indexOf("UnifiedFogRenderer.render(")
        val blurCall = method.indexOf("CustomFogBlurRenderer.render(")
        val volumeCall = method.indexOf("CustomFogVolumeRenderer.render(")
        assertTrue(unifiedCall in 0 until blurCall)
        assertTrue(blurCall in 0 until volumeCall, "Legacy must preserve blur-before-volume ordering")

        val handHook = source.indexOf("GameRenderer;renderItemInHand")
        val espComposite = source.indexOf("EspShaderRenderer.composite", handHook)
        val worldOverlays = source.indexOf("new WorldRenderEvent")
        assertTrue(handHook > source.indexOf("private void renderCustomFogPostProcessing"))
        assertTrue(espComposite > handHook)
        assertTrue(worldOverlays > source.indexOf("private void renderCustomFogPostProcessing"))
    }

    @Test
    fun `unified disables native world fog while legacy keeps its previous mode policy`() {
        val source = readSource(GAME_RENDERER_MIXIN_PATH)
        val method = bracedDeclaration(source, "private FogRenderer.FogMode customFogMode")

        assertInOrder(
            method,
            "if (!ModuleCustomAmbience.FogValueGroup.INSTANCE.getRunning())",
            "return fogMode;",
            "ModuleCustomAmbience.FogValueGroup.INSTANCE.isUnified()",
            "UnifiedFogRenderer.shouldReplaceNativeFog()",
            "return FogRenderer.FogMode.NONE;",
            "return FogRenderer.FogMode.WORLD;",
            "ModuleCustomAmbience.FogValueGroup.VolumetricFog.INSTANCE.getRunning()",
            "return FogRenderer.FogMode.NONE;",
            "return FogRenderer.FogMode.WORLD;",
        )
    }

    @Test
    fun `unified receives the computed environment color through camera state`() {
        val gameRenderer = readSource(GAME_RENDERER_MIXIN_PATH)
        val fogRenderer = readSource(FOG_RENDERER_MIXIN_PATH)
        val colorHook = bracedDeclaration(fogRenderer, "private void editFogColor")

        assertTrue(
            gameRenderer.contains(
                "UnifiedFogRenderer.render(cameraState, projectionMatrix)",
            ),
            "CameraRenderState carries the environment fog color computed for this rendered frame",
        )
        assertTrue(colorHook.contains("FogColorOverride.INSTANCE"))
        assertTrue(colorHook.contains("if (fogColorOverride.getRunning())"))
        assertTrue(colorHook.contains("fogColorOverride.getColor().toVector4f(dest)"))
        assertTrue(colorHook.contains("ci.cancel()"))
        assertEquals(
            1,
            colorHook.countOccurrences("ci.cancel()"),
            "Vanilla environment color must remain available whenever the override is disabled",
        )
    }

    private fun assertInOrder(source: String, vararg markers: String) {
        var previousIndex = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previousIndex + 1)
            assertTrue(index > previousIndex, "$marker is missing or out of order")
            previousIndex = index
        }
    }

    private fun bracedDeclaration(source: String, marker: String): String {
        val markerIndex = source.indexOf(marker)
        check(markerIndex >= 0) { "Missing declaration marker: $marker" }
        val openingBrace = source.indexOf('{', markerIndex)
        check(openingBrace >= 0) { "Missing opening brace after: $marker" }

        var depth = 0
        for (index in openingBrace until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(markerIndex, index + 1)
            }
        }
        error("Unclosed declaration after: $marker")
    }

    private fun String.countOccurrences(needle: String): Int = windowed(needle.length).count { it == needle }

    private fun readSource(path: Path): String = Files.readString(path)

    private companion object {
        val GAME_RENDERER_MIXIN_PATH: Path = Path.of(
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/render/MixinGameRenderer.java",
        )
        val FOG_RENDERER_MIXIN_PATH: Path = Path.of(
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/render/fog/MixinFogRenderer.java",
        )
    }
}
