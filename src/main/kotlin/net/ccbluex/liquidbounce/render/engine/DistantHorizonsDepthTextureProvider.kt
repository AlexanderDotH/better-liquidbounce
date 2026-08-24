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

package net.ccbluex.liquidbounce.render.engine

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.opengl.FrameBufferCache
import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.opengl.GlTextureView
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.ClientShutdownEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.render.customambience.ModuleCustomAmbience.FogValueGroup
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11C
import org.lwjgl.opengl.GL45C
import java.lang.reflect.InvocationTargetException

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

/** Optional DH integration. No always-loaded member has a hard DH API type. */
@Suppress("TooManyFunctions") // The coordinator mirrors the typed DH lifecycle plus Legacy/Unified query seams.
internal object DistantHorizonsDepthTextureProvider : EventListener, DistantHorizonsPublicEventSink {

    private val lifecycle = DistantHorizonsFrameLifecycle()
    private val openGlFrameBufferCache = OpenGlFrameBufferCacheResolver()

    private var installAttempted = false
    private var apiInstalled = false
    private var capabilitySupported = true
    private var integration: DistantHorizonsPublicIntegration? = null
    private var apiVersion: String? = null
    private var modVersion: String? = null
    private var backend: String? = null
    private var statusDetail: String? = null
    private var borrowedTexture: BorrowedRawDepthTexture? = null
    private var capturedDepth: CapturedDepthTexture? = null
    private var warningReported = false
    private var externallyTokenedFrames = false
    private var legacyEventFrameToken = 0L

    fun beginFrame(frameToken: Long) {
        externallyTokenedFrames = true
        lifecycle.beginFrame(frameToken)
        ensureIntegration()
    }

    /** Legacy accepts the latest compatible capture and intentionally does not enforce a frame token. */
    fun resolve(width: Int, height: Int): DistantHorizonsDepthTexture? {
        ensureIntegration()
        return capturedDepth
            ?.takeIf { it.matches(width, height) }
            ?.snapshot()
    }

    /** Unified fails closed unless DH depth belongs to this exact terrain frame and resolution. */
    fun resolve(width: Int, height: Int, frameToken: Long): DistantHorizonsDepthTexture? {
        ensureIntegration()
        val readiness = lifecycle.readiness(apiInstalled, capabilitySupported, width, height, frameToken)
        if (readiness != DistantHorizonsSourceReadiness.READY) return null
        return capturedDepth?.snapshot()
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
            .onFailure { markUnsupported("DH supplied an invalid render transform", it) }
    }

    override fun onBeforeRenderCleanup(renderParam: DistantHorizonsPublicRenderParam) {
        runCatching { captureBeforeCleanup(renderParam) }
            .onFailure { markUnsupported("Unable to preserve Distant Horizons depth before cleanup", it) }
    }

    override fun shouldSuppressNativeFog(): Boolean =
        FogValueGroup.isUnified() || FogValueGroup.shouldRenderVolume

    override fun onResize() {
        invalidateDepth()
    }

    override fun onWorldChanged() {
        DistantHorizonsRenderApi.invalidate()
        invalidateDepth()
    }

    private fun captureBeforeCleanup(renderParam: DistantHorizonsPublicRenderParam) {
        val frameToken = lifecycle.currentFrameToken() ?: eventFrameToken()
        val renderState = DistantHorizonsRenderApi.captureRenderParam(renderParam, frameToken)
        val fetch = integration?.depthTexture() ?: return
        backend = fetch.backend ?: backend
        fetch.unsupportedReason?.let { reason ->
            markUnsupported(reason)
            return
        }
        val snapshot = fetch.snapshot ?: return
        val source = liveTextureView(snapshot) ?: return
        val width = source.getWidth(0)
        val height = source.getHeight(0)
        val previousBackend = capturedDepth?.backend
        if (previousBackend != null && previousBackend != snapshot.backend.name) {
            invalidateDepth()
        }
        val destination = capturedDepth
            ?.takeIf { it.matches(width, height) }
            ?: CapturedDepthTexture(width, height).also { replacement ->
                capturedDepth?.close()
                capturedDepth = replacement
            }

        destination.capture(source, snapshot, renderState, apiVersion)
        lifecycle.capture(width, height)
        capabilitySupported = true
        statusDetail = null
    }

