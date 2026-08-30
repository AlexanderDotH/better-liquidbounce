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

@file:JvmName("DistantHorizonsDepthTextureProviderKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.render.engine.distanthorizons

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.ClientShutdownEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.render.engine.unifiedfog.FrameDimensions
import net.ccbluex.liquidbounce.render.engine.unifiedfog.TerrainDepthValidationPolicy

/** Optional DH integration. No always-loaded member has a hard DH API type. */
internal object DistantHorizonsDepthTextureProvider : EventListener, DistantHorizonsPublicEventSink {

    internal val lifecycle = DistantHorizonsFrameLifecycle()
    internal val openGlFrameBufferCache = OpenGlFrameBufferCacheResolver()

    internal var installAttempted = false
    internal var apiInstalled = false
    internal var capabilitySupported = true
    internal var integration: DistantHorizonsPublicIntegration? = null
    internal var apiVersion: String? = null
    internal var modVersion: String? = null
    internal var backend: String? = null
    internal var statusDetail: String? = null
    internal var borrowedTexture: BorrowedRawDepthTexture? = null
    internal var capturedDepth: CapturedDepthTexture? = null
    internal var warningReported = false
    internal var externallyTokenedFrames = false
    internal var legacyEventFrameToken = 0L
    internal var textureClearPrimed = false
    internal var currentRenderState: DistantHorizonsRenderState? = null
    internal var completedRenderState: DistantHorizonsRenderState? = null

    fun beginFrame(frameToken: Long) {
        externallyTokenedFrames = true
        lifecycle.beginFrame(frameToken)
        ensureIntegration()
    }

    /** Legacy accepts the latest compatible capture and intentionally does not enforce a frame token. */
    fun resolve(width: Int, height: Int): DistantHorizonsDepthTexture? {
        ensureIntegration()
        val requestedDimensions = FrameDimensions(width, height)
        return capturedDepth?.snapshot()?.takeIf { depth ->
            FrameDimensions(depth.width, depth.height).hasCompatibleAspect(
                requestedDimensions,
                TerrainDepthValidationPolicy.RENDER_ASPECT_TOLERANCE,
            )
        }
    }

    /** Unified fails closed unless DH depth belongs to this exact terrain frame and resolution. */
    fun resolve(width: Int, height: Int, frameToken: Long): DistantHorizonsDepthTexture? {
        ensureIntegration()
        val readiness = lifecycle.readiness(apiInstalled, capabilitySupported, width, height, frameToken)
        if (readiness != DistantHorizonsSourceReadiness.READY) return null
        return capturedDepth?.snapshot()
    }

    /**
     * Accepts the newest captured DH depth within a small frame-age budget. Normalized texture coordinates allow
     * render-scaled DH targets to participate without pretending they have Minecraft's exact pixel dimensions.
     */
    fun resolveRecent(frameToken: Long, maximumFrameAge: Long): DistantHorizonsDepthTexture? {
        ensureIntegration()
        if (!apiInstalled || !capabilitySupported) return null
        lifecycle.recentCapture(frameToken, maximumFrameAge) ?: return null
        return capturedDepth?.snapshot()
    }

    /**
     * Refreshes the public DH depth after terrain rendering. The public texture id is guaranteed to be active during
     * the DH render pass, whereas cleanup timing differs between the OpenGL and Blaze implementations.
     */
    fun captureCurrentFrame(frameToken: Long): Boolean {
        ensureIntegration()
        val renderState = currentRenderState ?: completedRenderState ?: return false
        return runCatching { captureAvailableDepth(renderState.copy(frameToken = frameToken)) }
            .onFailure { markUnsupported("Unable to preserve current Distant Horizons depth", it) }
            .getOrDefault(false)
    }

    fun status(width: Int, height: Int, currentFrameToken: Long): DistantHorizonsDepthStatus {
        ensureIntegration()
        val capture = lifecycle.capturedFrame()
        return DistantHorizonsDepthStatus(
            readiness = lifecycle.readiness(
                installed = apiInstalled,
                supported = capabilitySupported,
                width = width,
                height = height,
                expectedFrameToken = currentFrameToken,
            ),
            backend = backend,
            apiVersion = apiVersion,
            modVersion = modVersion,
            currentFrameToken = currentFrameToken,
            capturedFrameToken = capture?.frameToken,
            frameAge = lifecycle.frameAge(currentFrameToken),
            detail = statusDetail,
        )
    }

    override fun onRenderSetup(renderParam: DistantHorizonsPublicRenderParam) {
        val frameToken = eventFrameToken()
        runCatching { DistantHorizonsRenderApi.captureRenderParam(renderParam, frameToken) }
            .onSuccess { currentRenderState = it }
            .onFailure { markUnsupported("DH supplied an invalid render transform", it) }
    }

    override fun onBeforeRenderCleanup(renderParam: DistantHorizonsPublicRenderParam) {
        val renderState = runCatching { captureRenderState(renderParam) }
            .onFailure { markUnsupported("DH supplied an invalid completed render transform", it) }
            .getOrNull() ?: return
        runCatching { captureAvailableDepth(renderState) }
            .onFailure { markUnsupported("Unable to preserve Distant Horizons depth before cleanup", it) }
        completedRenderState = renderState
    }

    override fun onBeforeTextureClear(renderParam: DistantHorizonsPublicRenderParam) {
        if (!textureClearPrimed) {
            textureClearPrimed = true
            return
        }
        val frameToken = lifecycle.currentFrameToken() ?: eventFrameToken()
        val completedState = completedRenderState
            ?: runCatching { captureRenderState(renderParam) }.getOrNull()
            ?: return
        val renderState = completedState.copy(frameToken = frameToken)
        runCatching { captureAvailableDepth(renderState) }
            .onFailure { markUnsupported("Unable to preserve Distant Horizons depth before texture clear", it) }
    }

    override fun shouldSuppressNativeFog(): Boolean = DistantHorizonsFogPolicy.shouldSuppressNativeFog()

    override fun onResize() {
        invalidateDepth()
    }

    override fun onWorldChanged() {
        DistantHorizonsRenderApi.invalidate()
        invalidateDepth()
    }

    private fun captureRenderState(renderParam: DistantHorizonsPublicRenderParam): DistantHorizonsRenderState {
        val frameToken = lifecycle.currentFrameToken() ?: eventFrameToken()
        return DistantHorizonsRenderApi.captureRenderParam(renderParam, frameToken)
    }

    @Suppress("unused")
    private val shutdownHandler = handler<ClientShutdownEvent> {
        shutdown()
    }

}
