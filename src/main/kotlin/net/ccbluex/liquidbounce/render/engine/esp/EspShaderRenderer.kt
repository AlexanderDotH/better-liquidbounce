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
import net.ccbluex.liquidbounce.common.EspMaskLayer
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.ClientShutdownEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleBlockESP
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleItemESP
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleOrbESP
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
import org.joml.Matrix4f
import java.util.EnumMap

/**
 * Custom 2020-style ESP compositor.
 *
 * Selected geometry is first rendered into LiquidBounce-owned full-resolution masks. Glow is then
 * downsampled and blurred in two separable passes before both effects are composited over the finished
 * world. It never enables Minecraft's glowing flag or its entity-outline post chain.
 */
object EspShaderRenderer : MinecraftShortcuts, EventListener {

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
    private val blurPing = EspRenderTargetHolder("LiquidBounce ESP Blur Ping", false, GpuFormat.RGBA16_FLOAT)
    private val blurPong = EspRenderTargetHolder("LiquidBounce ESP Blur Pong", false, GpuFormat.RGBA16_FLOAT)

    private val linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
    private val outlineStyleData = ClientUniformDefine.ESP_STYLE.createSingleBuffer()

    private val glowFrameSources = EspGlowFrameSources()
    private val preparedGlowCapturer = EspPreparedGlowCapturer(::prepareGlowMask, glowFrameSources::contribute)
    private val protectedMaskRenderer = EspProtectedMaskRenderer(
        protectedSurfaceMask,
        sourceExclusionMask,
        ::resources,
    )
    private var hasOutline = false
    private var outlineStyle = EspOutlineStyle.DEFAULT

    @JvmStatic
    fun beginFrame() {
        TargetGlowSourceRegistry.beginFrame()
        glowFrameSources.reset()
        protectedMaskRenderer.beginFrame()
        hasOutline = false
        outlineStyle = EspOutlineStyle.DEFAULT
    }

    @JvmStatic
    fun capture(preparedFrame: FeatureRenderDispatcher.PreparedFrame) {
        IrisPipelineBypass.run {
            val bridge = preparedFrame as PreparedFrameAddition
            val mainTarget = mc.gameRenderer.mainRenderTarget()
            preparedGlowCapturer.capture(bridge, mainTarget)
            protectedMaskRenderer.capture(bridge, mainTarget)
            captureOutline(bridge, mainTarget)
        }
    }

