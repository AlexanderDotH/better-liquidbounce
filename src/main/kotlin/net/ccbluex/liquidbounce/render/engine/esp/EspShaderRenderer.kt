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

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.pipeline.RenderTarget
import net.ccbluex.liquidbounce.common.EspMaskLayer
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.WorldRenderContext
import net.ccbluex.liquidbounce.event.events.ClientShutdownEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.interfaces.PreparedFrameAddition
import net.ccbluex.liquidbounce.render.ClientUniformDefine
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.render.buffer.withOutputTextureOverride
import net.ccbluex.liquidbounce.render.buffer.writeStd140
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher
import java.util.EnumMap

/**
 * Custom 2020-style ESP compositor.
 *
 * Selected geometry is first rendered into LiquidBounce-owned full-resolution masks. Glow is then
 * downsampled and blurred in two separable passes before both effects are composited over the finished
 * world. It never enables Minecraft's glowing flag or its entity-outline post chain.
 */
object EspShaderRenderer : EventListener {

    private val glowResources = EnumMap<EspGlowSource, EspGlowSourceResources>(EspGlowSource::class.java)
    private val outlineMask = EspRenderTargetHolder("LiquidBounce ESP Outline Mask", true, GpuFormat.RGBA8_UNORM)
    private val protectedSurfaceMask = EspRenderTargetHolder(
        "LiquidBounce ESP Protected Surfaces",
        true,
        GpuFormat.RGBA8_UNORM,
    )
    private val sourceExclusionMask = EspRenderTargetHolder(
        "LiquidBounce ESP Source Exclusion",
        false,
        GpuFormat.RGBA8_UNORM,
    )
    private val outlineStyleData = ClientUniformDefine.ESP_STYLE.createSingleBuffer()
    private val glowBlurRenderer = EspGlowBlurRenderer()

    private val glowFrameSources = EspGlowFrameSources()
    private val preparedGlowCapturer = EspPreparedGlowCapturer(::prepareGlowMask, glowFrameSources::contribute)
    private val protectedMaskRenderer = EspProtectedMaskRenderer(
        protectedSurfaceMask,
        sourceExclusionMask,
        ::resources,
    )
    private val chamsCapturer = EspPreparedChamsCapturer()
    private var hasOutline = false
    private var outlineStyle = EspOutlineStyle.DEFAULT

    @JvmStatic
    fun beginFrame() {
        TargetGlowSourceRegistry.beginFrame()
        glowFrameSources.reset()
        protectedMaskRenderer.beginFrame()
        hasOutline = false
        outlineStyle = EspOutlineStyle.DEFAULT
        chamsCapturer.beginFrame()
    }

    @JvmStatic
    fun capture(preparedFrame: FeatureRenderDispatcher.PreparedFrame) {
        IrisPipelineBypass.run {
            val bridge = preparedFrame as PreparedFrameAddition
            val mainTarget = mc.gameRenderer.mainRenderTarget()
            preparedGlowCapturer.capture(bridge, mainTarget)
            protectedMaskRenderer.capture(bridge, mainTarget)
            chamsCapturer.capture(bridge, mainTarget)
            captureOutline(bridge, mainTarget)
        }
    }

