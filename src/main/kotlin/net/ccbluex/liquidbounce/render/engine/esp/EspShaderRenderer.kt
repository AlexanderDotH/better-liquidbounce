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

package net.ccbluex.liquidbounce.render.engine.esp

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.ClientShutdownEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleStorageESP
import net.ccbluex.liquidbounce.features.module.modules.render.esp.modes.EspGlowMode
import net.ccbluex.liquidbounce.features.module.modules.render.esp.modes.EspOutlineMode
import net.ccbluex.liquidbounce.interfaces.PreparedFrameAddition
import net.ccbluex.liquidbounce.render.ClientRenderPipelines
import net.ccbluex.liquidbounce.render.ClientUniformDefine
import net.ccbluex.liquidbounce.render.createRenderPass
import net.ccbluex.liquidbounce.utils.render.withOutputTextureOverride
import net.ccbluex.liquidbounce.utils.render.writeStd140
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher

/**
 * Custom 2020-style ESP compositor.
 *
 * Selected geometry is first rendered into LiquidBounce-owned full-resolution masks. Glow is then
 * downsampled and blurred in two separable passes before both effects are composited over the finished
 * world. It never enables Minecraft's glowing flag or its entity-outline post chain.
 */
object EspShaderRenderer : MinecraftShortcuts, EventListener {

    private val glowMask = EspRenderTargetHolder("LiquidBounce ESP Glow Mask", true, GpuFormat.RGBA8_UNORM)
    private val outlineMask = EspRenderTargetHolder("LiquidBounce ESP Outline Mask", true, GpuFormat.RGBA8_UNORM)
    private val blurPing = EspRenderTargetHolder("LiquidBounce ESP Blur Ping", false, GpuFormat.RGBA16_FLOAT)
    private val blurPong = EspRenderTargetHolder("LiquidBounce ESP Blur Pong", false, GpuFormat.RGBA16_FLOAT)

    private val linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
    private val horizontalBlurData = ClientUniformDefine.ESP_BLUR.createSingleBuffer()
    private val verticalBlurData = ClientUniformDefine.ESP_BLUR.createSingleBuffer()
    private val styleData = ClientUniformDefine.ESP_STYLE.createSingleBuffer()

    private var hasGlow = false
    private var hasOutline = false
    private var glowStyle = EspGlowStyle.DEFAULT
    private var outlineStyle = EspOutlineStyle.DEFAULT

    @JvmStatic
    fun beginFrame() {
        hasGlow = false
        hasOutline = false
        glowStyle = EspGlowStyle.DEFAULT
        outlineStyle = EspOutlineStyle.DEFAULT
    }

    @JvmStatic
    fun capture(preparedFrame: FeatureRenderDispatcher.PreparedFrame) {
        IrisPipelineBypass.run {
            val bridge = preparedFrame as PreparedFrameAddition
            val mainTarget = mc.gameRenderer.mainRenderTarget()

            glowStyle = EspShaderStyleResolver.resolveGlow(
                EspGlowMode.style.takeIf { EspGlowMode.running },
                ModuleStorageESP.GlowMode.style.takeIf { ModuleStorageESP.GlowMode.running },
            )
            outlineStyle = EspShaderStyleResolver.resolveOutline(
                EspOutlineMode.style.takeIf { EspOutlineMode.running },
                ModuleStorageESP.OutlineMode.style.takeIf { ModuleStorageESP.OutlineMode.running },
            )

            val hasGlowNodes = bridge.`liquid_bounce$hasEspGlow`()
            if (hasGlowNodes || ModuleStorageESP.GlowMode.running) {
                val target = glowMask.initAndClear(mainTarget.width, mainTarget.height)
                if (hasGlowNodes) {
                    withOutputTextureOverride(target.colorTextureView, target.depthTextureView) {
                        bridge.`liquid_bounce$executeEspGlow`()
                    }
                }
                hasGlow = hasGlowNodes || ModuleStorageESP.GlowMode.drawMask(target)
            }

            val hasOutlineNodes = bridge.`liquid_bounce$hasEspOutline`()
            if (hasOutlineNodes || ModuleStorageESP.OutlineMode.running) {
                val target = outlineMask.initAndClear(mainTarget.width, mainTarget.height)
                if (hasOutlineNodes) {
                    withOutputTextureOverride(target.colorTextureView, target.depthTextureView) {
                        bridge.`liquid_bounce$executeEspOutline`()
                    }
                }
                hasOutline = hasOutlineNodes || ModuleStorageESP.OutlineMode.drawMask(target)
            }
        }
    }

