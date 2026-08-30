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

package net.ccbluex.liquidbounce.render.engine.unifiedfog

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnifiedFogGpuContractTest {

    @Test
    fun `unified renderer validates one terrain frame before allocating or drawing`() {
        val renderer = read(RENDERER)
        val frameInput = read(FRAME_INPUT)

        assertTrue(renderer.contains("object UnifiedFogRenderer"))
        assertTrue(renderer.contains("fun render(cameraState: CameraRenderState, projectionMatrix: Matrix4fc)"))
        assertTrue(renderer.contains("UnifiedFogFrameFactory.build"))
        assertTrue(renderer.contains("mc.options.getEffectiveRenderDistance()"))
        assertTrue(renderer.contains("DistantHorizonsDepthTextureProvider.resolveRecent"))
        assertTrue(renderer.contains("DistantHorizonsDepthTextureProvider.captureCurrentFrame(token.frameIndex)"))
        assertTrue(frameInput.contains("TerrainDepthValidationPolicy.renderCompatible"))
        assertTrue(renderer.contains("is UnifiedFogFrameBuild.Skipped"))
        assertTrue(renderer.contains("return FogFrameDiagnostics.recordSkipped"))

        val validation = renderer.indexOf("UnifiedFogFrameFactory.build")
        val firstDraw = renderer.indexOf("UnifiedFogPassRenderer.render")
        val liveDepthRefresh = renderer.indexOf("DistantHorizonsDepthTextureProvider.captureCurrentFrame")
        val fallbackDecision = renderer.indexOf("if (!replaceNativeFogThisFrame)")
        assertTrue(validation in 0 until firstDraw, "Frame validation must happen before GPU target allocation")
        assertTrue(liveDepthRefresh in 0 until fallbackDecision, "DH depth must refresh before the fallback decision")
    }

    @Test
    fun `unified renderer keeps full-resolution fog and applies depth-aware scene blur`() {
        val renderer = read(RENDERER) + read(PASS_RENDERER) + read(RESOURCES)

        assertTrue(renderer.contains("GpuFormat.RGBA16_FLOAT"))
        assertTrue(renderer.contains("terrainMaskTarget.initAndGet(target.width, target.height)"))
        assertTrue(renderer.contains("fogTarget.initAndGet(target.width, target.height)"))
        assertTrue(renderer.contains("CustomFogBlurRenderer.render"))
        assertTrue(renderer.contains("bindTexture(\"TerrainMaskSampler\""))
        assertTrue(renderer.contains("bindTexture(\"DepthSampler\""))
        assertTrue(renderer.contains("bindTexture(\"DhDepthSampler\""))
        assertFalse(renderer.contains("val fogForComposite = blurFogIfEnabled"))
        assertFalse(renderer.contains("bindTexture(\"SceneSampler\""))
    }

    @Test
    fun `all unified passes bypass Iris and final composite has no depth attachment`() {
        val renderer = read(RENDERER) + read(PASS_RENDERER) + read(RESOURCES)
        val pipelines = read(PIPELINES)

        assertTrue(renderer.contains("IrisPipelineBypass.run"))
        assertTrue(renderer.contains("ClientRenderPipelines.UnifiedFogTerrainMask"))
        assertTrue(renderer.contains("ClientRenderPipelines.UnifiedFogGenerate"))
        assertTrue(renderer.contains("ClientRenderPipelines.UnifiedFogComposite"))
        assertTrue(renderer.contains("useDepthAttachment = false"))
        assertTrue(pipelines.contains("ColorTargetState.WRITE_COLOR"))
    }

    @Test
    fun `legacy blur and volume install DH public events before terrain renders`() {
        val renderer = read(RENDERER)

        assertTrue(renderer.contains("beginLegacyDistantHorizonsFrame"))
        assertTrue(renderer.contains("activity.shouldRenderBlur"))
        assertTrue(renderer.contains("activity.shouldRenderVolume"))
        assertTrue(renderer.contains("DistantHorizonsDepthTextureProvider.beginFrame(frameIndex)"))
    }

    @Test
    fun `unified resources and debug state cover lifecycle and fail closed frames`() {
        val renderer = read(RENDERER) + read(RESOURCES)
        val debugState = read(DEBUG_STATE)

        assertTrue(renderer.contains("handler<ClientShutdownEvent>"))
        assertTrue(renderer.contains("terrainMaskTarget.close()"))
        assertTrue(renderer.contains("fogTarget.close()"))
        assertTrue(renderer.contains("fogBlurTarget.close()"))
        assertTrue(renderer.contains("fogData.close()"))
        assertTrue(renderer.contains("fogKernelData.close()"))
        assertTrue(debugState.contains("data class UnifiedFogDebugState"))
        assertTrue(debugState.contains("vanillaReady"))
        assertTrue(debugState.contains("distantHorizonsReady"))
        assertTrue(debugState.contains("distantHorizonsBackend"))
        assertTrue(debugState.contains("distantHorizonsApiVersion"))
        assertTrue(debugState.contains("frameAge"))
        assertTrue(debugState.contains("horizonStartBlocks"))
        assertTrue(debugState.contains("horizonEndBlocks"))
        assertTrue(debugState.contains("passCount"))
        assertTrue(debugState.contains("CustomFogRenderBridge.publishDebug(state)"))
    }

    @Test
    fun `unified shader pipeline and uniform registrations stay aligned`() {
        val shaders = read(SHADERS)
        val pipelines = read(PIPELINES)
        val uniforms = read(UNIFORMS)

        assertTrue(shaders.contains("val UnifiedFogTerrainMask = \"unified_fog_terrain_mask\""))
        assertTrue(shaders.contains("val UnifiedFogGenerate = \"unified_fog_generate\""))
        assertTrue(Regex("""val\s+UnifiedFogBlurHorizontal\s*=\s*\"unified_fog_blur_horizontal\"""")
            .containsMatchIn(shaders))
        assertTrue(Regex("""val\s+UnifiedFogBlurVertical\s*=\s*\"unified_fog_blur_vertical\"""")
            .containsMatchIn(shaders))
        assertTrue(shaders.contains("val UnifiedFogComposite = \"unified_fog_composite\""))
        assertTrue(pipelines.contains("newPipeline(\"fog/unified/terrain_mask\")"))
        assertTrue(pipelines.contains("newPipeline(\"fog/unified/generate\")"))
        assertTrue(pipelines.contains("newPipeline(\"fog/unified/blur_horizontal\")"))
        assertTrue(pipelines.contains("newPipeline(\"fog/unified/blur_vertical\")"))
        assertTrue(pipelines.contains("newPipeline(\"fog/unified/composite\")"))
        assertTrue(pipelines.contains("GpuFormat.RGBA16_FLOAT"))
        assertTrue(uniforms.contains("UNIFIED_FOG(\"UnifiedFogData\""))
        assertTrue(uniforms.contains("UNIFIED_FOG_KERNEL(\"UnifiedFogKernelData\""))
    }

    private fun read(path: String): String = Files.readString(Path.of(path))

    private companion object {
        const val RENDERER =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/UnifiedFogRenderer.kt"
        const val DEBUG_STATE =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/UnifiedFogDebugState.kt"
        const val RESOURCES =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/UnifiedFogGpuResources.kt"
        const val FRAME_INPUT =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/FogFrameInput.kt"
        const val PASS_RENDERER =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/UnifiedFogPassRenderer.kt"
        const val SHADERS = "src/main/kotlin/net/ccbluex/liquidbounce/render/ClientShaders.kt"
        const val PIPELINES = "src/main/kotlin/net/ccbluex/liquidbounce/render/FogPipelineDefinitions.kt"
        const val UNIFORMS = "src/main/kotlin/net/ccbluex/liquidbounce/render/ClientUniformDefine.kt"
    }
}
