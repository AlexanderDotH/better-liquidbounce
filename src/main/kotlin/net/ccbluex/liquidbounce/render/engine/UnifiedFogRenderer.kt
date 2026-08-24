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

@file:Suppress("TooManyFunctions")

package net.ccbluex.liquidbounce.render.engine

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTextureView
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.ClientShutdownEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.features.module.modules.render.customambience.ModuleCustomAmbience
import net.ccbluex.liquidbounce.features.module.modules.render.customambience.ModuleCustomAmbience.FogValueGroup
import net.ccbluex.liquidbounce.features.module.modules.world.nuker.ModuleNuker
import net.ccbluex.liquidbounce.render.ClientRenderPipelines
import net.ccbluex.liquidbounce.render.ClientUniformDefine
import net.ccbluex.liquidbounce.render.createRenderPass
import net.ccbluex.liquidbounce.render.engine.esp.IrisPipelineBypass
import net.ccbluex.liquidbounce.render.engine.unifiedfog.ClipDepthRange
import net.ccbluex.liquidbounce.render.engine.unifiedfog.FrameDimensions
import net.ccbluex.liquidbounce.render.engine.unifiedfog.InverseReconstructionMatrix
import net.ccbluex.liquidbounce.render.engine.unifiedfog.OptionalTerrainDepthSource
import net.ccbluex.liquidbounce.render.engine.unifiedfog.PhysicalFogHorizonRange
import net.ccbluex.liquidbounce.render.engine.unifiedfog.TerrainClipRange
import net.ccbluex.liquidbounce.render.engine.unifiedfog.TerrainDepthConvention
import net.ccbluex.liquidbounce.render.engine.unifiedfog.TerrainDepthKind
import net.ccbluex.liquidbounce.render.engine.unifiedfog.TerrainDepthSource
import net.ccbluex.liquidbounce.render.engine.unifiedfog.TerrainDepthUnavailableReason
import net.ccbluex.liquidbounce.render.engine.unifiedfog.TerrainFrameToken
import net.ccbluex.liquidbounce.render.engine.unifiedfog.UnifiedFogFrame
import net.ccbluex.liquidbounce.render.engine.unifiedfog.UnifiedFogFrameBuild
import net.ccbluex.liquidbounce.render.engine.unifiedfog.UnifiedFogFrameFactory
import net.ccbluex.liquidbounce.render.engine.unifiedfog.UnifiedFogFrameRequest
import net.ccbluex.liquidbounce.utils.client.clientStartDurationMs
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import net.ccbluex.liquidbounce.utils.client.inGame
import net.minecraft.client.renderer.state.level.CameraRenderState
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector4f

/** Terrain-safe fog compositor shared by Vanilla and Distant Horizons. */
object UnifiedFogRenderer : MinecraftShortcuts, EventListener {

