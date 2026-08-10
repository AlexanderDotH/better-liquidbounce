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
package net.ccbluex.liquidbounce.render.engine.gui

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.vertex.VertexConsumer
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.ClientShutdownEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.render.ClientRenderPipelines
import net.ccbluex.liquidbounce.render.ClientUniformDefine
import net.ccbluex.liquidbounce.render.createRenderPass
import net.ccbluex.liquidbounce.render.engine.RenderDrawKey
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.esp.EspRenderTargetHolder
import net.ccbluex.liquidbounce.render.engine.esp.EspTargetSize
import net.ccbluex.liquidbounce.render.engine.esp.GaussianKernel
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.getDynamicTransformsUniform
import net.ccbluex.liquidbounce.render.mesh.BatchCollector
import net.ccbluex.liquidbounce.utils.render.writeStd140
import org.joml.Matrix3x2fc
import org.joml.Matrix4f
import kotlin.math.roundToInt

/** Rounded backdrop-blur and Gaussian-halo compositor for deferred GUI elements. */
object GuiGlowRenderer : EventListener {

    private val mask = EspRenderTargetHolder("LiquidBounce GUI Frame Mask", false, GpuFormat.RGBA8_UNORM)
    private val blurPing = EspRenderTargetHolder("LiquidBounce GUI Frame Blur Ping", false, GpuFormat.RGBA16_FLOAT)
    private val blurPong = EspRenderTargetHolder("LiquidBounce GUI Frame Blur Pong", false, GpuFormat.RGBA16_FLOAT)

    private val linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
    private val backdropHorizontalBlurData = ClientUniformDefine.ESP_BLUR.createSingleBuffer()
    private val backdropVerticalBlurData = ClientUniformDefine.ESP_BLUR.createSingleBuffer()
    private val horizontalBlurData = ClientUniformDefine.ESP_BLUR.createSingleBuffer()
    private val verticalBlurData = ClientUniformDefine.ESP_BLUR.createSingleBuffer()
    private val styleData = ClientUniformDefine.ESP_STYLE.createSingleBuffer()
    private val frameState = GuiGlowFrameState()

    @JvmStatic
    fun beginFrame() {
        frameState.beginFrame()
    }

    fun requestRoundedFrame(
        pose: Matrix3x2fc,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        radius: Float,
        color: Color4b,
        style: EspGlowStyle,
        backgroundBlurRadius: Float,
    ) {
        frameState.append(
            GuiGlowFrameRequest.transformed(
                pose = pose,
                x1 = x1,
                y1 = y1,
                x2 = x2,
                y2 = y2,
                radius = radius,
                color = color,
                style = style,
                backgroundBlurRadius = backgroundBlurRadius,
            )
        )
    }

    @JvmStatic
    fun composite(mainTarget: RenderTarget, destination: RenderTarget) {
        if (frameState.pendingCount == 0) return

        val shouldClear = frameState.prepareMask(mainTarget.width, mainTarget.height)
        val maskTarget = if (shouldClear) {
            mask.initAndClear(mainTarget.width, mainTarget.height)
        } else {
            requireNotNull(mask.raw)
        }
        val batch = frameState.consume() ?: return

        drawMask(maskTarget, batch.requests)
        compositeBackdropBlur(mainTarget, maskTarget, batch.backgroundBlurRadius)
        val blurredMask = downsampleAndBlur(maskTarget, batch.style)
        writeStyleData(batch.style)

        destination.createRenderPass({ "LiquidBounce GUI glow composite" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspGlowComposite)
            pass.bindTexture("MaskSampler", maskTarget.colorTextureView, linearSampler)
            pass.bindTexture("BlurSampler", blurredMask.colorTextureView, linearSampler)
            pass.bindTexture("CoreExclusionSampler", maskTarget.colorTextureView, linearSampler)
            pass.setUniform(ClientUniformDefine.ESP_STYLE.uboName, styleData)
            pass.draw(3, 1, 0, 0)
        }
    }

    private fun compositeBackdropBlur(
        mainTarget: RenderTarget,
        maskTarget: RenderTarget,
        radius: Float,
    ) {
        val size = EspTargetSize.halfOf(mainTarget.width, mainTarget.height)
        val ping = blurPing.initAndClear(size.width, size.height)
        val pong = blurPong.initAndClear(size.width, size.height)

        ping.createRenderPass({ "LiquidBounce GUI backdrop downsample" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.GuiBackdropDownsample)
            pass.bindTexture("SceneSampler", mainTarget.colorTextureView, linearSampler)
            pass.draw(3, 1, 0, 0)
        }

        writeBlurData(backdropHorizontalBlurData, size, horizontal = true, radius = radius, softness = 1f)
        pong.createRenderPass({ "LiquidBounce GUI backdrop horizontal blur" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspGaussianBlur)
            pass.bindTexture("InputSampler", ping.colorTextureView, linearSampler)
            pass.setUniform(ClientUniformDefine.ESP_BLUR.uboName, backdropHorizontalBlurData)
            pass.draw(3, 1, 0, 0)
        }

        writeBlurData(backdropVerticalBlurData, size, horizontal = false, radius = radius, softness = 1f)
        ping.createRenderPass({ "LiquidBounce GUI backdrop vertical blur" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspGaussianBlur)
            pass.bindTexture("InputSampler", pong.colorTextureView, linearSampler)
            pass.setUniform(ClientUniformDefine.ESP_BLUR.uboName, backdropVerticalBlurData)
            pass.draw(3, 1, 0, 0)
        }

        mainTarget.createRenderPass({ "LiquidBounce GUI backdrop composite" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.GuiBackdropBlurComposite)
            pass.bindTexture("BlurSampler", ping.colorTextureView, linearSampler)
            pass.bindTexture("MaskSampler", maskTarget.colorTextureView, linearSampler)
            pass.draw(3, 1, 0, 0)
        }
    }

