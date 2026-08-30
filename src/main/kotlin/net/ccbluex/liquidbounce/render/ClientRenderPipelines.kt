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

@file:Suppress("NOTHING_TO_INLINE")

package net.ccbluex.liquidbounce.render

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BindGroupLayout
import com.mojang.blaze3d.pipeline.RenderPipeline
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.ccbluex.fastutil.fastIterator
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import net.ccbluex.liquidbounce.utils.client.logger
import net.minecraft.resources.Identifier

object ClientRenderPipelines {

    private val renderPipelines = Object2ObjectOpenHashMap<Identifier, RenderPipeline>()

    internal inline fun newPipeline(
        name: String,
        builderAction: RenderPipeline.Builder.() -> Unit,
    ): RenderPipeline {
        val id = renderIdentifier("pipeline/$name")
        return RenderPipeline.Builder()
            .withLocation(id)
            .apply(builderAction)
            .build().also { pipeline ->
                renderPipelines.put(id, pipeline)?.let { error("Duplicated render pipeline: $id") }
            }
    }

    inline fun RenderPipeline.Builder.withBindGroupLayout(block: BindGroupLayout.Builder.() -> Unit) =
        this.withBindGroupLayout(BindGroupLayout.builder().apply(block).build())

    inline fun BindGroupLayout.Builder.withUniformBuffer(define: ClientUniformDefine) = define.appendTo(this)

    inline fun RenderPipeline.Builder.withUniformBuffer(define: ClientUniformDefine) =
        withBindGroupLayout(define.bindGroupLayout)

    inline fun RenderPipeline.Builder.screenQuadSnippet() = apply {
        withVertexShader("core/screenquad")
        withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
    }

    object JCEF {
        @JvmField
        val SMOOTH_TEXTURE = BrowserPipelineDefinitions.SmoothTexture

        @JvmField
        val BLURRED_TEXTURE = BrowserPipelineDefinitions.BlurredTexture

        @JvmField
        val BGRA_TEXTURE = BrowserPipelineDefinitions.BgraTexture

        @JvmField
        val BGRA_BLURRED_TEXTURE = BrowserPipelineDefinitions.BgraBlurredTexture

        @JvmField
        val Blit = BrowserPipelineDefinitions.Blit
    }

    object GUI {
        @JvmField
        val TexQuadNoCull = GuiPipelineDefinitions.TexQuadNoCull

        @JvmField
        val FontMask = GuiPipelineDefinitions.FontMask

        @JvmStatic
        fun lines(cull: Boolean) = if (cull) GuiPipelineDefinitions.Lines else GuiPipelineDefinitions.LinesNoCull

        @JvmStatic
        fun triangles(cull: Boolean) =
            if (cull) GuiPipelineDefinitions.Triangles else GuiPipelineDefinitions.TrianglesNoCull

        @JvmStatic
        fun circleLut() = GuiPipelineDefinitions.CircleLut

        @JvmStatic
        fun roundedRect() = GuiPipelineDefinitions.RoundedRect
    }

    @JvmField
    val LinesWithWidth = WorldPrimitivePipelineDefinitions.LinesWithWidth

    @JvmStatic
    fun lines(noDepthTest: Boolean) = if (noDepthTest) {
        WorldPrimitivePipelineDefinitions.Lines
    } else {
        WorldPrimitivePipelineDefinitions.LinesDepthTested
    }

    @JvmStatic
    fun relativeLines(useColor: Boolean) = if (useColor) {
        WorldPrimitivePipelineDefinitions.LinesRelativeToCamera
    } else {
        WorldPrimitivePipelineDefinitions.LinesRelativeToCameraNoColor
    }

    @JvmField
    val LineStrip = WorldPrimitivePipelineDefinitions.LineStrip

    @JvmStatic
    fun triangles(noDepthTest: Boolean) = if (noDepthTest) {
        WorldPrimitivePipelineDefinitions.Triangles
    } else {
        WorldPrimitivePipelineDefinitions.TrianglesDepthTested
    }

    @JvmStatic
    fun triangleStrip(noDepthTest: Boolean) = if (noDepthTest) {
        WorldPrimitivePipelineDefinitions.TriangleStripNoDepthTest
    } else {
        WorldPrimitivePipelineDefinitions.TriangleStrip
    }

