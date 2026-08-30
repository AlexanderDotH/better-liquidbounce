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
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.ccbluex.liquidbounce.render.ClientRenderPipelines.newPipeline
import net.ccbluex.liquidbounce.render.ClientRenderPipelines.screenQuadSnippet
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.RenderPipelines
import java.util.Optional

internal object BrowserPipelineDefinitions {
    private val compatibleBlend = BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA

    val SmoothTexture = newPipeline("jcef/smooth_texture") {
        withSnippet(RenderPipelines.GUI_TEXTURED_SNIPPET)
        withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }

    val BlurredTexture = newPipeline("jcef/blurred_texture") {
        withSnippet(RenderPipelines.GUI_TEXTURED_SNIPPET)
        withColorTargetState(ColorTargetState(compatibleBlend))
    }

    val BgraTexture = newPipeline("jcef/bgra_texture") {
        bgraPosTexColorQuads()
        withColorTargetState(ColorTargetState(compatibleBlend))
    }

    val BgraBlurredTexture = newPipeline("jcef/bgra_blurred_texture") {
        bgraPosTexColorQuads()
        withColorTargetState(ColorTargetState(compatibleBlend))
    }

    val Blit = newPipeline("jcef_blit") {
        screenQuadSnippet()
        withFragmentShader("core/blit_screen")
        withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
        withColorTargetState(
            ColorTargetState(
                Optional.of(compatibleBlend),
                GpuFormat.RGBA8_UNORM,
                ColorTargetState.WRITE_COLOR,
            )
        )
        withDepthStencilState(Optional.empty<DepthStencilState>())
    }

    private fun RenderPipeline.Builder.bgraPosTexColorQuads() {
        withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
        withVertexShader("core/position_tex_color")
        withFragmentShader(ClientShaders.Fragment.BgraPosTex)
        withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        withPrimitiveTopology(PrimitiveTopology.QUADS)
    }
}

internal object GuiPipelineDefinitions {
    val CircleLut = newPipeline("gui/circle_lut") {
        withSnippet(RenderPipelines.GUI_SNIPPET)
        withVertexShader(ClientShaders.Vertex.GuiCircleLut)
        withFragmentShader(ClientShaders.Fragment.GuiCircleLut)
        withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        withVertexBinding(0, ClientVertexFormats.GUI_CIRCLE_LUT)
        withPrimitiveTopology(PrimitiveTopology.QUADS)
    }

    val RoundedRect = newPipeline("gui/rounded_rect") {
        withSnippet(RenderPipelines.GUI_SNIPPET)
        withVertexShader(ClientShaders.Vertex.GuiRoundedRect)
        withFragmentShader(ClientShaders.Fragment.GuiRoundedRect)
        withVertexBinding(0, ClientVertexFormats.GUI_ROUNDED_RECT)
        withPrimitiveTopology(PrimitiveTopology.QUADS)
    }

    val Lines = newPipeline("gui/lines") { guiPosColorSnippet(PrimitiveTopology.DEBUG_LINES) }
    val Triangles = newPipeline("gui/triangles") { guiPosColorSnippet(PrimitiveTopology.TRIANGLES) }
    val LinesNoCull = newPipeline("gui/lines_no_cull") {
        guiPosColorSnippet(PrimitiveTopology.DEBUG_LINES)
        withCull(false)
    }
    val TrianglesNoCull = newPipeline("gui/triangles_no_cull") {
        guiPosColorSnippet(PrimitiveTopology.TRIANGLES)
        withCull(false)
    }

    val TexQuadNoCull = newPipeline("gui/tex_quad_no_cull") {
        withSnippet(RenderPipelines.GUI_TEXTURED_SNIPPET)
        withCull(false)
    }

    val FontMask = newPipeline("gui/font_mask") {
        withSnippet(RenderPipelines.GUI_TEXTURED_SNIPPET)
        withFragmentShader(ClientShaders.Fragment.FontMask)
    }

    private fun RenderPipeline.Builder.guiPosColorSnippet(mode: PrimitiveTopology) {
        withSnippet(RenderPipelines.GUI_SNIPPET)
        withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        withPrimitiveTopology(mode)
    }
}
