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

import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTextureView
import net.ccbluex.liquidbounce.render.ClientRenderPipelines
import net.ccbluex.liquidbounce.render.ClientUniformDefine
import net.ccbluex.liquidbounce.render.createRenderPass
import net.ccbluex.liquidbounce.render.engine.esp.IrisPipelineBypass
import net.ccbluex.liquidbounce.render.engine.unifiedfog.UnifiedFogFrame

internal object UnifiedFogPassRenderer {

    private val nearestSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
    private val linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)

    fun render(
        resources: UnifiedFogGpuResources,
        target: RenderTarget,
        frame: UnifiedFogFrame<GpuTextureView>,
        uniform: UnifiedFogUniform,
    ): Int {
        val terrainMask = resources.terrainMaskTarget.initAndGet(target.width, target.height)
        val generatedFog = resources.fogTarget.initAndGet(target.width, target.height)
        val uniformSlice = resources.fogData.get(uniform)
        val distantHorizonsDepth = frame.distantHorizonsSource?.textureView ?: frame.vanillaSource.textureView

        return IrisPipelineBypass.run {
            drawTerrainMask(terrainMask, frame.vanillaSource.textureView, distantHorizonsDepth, uniformSlice)
            drawFogField(
                generatedFog,
                terrainMask,
                frame.vanillaSource.textureView,
                distantHorizonsDepth,
                uniformSlice,
            )
            composite(target, generatedFog, terrainMask, uniformSlice)
            BASE_PASS_COUNT
        }
    }

    private fun drawTerrainMask(
        destination: RenderTarget,
        vanillaDepth: GpuTextureView,
        distantHorizonsDepth: GpuTextureView,
        uniform: GpuBufferSlice,
    ) {
        destination.createRenderPass(
            { "LiquidBounce unified fog terrain mask" },
            useDepthAttachment = false,
        ).use { pass ->
            pass.setPipeline(ClientRenderPipelines.UnifiedFogTerrainMask)
            pass.bindTexture("DepthSampler", vanillaDepth, nearestSampler)
            pass.bindTexture("DhDepthSampler", distantHorizonsDepth, nearestSampler)
            pass.setUniform(ClientUniformDefine.UNIFIED_FOG.uboName, uniform)
            pass.draw(3, 1, 0, 0)
        }
    }

    private fun drawFogField(
        destination: RenderTarget,
        terrainMask: RenderTarget,
        vanillaDepth: GpuTextureView,
        distantHorizonsDepth: GpuTextureView,
        uniform: GpuBufferSlice,
    ) {
        destination.createRenderPass({ "LiquidBounce unified fog field" }, useDepthAttachment = false).use { pass ->
            pass.setPipeline(ClientRenderPipelines.UnifiedFogGenerate)
            pass.bindTexture("TerrainMaskSampler", terrainMask.colorTextureView, nearestSampler)
            pass.bindTexture("DepthSampler", vanillaDepth, nearestSampler)
            pass.bindTexture("DhDepthSampler", distantHorizonsDepth, nearestSampler)
            pass.setUniform(ClientUniformDefine.UNIFIED_FOG.uboName, uniform)
            pass.draw(3, 1, 0, 0)
        }
    }

    private fun composite(
        target: RenderTarget,
        fog: RenderTarget,
        terrainMask: RenderTarget,
        uniform: GpuBufferSlice,
    ) {
        target.createRenderPass(
            { "LiquidBounce unified fog composite" },
            useDepthAttachment = false,
        ).use { pass ->
            pass.setPipeline(ClientRenderPipelines.UnifiedFogComposite)
            pass.bindTexture("FogSampler", fog.colorTextureView, linearSampler)
            pass.bindTexture("TerrainMaskSampler", terrainMask.colorTextureView, nearestSampler)
            pass.setUniform(ClientUniformDefine.UNIFIED_FOG.uboName, uniform)
            pass.draw(3, 1, 0, 0)
        }
    }

    private const val BASE_PASS_COUNT = 3
}