    private fun captureOutline(bridge: PreparedFrameAddition, mainTarget: RenderTarget) {
        val hasPlayerNodes = bridge.`liquid_bounce$hasEspMask`(EspMaskLayer.PLAYER_OUTLINE)
        val hasStorageNodes = bridge.`liquid_bounce$hasEspMask`(EspMaskLayer.STORAGE_OUTLINE)
        val playerProvider = EspFeatureRendererRegistry.outline(EspMaskLayer.PLAYER_OUTLINE)
        val storageProvider = EspFeatureRendererRegistry.outline(EspMaskLayer.STORAGE_OUTLINE)
        val playerStyle = playerProvider?.style?.invoke()
        val storageStyle = storageProvider?.style?.invoke()
        if (!hasPlayerNodes && !hasStorageNodes && storageStyle == null) return

        outlineStyle = EspShaderStyleResolver.resolveOutline(
            playerStyle,
            storageStyle,
        )
        val target = outlineMask.initAndClear(mainTarget.width, mainTarget.height)
        if (hasPlayerNodes) {
            withOutputTextureOverride(target.colorTextureView, target.depthTextureView) {
                bridge.`liquid_bounce$executeEspMask`(EspMaskLayer.PLAYER_OUTLINE)
            }
        }
        if (hasStorageNodes) {
            withOutputTextureOverride(target.colorTextureView, target.depthTextureView) {
                bridge.`liquid_bounce$executeEspMask`(EspMaskLayer.STORAGE_OUTLINE)
            }
        }
        val hasStorageMask = storageStyle != null && storageProvider.drawMask(target)
        hasOutline = hasPlayerNodes || hasStorageNodes || hasStorageMask
    }

    /**
     * Appends dynamic world geometry after the prepared feature frame has been captured.
     * BlockOverlay uses this seam because its interpolated highlight is created by WorldRenderEvent.
     */
    internal fun contributeGlow(
        event: WorldRenderContext,
        source: EspGlowSource,
        style: EspGlowStyle?,
        draw: WorldRenderEnvironment.() -> Unit,
    ) {
        IrisPipelineBypass.run {
            val target = prepareGlowMask(source, event.renderTarget.width, event.renderTarget.height)
            EspDynamicMaskRenderer.draw(event, target, draw)
            style?.let { glowFrameSources.contribute(source, it) }
        }
    }

    @JvmStatic
    fun composite(target: RenderTarget) {
        val plan = EspPostProcessPlan.create(
            glowFrameSources.hasAnyContribution,
            hasOutline,
            chamsCapturer.hasContribution,
        )
        if (plan.isEmpty()) return

        IrisPipelineBypass.run {
            try {
                if (EspPostProcessPass.CHAMS_COMPOSITE in plan) {
                    chamsCapturer.composite(target)
                }
                val activeSources = glowFrameSources.activeSources
                val maskSources = glowFrameSources.maskSources
                activeSources.forEach { source ->
                    val exclusion = protectedMaskRenderer.prepare(source, maskSources)
                        ?: requireNotNull(resources(source).mask.raw)
                    compositeGlow(target, source, exclusion)
                }

                if (EspPostProcessPass.OUTLINE_COMPOSITE in plan) {
                    writeStyleData(outlineStyleData, EspGlowStyle.DEFAULT)
                    EspCompositePassRenderer.outline(target, requireNotNull(outlineMask.raw), outlineStyleData)
                }
            } finally {
                glowFrameSources.reset()
                hasOutline = false
            }
        }
    }

    private fun compositeGlow(target: RenderTarget, source: EspGlowSource, exclusionMask: RenderTarget) {
        val state = glowFrameSources.state(source)
        val resources = resources(source)
        val mask = requireNotNull(resources.mask.raw)
        writeStyleData(resources.styleData, state.style)
        val blurredMask = glowBlurRenderer.blur(mask, state.style, resources)
        EspCompositePassRenderer.glow(target, mask, blurredMask, exclusionMask, resources.styleData)
    }

    private fun prepareGlowMask(source: EspGlowSource, width: Int, height: Int): RenderTarget {
        val holder = resources(source).mask
        val current = holder.raw
        val needsClear = glowFrameSources.prepareMask(source) ||
            current == null || current.width != width || current.height != height
        if (needsClear) {
            return holder.initAndClear(width, height)
        }
        return current
    }

    private fun resources(source: EspGlowSource): EspGlowSourceResources =
        glowResources.getOrPut(source) { EspGlowSourceResources(source) }

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
        glowResources.values.forEach(EspGlowSourceResources::close)
        glowResources.clear()
        outlineMask.close()
        protectedSurfaceMask.close()
        sourceExclusionMask.close()
        glowBlurRenderer.close()
        outlineStyleData.buffer().close()
        chamsCapturer.close()
    }
}
