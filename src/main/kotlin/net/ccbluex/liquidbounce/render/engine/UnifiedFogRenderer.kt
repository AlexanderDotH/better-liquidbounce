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

import com.mojang.blaze3d.pipeline.RenderTarget
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.ClientShutdownEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.render.engine.distanthorizons.DistantHorizonsDepthStatus
import net.ccbluex.liquidbounce.render.engine.distanthorizons.DistantHorizonsDepthTextureProvider
import net.ccbluex.liquidbounce.render.engine.distanthorizons.DistantHorizonsSourceReadiness
import net.ccbluex.liquidbounce.render.engine.unifiedfog.FrameDimensions
import net.ccbluex.liquidbounce.render.engine.unifiedfog.TerrainDepthKind
import net.ccbluex.liquidbounce.render.engine.unifiedfog.TerrainDepthValidationPolicy
import net.ccbluex.liquidbounce.render.engine.unifiedfog.TerrainFrameToken
import net.ccbluex.liquidbounce.render.engine.unifiedfog.UnifiedFogFrameBuild
import net.ccbluex.liquidbounce.render.engine.unifiedfog.UnifiedFogFrameFactory
import net.ccbluex.liquidbounce.render.engine.unifiedfog.shouldReplaceNativeFog as shouldReplaceNativeFogForSources
import net.ccbluex.liquidbounce.utils.client.inGame
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.renderer.state.level.CameraRenderState
import org.joml.Matrix4fc

/** Terrain-safe fog compositor shared by Vanilla and Distant Horizons. */
object UnifiedFogRenderer : EventListener {

    private var resources: UnifiedFogGpuResources? = null
    private var lifecycleKey: FogLifecycleKey? = null
    private var lifecycleGeneration = 0L
    private var frameIndex = 0L
    private var currentFrameToken: TerrainFrameToken? = null
    private var lastDistantHorizonsBackend: String? = null
    private var pendingBackendInvalidation = false
    private var replaceNativeFogThisFrame = false
    private var preflightStatus: DistantHorizonsDepthStatus? = null

    /** Starts the token before either terrain renderer can publish or clear depth. */
    @JvmStatic
    fun beginFrame() {
        if (!inGame) {
            deactivate(publishDebug = false)
            return
        }
        val activity = CustomFogRenderBridge.activity()
        if (!activity.customAmbienceRunning || !activity.shouldRenderUnified) {
            beginLegacyDistantHorizonsFrame(activity)
            deactivate(publishDebug = true)
            return
        }

        val target = mc.gameRenderer.mainRenderTarget()
        val nextKey = FogLifecycleKey.capture(target)
        if (pendingBackendInvalidation || lifecycleKey != nextKey) invalidateLifecycle(nextKey)

        frameIndex++
        currentFrameToken = TerrainFrameToken(lifecycleGeneration, frameIndex)
        DistantHorizonsDepthTextureProvider.beginFrame(frameIndex)
        updateNativeFogReplacement(target)
    }

    @JvmStatic
    fun shouldReplaceNativeFog(): Boolean = replaceNativeFogThisFrame

    /** Runs after both terrain systems and before sharp hand, ESP, waypoint, overlay, and HUD rendering. */
    @JvmStatic
    fun render(cameraState: CameraRenderState, projectionMatrix: Matrix4fc) {
        if (!inGame || !CustomFogRenderBridge.activity().shouldRenderUnified) return
        val token = currentFrameToken ?: return FogFrameDiagnostics.recordSkipped(
            "missing unified frame token"
        )
        val target = mc.gameRenderer.mainRenderTarget()
        DistantHorizonsDepthTextureProvider.captureCurrentFrame(token.frameIndex)
        updateNativeFogReplacement(target)
        if (!replaceNativeFogThisFrame) return FogFrameDiagnostics.recordSkipped(
            reason = "native fog fallback while Distant Horizons depth warms up",
            status = preflightStatus,
            vanillaReady = true,
        )
        if (target.width <= 0 || target.height <= 0) {
            return FogFrameDiagnostics.recordSkipped("invalid render-target size")
        }

        val input = buildFrameInput(target, token, projectionMatrix)
            ?: return FogFrameDiagnostics.recordSkipped("required terrain depth is unavailable")
        val frame = when (val build = UnifiedFogFrameFactory.build(input.request)) {
            is UnifiedFogFrameBuild.Ready -> build.frame
            is UnifiedFogFrameBuild.Skipped -> return FogFrameDiagnostics.recordSkipped(
                build.rejection.toString(),
                input.status,
                input.horizon.startBlocks,
                input.horizon.endBlocks,
                vanillaReady = build.rejection.sourceKind != TerrainDepthKind.VANILLA,
            )
        }

        val blurPasses = renderSceneBlur(target, cameraState, projectionMatrix)
        val uniform = FogUniformFactory.snapshot(frame, cameraState)
        val passCount = blurPasses + UnifiedFogPassRenderer.render(gpuResources(), target, frame, uniform)
        FogFrameDiagnostics.recordRendered(input, frame, passCount)
    }

