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

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import net.ccbluex.liquidbounce.render.ClientRenderPipelines.newPipeline
import net.ccbluex.liquidbounce.render.ClientRenderPipelines.screenQuadSnippet
import net.ccbluex.liquidbounce.render.ClientRenderPipelines.withBindGroupLayout
import net.ccbluex.liquidbounce.render.ClientRenderPipelines.withUniformBuffer
import java.util.Optional

internal object FogPipelineDefinitions {
    val FogVolume = newPipeline("fog/volume") {
        screenQuadSnippet()
        withFragmentShader(ClientShaders.Fragment.FogVolume)
        withBindGroupLayout {
            withSampler("DepthSampler")
            withSampler("DhDepthSampler")
            withUniformBuffer(ClientUniformDefine.FOG_VOLUME)
        }
        withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }

    val FogBlurHorizontal = newPipeline("fog/blur_horizontal") {
        screenQuadSnippet()
        withFragmentShader(ClientShaders.Fragment.FogBlurHorizontal)
        withBindGroupLayout {
            withSampler("SceneSampler")
            withSampler("DepthSampler")
            withSampler("DhDepthSampler")
            withUniformBuffer(ClientUniformDefine.FOG_BLUR)
        }
        withColorTargetState(
            ColorTargetState(Optional.empty<BlendFunction>(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL)
        )
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }

    val FogBlurComposite = newPipeline("fog/blur_composite") {
        screenQuadSnippet()
        withFragmentShader(ClientShaders.Fragment.FogBlurComposite)
        withBindGroupLayout {
            withSampler("BlurSampler")
            withSampler("DepthSampler")
            withSampler("DhDepthSampler")
            withUniformBuffer(ClientUniformDefine.FOG_BLUR)
        }
        withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }

    val UnifiedFogTerrainMask = newPipeline("fog/unified/terrain_mask") {
        screenQuadSnippet()
        withFragmentShader(ClientShaders.Fragment.UnifiedFogTerrainMask)
        withBindGroupLayout {
            withSampler("DepthSampler")
            withSampler("DhDepthSampler")
            withUniformBuffer(ClientUniformDefine.UNIFIED_FOG)
        }
        withColorTargetState(
            ColorTargetState(Optional.empty<BlendFunction>(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL)
        )
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }

    val UnifiedFogGenerate = newPipeline("fog/unified/generate") {
        screenQuadSnippet()
        withFragmentShader(ClientShaders.Fragment.UnifiedFogGenerate)
        withBindGroupLayout {
            withSampler("TerrainMaskSampler")
            withSampler("DepthSampler")
            withSampler("DhDepthSampler")
            withUniformBuffer(ClientUniformDefine.UNIFIED_FOG)
        }
        withColorTargetState(
            ColorTargetState(Optional.empty<BlendFunction>(), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL)
        )
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }

    val UnifiedFogBlurHorizontal = newPipeline("fog/unified/blur_horizontal") {
        screenQuadSnippet()
        withFragmentShader(ClientShaders.Fragment.UnifiedFogBlurHorizontal)
        withBindGroupLayout {
            withSampler("FogSampler")
            withSampler("TerrainMaskSampler")
            withUniformBuffer(ClientUniformDefine.UNIFIED_FOG)
            withUniformBuffer(ClientUniformDefine.UNIFIED_FOG_KERNEL)
        }
        withColorTargetState(
            ColorTargetState(Optional.empty<BlendFunction>(), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL)
        )
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }

    val UnifiedFogBlurVertical = newPipeline("fog/unified/blur_vertical") {
        screenQuadSnippet()
        withFragmentShader(ClientShaders.Fragment.UnifiedFogBlurVertical)
        withBindGroupLayout {
            withSampler("FogSampler")
            withSampler("TerrainMaskSampler")
            withUniformBuffer(ClientUniformDefine.UNIFIED_FOG)
            withUniformBuffer(ClientUniformDefine.UNIFIED_FOG_KERNEL)
        }
        withColorTargetState(
            ColorTargetState(Optional.empty<BlendFunction>(), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL)
        )
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }

    val UnifiedFogComposite = newPipeline("fog/unified/composite") {
        screenQuadSnippet()
        withFragmentShader(ClientShaders.Fragment.UnifiedFogComposite)
        withBindGroupLayout {
            withSampler("FogSampler")
            withSampler("TerrainMaskSampler")
            withUniformBuffer(ClientUniformDefine.UNIFIED_FOG)
        }
        withColorTargetState(
            ColorTargetState(
                Optional.of(BlendFunction.TRANSLUCENT),
                GpuFormat.RGBA8_UNORM,
                ColorTargetState.WRITE_COLOR,
            )
        )
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }
}
