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
package net.ccbluex.liquidbounce.render.engine

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FogBlurShaderContractTest {

    @Test
    fun `fog blur stays full resolution and treats DH as a backend independent far field`() {
        val renderer = read(RENDERER)

        assertTrue(renderer.contains("object CustomFogBlurRenderer"))
        assertTrue(renderer.contains("intermediateTarget.initAndGet(target.width, target.height)"))
        assertFalse(renderer.contains("halfOf("))
        assertTrue(renderer.contains("GaussianKernel.forScreenRadius"))
        assertTrue(renderer.contains("FilterMode.LINEAR"))
        assertTrue(renderer.contains("FilterMode.NEAREST"))
        assertTrue(renderer.contains("DistantHorizonsDepthTextureProvider.resolve"))
        assertTrue(renderer.contains("IrisPipelineBypass.run"))
        assertTrue(renderer.contains("bindTexture(\"SceneSampler\""))
        assertTrue(renderer.contains("bindTexture(\"BlurSampler\""))
        assertTrue(renderer.contains("bindTexture(\"DepthSampler\""))
        assertTrue(renderer.contains("bindTexture(\"DhDepthSampler\""))
    }

    @Test
    fun `horizontal and composite shaders protect Vanilla edges while blending the DH sky horizon`() {
        val horizontal = read(HORIZONTAL_SHADER)
        val composite = read(COMPOSITE_SHADER)

        assertTrue(horizontal.contains("uniform sampler2D SceneSampler"))
        assertTrue(horizontal.contains("bilateralWeight"))
        assertTrue(horizontal.contains("MC_LAYER"))
        assertTrue(horizontal.contains("FAR_LAYER"))
        assertTrue(horizontal.contains("DH_LAYER"))
        assertTrue(horizontal.contains("farLayerCompatible"))
        assertTrue(horizontal.contains("uniform sampler2D DhDepthSampler"))
        assertTrue(horizontal.contains("DhInverseMvmProjection"))
        assertTrue(horizontal.contains("DhDistanceInfo"))
        assertTrue(horizontal.contains("reconstructDhRelative"))
        assertTrue(horizontal.contains("FogBlurData"))
        assertTrue(composite.contains("uniform sampler2D BlurSampler"))
        assertTrue(composite.contains("fogFactor"))
        assertTrue(composite.contains("FAR_LAYER"))
        assertTrue(composite.contains("DH_LAYER"))
        assertTrue(composite.contains("farLayerCompatible"))
        assertTrue(composite.contains("uniform sampler2D DhDepthSampler"))
        assertTrue(composite.contains("DhInverseMvmProjection"))
        assertTrue(composite.contains("DhDistanceInfo"))
        assertTrue(composite.contains("reconstructDhRelative"))
        assertTrue(composite.contains("remapDhFogPosition"))
        assertTrue(composite.contains("center.layer == DH_LAYER"))
        assertTrue(composite.contains("distanceFogFactor(remapDhFogPosition"))
        assertFalse(composite.contains("if (center.layer == DH_LAYER) return 0.0"))
        assertTrue(composite.contains("FogBlurData"))
    }

    @Test
    fun `fog blur shaders pipelines and uniform definitions stay aligned`() {
        val shaders = read(SHADERS)
        val pipelines = read(PIPELINES)
        val uniforms = read(UNIFORMS)

        assertTrue(shaders.contains("val FogBlurHorizontal = \"fog_blur_horizontal\""))
        assertTrue(shaders.contains("val FogBlurComposite = \"fog_blur_composite\""))
        assertTrue(pipelines.contains("val FogBlurHorizontal = newPipeline(\"fog/blur_horizontal\")"))
        assertTrue(pipelines.contains("val FogBlurComposite = newPipeline(\"fog/blur_composite\")"))
        assertTrue(pipelines.contains("ClientUniformDefine.FOG_BLUR"))
        assertTrue(pipelines.contains("BlendFunction.TRANSLUCENT"))
        assertTrue(uniforms.contains("FOG_BLUR(\"FogBlurData\""))
    }

    @Test
    fun `fog blur keeps horizontal and vertical uniforms on independent GPU rings`() {
        val renderer = read(RENDERER)

        assertTrue(renderer.contains("horizontalBlurData = createFogBlurData()"))
        assertTrue(renderer.contains("verticalBlurData = createFogBlurData()"))
        assertTrue(renderer.contains("horizontalBlurData.get(frame.uniform(1f / target.width, 0f))"))
        assertTrue(renderer.contains("verticalBlurData.get(frame.uniform(0f, 1f / target.height))"))
    }

    @Test
    fun `fog blur releases its full resolution target and both uniform rings`() {
        val renderer = read(RENDERER)

        assertTrue(renderer.contains("handler<ClientShutdownEvent>"))
        assertTrue(renderer.contains("intermediateTarget.close()"))
        assertTrue(renderer.contains("horizontalBlurData.close()"))
        assertTrue(renderer.contains("verticalBlurData.close()"))
    }

    private fun read(path: String): String = Files.readString(Path.of(path))

    private companion object {
        const val RENDERER =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/CustomFogBlurRenderer.kt"
        const val HORIZONTAL_SHADER =
            "src/main/resources/resources/liquidbounce/shaders/fog/fog_blur_horizontal.frag"
        const val COMPOSITE_SHADER =
            "src/main/resources/resources/liquidbounce/shaders/fog/fog_blur_composite.frag"
        const val SHADERS = "src/main/kotlin/net/ccbluex/liquidbounce/render/ClientShaders.kt"
        const val PIPELINES = "src/main/kotlin/net/ccbluex/liquidbounce/render/FogPipelineDefinitions.kt"
        const val UNIFORMS = "src/main/kotlin/net/ccbluex/liquidbounce/render/ClientUniformDefine.kt"
    }
}
