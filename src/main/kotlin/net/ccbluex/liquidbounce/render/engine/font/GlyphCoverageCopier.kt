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

@file:JvmName("GlyphAtlasTextureKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.render.engine.font

import com.mojang.blaze3d.platform.NativeImage
import org.lwjgl.system.MemoryUtil
import java.awt.image.BufferedImage
import java.awt.image.ComponentSampleModel
import java.awt.image.DataBufferByte
import java.awt.image.DataBufferInt
import java.awt.image.SinglePixelPackedSampleModel

internal class GlyphCoverageCopier(
    private val source: BufferedImage,
    target: NativeImage,
    private val sourceX: Int,
    private val sourceY: Int,
    targetX: Int,
    targetY: Int,
    private val width: Int,
    private val height: Int,
) {
    private val raster = source.raster
    private val targetPixels = MemoryUtil.memByteBuffer(target.pointer, target.width * target.height)
    private val targetOffset = targetX + targetY * target.width
    private val targetStride = target.width

    fun copy(scratchBuffer: IntArray): IntArray {
        if (copyGrayCoverage()) return scratchBuffer
        if (copyArgbAlpha()) return scratchBuffer
        return copyFallbackAlpha(scratchBuffer)
    }

    private fun copyGrayCoverage(): Boolean {
        val dataBuffer = raster.dataBuffer
        val sampleModel = raster.sampleModel
        if (source.type != BufferedImage.TYPE_BYTE_GRAY ||
            dataBuffer !is DataBufferByte || sampleModel !is ComponentSampleModel
        ) return false

        val sourceOffset = dataBuffer.offset +
            (sourceY - raster.sampleModelTranslateY) * sampleModel.scanlineStride +
            (sourceX - raster.sampleModelTranslateX) * sampleModel.pixelStride +
            sampleModel.bandOffsets[0]
        copyCoverageRows(
            dataBuffer.data,
            sourceOffset,
            sampleModel.scanlineStride,
            targetPixels,
            targetOffset,
            targetStride,
            width,
            height,
        )
        return true
    }

    private fun copyArgbAlpha(): Boolean {
        val dataBuffer = raster.dataBuffer
        val sampleModel = raster.sampleModel
        if (source.type != BufferedImage.TYPE_INT_ARGB ||
            dataBuffer !is DataBufferInt || sampleModel !is SinglePixelPackedSampleModel
        ) return false

        val sourceOffset = dataBuffer.offset +
            (sourceY - raster.sampleModelTranslateY) * sampleModel.scanlineStride +
            sourceX - raster.sampleModelTranslateX
        copyAlphaRows(
            dataBuffer.data,
            sourceOffset,
            sampleModel.scanlineStride,
            targetPixels,
            targetOffset,
            targetStride,
            width,
            height,
        )
        return true
    }

    private fun copyFallbackAlpha(scratchBuffer: IntArray): IntArray {
        val requiredSize = width * height
        val argbPixels = scratchBuffer.takeIf { it.size >= requiredSize } ?: IntArray(requiredSize)
        source.getRGB(sourceX, sourceY, width, height, argbPixels, 0, width)
        copyAlphaRows(
            argbPixels,
            0,
            width,
            targetPixels,
            targetOffset,
            targetStride,
            width,
            height,
        )
        return argbPixels
    }
}

@Suppress("LongParameterList")
internal fun BufferedImage.validateCoverageCopy(
    target: NativeImage,
    sourceX: Int,
    sourceY: Int,
    targetX: Int,
    targetY: Int,
    width: Int,
    height: Int,
) {
    require(!target.isClosed) { "Target image is closed" }
    require(target.format() == NativeImage.Format.LUMINANCE) { "Target image must use LUMINANCE format" }
    require(width >= 0 && height >= 0) { "Copy dimensions must not be negative" }
    require(sourceX >= 0 && sourceY >= 0 && width <= this.width - sourceX && height <= this.height - sourceY) {
        "Source rectangle is outside the BufferedImage"
    }
    require(targetX >= 0 && targetY >= 0 && width <= target.width - targetX && height <= target.height - targetY) {
        "Target rectangle is outside the NativeImage"
    }
}

internal fun copyCoverageRows(
    source: ByteArray,
    sourceOffset: Int,
    sourceStride: Int,
    target: java.nio.ByteBuffer,
    targetOffset: Int,
    targetStride: Int,
    width: Int,
    height: Int,
) {
    for (y in 0 until height) {
        val sourceRow = sourceOffset + y * sourceStride
        val targetRow = targetOffset + y * targetStride
        for (x in 0 until width) {
            target.put(targetRow + x, source[sourceRow + x])
        }
    }
}

internal fun copyAlphaRows(
    source: IntArray,
    sourceOffset: Int,
    sourceStride: Int,
    target: java.nio.ByteBuffer,
    targetOffset: Int,
    targetStride: Int,
    width: Int,
    height: Int,
) {
    for (y in 0 until height) {
        val sourceRow = sourceOffset + y * sourceStride
        val targetRow = targetOffset + y * targetStride
        for (x in 0 until width) {
            target.put(targetRow + x, (source[sourceRow + x] ushr 24).toByte())
        }
    }
}
