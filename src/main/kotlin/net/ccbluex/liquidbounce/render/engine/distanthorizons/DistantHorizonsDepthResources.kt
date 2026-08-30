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

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.opengl.FrameBufferCache
import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.opengl.GlTextureView
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11C
import org.lwjgl.opengl.GL43C

internal data class DepthTextureSize(val width: Int, val height: Int)

internal class OpenGlFrameBufferCacheResolver {

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

internal class CapturedDepthTexture(
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
        updateMetadata(snapshot, renderState, apiVersion)
    }

    fun captureOpenGl(
        sourceTextureId: Int,
        snapshot: DistantHorizonsDepthSnapshot,
        renderState: DistantHorizonsRenderState,
        apiVersion: String?,
    ) {
        val destination = texture as? GlTexture
            ?: error("Minecraft's OpenGL device did not create an OpenGL depth texture")
        GL43C.glCopyImageSubData(
            sourceTextureId,
            GL11C.GL_TEXTURE_2D,
            0,
            0,
            0,
            0,
            destination.glId(),
            GL11C.GL_TEXTURE_2D,
            0,
            0,
            0,
            0,
            width,
            height,
            1,
        )
        updateMetadata(snapshot, renderState, apiVersion)
    }

    private fun updateMetadata(
        snapshot: DistantHorizonsDepthSnapshot,
        renderState: DistantHorizonsRenderState,
        apiVersion: String?,
    ) {
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
        width = width,
        height = height,
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
internal class BorrowedRawDepthTexture(
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

internal class BorrowedGlTexture(
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

internal class BorrowedGlTextureView(
    texture: BorrowedGlTexture,
    frameBufferCache: FrameBufferCache,
) : GlTextureView(texture, 0, 1, frameBufferCache)
