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

package net.ccbluex.liquidbounce.render.engine.distanthorizons

import com.mojang.blaze3d.textures.GpuTextureView
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import org.lwjgl.opengl.GL11C
import org.lwjgl.opengl.GL45C

internal fun DistantHorizonsDepthTextureProvider.captureAvailableDepth(
    renderState: DistantHorizonsRenderState,
): Boolean {
    val snapshot = fetchDepthSnapshot() ?: return false
    val openGlTextureId = snapshot.openGlTextureId
    val openGlSize = openGlTextureId?.let(::openGlTextureSize)
    if (openGlTextureId != null && openGlSize != null) {
        captureOpenGlDepth(openGlTextureId, openGlSize, snapshot, renderState)
        return true
    }
    return captureDeviceDepth(snapshot, renderState)
}

private fun DistantHorizonsDepthTextureProvider.fetchDepthSnapshot(): DistantHorizonsDepthSnapshot? {
    val fetch = integration?.depthTexture() ?: return null
    backend = fetch.backend ?: backend
    fetch.unsupportedReason?.let { reason ->
        markUnsupported(reason)
        return null
    }
    return fetch.snapshot ?: run {
        statusDetail = "Distant Horizons depth texture is not ready"
        null
    }
}

private fun DistantHorizonsDepthTextureProvider.captureDeviceDepth(
    snapshot: DistantHorizonsDepthSnapshot,
    renderState: DistantHorizonsRenderState,
): Boolean {
    val source = liveTextureView(snapshot) ?: return false
    val width = source.getWidth(0)
    val height = source.getHeight(0)
    captureTarget(width, height, snapshot).capture(source, snapshot, renderState, apiVersion)
    finishCapture(width, height)
    return true
}

private fun DistantHorizonsDepthTextureProvider.captureOpenGlDepth(
    textureId: Int,
    size: DepthTextureSize,
    snapshot: DistantHorizonsDepthSnapshot,
    renderState: DistantHorizonsRenderState,
) {
    captureTarget(size.width, size.height, snapshot).captureOpenGl(textureId, snapshot, renderState, apiVersion)
    finishCapture(size.width, size.height)
}

private fun DistantHorizonsDepthTextureProvider.captureTarget(
    width: Int,
    height: Int,
    snapshot: DistantHorizonsDepthSnapshot,
): CapturedDepthTexture {
    val previousBackend = capturedDepth?.backend
    if (previousBackend != null && previousBackend != snapshot.backend.name) {
        invalidateDepth()
    }
    return capturedDepth?.takeIf { it.matches(width, height) }
        ?: CapturedDepthTexture(width, height).also { replacement ->
            capturedDepth?.close()
            capturedDepth = replacement
        }
}

private fun DistantHorizonsDepthTextureProvider.finishCapture(width: Int, height: Int) {
    lifecycle.capture(width, height)
    capabilitySupported = true
    statusDetail = null
}

private fun DistantHorizonsDepthTextureProvider.liveTextureView(
    snapshot: DistantHorizonsDepthSnapshot,
): GpuTextureView? = snapshot.blazeTextureView ?: resolveOpenGl(snapshot)

private fun DistantHorizonsDepthTextureProvider.resolveOpenGl(
    snapshot: DistantHorizonsDepthSnapshot,
): GpuTextureView? {
    val glId = snapshot.openGlTextureId ?: return null
    val size = openGlTextureSize(glId) ?: return null
    val deviceIdentity = System.identityHashCode(gpuDevice)
    val frameBufferCache = openGlFrameBufferCache.resolve() ?: return null
    val borrowed = borrowedTexture?.takeIf { it.matches(glId, size.width, size.height, deviceIdentity) }
        ?: BorrowedRawDepthTexture(glId, size.width, size.height, deviceIdentity, frameBufferCache).also { replacement ->
            borrowedTexture?.close()
            borrowedTexture = replacement
        }
    return borrowed.view
}

private fun openGlTextureSize(glId: Int): DepthTextureSize? {
    if (gpuDevice.javaClass.name != GL_DEVICE_CLASS || !GL11C.glIsTexture(glId)) return null
    val width = GL45C.glGetTextureLevelParameteri(glId, 0, GL11C.GL_TEXTURE_WIDTH)
    val height = GL45C.glGetTextureLevelParameteri(glId, 0, GL11C.GL_TEXTURE_HEIGHT)
    return DepthTextureSize(width, height).takeIf { width > 0 && height > 0 }
}

private const val GL_DEVICE_CLASS = "com.mojang.blaze3d.opengl.GlDevice"