    @JvmStatic
    fun composite(target: RenderTarget) {
        val plan = EspPostProcessPlan.create(hasGlow, hasOutline)
        if (plan.isEmpty()) return

        IrisPipelineBypass.run {
            try {
                writeStyleData()
                var blurredMask: RenderTarget? = null
                if (EspPostProcessPass.DOWNSAMPLE in plan) {
                    blurredMask = downsampleAndBlur(requireNotNull(glowMask.raw), glowStyle)
                }

                if (EspPostProcessPass.GLOW_COMPOSITE in plan) {
                    target.createRenderPass({ "LiquidBounce ESP glow composite" }).use { pass ->
                        pass.setPipeline(ClientRenderPipelines.EspGlowComposite)
                        pass.bindTexture("MaskSampler", requireNotNull(glowMask.raw).colorTextureView, linearSampler)
                        pass.bindTexture("BlurSampler", requireNotNull(blurredMask).colorTextureView, linearSampler)
                        pass.setUniform(ClientUniformDefine.ESP_STYLE.uboName, styleData)
                        pass.draw(3, 1, 0, 0)
                    }
                }

                if (EspPostProcessPass.OUTLINE_COMPOSITE in plan) {
                    target.createRenderPass({ "LiquidBounce ESP outline composite" }).use { pass ->
                        pass.setPipeline(ClientRenderPipelines.EspOutlineComposite)
                        pass.bindTexture("MaskSampler", requireNotNull(outlineMask.raw).colorTextureView, linearSampler)
                        pass.setUniform(ClientUniformDefine.ESP_STYLE.uboName, styleData)
                        pass.draw(3, 1, 0, 0)
                    }
                }
            } finally {
                hasGlow = false
                hasOutline = false
            }
        }
    }

    private fun downsampleAndBlur(mask: RenderTarget, style: EspGlowStyle): RenderTarget {
        val size = EspTargetSize.halfOf(mask.width, mask.height)
        val ping = blurPing.initAndClear(size.width, size.height)
        val pong = blurPong.initAndClear(size.width, size.height)

        ping.createRenderPass({ "LiquidBounce ESP glow downsample" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspDownsample)
            pass.bindTexture("MaskSampler", mask.colorTextureView, linearSampler)
            pass.draw(3, 1, 0, 0)
        }

        writeBlurData(horizontalBlurData, size.width, size.height, horizontal = true, style)
        pong.createRenderPass({ "LiquidBounce ESP glow horizontal blur" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspGaussianBlur)
            pass.bindTexture("InputSampler", ping.colorTextureView, linearSampler)
            pass.setUniform(ClientUniformDefine.ESP_BLUR.uboName, horizontalBlurData)
            pass.draw(3, 1, 0, 0)
        }

        writeBlurData(verticalBlurData, size.width, size.height, horizontal = false, style)
        ping.createRenderPass({ "LiquidBounce ESP glow vertical blur" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspGaussianBlur)
            pass.bindTexture("InputSampler", pong.colorTextureView, linearSampler)
            pass.setUniform(ClientUniformDefine.ESP_BLUR.uboName, verticalBlurData)
            pass.draw(3, 1, 0, 0)
        }

        return ping
    }

    private fun writeBlurData(
        buffer: com.mojang.blaze3d.buffers.GpuBufferSlice,
        width: Int,
        height: Int,
        horizontal: Boolean,
        style: EspGlowStyle,
    ) {
        val kernel = GaussianKernel.forScreenRadius(style.radius, style.softness)
        buffer.writeStd140 {
            putVec4(
                if (horizontal) 1f / width else 0f,
                if (horizontal) 0f else 1f / height,
                kernel.centerWeight,
                0f,
            )
            for (pair in kernel.pairs) {
                putVec4(pair.offset, pair.weight, 0f, 0f)
            }
        }
    }

    private fun writeStyleData() {
        styleData.writeStd140 {
            putVec4(glowStyle.coreSize, glowStyle.intensity, glowStyle.opacity, 0f)
            putVec4(outlineStyle.thickness, outlineStyle.opacity, 0f, 0f)
        }
    }

    @Suppress("unused")
    private val shutdownHandler = handler<ClientShutdownEvent> {
        glowMask.close()
        outlineMask.close()
        blurPing.close()
        blurPong.close()
        horizontalBlurData.buffer().close()
        verticalBlurData.buffer().close()
        styleData.buffer().close()
    }
}
