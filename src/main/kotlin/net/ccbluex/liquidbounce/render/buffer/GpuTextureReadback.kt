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

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.textures.GpuTexture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.io.ensurePngOrConvertJpeg
import net.minecraft.util.ARGB
import okio.buffer
import okio.source
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.CompletableFuture

fun GpuTexture.saveToFile(file: File): CompletableFuture<*> =
    this.toNativeImage().thenAcceptAsync({ nativeImage ->
        nativeImage.writeToFile(file)
        nativeImage.close()
    }, Dispatchers.IO.asExecutor())

private fun GpuBufferSlice.readNativeImageRGBA(
    width: Int,
    height: Int,
    destination: NativeImage = NativeImage(width, height, false),
): NativeImage {
    this.mapBuffer(read = true, write = false).use { mappedView ->
        for (y in 0..<height) {
            for (x in 0..<width) {
                val abgr = mappedView.data().getInt((x + y * width) * GpuFormat.RGBA8_UNORM.blockSize())
                destination.setPixelABGR(x, height - y - 1, abgr)
            }
        }
    }
    return destination
}

/**
 * @see net.minecraft.client.Screenshot.takeScreenshot
 */
@JvmOverloads
fun GpuTexture.toNativeImage(mipLevel: Int = 0): CompletableFuture<NativeImage> {
    val width = this.getWidth(mipLevel)
    val height = this.getHeight(mipLevel)
    val pixelSize = this.format.blockSize()
    val gpuBuffer = gpuDevice.createBuffer(
        { "PixelBuffer - " + (this.label ?: "Anonymous") },
        GpuBuffer.USAGE_MAP_READ or GpuBuffer.USAGE_COPY_DST,
        width * height * pixelSize.toLong()
    )

    val future = CompletableFuture<NativeImage>()

    this.copyTo(gpuBuffer, mipLevel = mipLevel) {
        future.complete(gpuBuffer.slice().readNativeImageRGBA(width, height))
        gpuBuffer.close()
    }

    return future
}

@JvmOverloads
fun GpuTexture.toBufferedImage(mipLevel: Int = 0): CompletableFuture<BufferedImage> {
    val width = this.getWidth(mipLevel)
    val height = this.getHeight(mipLevel)
    val pixelSize = this.format.blockSize()
    val gpuBuffer = gpuDevice.createBuffer(
        { "PixelBuffer - " + (this.label ?: "Anonymous") },
        GpuBuffer.USAGE_MAP_READ or GpuBuffer.USAGE_COPY_DST,
        width * height * pixelSize.toLong()
    )

    val future = CompletableFuture<BufferedImage>()
    this.copyTo(gpuBuffer, mipLevel = mipLevel) {
        gpuBuffer.mapBuffer(read = true, write = false).use { mappedView ->
            val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            for (y in 0..<height) {
                for (x in 0..<width) {
                    val abgr = mappedView.data().getInt((x + y * width) * pixelSize)
                    bufferedImage.setRGB(x, height - y - 1, ARGB.fromABGR(abgr))
                }
            }
            future.complete(bufferedImage)
        }
        gpuBuffer.close()
    }

    return future
}
