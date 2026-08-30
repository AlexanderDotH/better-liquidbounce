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

import com.mojang.blaze3d.textures.GpuTextureView
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import org.joml.Matrix4f

internal enum class DistantHorizonsBackend {
    OPEN_GL,
    BLAZE_3D,
}

internal enum class DistantHorizonsSourceReadiness {
    ABSENT,
    INITIALIZING,
    READY,
    STALE,
    WRONG_SIZE,
    UNSUPPORTED,
}

internal data class DistantHorizonsCaptureMetadata(
    val width: Int,
    val height: Int,
    val frameToken: Long,
)

/** Refreshes a cached GPU-side collaborator whenever Minecraft replaces its rendering device. */
internal class DistantHorizonsDeviceResourceCache<T> {

    private var deviceIdentity: Int? = null
    private var resource: T? = null

    @Synchronized
    fun resolve(identity: Int, factory: () -> T?): T? {
        if (deviceIdentity == identity) return resource
        deviceIdentity = identity
        return factory().also { resource = it }
    }

    @Synchronized
    fun invalidate() {
        deviceIdentity = null
        resource = null
    }
}

/** Pure frame-validity policy shared by GPU code and unit tests. */
internal class DistantHorizonsFrameLifecycle {

    private var currentFrameToken: Long? = null
    private var capturedFrame: DistantHorizonsCaptureMetadata? = null

    @Synchronized
    fun beginFrame(frameToken: Long) {
        currentFrameToken = frameToken
    }

    @Synchronized
    fun currentFrameToken(): Long? = currentFrameToken

    @Synchronized
    fun capture(width: Int, height: Int): DistantHorizonsCaptureMetadata? {
        val frameToken = currentFrameToken ?: return null
        return DistantHorizonsCaptureMetadata(width, height, frameToken).also { capturedFrame = it }
    }

    @Synchronized
    fun capturedFrame(): DistantHorizonsCaptureMetadata? = capturedFrame

    @Synchronized
    fun recentCapture(expectedFrameToken: Long, maximumFrameAge: Long): DistantHorizonsCaptureMetadata? {
        require(maximumFrameAge >= 0L) { "Maximum frame age must not be negative" }
        val capture = capturedFrame ?: return null
        val age = expectedFrameToken - capture.frameToken
        return capture.takeIf { age in 0L..maximumFrameAge }
    }

    @Synchronized
    fun readiness(
        installed: Boolean,
        supported: Boolean,
        width: Int,
        height: Int,
        expectedFrameToken: Long,
    ): DistantHorizonsSourceReadiness {
        if (!installed) return DistantHorizonsSourceReadiness.ABSENT
        if (!supported) return DistantHorizonsSourceReadiness.UNSUPPORTED
        val capture = capturedFrame ?: return DistantHorizonsSourceReadiness.INITIALIZING
        if (capture.frameToken != expectedFrameToken) return DistantHorizonsSourceReadiness.STALE
        if (capture.width != width || capture.height != height) return DistantHorizonsSourceReadiness.WRONG_SIZE
        return DistantHorizonsSourceReadiness.READY
    }

    @Synchronized
    fun frameAge(expectedFrameToken: Long): Long? {
        val capture = capturedFrame ?: return null
        return (expectedFrameToken - capture.frameToken).takeIf { it >= 0L }
    }

    @Synchronized
    fun invalidate() {
        capturedFrame = null
    }

    @Synchronized
    fun reset() {
        currentFrameToken = null
        capturedFrame = null
    }
}

internal data class DistantHorizonsDepthStatus(
    val readiness: DistantHorizonsSourceReadiness,
    val backend: String?,
    val apiVersion: String?,
    val modVersion: String?,
    val currentFrameToken: Long,
    val capturedFrameToken: Long?,
    val frameAge: Long?,
    val detail: String?,
)

internal data class DistantHorizonsDepthTexture(
    val textureView: GpuTextureView,
    val width: Int,
    val height: Int,
    val clearDepth: Float,
    val zeroToOneDepth: Boolean,
    val inverseMvmProjection: Matrix4f,
    val nearClipPlane: Float,
    val farClipPlane: Float,
    val frameToken: Long,
    val backend: String,
    val apiVersion: String?,
)

internal data class DistantHorizonsPublicRenderParam(
    val inverseMvmProjection: FloatArray,
    val nearClipPlane: Float,
    val farClipPlane: Float,
)

internal interface DistantHorizonsPublicEventSink {
    fun onRenderSetup(renderParam: DistantHorizonsPublicRenderParam)
    fun onBeforeTextureClear(renderParam: DistantHorizonsPublicRenderParam)
    fun onBeforeRenderCleanup(renderParam: DistantHorizonsPublicRenderParam)
    fun shouldSuppressNativeFog(): Boolean
    fun onResize()
    fun onWorldChanged()
}

/** Deliberately DH-type-free runtime port implemented by the string-loaded typed adapter. */
internal interface DistantHorizonsPublicIntegration : AutoCloseable {
    val apiVersion: String
    val modVersion: String
    fun depthTexture(): DistantHorizonsDepthFetch
}

internal data class DistantHorizonsDepthFetch(
    val snapshot: DistantHorizonsDepthSnapshot? = null,
    val backend: String? = null,
    val unsupportedReason: String? = null,
)

internal data class DistantHorizonsDepthSnapshot(
    val convention: DistantHorizonsDepthConvention,
    val backend: DistantHorizonsBackend,
    val blazeTextureView: GpuTextureView? = null,
    val openGlTextureId: Int? = null,
)