    private fun captureOutline(bridge: PreparedFrameAddition, mainTarget: RenderTarget) {
        val hasPlayerNodes = bridge.`liquid_bounce$hasEspMask`(EspMaskLayer.PLAYER_OUTLINE)
        val hasStorageNodes = bridge.`liquid_bounce$hasEspMask`(EspMaskLayer.STORAGE_OUTLINE)
        if (!hasPlayerNodes && !hasStorageNodes && !ModuleStorageESP.OutlineMode.running) return

        outlineStyle = EspShaderStyleResolver.resolveOutline(
            EspOutlineMode.style.takeIf { EspOutlineMode.running },
            ModuleStorageESP.OutlineMode.style.takeIf { ModuleStorageESP.OutlineMode.running },
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
        val hasStorageMask = ModuleStorageESP.OutlineMode.running && ModuleStorageESP.OutlineMode.drawMask(target)
        hasOutline = hasPlayerNodes || hasStorageNodes || hasStorageMask
    }

    /**
     * Appends dynamic world geometry after the prepared feature frame has been captured.
     * BlockOverlay uses this seam because its interpolated highlight is created by WorldRenderEvent.
     */
    internal fun contributeGlow(
        event: WorldRenderEvent,
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
        val plan = EspPostProcessPlan.create(glowFrameSources.hasAnyContribution, hasOutline)
        if (plan.isEmpty()) return

        IrisPipelineBypass.run {
            try {
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
        val blurredMask = downsampleAndBlur(mask, state.style, resources)
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

    private fun downsampleAndBlur(
        mask: RenderTarget,
        style: EspGlowStyle,
        resources: EspGlowSourceResources,
    ): RenderTarget {
        val size = EspTargetSize.halfOf(mask.width, mask.height)
        val ping = blurPing.initAndClear(size.width, size.height)
        val pong = blurPong.initAndClear(size.width, size.height)

        ping.createRenderPass({ "LiquidBounce ESP glow downsample" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspDownsample)
            pass.bindTexture("MaskSampler", mask.colorTextureView, linearSampler)
            pass.draw(3, 1, 0, 0)
        }

        writeBlurData(resources.horizontalBlurData, size.width, size.height, horizontal = true, style)
        pong.createRenderPass({ "LiquidBounce ESP glow horizontal blur" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspGaussianBlur)
            pass.bindTexture("InputSampler", ping.colorTextureView, linearSampler)
            pass.setUniform(ClientUniformDefine.ESP_BLUR.uboName, resources.horizontalBlurData)
            pass.draw(3, 1, 0, 0)
        }

        writeBlurData(resources.verticalBlurData, size.width, size.height, horizontal = false, style)
        ping.createRenderPass({ "LiquidBounce ESP glow vertical blur" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspGaussianBlur)
            pass.bindTexture("InputSampler", pong.colorTextureView, linearSampler)
            pass.setUniform(ClientUniformDefine.ESP_BLUR.uboName, resources.verticalBlurData)
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
        glowResources.values.forEach(EspGlowSourceResources::close)
        glowResources.clear()
        outlineMask.close()
        protectedSurfaceMask.close()
        sourceExclusionMask.close()
        blurPing.close()
        blurPong.close()
        outlineStyleData.buffer().close()
    }
}

private typealias PrepareGlowMask = (EspGlowSource, Int, Int) -> RenderTarget
private typealias ContributeGlow = (EspGlowSource, EspGlowStyle) -> Unit

/** Captures prepared models and cached block meshes without merging their module ownership. */
private class EspPreparedGlowCapturer(
    private val prepareMask: PrepareGlowMask,
    private val contribute: ContributeGlow,
) {

    fun capture(bridge: PreparedFrameAddition, mainTarget: RenderTarget) {
        val targetGlowStyles = TargetGlowSourceRegistry.consumeContributedStyles()
        if (EspGlowMode.running) {
            capturePrepared(bridge, mainTarget, EspGlowSource.PLAYER_ESP, EspGlowMode.style)
        }
        if (targetGlowStyles.isNotEmpty()) {
            capturePrepared(
                bridge,
                mainTarget,
                EspGlowSource.TARGET_GLOW,
                EspShaderStyleResolver.resolveGlow(*targetGlowStyles.toTypedArray()),
            )
        }
        if (ModuleItemESP.ShaderEspMode.running) {
            capturePrepared(
                bridge,
                mainTarget,
                EspGlowSource.ITEM_ESP,
                ModuleItemESP.ShaderEspMode.style,
            )
        }
        if (ModuleOrbESP.GlowMode.running) {
            capturePrepared(
                bridge,
                mainTarget,
                EspGlowSource.ORB_ESP,
                ModuleOrbESP.GlowMode.style,
            )
        }
        captureStorage(bridge, mainTarget)
        captureBlock(mainTarget)
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
        if (!ModuleStorageESP.GlowMode.running) return

        val layer = EspMaskLayer.STORAGE_GLOW
        val hasNodes = bridge.`liquid_bounce$hasEspMask`(layer)
        val target = prepareMask(EspGlowSource.STORAGE_ESP, mainTarget.width, mainTarget.height)
        if (hasNodes) {
            withOutputTextureOverride(target.colorTextureView, target.depthTextureView) {
                bridge.`liquid_bounce$executeEspMask`(layer)
            }
        }
        val hasCachedMask = ModuleStorageESP.GlowMode.drawMask(target)
        if (hasNodes || hasCachedMask) {
            contribute(EspGlowSource.STORAGE_ESP, ModuleStorageESP.GlowMode.style)
        }
    }

    private fun captureBlock(mainTarget: RenderTarget) {
        val mode = ModuleBlockESP.activeShaderMode ?: return

        val source = EspGlowSource.BLOCK_ESP
        val target = prepareMask(source, mainTarget.width, mainTarget.height)
        if (mode.drawMask(target)) {
            contribute(source, mode.style)
        }
    }
}

/** Builds the alpha-only union used to keep Glow off selected model surfaces. */
private class EspProtectedMaskRenderer(
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
private object EspDynamicMaskRenderer {

    fun draw(
        event: WorldRenderEvent,
        target: RenderTarget,
        draw: WorldRenderEnvironment.() -> Unit,
    ) {
        val collector = BatchCollector()
        val environment = WorldRenderEnvironment(target, event.poseStack, event.camera, collector)
        try {
            environment.draw()
        } finally {
            collector.flush(target, getDynamicTransformsUniform(Matrix4f(event.modelViewMatrix)))
        }
    }
}

private class EspGlowSourceResources(source: EspGlowSource) : AutoCloseable {

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

private object EspCompositePassRenderer {

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
