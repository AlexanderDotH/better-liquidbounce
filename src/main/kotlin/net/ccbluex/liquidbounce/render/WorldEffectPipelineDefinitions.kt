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

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.ccbluex.liquidbounce.render.ClientRenderPipelines.newPipeline
import net.ccbluex.liquidbounce.render.ClientRenderPipelines.withUniformBuffer
import net.minecraft.client.renderer.RenderPipelines
import java.util.Optional

internal object WorldEffectPipelineDefinitions {
    val OutlineQuads = newPipeline("outline_quads") {
        withSnippet(RenderPipelines.DEBUG_FILLED_SNIPPET)
        withVertexShader(ClientShaders.Vertex.PosColorRelativeToCamera)
        withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        withPrimitiveTopology(PrimitiveTopology.QUADS)
        withUniformBuffer(ClientUniformDefine.MESH_BASE_BLOCK_POS)
        withUniformBuffer(ClientUniformDefine.DISTANCE_FADE)
        withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
    }

    val OutlineQuadsNoColor = newPipeline("outline_quads_no_color") {
        withSnippet(RenderPipelines.DEBUG_FILLED_SNIPPET)
        withVertexShader(ClientShaders.Vertex.PosRelativeToCamera)
        withFragmentShader(ClientShaders.Fragment.PosRelativeToCamera)
        withVertexBinding(0, DefaultVertexFormat.POSITION)
        withPrimitiveTopology(PrimitiveTopology.QUADS)
        withUniformBuffer(ClientUniformDefine.MESH_BASE_BLOCK_POS)
        withUniformBuffer(ClientUniformDefine.DISTANCE_FADE)
        withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
    }

    val TexQuads = newPipeline("tex_quads") {
        withSnippet(RenderPipelines.GUI_TEXTURED_SNIPPET)
        withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        withPrimitiveTopology(PrimitiveTopology.QUADS)
        forWorldRender()
    }

    val TexQuadsDepthTested = newPipeline("tex_quads_depth_tested") {
        withSnippet(RenderPipelines.GUI_TEXTURED_SNIPPET)
        withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        withPrimitiveTopology(PrimitiveTopology.QUADS)
        forWorldRender(noDepthTest = false)
        withDepthStencilState(DepthStencilState.DEFAULT)
    }

    val FontMaskQuads = newPipeline("font_mask_quads") {
        withSnippet(RenderPipelines.GUI_TEXTURED_SNIPPET)
        withFragmentShader(ClientShaders.Fragment.FontMask)
        withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        withPrimitiveTopology(PrimitiveTopology.QUADS)
        forWorldRender()
    }

    val RoundedRect = newPipeline("rounded_rect") {
        roundedRectSnippet()
        forWorldRender(noDepthTest = false)
    }

    val RoundedRectNoDepthTest = newPipeline("rounded_rect_no_depth_test") {
        roundedRectSnippet()
        forWorldRender(noDepthTest = true)
    }

    val GradientCircle = newPipeline("gradient_circle") {
        gradientCircleSnippet()
        forWorldRender(noDepthTest = false)
    }

    val GradientCircleNoDepthTest = newPipeline("gradient_circle_no_depth_test") {
        gradientCircleSnippet()
        forWorldRender(noDepthTest = true)
    }

    val Heart = newPipeline("heart") {
        heartSdfSnippet()
        forWorldRender(noDepthTest = false)
    }

    val HeartNoDepthTest = newPipeline("heart_no_depth_test") {
        heartSdfSnippet()
        forWorldRender(noDepthTest = true)
    }

    private fun RenderPipeline.Builder.roundedRectSnippet() {
        withSnippet(RenderPipelines.DEBUG_FILLED_SNIPPET)
        withVertexShader(ClientShaders.Vertex.Circle)
        withFragmentShader(ClientShaders.Fragment.RoundedRect)
        withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        withPrimitiveTopology(PrimitiveTopology.QUADS)
        withUniformBuffer(ClientUniformDefine.ROUNDED_RECT)
    }

    private fun RenderPipeline.Builder.gradientCircleSnippet() {
        withSnippet(RenderPipelines.DEBUG_FILLED_SNIPPET)
        withVertexShader(ClientShaders.Vertex.GradientCircle)
        withFragmentShader(ClientShaders.Fragment.GradientCircle)
        withVertexBinding(0, ClientVertexFormats.GRADIENT_CIRCLE)
        withPrimitiveTopology(PrimitiveTopology.QUADS)
    }

    private fun RenderPipeline.Builder.heartSdfSnippet() {
        withSnippet(RenderPipelines.DEBUG_FILLED_SNIPPET)
        withVertexShader(ClientShaders.Vertex.Circle)
        withFragmentShader(ClientShaders.Fragment.HeartSDF)
        withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        withPrimitiveTopology(PrimitiveTopology.QUADS)
    }

    private fun RenderPipeline.Builder.forWorldRender(noDepthTest: Boolean = true) {
        withCull(false)
        withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        if (noDepthTest) withDepthStencilState(Optional.empty<DepthStencilState>())
    }
}