    private fun liveTextureView(snapshot: DistantHorizonsDepthSnapshot): GpuTextureView? =
        snapshot.blazeTextureView ?: resolveOpenGl(snapshot)

    private fun resolveOpenGl(snapshot: DistantHorizonsDepthSnapshot): GpuTextureView? {
        val glId = snapshot.openGlTextureId ?: return null
        val size = openGlTextureSize(glId) ?: return null
        val deviceIdentity = System.identityHashCode(gpuDevice)
        val frameBufferCache = openGlFrameBufferCache.resolve() ?: return null
        val borrowed = borrowedTexture
            ?.takeIf { it.matches(glId, size.width, size.height, deviceIdentity) }
            ?: BorrowedRawDepthTexture(
                glId,
                size.width,
                size.height,
                deviceIdentity,
                frameBufferCache,
            ).also { replacement ->
                borrowedTexture?.close()
                borrowedTexture = replacement
            }
        return borrowed.view
    }

    private fun openGlTextureSize(glId: Int): DepthTextureSize? {
        if (gpuDevice.javaClass.name != GL_DEVICE_CLASS || !GL11C.glIsTexture(glId)) return null
        val textureWidth = GL45C.glGetTextureLevelParameteri(glId, 0, GL11C.GL_TEXTURE_WIDTH)
        val textureHeight = GL45C.glGetTextureLevelParameteri(glId, 0, GL11C.GL_TEXTURE_HEIGHT)
        if (textureWidth <= 0 || textureHeight <= 0) return null
        return DepthTextureSize(textureWidth, textureHeight)
    }

    private fun ensureIntegration() {
        if (installAttempted) return
        installAttempted = true
        val loader = DistantHorizonsDepthTextureProvider::class.java.classLoader
        apiInstalled = runCatching { Class.forName(DH_API_CLASS, false, loader) }.isSuccess
        if (!apiInstalled) return

        runCatching {
            val bridge = Class.forName(PUBLIC_BRIDGE_CLASS, true, loader)
            val install = bridge.getMethod("install", DistantHorizonsPublicEventSink::class.java)
            install.invoke(null, this) as DistantHorizonsPublicIntegration
        }.onSuccess { installed ->
            integration = installed
            apiVersion = installed.apiVersion
            modVersion = installed.modVersion
        }.onFailure { failure ->
            markUnsupported("Unable to register Distant Horizons public render events", unwrap(failure))
        }
    }

    private fun eventFrameToken(): Long {
        if (externallyTokenedFrames) return requireNotNull(lifecycle.currentFrameToken())
        legacyEventFrameToken++
        lifecycle.beginFrame(legacyEventFrameToken)
        return legacyEventFrameToken
    }

    private fun markUnsupported(detail: String, failure: Throwable? = null) {
        capabilitySupported = false
        statusDetail = detail
        if (warningReported) return
        warningReported = true
        if (failure == null) {
            LiquidBounce.logger.warn(detail)
        } else {
            LiquidBounce.logger.warn(detail, failure)
        }
    }

    private fun invalidateDepth() {
        capturedDepth?.close()
        capturedDepth = null
        borrowedTexture?.close()
        borrowedTexture = null
        openGlFrameBufferCache.invalidate()
        lifecycle.invalidate()
    }

    private fun shutdown() {
        integration?.close()
        integration = null
        invalidateDepth()
        DistantHorizonsRenderApi.invalidate()
        lifecycle.reset()
        installAttempted = false
        apiInstalled = false
        capabilitySupported = true
        apiVersion = null
        modVersion = null
        backend = null
        statusDetail = null
        warningReported = false
        externallyTokenedFrames = false
        legacyEventFrameToken = 0L
    }

    @Suppress("unused")
    private val shutdownHandler = handler<ClientShutdownEvent> {
        shutdown()
    }

    private fun unwrap(failure: Throwable): Throwable =
        (failure as? InvocationTargetException)?.targetException ?: failure

    private const val DH_API_CLASS = "com.seibel.distanthorizons.api.DhApi"
    private const val PUBLIC_BRIDGE_CLASS =
        "net.ccbluex.liquidbounce.render.engine.DistantHorizonsPublicEventBridge"
    private const val GL_DEVICE_CLASS = "com.mojang.blaze3d.opengl.GlDevice"
}

