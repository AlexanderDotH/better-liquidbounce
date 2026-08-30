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

@file:Suppress("NOTHING_TO_INLINE")

@file:JvmName("RenderExtensionsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.render.buffer

import com.google.common.util.concurrent.Runnables
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.textures.GpuTexture
import kotlinx.coroutines.asExecutor
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.io.ensurePngOrConvertJpeg
import okio.buffer
import okio.source
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

inline fun GpuBufferSlice.write(byteBuffer: ByteBuffer) =
    gpuDevice.createCommandEncoder().writeToBuffer(this, byteBuffer)

inline fun GpuBufferSlice.copyFrom(source: GpuBufferSlice) =
    gpuDevice.createCommandEncoder().copyToBuffer(source, this)

@Suppress("LongParameterList")
inline fun GpuTexture.write(
    source: NativeImage,
    mipLevel: Int = 0,
    depthOrLayer: Int = 0,
    destX: Int = 0,
    destY: Int = 0,
    width: Int = getWidth(mipLevel),
    height: Int = getHeight(mipLevel),
    sourceX: Int = 0,
    sourceY: Int = 0,
) {
    val commandEncoder = gpuDevice.createCommandEncoder()
    val slice = commandEncoder.transientMemory()
        .uploadStaging(source.pixelBytes, 1L, GpuBuffer.USAGE_COPY_SRC)
    commandEncoder.copyBufferToTexture(
        slice, sourceX, sourceY, source.width, source.height,
        this, destX, destY, width, height,
        mipLevel,depthOrLayer,
    )
}

inline fun GpuTexture.copyTo(
    destination: GpuBuffer,
    offset: Long = 0L,
    mipLevel: Int = 0,
    x: Int = 0,
    y: Int = 0,
    width: Int = getWidth(mipLevel),
    height: Int = getHeight(mipLevel),
    callback: Runnable = Runnables.doNothing(),
) = gpuDevice.createCommandEncoder().copyTextureToBuffer(
    this, destination, offset, callback, mipLevel,
    x, y, width, height,
)

fun GpuTexture.asyncCopyTo(
    destination: GpuBuffer,
    offset: Long = 0L,
    mipLevel: Int = 0,
    x: Int = 0,
    y: Int = 0,
    width: Int = getWidth(mipLevel),
    height: Int = getHeight(mipLevel),
): CompletableFuture<*> {
    val future = CompletableFuture<Any?>()
    copyTo(destination, offset, mipLevel, x, y, width, height) { future.complete(null) }
    return future
}

@JvmOverloads
fun GpuTexture.copyFully(
    labelGetter: Supplier<String>? = null,
    usage: @GpuTexture.Usage Int = 0,
): GpuTexture {
    val dest = gpuDevice.createTexture(
        labelGetter,
        GpuTexture.USAGE_COPY_DST or usage,
        format,
        getWidth(0), getHeight(0),
        depthOrLayers, mipLevels,
    )

    for (mipLevel in 0 until mipLevels) {
        dest.copyFrom(this, mipLevel)
    }

    return dest
}

@Suppress("LongParameterList")
inline fun GpuTexture.copyFrom(
    source: GpuTexture,
    mipLevel: Int = 0,
    intoX: Int = 0,
    intoY: Int = 0,
    sourceX: Int = 0,
    sourceY: Int = 0,
    width: Int = source.getWidth(mipLevel),
    height: Int = source.getHeight(mipLevel),
) = gpuDevice.createCommandEncoder().copyTextureToTexture(
    source, this, mipLevel, intoX, intoY, sourceX, sourceY, width, height
)
