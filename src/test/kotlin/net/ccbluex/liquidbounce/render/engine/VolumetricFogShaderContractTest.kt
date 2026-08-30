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

package net.ccbluex.liquidbounce.render.engine

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class VolumetricFogShaderContractTest {

    @Test
    fun `volume raymarch never samples or blurs scene color`() {
        val shader = read(VOLUME_SHADER)

        assertTrue(shader.contains("VOLUME_STEPS"))
        assertTrue(shader.contains("worldFogDensity"))
        assertTrue(shader.contains("CameraPositionAndTime"))
        assertTrue(shader.contains("TWO_PI / max(VolumeSettings.y"))
        assertTrue(shader.contains("uniform sampler2D DepthSampler"))
        assertTrue(shader.contains("LayerSettings"))
        assertTrue(shader.contains("multiLayerFogDensity"))
        assertTrue(shader.contains("cameraClearFactor"))
        assertTrue(shader.contains(": VolumeSettings.z"))
        assertTrue(shader.contains("uniform sampler2D DhDepthSampler"))
        assertTrue(shader.contains("DhInverseMvmProjection"))
        assertTrue(shader.contains("DhDistanceInfo"))
        assertTrue(shader.contains("reconstructDhRelative"))
        assertTrue(shader.contains("remapDhFogPosition"))
        assertTrue(shader.contains("actualDistance * DhDistanceInfo.x + DhDistanceInfo.y"))
        assertTrue(shader.contains("float dhSurfaceDistance = dhDrawn ? length(dhRelative)"))
        assertFalse(shader.contains("dhOccludesFog"))
        assertFalse(shader.contains("SceneSampler"))
        assertFalse(shader.contains("BlurSampler"))
    }

    @Test
    fun `renderer composites one fog volume pass over sharp terrain`() {
        val renderer = read(VOLUME_RENDERER)
        val pipelines = read(PIPELINES)
        val settings = read(VOLUME_SETTINGS)
        val interactionBridge = read(INTERACTION_BRIDGE)
        val nuker = read(NUKER)

        assertTrue(renderer.contains("object CustomFogVolumeRenderer"))
        assertTrue(renderer.contains("ClientRenderPipelines.FogVolume"))
        assertTrue(renderer.contains("IrisPipelineBypass.run"))
        assertTrue(renderer.contains("VolumetricFogLayerSettings.from"))
        assertTrue(renderer.contains("val volume = CustomFogRenderBridge.volumeSettings()"))
        assertTrue(renderer.contains("volumetricInteractionStrength(volume.interactionActive)"))
        assertTrue(settings.contains("interactionActive = CustomFogInteractionBridge.active()"))
        assertTrue(interactionBridge.contains("fun active(): Boolean = provider.active()"))
        assertTrue(nuker.contains("CustomFogInteractionBridge.install { running }"))
        assertTrue(renderer.contains("DistantHorizonsDepthTextureProvider.resolve"))
        assertTrue(renderer.contains("bindTexture(\"DhDepthSampler\""))
        assertTrue(renderer.contains("distantHorizonsDepth?.inverseMvmProjection"))
        assertTrue(renderer.contains("DistantHorizonsFogDistanceMapping.from"))
        assertTrue(renderer.contains("VOLUME_WORLD_PERIOD"))
        assertFalse(renderer.contains("intermediateTarget"))
        assertFalse(renderer.contains("GaussianKernel"))

        val pipeline = declaration(pipelines, "val FogVolume")
        assertTrue(pipeline.contains("newPipeline(\"fog/volume\")"))
        assertTrue(pipeline.contains("withSampler(\"DepthSampler\")"))
        assertTrue(pipeline.contains("withSampler(\"DhDepthSampler\")"))
        assertTrue(pipeline.contains("BlendFunction.TRANSLUCENT"))
    }

    private fun declaration(source: String, marker: String): String {
        val start = source.indexOf(marker)
        check(start >= 0) { "Missing $marker" }
        val openingBrace = source.indexOf('{', start)
        var depth = 0
        for (index in openingBrace until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(start, index + 1)
            }
        }
        error("Unclosed $marker")
    }

    private fun read(path: String): String = Files.readString(Path.of(path))

    private companion object {
        const val VOLUME_SHADER =
            "src/main/resources/resources/liquidbounce/shaders/fog/volumetric_fog.frag"
        const val VOLUME_RENDERER =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/CustomFogVolumeRenderer.kt"
        const val VOLUME_SETTINGS =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/customambience/" +
                "CustomAmbienceFogSettings.kt"
        const val INTERACTION_BRIDGE =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/CustomFogInteractionBridge.kt"
        const val NUKER =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/nuker/ModuleNuker.kt"
        const val PIPELINES =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/FogPipelineDefinitions.kt"
    }
}