private data class DepthTextureSize(val width: Int, val height: Int)

private class OpenGlFrameBufferCacheResolver {

    private val cache = DistantHorizonsDeviceResourceCache<FrameBufferCache>()

    fun resolve(): FrameBufferCache? {
        val device = gpuDevice
        return cache.resolve(System.identityHashCode(device)) {
            runCatching {
                val method = device.javaClass.getDeclaredMethod("frameBufferCache")
                check(method.trySetAccessible())
                method.invoke(device) as FrameBufferCache
            }.getOrNull()
        }
    }

    fun invalidate() {
        cache.invalidate()
    }
}

private class CapturedDepthTexture(
    private val width: Int,
    private val height: Int,
) : AutoCloseable {

    private val texture = gpuDevice.createTexture(
        { "LiquidBounce captured Distant Horizons depth" },
        GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_TEXTURE_BINDING,
        GpuFormat.D32_FLOAT,
        width,
        height,
        1,
        1,
    )
    private val view = gpuDevice.createTextureView(texture)
    private var convention = DistantHorizonsDepthConvention.OPEN_GL
    private var inverseMvmProjection = Matrix4f()
    private var nearClipPlane = 0f
    private var farClipPlane = 0f
    private var frameToken = Long.MIN_VALUE
    var backend = DistantHorizonsBackend.OPEN_GL.name
        private set
    private var apiVersion: String? = null

    fun capture(
        source: GpuTextureView,
        snapshot: DistantHorizonsDepthSnapshot,
        renderState: DistantHorizonsRenderState,
        apiVersion: String?,
    ) {
        val encoder = gpuDevice.createCommandEncoder()
        encoder.copyTextureToTexture(source.texture(), texture, 0, 0, 0, 0, 0, width, height)
        encoder.submit()
        convention = snapshot.convention
        backend = snapshot.backend.name
        inverseMvmProjection = Matrix4f(renderState.inverseMvmProjection)
        nearClipPlane = renderState.nearClipPlane
        farClipPlane = renderState.farClipPlane
        frameToken = renderState.frameToken
        this.apiVersion = apiVersion
    }

    fun matches(width: Int, height: Int): Boolean = this.width == width && this.height == height

    fun snapshot() = DistantHorizonsDepthTexture(
        textureView = view,
        clearDepth = convention.clearDepth,
        zeroToOneDepth = convention.zeroToOneDepth,
        inverseMvmProjection = Matrix4f(inverseMvmProjection),
        nearClipPlane = nearClipPlane,
        farClipPlane = farClipPlane,
        frameToken = frameToken,
        backend = backend,
        apiVersion = apiVersion,
    )

    override fun close() {
        view.close()
        texture.close()
    }
}

/** Read-only Blaze view over a raw DH-owned OpenGL texture. It must never delete the DH texture id. */
private class BorrowedRawDepthTexture(
    id: Int,
    width: Int,
    height: Int,
    private val deviceIdentity: Int,
    frameBufferCache: FrameBufferCache,
) : AutoCloseable {
    private val texture = BorrowedGlTexture(id, width, height, frameBufferCache)
    val view: GpuTextureView = BorrowedGlTextureView(texture, frameBufferCache)

    fun matches(id: Int, width: Int, height: Int, deviceIdentity: Int): Boolean =
        this.deviceIdentity == deviceIdentity &&
            texture.glId() == id &&
            texture.getWidth(0) == width &&
            texture.getHeight(0) == height

    override fun close() {
        view.close()
    }
}

private class BorrowedGlTexture(
    id: Int,
    width: Int,
    height: Int,
    frameBufferCache: FrameBufferCache,
) : GlTexture(
    GpuTexture.USAGE_COPY_SRC or GpuTexture.USAGE_TEXTURE_BINDING,
    "Distant Horizons depth (borrowed)",
    GpuFormat.D32_FLOAT,
    width,
    height,
    1,
    1,
    id,
    frameBufferCache,
) {
    override fun close() = Unit
    override fun isClosed() = false
}

private class BorrowedGlTextureView(
    texture: BorrowedGlTexture,
    frameBufferCache: FrameBufferCache,
) : GlTextureView(texture, 0, 1, frameBufferCache)
