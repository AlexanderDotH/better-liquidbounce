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

@file:JvmName("EspShaderRendererKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.render.engine.esp

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.render.ClientRenderPipelines
import net.ccbluex.liquidbounce.render.ClientUniformDefine
import net.ccbluex.liquidbounce.render.createRenderPass
import net.ccbluex.liquidbounce.render.getDynamicTransformsUniform
import net.ccbluex.liquidbounce.render.buffer.withOutputTextureOverride
import net.ccbluex.liquidbounce.render.buffer.writeStd140

internal object EspCompositePassRenderer {

    private val linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)

    fun glow(
        target: RenderTarget,
        mask: RenderTarget,
        blurredMask: RenderTarget,
        exclusionMask: RenderTarget,
        styleData: com.mojang.blaze3d.buffers.GpuBufferSlice,
    ) {
        target.createRenderPass({ "LiquidBounce ESP glow composite" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspGlowComposite)
            pass.bindTexture("MaskSampler", mask.colorTextureView, linearSampler)
            pass.bindTexture("BlurSampler", blurredMask.colorTextureView, linearSampler)
            pass.bindTexture(
                "CoreExclusionSampler",
                exclusionMask.colorTextureView,
                linearSampler,
            )
            pass.setUniform(ClientUniformDefine.ESP_STYLE.uboName, styleData)
            pass.draw(3, 1, 0, 0)
        }
    }

    fun unionMask(target: RenderTarget, mask: RenderTarget) {
        target.createRenderPass({ "LiquidBounce ESP protected-surface union" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspMaskUnion)
            pass.bindTexture("MaskSampler", mask.colorTextureView, linearSampler)
            pass.draw(3, 1, 0, 0)
        }
    }

    fun outline(
        target: RenderTarget,
        mask: RenderTarget,
        styleData: com.mojang.blaze3d.buffers.GpuBufferSlice,
    ) {
        target.createRenderPass({ "LiquidBounce ESP outline composite" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspOutlineComposite)
            pass.bindTexture("MaskSampler", mask.colorTextureView, linearSampler)
            pass.setUniform(ClientUniformDefine.ESP_STYLE.uboName, styleData)
            pass.draw(3, 1, 0, 0)
        }
    }

}
