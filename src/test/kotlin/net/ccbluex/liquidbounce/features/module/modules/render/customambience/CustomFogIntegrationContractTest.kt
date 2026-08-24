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
package net.ccbluex.liquidbounce.features.module.modules.render.customambience

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CustomFogIntegrationContractTest {

    @Test
    fun `serialized fog settings retain their names defaults and stable order`() {
        val module = bracedDeclaration(
            readSource(MODULE_PATH),
            "object FogValueGroup",
        )

        assertTrue(
            Regex("""int\(\s*"FogDensity"\s*,\s*0\s*,\s*0\.\.100\s*,\s*"%"\s*\)""")
                .containsMatchIn(module),
            "FogDensity must remain a 0%-default serialized integer",
        )
        assertTrue(
            Regex(
                """object\s+BlurFog\s*:\s*ToggleableValueGroup\(""" +
                    """\s*this\s*,\s*"BlurFog"\s*,\s*false\s*\)"""
            ).containsMatchIn(module),
            "BlurFog must be restored as an independent serialized group",
        )
        assertTrue(
            Regex(
                """object\s+VolumetricFog\s*:\s*ToggleableValueGroup\(""" +
                    """\s*this\s*,\s*"VolumetricFog"\s*,\s*false\s*\)"""
            ).containsMatchIn(module),
            "VolumetricFog must remain independent from BlurFog",
        )
        assertTrue(
            Regex("""float\(\s*"Strength"\s*,\s*14[fF]\s*,\s*4[fF]\s*\.\.\s*24[fF]\s*\)""")
                .containsMatchIn(module),
            "VolumetricFog Strength must retain its legacy value range",
        )
        assertTrue(
            Regex(
                """ToggleableValueGroup\(\s*this\s*,\s*"Fog"\s*,\s*true\s*\)"""
            ).containsMatchIn(module),
            "the existing Fog group default is a serialized compatibility contract",
        )

        assertInOrder(
            module,
            listOf(
                "\"FogDensity\"",
                "\"FogColorOverride\"",
                "\"BlurFog\"",
                "\"VolumetricFog\"",
                "\"BackgroundColor\"",
                "\"Environmental\"",
                "\"RenderDistance\"",
                "\"SkyEnd\"",
                "\"CloudEnd\"",
            ),
        )
        assertFalse(module.contains("\"DisableWorldFog\""))
    }

    @Test
    fun `enabled custom fog forces the world fog buffer instead of following vanilla mode selection`() {
        val gameRenderer = readSource(GAME_RENDERER_MIXIN_PATH)
        val methodMarker = "private FogRenderer.FogMode customFogMode"
        val methodIndex = gameRenderer.indexOf(methodMarker)
        val hookIndex = gameRenderer.lastIndexOf("@ModifyArg(", methodIndex)
        assertTrue(methodIndex >= 0 && hookIndex >= 0)
        val hook = gameRenderer.substring(hookIndex, methodIndex)
        val fogMode = bracedDeclaration(gameRenderer, methodMarker)

        assertTrue(hook.contains("ordinal = 0"), "only the world fog lookup may be forced")
        assertTrue(fogMode.contains("FogValueGroup.INSTANCE.getRunning()"))
        assertTrue(fogMode.contains("FogValueGroup.VolumetricFog.INSTANCE.getRunning()"))
        assertTrue(fogMode.contains("return FogRenderer.FogMode.NONE"))
        assertTrue(fogMode.contains("return FogRenderer.FogMode.WORLD"))
        assertTrue(fogMode.contains("return fogMode"))
    }

    @Test
    fun `volumetric fog shader pipeline and sampler layouts agree`() {
        val shaders = readSource(CLIENT_SHADERS_PATH)
        val pipelines = readSource(CLIENT_PIPELINES_PATH)
        val uniformDefinitions = readSource(CLIENT_UNIFORMS_PATH)

        verifyShaderPipelinePair(
            shaders = shaders,
            pipelines = pipelines,
            fieldName = "FogVolume",
            shaderRegistrationName = "fog_volume",
            pipelineRegistrationName = "fog/volume",
            resourcePath = VOLUME_SHADER_PATH,
            expectedSamplers = listOf("DepthSampler", "DhDepthSampler"),
        )

        assertTrue(
            Regex(
                    """FOG_VOLUME\(\s*"FogVolumeData"\s*,\s*std140Size\s*\{""" +
                    """[^}]*mat4f\s*\+\s*mat4f\s*\+\s*mat4f\s*\+\s*repeat\(7\)"""
            )
                .containsMatchIn(uniformDefinitions),
            "FogVolumeData must carry MC and DH reconstruction matrices plus multilayer settings",
        )
    }

    @Test
    fun `fog blur and volume run after terrain with blur first and before later world overlays`() {
        val gameRenderer = readSource(GAME_RENDERER_MIXIN_PATH)
        val blurCall = gameRenderer.indexOf("CustomFogBlurRenderer.render(")
        val renderCall = gameRenderer.indexOf("CustomFogVolumeRenderer.render(")
        val hookStart = gameRenderer.lastIndexOf("@Inject(", blurCall)
        assertTrue(blurCall >= 0, "MixinGameRenderer must call CustomFogBlurRenderer")
        assertTrue(renderCall >= 0, "MixinGameRenderer must call CustomFogVolumeRenderer")
        assertTrue(blurCall < renderCall, "blurred fog terrain must be composited before world-space volume")
        assertTrue(hookStart >= 0, "custom fog postprocessing must be called from an inject hook")
        val hook = gameRenderer.substring(hookStart, blurCall)

        assertTrue(hook.contains("method = \"renderLevel\""))
        assertTrue(hook.contains("Lnet/minecraft/client/renderer/LevelRenderer;render("))
        assertTrue(hook.contains("Lnet/minecraft/client/renderer/state/level/CameraRenderState;"))
        assertTrue(hook.contains("shift = At.Shift.AFTER"))
        assertTrue(hook.contains("@Local(name = \"projectionMatrix\") Matrix4f projectionMatrix"))
        assertTrue(
            Regex(
                """CustomFogBlurRenderer\.render\(\s*(?:this\.)?mainRenderTarget\s*,\s*cameraState\s*,""" +
                    """\s*projectionMatrix\s*\)"""
            ).containsMatchIn(gameRenderer),
            "the blur needs the live target, camera state, and rendered projection matrix",
        )
        assertTrue(
            Regex(
                """CustomFogVolumeRenderer\.render\(\s*(?:this\.)?mainRenderTarget\s*,\s*cameraState\s*,""" +
                    """\s*projectionMatrix\s*\)"""
            ).containsMatchIn(gameRenderer),
            "the postprocessor needs the live target, camera state, and rendered projection matrix",
        )

        val handHook = gameRenderer.indexOf("GameRenderer;renderItemInHand")
        val worldRenderEvent = gameRenderer.indexOf("new WorldRenderEvent")
        assertTrue(handHook > renderCall, "fog postprocessing must run before held-item and ESP rendering")
        assertTrue(worldRenderEvent > renderCall, "fog postprocessing must run before WorldRenderEvent overlays")
    }

    @Test
    fun `fog volume keeps terrain sharp and owns one frame-safe uniform ring`() {
        val renderer = readSource(FOG_VOLUME_RENDERER_PATH)
        val cachedUniform = readSource(CACHED_UNIFORM_PATH)

        assertTrue(renderer.contains("object CustomFogVolumeRenderer : MinecraftShortcuts, EventListener"))
        assertTrue(
            Regex(
                """volumeData\s*=\s*CachedUniform(?:<[^>\n]+>)?\(\s*ClientUniformDefine\.FOG_VOLUME"""
            ).containsMatchIn(renderer),
            "fog volume uniforms must be backed by a CachedUniform GPU ring",
        )
        assertFalse(renderer.contains("intermediateTarget"))
        assertFalse(renderer.contains("SceneSampler"))
        assertFalse(renderer.contains("BlurSampler"))
        assertTrue(renderer.contains("depthSampler") && renderer.contains("FilterMode.NEAREST"))
        assertTrue(renderer.contains("bindTexture(\"DepthSampler\", depthTexture, depthSampler)"))
        assertTrue(renderer.contains("bindTexture(\"DhDepthSampler\""))
        assertTrue(renderer.contains("DistantHorizonsDepthTextureProvider.resolve"))
        assertTrue(renderer.contains("IrisPipelineBypass.run"))
        assertTrue(cachedUniform.contains("define.createRingBuffer()"))
        assertTrue(cachedUniform.contains("buffers.close()"))

        val shutdown = bracedDeclaration(renderer, "handler<ClientShutdownEvent>")
        assertTrue(shutdown.contains("volumeData.close()"))
    }

    private fun verifyShaderPipelinePair(
        shaders: String,
        pipelines: String,
        fieldName: String,
        shaderRegistrationName: String,
        pipelineRegistrationName: String,
        resourcePath: String,
        expectedSamplers: List<String>,
    ) {
        val registration = assertNotNull(Regex(
            """val\s+$fieldName\s*=\s*"([^"]+)"\(\s*"([^"]+)"\s*\)"""
        ).find(shaders), "$fieldName must be registered in ClientShaders")
        assertEquals(shaderRegistrationName, registration.groupValues[1])
        assertEquals(
            resourcePath.removePrefix("src/main/resources/resources/liquidbounce/"),
            registration.groupValues[2],
        )

        val pipeline = bracedDeclaration(pipelines, "val $fieldName")
        val pipelineName = Regex("""newPipeline\(\s*"([^"]+)"\s*\)""")
            .find(pipeline)
            ?.groupValues
            ?.get(1)
        assertEquals(pipelineRegistrationName, pipelineName, "$fieldName pipeline registration mismatch")
        assertTrue(pipeline.contains("ClientShaders.Fragment.$fieldName"))
        assertTrue(pipeline.contains("ClientUniformDefine.FOG_VOLUME"))

        val shader = readSource(resourcePath)
        val shaderSamplers = SHADER_SAMPLER.findAll(shader).map { it.groupValues[1] }.toList()
        val pipelineSamplers = PIPELINE_SAMPLER.findAll(pipeline).map { it.groupValues[1] }.toList()
        assertEquals(expectedSamplers, shaderSamplers, "$fieldName shader sampler contract")
        assertEquals(shaderSamplers, pipelineSamplers, "$fieldName pipeline sampler contract")
        assertTrue(shader.contains("uniform FogVolumeData"), "$fieldName must consume FogVolumeData")
        assertTrue(shader.contains("worldFogDensity"), "$fieldName must use moving world-space density")
        assertFalse(shader.contains("SceneSampler"), "$fieldName must not blur terrain color")
    }

    private fun assertInOrder(source: String, markers: List<String>) {
        var previousIndex = -1
        for (marker in markers) {
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

    private fun readSource(path: String): String = Files.readString(Path.of(path))

    private companion object {
        const val MODULE_PATH =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/customambience/" +
                "ModuleCustomAmbience.kt"
        const val FOG_VOLUME_RENDERER_PATH =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/CustomFogVolumeRenderer.kt"
        const val CLIENT_SHADERS_PATH =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/ClientShaders.kt"
        const val CLIENT_PIPELINES_PATH =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/ClientRenderPipelines.kt"
        const val CLIENT_UNIFORMS_PATH =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/ClientUniformDefine.kt"
        const val CACHED_UNIFORM_PATH =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/buffers/CachedUniform.kt"
        const val GAME_RENDERER_MIXIN_PATH =
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/render/MixinGameRenderer.java"
        const val VOLUME_SHADER_PATH =
            "src/main/resources/resources/liquidbounce/shaders/fog/volumetric_fog.frag"

        val SHADER_SAMPLER = Regex("""uniform\s+sampler2D\s+(\w+)\s*;""")
        val PIPELINE_SAMPLER = Regex("""withSampler\(\s*"([^"]+)"\s*\)""")
    }
}
