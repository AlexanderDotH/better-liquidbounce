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
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.ccbluex.liquidbounce.render.ClientRenderPipelines.newPipeline
import net.ccbluex.liquidbounce.render.ClientRenderPipelines.screenQuadSnippet
import net.ccbluex.liquidbounce.render.ClientRenderPipelines.withBindGroupLayout
import net.ccbluex.liquidbounce.render.ClientRenderPipelines.withUniformBuffer
import java.util.Optional

internal object CompositePipelineDefinitions {
    val EspGaussianBlur = newPipeline("esp/gaussian_blur") {
        screenQuadSnippet()
        withFragmentShader(ClientShaders.Fragment.EspGaussianBlur)
        withBindGroupLayout { withSampler("InputSampler") }
        withUniformBuffer(ClientUniformDefine.ESP_BLUR)
        withColorTargetState(
            ColorTargetState(Optional.empty<BlendFunction>(), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL)
        )
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }

    val EspGlowComposite = newPipeline("esp/glow_composite") {
        screenQuadSnippet()
        withFragmentShader(ClientShaders.Fragment.EspGlowComposite)
        withBindGroupLayout {
            withSampler("MaskSampler")
            withSampler("BlurSampler")
            withSampler("CoreExclusionSampler")
        }
        withUniformBuffer(ClientUniformDefine.ESP_STYLE)
        withColorTargetState(
            ColorTargetState(
                Optional.of(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA),
                GpuFormat.RGBA8_UNORM,
                ColorTargetState.WRITE_COLOR,
            )
        )
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }

    val EspMaskUnion = newPipeline("esp/mask_union") {
        screenQuadSnippet()
        withFragmentShader(ClientShaders.Fragment.EspMaskUnion)
        withBindGroupLayout { withSampler("MaskSampler") }
        withColorTargetState(
            ColorTargetState(
                Optional.of(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA),
                GpuFormat.RGBA8_UNORM,
                ColorTargetState.WRITE_ALL,
            )
        )
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }

    val EspOutlineComposite = newPipeline("esp/outline_composite") {
        screenQuadSnippet()
        withFragmentShader(ClientShaders.Fragment.EspOutlineComposite)
        withBindGroupLayout { withSampler("MaskSampler") }
        withUniformBuffer(ClientUniformDefine.ESP_STYLE)
        withColorTargetState(
            ColorTargetState(
                Optional.of(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA),
                GpuFormat.RGBA8_UNORM,
                ColorTargetState.WRITE_COLOR,
            )
        )
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }

    val EspChamsComposite = newPipeline("esp/chams_composite") {
        screenQuadSnippet()
        withFragmentShader(ClientShaders.Fragment.EspChamsComposite)
        withBindGroupLayout { withSampler("MaskSampler") }
        withUniformBuffer(ClientUniformDefine.ESP_CHAMS)
        withColorTargetState(
            ColorTargetState(
                Optional.of(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA),
                GpuFormat.RGBA8_UNORM,
                ColorTargetState.WRITE_COLOR,
            )
        )
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }

    val ChamsImage = newPipeline("chams/image_blit") {
        screenQuadSnippet()
        withFragmentShader(ClientShaders.Fragment.Chams)
        withBindGroupLayout {
            withSampler("entityColor")
            withSampler("entityDepth")
            withSampler("sceneDepth")
            withSampler("image")
        }
        withUniformBuffer(ClientUniformDefine.CHAMS)
        withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }

    val ItemChams = newPipeline("item_chams") {
        screenQuadSnippet()
        withFragmentShader(ClientShaders.Fragment.Glow)
        withBindGroupLayout {
            withSampler("texture0")
            withSampler("image")
            withUniformBuffer(ClientUniformDefine.HAND_ITEM_LIGHTMAP)
        }
        withColorTargetState(ColorTargetState.DEFAULT)
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }

    val GuiBlurH = newPipeline("blur_h") {
        screenQuadSnippet()
        withFragmentShader(ClientShaders.Fragment.GuiBlurH)
        withBindGroupLayout {
            withSampler("texture0")
            withUniformBuffer(ClientUniformDefine.GUI_BLUR_KERNEL)
        }
        withCull(false)
        withColorTargetState(ColorTargetState.DEFAULT)
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }

    val GuiBlurV = newPipeline("blur_v") {
        screenQuadSnippet()
        withFragmentShader(ClientShaders.Fragment.GuiBlurV)
        withBindGroupLayout {
            withSampler("texture0")
            withSampler("overlay")
            withUniformBuffer(ClientUniformDefine.GUI_BLUR)
            withUniformBuffer(ClientUniformDefine.GUI_BLUR_KERNEL)
        }
        withCull(false)
        withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }

    val Blend = newPipeline("blend") {
        withVertexShader(ClientShaders.Vertex.PlainPosTex)
        withFragmentShader(ClientShaders.Fragment.Blend)
        withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
        withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        withBindGroupLayout { withSampler("texture0") }
        withUniformBuffer(ClientUniformDefine.BLEND)
        withColorTargetState(ColorTargetState.DEFAULT)
    }
}