    private fun updateNativeFogReplacement(target: RenderTarget) {
        val targetDimensions = FrameDimensions(target.width, target.height)
        val status = DistantHorizonsDepthTextureProvider.status(target.width, target.height, frameIndex)
        val recentDepth = DistantHorizonsDepthTextureProvider.resolveRecent(frameIndex, FOG_MAX_DH_FRAME_AGE)
        val compatibleDepthAvailable = recentDepth?.let { depth ->
            FrameDimensions(depth.width, depth.height).hasCompatibleAspect(
                targetDimensions,
                TerrainDepthValidationPolicy.RENDER_ASPECT_TOLERANCE,
            )
        } == true

        preflightStatus = status
        replaceNativeFogThisFrame = shouldReplaceNativeFogForSources(
            unifiedEnabled = true,
            distantHorizonsInstalled = status.readiness != DistantHorizonsSourceReadiness.ABSENT,
            compatibleDistantHorizonsDepthAvailable = compatibleDepthAvailable,
        )
    }

    private fun beginLegacyDistantHorizonsFrame(activity: CustomFogActivity) {
        if (!activity.shouldRenderBlur && !activity.shouldRenderVolume) return
        frameIndex++
        DistantHorizonsDepthTextureProvider.beginFrame(frameIndex)
    }

    private fun buildFrameInput(
        target: RenderTarget,
        token: TerrainFrameToken,
        projectionMatrix: Matrix4fc,
    ): FogFrameInput? {
        val status = DistantHorizonsDepthTextureProvider.status(target.width, target.height, token.frameIndex)
        if (backendChanged(status.backend)) return null
        val depth = DistantHorizonsDepthTextureProvider.resolveRecent(
            token.frameIndex,
            FOG_MAX_DH_FRAME_AGE,
        )
        return FogFrameInputFactory.build(
            target,
            token,
            projectionMatrix,
            status,
            depth,
            mc.options.getEffectiveRenderDistance(),
        )
    }

    private fun renderSceneBlur(
        target: RenderTarget,
        cameraState: CameraRenderState,
        projectionMatrix: Matrix4fc,
    ): Int {
        if (!CustomFogRenderBridge.activity().shouldRenderBlur) return 0
        CustomFogBlurRenderer.render(target, cameraState, projectionMatrix)
        return SCENE_BLUR_PASS_COUNT
    }

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

    private fun invalidateLifecycle(nextKey: FogLifecycleKey) {
        lifecycleGeneration++
        lifecycleKey = nextKey
        pendingBackendInvalidation = false
        replaceNativeFogThisFrame = false
        preflightStatus = null
        resources?.close()
        resources = null
    }

    private fun deactivate(publishDebug: Boolean) {
        currentFrameToken = null
        lifecycleKey = null
        lastDistantHorizonsBackend = null
        pendingBackendInvalidation = false
        replaceNativeFogThisFrame = false
        preflightStatus = null
        resources?.close()
        resources = null
        UnifiedFogDebug.reset(publishDebug)
    }

    @Suppress("unused")
    private val shutdownHandler = handler<ClientShutdownEvent> {
        deactivate(publishDebug = false)
    }

    private const val SCENE_BLUR_PASS_COUNT = 2
}
