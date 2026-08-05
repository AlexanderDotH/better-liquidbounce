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
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleStorageESP
import net.ccbluex.liquidbounce.features.module.modules.render.esp.modes.EspGlowMode
import net.ccbluex.liquidbounce.features.module.modules.render.esp.modes.EspOutlineMode
import net.ccbluex.liquidbounce.interfaces.PreparedFrameAddition
import net.ccbluex.liquidbounce.render.ClientRenderPipelines
import net.ccbluex.liquidbounce.render.ClientUniformDefine
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.createRenderPass
import net.ccbluex.liquidbounce.render.getDynamicTransformsUniform
import net.ccbluex.liquidbounce.render.mesh.BatchCollector
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
    private val haloOnlyMask = EspRenderTargetHolder(
        "LiquidBounce ESP Halo-Only Mask",
        false,
        GpuFormat.RGBA8_UNORM,
    )
    private val outlineMask = EspRenderTargetHolder("LiquidBounce ESP Outline Mask", true, GpuFormat.RGBA8_UNORM)
    private val blurPing = EspRenderTargetHolder("LiquidBounce ESP Blur Ping", false, GpuFormat.RGBA16_FLOAT)
    private val blurPong = EspRenderTargetHolder("LiquidBounce ESP Blur Pong", false, GpuFormat.RGBA16_FLOAT)

    private val linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
    private val horizontalBlurData = ClientUniformDefine.ESP_BLUR.createSingleBuffer()
    private val verticalBlurData = ClientUniformDefine.ESP_BLUR.createSingleBuffer()
    private val styleData = ClientUniformDefine.ESP_STYLE.createSingleBuffer()
    private val haloHorizontalBlurData = ClientUniformDefine.ESP_BLUR.createSingleBuffer()
    private val haloVerticalBlurData = ClientUniformDefine.ESP_BLUR.createSingleBuffer()
    private val haloStyleData = ClientUniformDefine.ESP_STYLE.createSingleBuffer()

    private val glowFrameLanes = EspGlowFrameLanes()
    private var hasOutline = false
    private var outlineStyle = EspOutlineStyle.DEFAULT

    @JvmStatic
    fun beginFrame() {
        glowFrameLanes.reset()
        hasOutline = false
        outlineStyle = EspOutlineStyle.DEFAULT
    }

    @JvmStatic
    fun capture(preparedFrame: FeatureRenderDispatcher.PreparedFrame) {
        IrisPipelineBypass.run {
            val bridge = preparedFrame as PreparedFrameAddition
            val mainTarget = mc.gameRenderer.mainRenderTarget()
            captureGlow(bridge, mainTarget)
            captureOutline(bridge, mainTarget)
        }
    }

    private fun captureGlow(bridge: PreparedFrameAddition, mainTarget: RenderTarget) {
        val hasNodes = bridge.`liquid_bounce$hasEspGlow`()
        if (!hasNodes && !ModuleStorageESP.GlowMode.running) return

        val target = prepareGlowMask(glowFrameLanes.full, glowMask, mainTarget.width, mainTarget.height)
        if (hasNodes) {
            withOutputTextureOverride(target.colorTextureView, target.depthTextureView) {
                bridge.`liquid_bounce$executeEspGlow`()
            }
        }

        val hasStorageMask = ModuleStorageESP.GlowMode.running && ModuleStorageESP.GlowMode.drawMask(target)
        if (hasNodes || hasStorageMask) {
            glowFrameLanes.full.contribute(
                EspShaderStyleResolver.resolveGlow(
                    EspGlowMode.style.takeIf { EspGlowMode.running },
                    ModuleStorageESP.GlowMode.style.takeIf { ModuleStorageESP.GlowMode.running },
                )
            )
        }
    }

    private fun captureOutline(bridge: PreparedFrameAddition, mainTarget: RenderTarget) {
        val hasNodes = bridge.`liquid_bounce$hasEspOutline`()
        if (!hasNodes && !ModuleStorageESP.OutlineMode.running) return

        outlineStyle = EspShaderStyleResolver.resolveOutline(
            EspOutlineMode.style.takeIf { EspOutlineMode.running },
            ModuleStorageESP.OutlineMode.style.takeIf { ModuleStorageESP.OutlineMode.running },
        )
        val target = outlineMask.initAndClear(mainTarget.width, mainTarget.height)
        if (hasNodes) {
            withOutputTextureOverride(target.colorTextureView, target.depthTextureView) {
                bridge.`liquid_bounce$executeEspOutline`()
            }
        }
        val hasStorageMask = ModuleStorageESP.OutlineMode.running && ModuleStorageESP.OutlineMode.drawMask(target)
        hasOutline = hasNodes || hasStorageMask
    }

    /**
     * Appends dynamic world geometry after the prepared feature frame has been captured.
     * BlockOverlay uses this seam because its interpolated highlight is created by WorldRenderEvent.
     */
    internal fun contributeGlow(
        event: WorldRenderEvent,
        style: EspGlowStyle,
        role: EspGlowContributionRole = EspGlowContributionRole.FULL,
        draw: WorldRenderEnvironment.() -> Unit,
    ) {
        IrisPipelineBypass.run {
            val lane = glowFrameLanes.lane(role)
            val mask = when (role) {
                EspGlowContributionRole.FULL -> glowMask
                EspGlowContributionRole.HALO_ONLY -> haloOnlyMask
            }
            val target = prepareGlowMask(lane, mask, event.renderTarget.width, event.renderTarget.height)
            drawDynamicMask(event, target, draw)
            glowFrameLanes.contribute(role, style)
        }
    }

    @JvmStatic
    fun composite(target: RenderTarget) {
        val plan = EspPostProcessPlan.create(glowFrameLanes.hasAnyContribution, hasOutline)
        if (plan.isEmpty()) return

        IrisPipelineBypass.run {
            try {
                if (glowFrameLanes.full.hasContribution) {
                    writeStyleData(styleData, glowFrameLanes.full.style)
                    val blurredMask = downsampleAndBlur(
                        mask = requireNotNull(glowMask.raw),
                        style = glowFrameLanes.full.style,
                        horizontalData = horizontalBlurData,
                        verticalData = verticalBlurData,
                    )
                    EspCompositePassRenderer.glow(
                        target = target,
                        mask = requireNotNull(glowMask.raw),
                        blurredMask = blurredMask,
                        styleData = styleData,
                    )
                }

                if (glowFrameLanes.haloOnly.hasContribution) {
                    writeStyleData(haloStyleData, glowFrameLanes.haloOnly.style)
                    val blurredMask = downsampleAndBlur(
                        mask = requireNotNull(haloOnlyMask.raw),
                        style = glowFrameLanes.haloOnly.style,
                        horizontalData = haloHorizontalBlurData,
                        verticalData = haloVerticalBlurData,
                    )
                    EspCompositePassRenderer.glow(
                        target = target,
                        mask = requireNotNull(haloOnlyMask.raw),
                        blurredMask = blurredMask,
                        styleData = haloStyleData,
                    )
                }

                if (EspPostProcessPass.OUTLINE_COMPOSITE in plan) {
                    if (!glowFrameLanes.full.hasContribution) {
                        writeStyleData(styleData, EspGlowStyle.DEFAULT)
                    }
                    EspCompositePassRenderer.outline(target, requireNotNull(outlineMask.raw), styleData)
                }
            } finally {
                glowFrameLanes.reset()
                hasOutline = false
            }
        }
    }

    private fun prepareGlowMask(
        state: EspGlowFrameState,
        holder: EspRenderTargetHolder,
        width: Int,
        height: Int,
    ): RenderTarget {
        val current = holder.raw
        if (state.prepareMask() || current == null || current.width != width || current.height != height) {
            return holder.initAndClear(width, height)
        }
        return current
    }

    private fun drawDynamicMask(
        event: WorldRenderEvent,
        target: RenderTarget,
        draw: WorldRenderEnvironment.() -> Unit,
    ) {
        val collector = BatchCollector()
        val environment = WorldRenderEnvironment(target, event.poseStack, event.camera, collector)
        try {
            environment.draw()
        } finally {
            collector.flush(target, getDynamicTransformsUniform())
        }
    }

    private fun downsampleAndBlur(
        mask: RenderTarget,
        style: EspGlowStyle,
        horizontalData: com.mojang.blaze3d.buffers.GpuBufferSlice,
        verticalData: com.mojang.blaze3d.buffers.GpuBufferSlice,
    ): RenderTarget {
        val size = EspTargetSize.halfOf(mask.width, mask.height)
        val ping = blurPing.initAndClear(size.width, size.height)
        val pong = blurPong.initAndClear(size.width, size.height)

        ping.createRenderPass({ "LiquidBounce ESP glow downsample" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspDownsample)
            pass.bindTexture("MaskSampler", mask.colorTextureView, linearSampler)
            pass.draw(3, 1, 0, 0)
        }

        writeBlurData(horizontalData, size.width, size.height, horizontal = true, style)
        pong.createRenderPass({ "LiquidBounce ESP glow horizontal blur" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspGaussianBlur)
            pass.bindTexture("InputSampler", ping.colorTextureView, linearSampler)
            pass.setUniform(ClientUniformDefine.ESP_BLUR.uboName, horizontalData)
            pass.draw(3, 1, 0, 0)
        }

        writeBlurData(verticalData, size.width, size.height, horizontal = false, style)
        ping.createRenderPass({ "LiquidBounce ESP glow vertical blur" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspGaussianBlur)
            pass.bindTexture("InputSampler", pong.colorTextureView, linearSampler)
            pass.setUniform(ClientUniformDefine.ESP_BLUR.uboName, verticalData)
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

    private fun writeStyleData(
        buffer: com.mojang.blaze3d.buffers.GpuBufferSlice,
        glowStyle: EspGlowStyle,
    ) {
        buffer.writeStd140 {
            putVec4(
                glowStyle.coreSize,
                glowStyle.intensity,
                glowStyle.opacity,
                0f,
            )
            putVec4(outlineStyle.thickness, outlineStyle.opacity, 0f, 0f)
        }
    }

    @Suppress("unused")
    private val shutdownHandler = handler<ClientShutdownEvent> {
        glowMask.close()
        haloOnlyMask.close()
        outlineMask.close()
        blurPing.close()
        blurPong.close()
        horizontalBlurData.buffer().close()
        verticalBlurData.buffer().close()
        styleData.buffer().close()
        haloHorizontalBlurData.buffer().close()
        haloVerticalBlurData.buffer().close()
        haloStyleData.buffer().close()
    }
}

internal enum class EspGlowContributionRole {
    FULL,
    HALO_ONLY,
}

private object EspCompositePassRenderer {

    private val linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)

    fun glow(
        target: RenderTarget,
        mask: RenderTarget,
        blurredMask: RenderTarget,
        styleData: com.mojang.blaze3d.buffers.GpuBufferSlice,
    ) {
        target.createRenderPass({ "LiquidBounce ESP glow composite" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspGlowComposite)
            pass.bindTexture("MaskSampler", mask.colorTextureView, linearSampler)
            pass.bindTexture("BlurSampler", blurredMask.colorTextureView, linearSampler)
            pass.bindTexture(
                "CoreExclusionSampler",
                mask.colorTextureView,
                linearSampler,
            )
            pass.setUniform(ClientUniformDefine.ESP_STYLE.uboName, styleData)
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