    @JvmStatic
    fun quads(noDepthTest: Boolean) = if (noDepthTest) {
        WorldPrimitivePipelineDefinitions.Quads
    } else {
        WorldPrimitivePipelineDefinitions.QuadsDepthTested
    }

    @JvmStatic
    fun relativeQuads(useColor: Boolean) = if (useColor) {
        WorldPrimitivePipelineDefinitions.QuadsRelativeToCamera
    } else {
        WorldPrimitivePipelineDefinitions.QuadsRelativeToCameraNoColor
    }

    @JvmStatic
    fun outlineQuads(useColor: Boolean) = if (useColor) {
        WorldEffectPipelineDefinitions.OutlineQuads
    } else {
        WorldEffectPipelineDefinitions.OutlineQuadsNoColor
    }

    @JvmStatic
    fun texQuads(noDepthTest: Boolean) = if (noDepthTest) {
        WorldEffectPipelineDefinitions.TexQuads
    } else {
        WorldEffectPipelineDefinitions.TexQuadsDepthTested
    }

    @JvmField
    val FontMaskQuads = WorldEffectPipelineDefinitions.FontMaskQuads

    fun roundedRect(noDepthTest: Boolean) = if (noDepthTest) {
        WorldEffectPipelineDefinitions.RoundedRectNoDepthTest
    } else {
        WorldEffectPipelineDefinitions.RoundedRect
    }

    fun gradientCircle(noDepthTest: Boolean) = if (noDepthTest) {
        WorldEffectPipelineDefinitions.GradientCircleNoDepthTest
    } else {
        WorldEffectPipelineDefinitions.GradientCircle
    }

    fun heart(noDepthTest: Boolean) = if (noDepthTest) {
        WorldEffectPipelineDefinitions.HeartNoDepthTest
    } else {
        WorldEffectPipelineDefinitions.Heart
    }

    @JvmField
    val Outline = ScreenEffectPipelineDefinitions.Outline

    @JvmField
    val EspDownsample = ScreenEffectPipelineDefinitions.EspDownsample

    @JvmField
    val GuiBackdropDownsample = ScreenEffectPipelineDefinitions.GuiBackdropDownsample

    @JvmField
    val GuiBackdropBlurComposite = ScreenEffectPipelineDefinitions.GuiBackdropBlurComposite

    @JvmField
    val FogVolume = FogPipelineDefinitions.FogVolume

    @JvmField
    val FogBlurHorizontal = FogPipelineDefinitions.FogBlurHorizontal

    @JvmField
    val FogBlurComposite = FogPipelineDefinitions.FogBlurComposite

    @JvmField
    val UnifiedFogTerrainMask = FogPipelineDefinitions.UnifiedFogTerrainMask

    @JvmField
    val UnifiedFogGenerate = FogPipelineDefinitions.UnifiedFogGenerate

    @JvmField
    val UnifiedFogBlurHorizontal = FogPipelineDefinitions.UnifiedFogBlurHorizontal

    @JvmField
    val UnifiedFogBlurVertical = FogPipelineDefinitions.UnifiedFogBlurVertical

    @JvmField
    val UnifiedFogComposite = FogPipelineDefinitions.UnifiedFogComposite

    @JvmField
    val EspGaussianBlur = CompositePipelineDefinitions.EspGaussianBlur

    @JvmField
    val EspGlowComposite = CompositePipelineDefinitions.EspGlowComposite

    @JvmField
    val EspMaskUnion = CompositePipelineDefinitions.EspMaskUnion

    @JvmField
    val EspOutlineComposite = CompositePipelineDefinitions.EspOutlineComposite

    @JvmField
    val EspChamsComposite = CompositePipelineDefinitions.EspChamsComposite

    @JvmField
    val ChamsImage = CompositePipelineDefinitions.ChamsImage

    @JvmField
    val ItemChams = CompositePipelineDefinitions.ItemChams

    @JvmField
    val GuiBlurH = CompositePipelineDefinitions.GuiBlurH

    @JvmField
    val GuiBlurV = CompositePipelineDefinitions.GuiBlurV

    @JvmField
    val Blend = CompositePipelineDefinitions.Blend

    fun precompile() {
        JCEF
        GUI

        renderPipelines.fastIterator().forEach { (_, pipeline) ->
            gpuDevice.precompilePipeline(pipeline, ClientShaders)
        }
        logger.info("Loaded ${renderPipelines.size} Render Pipelines.")
    }
}
