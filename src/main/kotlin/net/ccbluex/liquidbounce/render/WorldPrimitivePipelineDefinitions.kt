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

internal object WorldPrimitivePipelineDefinitions {
    val LinesWithWidth = newPipeline("lines_with_width") {
        withSnippet(RenderPipelines.LINES_SNIPPET)
        forWorldRender()
    }

    val Lines = newPipeline("lines") {
        posColorSnippet(PrimitiveTopology.DEBUG_LINES)
        forWorldRender()
    }

    val LinesDepthTested = newPipeline("lines_depth_tested") {
        posColorSnippet(PrimitiveTopology.DEBUG_LINES)
        forWorldRender(noDepthTest = false)
    }

    val LinesRelativeToCamera = newPipeline("lines_relative_to_camera") {
        withSnippet(RenderPipelines.DEBUG_FILLED_SNIPPET)
        relativePosColorSnippet(PrimitiveTopology.DEBUG_LINES)
        withUniformBuffer(ClientUniformDefine.MESH_BASE_BLOCK_POS)
        withUniformBuffer(ClientUniformDefine.DISTANCE_FADE)
        forWorldRender()
    }

    val LinesRelativeToCameraNoColor = newPipeline("lines_relative_to_camera_no_color") {
        withSnippet(RenderPipelines.DEBUG_FILLED_SNIPPET)
        relativePosSnippet(PrimitiveTopology.DEBUG_LINES)
        withUniformBuffer(ClientUniformDefine.MESH_BASE_BLOCK_POS)
        withUniformBuffer(ClientUniformDefine.DISTANCE_FADE)
        forWorldRender()
    }

    val LineStrip = newPipeline("line_strip") {
        posColorSnippet(PrimitiveTopology.DEBUG_LINE_STRIP)
        forWorldRender()
    }

    val Triangles = newPipeline("triangles") {
        posColorSnippet(PrimitiveTopology.TRIANGLES)
        forWorldRender()
    }

    val TrianglesDepthTested = newPipeline("triangles_depth_tested") {
        posColorSnippet(PrimitiveTopology.TRIANGLES)
        forWorldRender(noDepthTest = false)
    }

    val TriangleStrip = newPipeline("triangle_strip") {
        posColorSnippet(PrimitiveTopology.TRIANGLE_STRIP)
        forWorldRender(noDepthTest = false)
    }

    val TriangleStripNoDepthTest = newPipeline("triangle_strip_no_depth_test") {
        posColorSnippet(PrimitiveTopology.TRIANGLE_STRIP)
        forWorldRender(noDepthTest = true)
    }

    val Quads = newPipeline("quads") {
        posColorSnippet(PrimitiveTopology.QUADS)
        forWorldRender()
    }

    val QuadsDepthTested = newPipeline("quads_depth_tested") {
        posColorSnippet(PrimitiveTopology.QUADS)
        forWorldRender(noDepthTest = false)
    }

    val QuadsRelativeToCamera = newPipeline("quads_relative_to_camera") {
        withSnippet(RenderPipelines.DEBUG_FILLED_SNIPPET)
        relativePosColorSnippet(PrimitiveTopology.QUADS)
        withUniformBuffer(ClientUniformDefine.MESH_BASE_BLOCK_POS)
        withUniformBuffer(ClientUniformDefine.DISTANCE_FADE)
        forWorldRender()
    }

    val QuadsRelativeToCameraNoColor = newPipeline("quads_relative_to_camera_no_color") {
        withSnippet(RenderPipelines.DEBUG_FILLED_SNIPPET)
        relativePosSnippet(PrimitiveTopology.QUADS)
        withUniformBuffer(ClientUniformDefine.MESH_BASE_BLOCK_POS)
        withUniformBuffer(ClientUniformDefine.DISTANCE_FADE)
        forWorldRender()
    }

    private fun RenderPipeline.Builder.posColorSnippet(mode: PrimitiveTopology) {
        withSnippet(RenderPipelines.DEBUG_FILLED_SNIPPET)
        withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        withPrimitiveTopology(mode)
    }

    private fun RenderPipeline.Builder.relativePosSnippet(mode: PrimitiveTopology) {
        withVertexShader(ClientShaders.Vertex.PosRelativeToCamera)
        withFragmentShader(ClientShaders.Fragment.PosRelativeToCamera)
        withVertexBinding(0, DefaultVertexFormat.POSITION)
        withPrimitiveTopology(mode)
    }

    private fun RenderPipeline.Builder.relativePosColorSnippet(mode: PrimitiveTopology) {
        withVertexShader(ClientShaders.Vertex.PosColorRelativeToCamera)
        withFragmentShader("core/position_color")
        withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        withPrimitiveTopology(mode)
    }

    private fun RenderPipeline.Builder.forWorldRender(noDepthTest: Boolean = true) {
        withCull(false)
        withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        if (noDepthTest) withDepthStencilState(Optional.empty<DepthStencilState>())
    }
}
