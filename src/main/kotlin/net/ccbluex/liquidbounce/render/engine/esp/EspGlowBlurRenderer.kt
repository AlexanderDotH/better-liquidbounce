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
package net.ccbluex.liquidbounce.render.engine.esp

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.ccbluex.liquidbounce.render.ClientRenderPipelines
import net.ccbluex.liquidbounce.render.ClientUniformDefine
import net.ccbluex.liquidbounce.render.buffer.writeStd140
import net.ccbluex.liquidbounce.render.createRenderPass
import net.ccbluex.liquidbounce.render.engine.GaussianKernel

internal class EspGlowBlurRenderer : AutoCloseable {
    private val blurPing = EspRenderTargetHolder("LiquidBounce ESP Blur Ping", false, GpuFormat.RGBA16_FLOAT)
    private val blurPong = EspRenderTargetHolder("LiquidBounce ESP Blur Pong", false, GpuFormat.RGBA16_FLOAT)
    private val linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)

    fun blur(mask: RenderTarget, style: EspGlowStyle, resources: EspGlowSourceResources): RenderTarget {
        val size = EspTargetSize.halfOf(mask.width, mask.height)
        val ping = blurPing.initAndClear(size.width, size.height)
        val pong = blurPong.initAndClear(size.width, size.height)
        downsample(mask, ping)
        blurHorizontal(ping, pong, resources, size, style)
        blurVertical(pong, ping, resources, size, style)
        return ping
    }

    private fun downsample(mask: RenderTarget, output: RenderTarget) {
        output.createRenderPass({ "LiquidBounce ESP glow downsample" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspDownsample)
            pass.bindTexture("MaskSampler", mask.colorTextureView, linearSampler)
            pass.draw(3, 1, 0, 0)
        }
    }

    private fun blurHorizontal(
        input: RenderTarget,
        output: RenderTarget,
        resources: EspGlowSourceResources,
        size: EspTargetSize,
        style: EspGlowStyle,
    ) {
        writeBlurData(resources.horizontalBlurData, size, horizontal = true, style)
        output.createRenderPass({ "LiquidBounce ESP glow horizontal blur" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspGaussianBlur)
            pass.bindTexture("InputSampler", input.colorTextureView, linearSampler)
            pass.setUniform(ClientUniformDefine.ESP_BLUR.uboName, resources.horizontalBlurData)
            pass.draw(3, 1, 0, 0)
        }
    }

    private fun blurVertical(
        input: RenderTarget,
        output: RenderTarget,
        resources: EspGlowSourceResources,
        size: EspTargetSize,
        style: EspGlowStyle,
    ) {
        writeBlurData(resources.verticalBlurData, size, horizontal = false, style)
        output.createRenderPass({ "LiquidBounce ESP glow vertical blur" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspGaussianBlur)
            pass.bindTexture("InputSampler", input.colorTextureView, linearSampler)
            pass.setUniform(ClientUniformDefine.ESP_BLUR.uboName, resources.verticalBlurData)
            pass.draw(3, 1, 0, 0)
        }
    }

    private fun writeBlurData(
        buffer: GpuBufferSlice,
        size: EspTargetSize,
        horizontal: Boolean,
        style: EspGlowStyle,
    ) {
        val kernel = GaussianKernel.forScreenRadius(style.radius, style.softness)
        buffer.writeStd140 {
            putVec4(
                if (horizontal) 1f / size.width else 0f,
                if (horizontal) 0f else 1f / size.height,
                kernel.centerWeight,
                0f,
            )
            for (pair in kernel.pairs) putVec4(pair.offset, pair.weight, 0f, 0f)
        }
    }

    override fun close() {
        blurPing.close()
        blurPong.close()
    }
}
