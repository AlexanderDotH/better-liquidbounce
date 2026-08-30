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
import net.minecraft.client.renderer.BindGroupLayouts
import java.util.Optional

internal object ScreenEffectPipelineDefinitions {
    val Outline = newPipeline("outline") {
        screenQuadSnippet()
        withFragmentShader(ClientShaders.Fragment.EntityOutline)
        withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
        withColorTargetState(
            ColorTargetState(
                Optional.of(BlendFunction.ENTITY_OUTLINE_BLIT),
                GpuFormat.RGBA8_UNORM,
                ColorTargetState.WRITE_COLOR,
            )
        )
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }

    val EspDownsample = newPipeline("esp/downsample") {
        screenQuadSnippet()
        withFragmentShader(ClientShaders.Fragment.EspDownsample)
        withBindGroupLayout { withSampler("MaskSampler") }
        withColorTargetState(
            ColorTargetState(Optional.empty<BlendFunction>(), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL)
        )
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }

    val GuiBackdropDownsample = newPipeline("gui/backdrop_downsample") {
        screenQuadSnippet()
        withFragmentShader(ClientShaders.Fragment.GuiBackdropDownsample)
        withBindGroupLayout { withSampler("SceneSampler") }
        withColorTargetState(
            ColorTargetState(Optional.empty<BlendFunction>(), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL)
        )
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }

    val GuiBackdropBlurComposite = newPipeline("gui/backdrop_blur_composite") {
        screenQuadSnippet()
        withFragmentShader(ClientShaders.Fragment.GuiBackdropBlurComposite)
        withBindGroupLayout {
            withSampler("BlurSampler")
            withSampler("MaskSampler")
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
