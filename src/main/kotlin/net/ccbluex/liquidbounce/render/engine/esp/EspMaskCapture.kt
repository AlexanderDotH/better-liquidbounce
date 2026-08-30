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
import net.ccbluex.liquidbounce.event.WorldRenderContext
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.interfaces.PreparedFrameAddition
import net.ccbluex.liquidbounce.render.ClientUniformDefine
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.createRenderPass
import net.ccbluex.liquidbounce.render.buffer.withOutputTextureOverride
import net.ccbluex.liquidbounce.render.buffer.writeStd140

internal typealias PrepareGlowMask = (EspGlowSource, Int, Int) -> RenderTarget
internal typealias ContributeGlow = (EspGlowSource, EspGlowStyle) -> Unit

/** Captures prepared models and cached block meshes without merging their module ownership. */
internal class EspPreparedGlowCapturer(
    private val prepareMask: PrepareGlowMask,
    private val contribute: ContributeGlow,
) {

    fun capture(bridge: PreparedFrameAddition, mainTarget: RenderTarget) {
        val targetGlowStyles = TargetGlowSourceRegistry.consumeContributedStyles()
        captureRegisteredPrepared(bridge, mainTarget, EspGlowSource.PLAYER_ESP)
        if (targetGlowStyles.isNotEmpty()) {
            capturePrepared(
                bridge,
                mainTarget,
                EspGlowSource.TARGET_GLOW,
                EspShaderStyleResolver.resolveGlow(*targetGlowStyles.toTypedArray()),
            )
        }
        captureRegisteredPrepared(bridge, mainTarget, EspGlowSource.ITEM_ESP)
        captureRegisteredPrepared(bridge, mainTarget, EspGlowSource.ORB_ESP)
        captureStorage(bridge, mainTarget)
        captureBlock(mainTarget)
    }

    private fun captureRegisteredPrepared(
        bridge: PreparedFrameAddition,
        mainTarget: RenderTarget,
        source: EspGlowSource,
    ) {
        val style = EspFeatureRendererRegistry.glow(source)?.style?.invoke() ?: return
        capturePrepared(bridge, mainTarget, source, style)
    }

    private fun capturePrepared(
        bridge: PreparedFrameAddition,
        mainTarget: RenderTarget,
        source: EspGlowSource,
        style: EspGlowStyle,
    ) {
        val layer = requireNotNull(source.preparedLayer)
        if (!bridge.`liquid_bounce$hasEspMask`(layer)) return

        val target = prepareMask(source, mainTarget.width, mainTarget.height)
        withOutputTextureOverride(target.colorTextureView, target.depthTextureView) {
            bridge.`liquid_bounce$executeEspMask`(layer)
        }
        contribute(source, style)
    }

    private fun captureStorage(bridge: PreparedFrameAddition, mainTarget: RenderTarget) {
        val provider = EspFeatureRendererRegistry.glow(EspGlowSource.STORAGE_ESP) ?: return
        val style = provider.style() ?: return

        val layer = EspMaskLayer.STORAGE_GLOW
        val hasNodes = bridge.`liquid_bounce$hasEspMask`(layer)
        val target = prepareMask(EspGlowSource.STORAGE_ESP, mainTarget.width, mainTarget.height)
        if (hasNodes) {
            withOutputTextureOverride(target.colorTextureView, target.depthTextureView) {
                bridge.`liquid_bounce$executeEspMask`(layer)
            }
        }
        val hasCachedMask = provider.drawMask(target)
        if (hasNodes || hasCachedMask) {
            contribute(EspGlowSource.STORAGE_ESP, style)
        }
    }

    private fun captureBlock(mainTarget: RenderTarget) {
        val source = EspGlowSource.BLOCK_ESP
        val provider = EspFeatureRendererRegistry.glow(source) ?: return
        val style = provider.style() ?: return
        val target = prepareMask(source, mainTarget.width, mainTarget.height)
        if (provider.drawMask(target)) {
            contribute(source, style)
        }
    }
}

/** Builds the alpha-only union used to keep Glow off selected model surfaces. */
internal class EspProtectedMaskRenderer(
    private val surfaceHolder: EspRenderTargetHolder,
    private val exclusionHolder: EspRenderTargetHolder,
    private val resources: (EspGlowSource) -> EspGlowSourceResources,
) {

    private var hasDedicatedSurfaceMask = false

    fun beginFrame() {
        hasDedicatedSurfaceMask = false
    }

    fun capture(
        bridge: PreparedFrameAddition,
        mainTarget: RenderTarget,
    ) {
        // WorldRenderEvent sources (including Tracers) contribute after this prepared-model pass.
        // Capture visible players now so those deferred glows cannot paint over their surfaces.
        if (!bridge.`liquid_bounce$hasEspMask`(EspMaskLayer.PROTECTED_SURFACE)) return

        val target = surfaceHolder.initAndClear(mainTarget.width, mainTarget.height)
        withOutputTextureOverride(target.colorTextureView, target.depthTextureView) {
            bridge.`liquid_bounce$executeEspMask`(EspMaskLayer.PROTECTED_SURFACE)
        }
        hasDedicatedSurfaceMask = true
    }

    fun prepare(
        source: EspGlowSource,
        availableSources: List<EspGlowSource>,
    ): RenderTarget? {
        val masks = EspGlowProtectionPlan.exclusionSources(source, availableSources).map { owner ->
            requireNotNull(resources(owner).mask.raw)
        }
        if (!hasDedicatedSurfaceMask) {
            if (masks.size <= 1) return masks.singleOrNull()

            val first = masks.first()
            val target = exclusionHolder.initAndClear(first.width, first.height)
            masks.forEach { EspCompositePassRenderer.unionMask(target, it) }
            return target
        }

        val protectedSurfaces = requireNotNull(surfaceHolder.raw)
        if (masks.isEmpty()) return protectedSurfaces

        val target = exclusionHolder.initAndClear(protectedSurfaces.width, protectedSurfaces.height)
        EspCompositePassRenderer.unionMask(target, protectedSurfaces)
        masks.forEach { EspCompositePassRenderer.unionMask(target, it) }
        return target
    }
}

/** Flushes one dynamic WorldRenderEvent contribution into its source-owned mask. */
internal object EspDynamicMaskRenderer {

    fun draw(
        event: WorldRenderContext,
        target: RenderTarget,
        draw: WorldRenderEnvironment.() -> Unit,
    ) {
        val environment = WorldRenderEnvironment.create(target, event.poseStack, event.camera)
        try {
            environment.draw()
        } finally {
            environment.flush(event.modelViewMatrix)
        }
    }
}

internal class EspGlowSourceResources(source: EspGlowSource) : AutoCloseable {

    val mask = EspRenderTargetHolder(
        "LiquidBounce ESP ${source.displayName} Mask",
        source.useDepth,
        GpuFormat.RGBA8_UNORM,
    )
    val horizontalBlurData = ClientUniformDefine.ESP_BLUR.createSingleBuffer()
    val verticalBlurData = ClientUniformDefine.ESP_BLUR.createSingleBuffer()
    val styleData = ClientUniformDefine.ESP_STYLE.createSingleBuffer()

    override fun close() {
        mask.close()
        horizontalBlurData.buffer().close()
        verticalBlurData.buffer().close()
        styleData.buffer().close()
    }
}