    private fun drawMask(target: RenderTarget, requests: List<GuiGlowFrameRequest>) {
        val collector = BatchCollector()
        val key = RenderDrawKey.of(ClientRenderPipelines.GUI.roundedRect())
        collector.start(key).use { scope ->
            for (request in requests) {
                writeRoundedRect(scope.consumer, request)
            }
        }

        collector.flush(
            target,
            getDynamicTransformsUniform(Matrix4f().setTranslation(0f, 0f, -11000f)),
        )
    }

    private fun writeRoundedRect(
        vertices: VertexConsumer,
        request: GuiGlowFrameRequest,
    ) {
        val width = request.width.roundToInt().coerceIn(1, MAX_ENCODED_VALUE)
        val height = request.height.roundToInt().coerceIn(1, MAX_ENCODED_VALUE)
        val radius = request.radius.roundToInt().coerceIn(0, minOf(width, height) / 2)
        for (index in CORNER_ORDER) {
            val offset = index * 2
            vertices.addVertex(request.corners[offset], request.corners[offset + 1], 0f)
                .setUv(U_COORDINATES[index], V_COORDINATES[index])
                .setColor(request.color.argb)
                .setUv1(width, height)
                .setUv2(radius, 0)
                .setLineWidth(0f)
        }
    }

    private fun downsampleAndBlur(mask: RenderTarget, style: EspGlowStyle): RenderTarget {
        val size = EspTargetSize.halfOf(mask.width, mask.height)
        val ping = blurPing.initAndClear(size.width, size.height)
        val pong = blurPong.initAndClear(size.width, size.height)

        ping.createRenderPass({ "LiquidBounce GUI glow downsample" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspDownsample)
            pass.bindTexture("MaskSampler", mask.colorTextureView, linearSampler)
            pass.draw(3, 1, 0, 0)
        }

        writeBlurData(
            horizontalBlurData,
            size,
            horizontal = true,
            radius = style.radius,
            softness = style.softness,
        )
        pong.createRenderPass({ "LiquidBounce GUI glow horizontal blur" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspGaussianBlur)
            pass.bindTexture("InputSampler", ping.colorTextureView, linearSampler)
            pass.setUniform(ClientUniformDefine.ESP_BLUR.uboName, horizontalBlurData)
            pass.draw(3, 1, 0, 0)
        }

        writeBlurData(
            verticalBlurData,
            size,
            horizontal = false,
            radius = style.radius,
            softness = style.softness,
        )
        ping.createRenderPass({ "LiquidBounce GUI glow vertical blur" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspGaussianBlur)
            pass.bindTexture("InputSampler", pong.colorTextureView, linearSampler)
            pass.setUniform(ClientUniformDefine.ESP_BLUR.uboName, verticalBlurData)
            pass.draw(3, 1, 0, 0)
        }

        return ping
    }

    private fun writeBlurData(
        buffer: GpuBufferSlice,
        size: EspTargetSize,
        horizontal: Boolean,
        radius: Float,
        softness: Float,
    ) {
        val kernel = GaussianKernel.forScreenRadius(radius, softness)
        buffer.writeStd140 {
            putVec4(
                if (horizontal) 1f / size.width else 0f,
                if (horizontal) 0f else 1f / size.height,
                kernel.centerWeight,
                0f,
            )
            for (pair in kernel.pairs) {
                putVec4(pair.offset, pair.weight, 0f, 0f)
            }
        }
    }

    private fun writeStyleData(style: EspGlowStyle) {
        styleData.writeStd140 {
            putVec4(style.coreSize, style.intensity, style.opacity, 0f)
            putVec4(0f, 0f, 0f, 0f)
        }
    }

    @Suppress("unused")
    private val shutdownHandler = handler<ClientShutdownEvent> {
        mask.close()
        blurPing.close()
        blurPong.close()
        backdropHorizontalBlurData.buffer().close()
        backdropVerticalBlurData.buffer().close()
        horizontalBlurData.buffer().close()
        verticalBlurData.buffer().close()
        styleData.buffer().close()
    }

    private const val MAX_ENCODED_VALUE = 32767
    private val CORNER_ORDER = intArrayOf(0, 1, 2, 3)
    private val U_COORDINATES = floatArrayOf(0f, 0f, 1f, 1f)
    private val V_COORDINATES = floatArrayOf(0f, 1f, 1f, 0f)
}