    private val nearestSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
    private val linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)

    private var resources: UnifiedFogGpuResources? = null
    private var lifecycleKey: UnifiedFogLifecycleKey? = null
    private var lifecycleGeneration = 0L
    private var frameIndex = 0L
    private var currentFrameToken: TerrainFrameToken? = null
    private var lastDistantHorizonsBackend: String? = null
    private var pendingBackendInvalidation = false

    private val terrainMaskTarget get() = gpuResources().terrainMaskTarget
    private val fogTarget get() = gpuResources().fogTarget
    private val fogBlurTarget get() = gpuResources().fogBlurTarget

    /** Starts the token before either terrain renderer can publish or clear depth. */
    @JvmStatic
    fun beginFrame() {
        if (!inGame) {
            deactivate(publishDebug = false)
            return
        }
        val customAmbienceRunning = ModuleCustomAmbience.running
        if (!customAmbienceRunning || !FogValueGroup.shouldRenderUnified) {
            beginLegacyDistantHorizonsFrame()
            deactivate(publishDebug = true)
            return
        }

        val target = mc.gameRenderer.mainRenderTarget()
        val nextKey = UnifiedFogLifecycleKey.capture(target)
        if (pendingBackendInvalidation || lifecycleKey != nextKey) {
            invalidateLifecycle(nextKey)
        }

        frameIndex++
        currentFrameToken = TerrainFrameToken(lifecycleGeneration, frameIndex)
        DistantHorizonsDepthTextureProvider.beginFrame(frameIndex)
    }

    private fun beginLegacyDistantHorizonsFrame() {
        if (!FogValueGroup.shouldRenderBlur && !FogValueGroup.shouldRenderVolume) return
        frameIndex++
        DistantHorizonsDepthTextureProvider.beginFrame(frameIndex)
    }

    /** Runs after both terrain systems and before sharp hand, ESP, waypoint, overlay, and HUD rendering. */
    @JvmStatic
    fun render(cameraState: CameraRenderState, projectionMatrix: Matrix4fc) {
        if (!inGame || !FogValueGroup.shouldRenderUnified) return
        val token = currentFrameToken ?: return recordSkippedFrame("missing unified frame token")
        val target = mc.gameRenderer.mainRenderTarget()
        if (target.width <= 0 || target.height <= 0) return recordSkippedFrame("invalid render-target size")

        val frameInput = buildFrameInput(target, token, projectionMatrix)
            ?: return recordSkippedFrame("required terrain depth is unavailable")
        val frameBuild = UnifiedFogFrameFactory.build(frameInput.request)
        val frame = when (frameBuild) {
            is UnifiedFogFrameBuild.Ready -> frameBuild.frame
            is UnifiedFogFrameBuild.Skipped -> return recordSkippedFrame(
                frameBuild.rejection.toString(),
                frameInput.status,
                frameInput.horizon.startBlocks,
                frameInput.horizon.endBlocks,
                vanillaReady = frameBuild.rejection.sourceKind != TerrainDepthKind.VANILLA,
            )
        }

        val uniform = snapshotUniform(frame, cameraState)
        val passCount = renderFrame(target, frame, uniform)
        recordRenderedFrame(frameInput, passCount)
    }

    private fun buildFrameInput(
        target: RenderTarget,
        token: TerrainFrameToken,
        projectionMatrix: Matrix4fc,
    ): UnifiedFogFrameInput? {
        val dimensions = FrameDimensions(target.width, target.height)
        val vanillaDepth = target.depthTextureView ?: return null
        val status = DistantHorizonsDepthTextureProvider.status(target.width, target.height, token.frameIndex)
        if (backendChanged(status.backend)) return null

        val dhDepth = DistantHorizonsDepthTextureProvider.resolve(target.width, target.height, token.frameIndex)
        val horizon = FogValueGroup.currentUnifiedHorizon(
            distantHorizonsFarClipBlocks = dhDepth?.farClipPlane,
            vanillaRenderDistanceChunks = mc.options.getEffectiveRenderDistance(),
        )
        val physicalHorizon = PhysicalFogHorizonRange(horizon.startBlocks, horizon.endBlocks)
        val vanillaSource = vanillaSource(
            vanillaDepth,
            dimensions,
            token,
            projectionMatrix,
            horizon.visibleDistanceBlocks,
        )
        val distantHorizonsSource = distantHorizonsSource(status, dhDepth, dimensions, token)

        return UnifiedFogFrameInput(
            request = UnifiedFogFrameRequest(
                expectedFrameToken = token,
                targetDimensions = dimensions,
                vanillaSource = vanillaSource,
                distantHorizonsSource = distantHorizonsSource,
                horizonRange = physicalHorizon,
            ),
            status = status,
            horizon = physicalHorizon,
        )
    }

    private fun vanillaSource(
        depth: GpuTextureView,
        dimensions: FrameDimensions,
        token: TerrainFrameToken,
        projectionMatrix: Matrix4fc,
        visibleDistanceBlocks: Float,
    ) = TerrainDepthSource(
        kind = TerrainDepthKind.VANILLA,
        textureView = depth,
        depthConvention = TerrainDepthConvention(MC_CLEAR_DEPTH, gpuClipDepthRange()),
        inverseReconstruction = InverseReconstructionMatrix(Matrix4f(projectionMatrix).invert()),
        clipRange = TerrainClipRange(0f, visibleDistanceBlocks.coerceAtLeast(MIN_CLIP_DISTANCE)),
        dimensions = dimensions,
        frameToken = token,
    )

    private fun distantHorizonsSource(
        status: DistantHorizonsDepthStatus,
        depth: DistantHorizonsDepthTexture?,
        dimensions: FrameDimensions,
        token: TerrainFrameToken,
    ): OptionalTerrainDepthSource<GpuTextureView> {
        if (status.readiness == DistantHorizonsSourceReadiness.ABSENT) {
            return OptionalTerrainDepthSource.Absent
        }
        if (depth == null) {
            return OptionalTerrainDepthSource.Unavailable(status.toUnavailableReason())
        }

        val clipRange = runCatching { TerrainClipRange(depth.nearClipPlane, depth.farClipPlane) }
            .getOrElse { return OptionalTerrainDepthSource.Unavailable(TerrainDepthUnavailableReason.DEPTH_NOT_READY) }
        return OptionalTerrainDepthSource.Ready(
            TerrainDepthSource(
                kind = TerrainDepthKind.DISTANT_HORIZONS,
                textureView = depth.textureView,
                depthConvention = TerrainDepthConvention(
                    depth.clearDepth,
                    if (depth.zeroToOneDepth) ClipDepthRange.ZERO_TO_ONE else ClipDepthRange.NEGATIVE_ONE_TO_ONE,
                ),
                inverseReconstruction = InverseReconstructionMatrix(depth.inverseMvmProjection),
                clipRange = clipRange,
                dimensions = dimensions,
                frameToken = TerrainFrameToken(token.lifecycleGeneration, depth.frameToken),
            )
        )
    }

    private fun snapshotUniform(
        frame: UnifiedFogFrame<GpuTextureView>,
        cameraState: CameraRenderState,
    ): UnifiedFogUniform {
        val dhSource = frame.distantHorizonsSource
        val volume = FogValueGroup.VolumetricFog
        val multiLayer = FogValueGroup.VolumetricFog.MultiLayerFog
        val layers = VolumetricFogLayerSettings.from(
            enabled = multiLayer.running,
            spacing = multiLayer.layerSpacing,
            groundDensity = multiLayer.groundDensity,
            middleDensity = multiLayer.middleDensity,
            upperDensity = multiLayer.upperDensity,
        )
        val fogColor = if (FogValueGroup.FogColorOverride.running) {
            FogValueGroup.FogColorOverride.color.toVector4f()
        } else {
            Vector4f(cameraState.fogData.color)
        }

        return UnifiedFogUniform(
            inverseProjection = frame.vanillaSource.inverseReconstruction.copy(),
            inverseViewRotation = Matrix4f(cameraState.viewRotationMatrix).invert(),
            dhInverseMvmProjection = dhSource?.inverseReconstruction?.copy() ?: Matrix4f(),
            fogColor = fogColor,
            horizonInfo = Vector4f(
                frame.horizonRange.startBlocks,
                frame.horizonRange.endBlocks,
                FogValueGroup.fogDensity / 100f,
                FogValueGroup.silhouetteFeather,
            ),
            cameraPositionAndTime = Vector4f(
                wrapVolumetricFogCoordinate(cameraState.pos.x),
                wrapVolumetricFogCoordinate(cameraState.pos.y),
                wrapVolumetricFogCoordinate(cameraState.pos.z),
                clientStartDurationMs / 1000f * VOLUME_SPEED,
            ),
            vanillaDepthInfo = frame.vanillaSource.depthConvention.toUniform(available = true),
            dhDepthInfo = dhSource?.depthConvention?.toUniform(available = true) ?: unavailableDepthInfo(),
            viewportInfo = Vector4f(
                frame.dimensions.width.toFloat(),
                frame.dimensions.height.toFloat(),
                1f / frame.dimensions.width,
                1f / frame.dimensions.height,
            ),
            volumeSettings = Vector4f(
                if (volume.running) 1f else 0f,
                volumetricFogStrength(volume.strength),
                volumetricCameraClearRadius(volume.cameraClearRadius),
                volumetricInteractionStrength(ModuleNuker.running),
            ),
            layerSettings = Vector4f(
                layers.spacing,
                layers.groundDensity,
                layers.middleDensity,
                layers.upperDensity,
            ),
        )
    }

    private fun renderFrame(
        target: RenderTarget,
        frame: UnifiedFogFrame<GpuTextureView>,
        uniform: UnifiedFogUniform,
    ): Int {
        val terrainMask = terrainMaskTarget.initAndGet(target.width, target.height)
        val generatedFog = fogTarget.initAndGet(target.width, target.height)
        val uniformSlice = gpuResources().fogData.get(uniform)
        val dhDepth = frame.distantHorizonsSource?.textureView ?: frame.vanillaSource.textureView

        return IrisPipelineBypass.run {
            drawTerrainMask(terrainMask, frame.vanillaSource.textureView, dhDepth, uniformSlice)
            drawFogField(generatedFog, terrainMask, uniformSlice)
            val fogForComposite = blurFogIfEnabled(generatedFog, terrainMask, uniformSlice)
            composite(target, fogForComposite, terrainMask, uniformSlice)
            if (FogValueGroup.BlurFog.running) BLURRED_PASS_COUNT else BASE_PASS_COUNT
        }
    }

    private fun drawTerrainMask(
        destination: RenderTarget,
        vanillaDepth: GpuTextureView,
        distantHorizonsDepth: GpuTextureView,
        uniform: com.mojang.blaze3d.buffers.GpuBufferSlice,
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
        uniform: com.mojang.blaze3d.buffers.GpuBufferSlice,
    ) {
        destination.createRenderPass({ "LiquidBounce unified fog field" }, useDepthAttachment = false).use { pass ->
            pass.setPipeline(ClientRenderPipelines.UnifiedFogGenerate)
            pass.bindTexture("TerrainMaskSampler", terrainMask.colorTextureView, nearestSampler)
            pass.setUniform(ClientUniformDefine.UNIFIED_FOG.uboName, uniform)
            pass.draw(3, 1, 0, 0)
        }
    }

    private fun blurFogIfEnabled(
        generatedFog: RenderTarget,
        terrainMask: RenderTarget,
        uniform: com.mojang.blaze3d.buffers.GpuBufferSlice,
    ): RenderTarget {
        if (!FogValueGroup.BlurFog.running) return generatedFog
        val intermediate = fogBlurTarget.initAndGet(generatedFog.width, generatedFog.height)
        val kernel = scaledKernel(FogValueGroup.BlurFog.strength)
        val kernelSlice = gpuResources().fogKernelData.get(UnifiedFogKernelUniform(kernel))

        drawFogBlur(
            ClientRenderPipelines.UnifiedFogBlurHorizontal,
            intermediate,
            generatedFog,
            terrainMask,
            uniform,
            kernelSlice,
        )
        drawFogBlur(
            ClientRenderPipelines.UnifiedFogBlurVertical,
            generatedFog,
            intermediate,
            terrainMask,
            uniform,
            kernelSlice,
        )
        return generatedFog
    }

    private fun drawFogBlur(
        pipeline: com.mojang.blaze3d.pipeline.RenderPipeline,
        destination: RenderTarget,
        source: RenderTarget,
        terrainMask: RenderTarget,
        uniform: com.mojang.blaze3d.buffers.GpuBufferSlice,
        kernel: com.mojang.blaze3d.buffers.GpuBufferSlice,
    ) {
        destination.createRenderPass({ "LiquidBounce unified fog-only blur" }, useDepthAttachment = false).use { pass ->
            pass.setPipeline(pipeline)
            pass.bindTexture("FogSampler", source.colorTextureView, linearSampler)
            pass.bindTexture("TerrainMaskSampler", terrainMask.colorTextureView, nearestSampler)
            pass.setUniform(ClientUniformDefine.UNIFIED_FOG.uboName, uniform)
            pass.setUniform(ClientUniformDefine.UNIFIED_FOG_KERNEL.uboName, kernel)
            pass.draw(3, 1, 0, 0)
        }
    }

    private fun composite(
        target: RenderTarget,
        fog: RenderTarget,
        terrainMask: RenderTarget,
        uniform: com.mojang.blaze3d.buffers.GpuBufferSlice,
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

    private fun scaledKernel(radius: Float): List<GaussianPair> {
        val effectiveRadius = radius.coerceIn(MIN_BLUR_RADIUS, MAX_BLUR_RADIUS)
        return GaussianKernel.forScreenRadius(effectiveRadius).pairs
    }

    private fun recordRenderedFrame(input: UnifiedFogFrameInput, passCount: Int) {
        UnifiedFogDebug.record(
            debugState(
                status = input.status,
                vanillaReady = true,
                distantHorizonsReady = input.status.readiness == DistantHorizonsSourceReadiness.READY,
                horizonStart = input.horizon.startBlocks,
                horizonEnd = input.horizon.endBlocks,
                passCount = passCount,
                skipReason = null,
            )
        )
    }

    private fun recordSkippedFrame(
        reason: String,
        status: DistantHorizonsDepthStatus? = null,
        horizonStart: Float = 0f,
        horizonEnd: Float = 0f,
        vanillaReady: Boolean = false,
    ) {
        UnifiedFogDebug.record(
            debugState(status, vanillaReady, false, horizonStart, horizonEnd, 0, reason)
        )
    }

    private fun debugState(
        status: DistantHorizonsDepthStatus?,
        vanillaReady: Boolean,
        distantHorizonsReady: Boolean,
        horizonStart: Float,
        horizonEnd: Float,
        passCount: Int,
        skipReason: String?,
    ) = UnifiedFogDebugState(
        engine = "Unified",
        vanillaReady = vanillaReady,
        distantHorizonsReady = distantHorizonsReady,
        distantHorizonsBackend = status?.backend,
        distantHorizonsApiVersion = status?.apiVersion,
        frameAge = status?.frameAge ?: 0L,
        horizonStartBlocks = horizonStart,
        horizonEndBlocks = horizonEnd,
        passCount = passCount,
        skipReason = skipReason,
    )

    private fun backendChanged(backend: String?): Boolean {
        val previous = lastDistantHorizonsBackend
        if (backend == null || previous == null) {
            if (backend != null) lastDistantHorizonsBackend = backend
            return false
        }
        if (backend == previous) return false
        lastDistantHorizonsBackend = backend
        pendingBackendInvalidation = true
        return true
    }

    private fun gpuResources(): UnifiedFogGpuResources =
        resources ?: UnifiedFogGpuResources().also { resources = it }

    private fun invalidateLifecycle(nextKey: UnifiedFogLifecycleKey) {
        lifecycleGeneration++
        lifecycleKey = nextKey
        pendingBackendInvalidation = false
        resources?.close()
        resources = null
    }

    private fun deactivate(publishDebug: Boolean) {
        currentFrameToken = null
        lifecycleKey = null
        lastDistantHorizonsBackend = null
        pendingBackendInvalidation = false
        resources?.close()
        resources = null
        UnifiedFogDebug.reset(publishDebug)
    }

    @Suppress("unused")
    private val shutdownHandler = handler<ClientShutdownEvent> {
        deactivate(publishDebug = false)
    }

    private fun gpuClipDepthRange() =
        if (gpuDevice.deviceInfo.isZZeroToOne) ClipDepthRange.ZERO_TO_ONE else ClipDepthRange.NEGATIVE_ONE_TO_ONE

    private fun TerrainDepthConvention.toUniform(available: Boolean) = Vector4f(
        clearDepth,
        if (clipDepthRange == ClipDepthRange.ZERO_TO_ONE) 1f else 0f,
        if (available) 1f else 0f,
        CLEAR_DEPTH_EPSILON,
    )

    private fun unavailableDepthInfo() = Vector4f(MC_CLEAR_DEPTH, 0f, 0f, CLEAR_DEPTH_EPSILON)

    private fun DistantHorizonsDepthStatus.toUnavailableReason() = when (readiness) {
        DistantHorizonsSourceReadiness.UNSUPPORTED -> TerrainDepthUnavailableReason.UNSUPPORTED_CAPABILITY
        else -> TerrainDepthUnavailableReason.DEPTH_NOT_READY
    }

    private const val MC_CLEAR_DEPTH = 0f
    private const val CLEAR_DEPTH_EPSILON = 1e-6f
    private const val MIN_CLIP_DISTANCE = 1f
    private const val MIN_BLUR_RADIUS = 4f
    private const val MAX_BLUR_RADIUS = 24f
    private const val VOLUME_SPEED = 0.15f
    private const val BASE_PASS_COUNT = 3
    private const val BLURRED_PASS_COUNT = 5
}

private data class UnifiedFogFrameInput(
    val request: UnifiedFogFrameRequest<GpuTextureView>,
    val status: DistantHorizonsDepthStatus,
    val horizon: PhysicalFogHorizonRange,
)

private data class UnifiedFogLifecycleKey(
    val worldIdentity: Int,
    val renderTargetWidth: Int,
    val renderTargetHeight: Int,
    val gpuDeviceIdentity: Int,
) {
    companion object : MinecraftShortcuts {
        fun capture(target: RenderTarget) = UnifiedFogLifecycleKey(
            worldIdentity = System.identityHashCode(mc.level),
            renderTargetWidth = target.width,
            renderTargetHeight = target.height,
            gpuDeviceIdentity = System.identityHashCode(gpuDevice),
        )
    }
}
