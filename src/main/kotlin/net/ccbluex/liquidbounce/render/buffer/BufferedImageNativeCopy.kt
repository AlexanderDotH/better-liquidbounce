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

package net.ccbluex.liquidbounce.render.buffer

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.util.ARGB
import org.lwjgl.system.MemoryUtil
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.awt.image.SinglePixelPackedSampleModel
import java.nio.IntBuffer

internal data class NativeImageCopyRegion(
    val sourceX: Int,
    val sourceY: Int,
    val targetX: Int,
    val targetY: Int,
    val width: Int,
    val height: Int,
) {
    fun requireValid(source: BufferedImage, target: NativeImage) {
        require(!target.isClosed) { "Target image is closed" }
        require(target.format() == NativeImage.Format.RGBA) { "Target image must use RGBA format" }
        require(width >= 0 && height >= 0) { "Copy dimensions must not be negative" }
        require(sourceX >= 0 && sourceY >= 0 && width <= source.width - sourceX && height <= source.height - sourceY) {
            "Source rectangle is outside the BufferedImage"
        }
        require(targetX >= 0 && targetY >= 0 && width <= target.width - targetX && height <= target.height - targetY) {
            "Target rectangle is outside the NativeImage"
        }
    }
}

internal object BufferedImageNativeCopy {

    fun copy(
        source: BufferedImage,
        target: NativeImage,
        region: NativeImageCopyRegion,
        scratchBuffer: IntArray,
    ): IntArray {
        region.requireValid(source, target)
        val targetPixels = MemoryUtil.memIntBuffer(target.pointer, target.width * target.height)

        if (copyPackedArgb(source, target, targetPixels, region)) {
            return scratchBuffer
        }

        return copyGenericArgb(source, target, targetPixels, region, scratchBuffer)
    }

    private fun copyPackedArgb(
        source: BufferedImage,
        target: NativeImage,
        targetPixels: IntBuffer,
        region: NativeImageCopyRegion,
    ): Boolean {
        val dataBuffer = source.raster.dataBuffer
        val sampleModel = source.raster.sampleModel
        if (source.type != BufferedImage.TYPE_INT_ARGB ||
            dataBuffer !is DataBufferInt || sampleModel !is SinglePixelPackedSampleModel
        ) {
            return false
        }

        val sourceOffset = dataBuffer.offset +
            (region.sourceY - source.raster.sampleModelTranslateY) * sampleModel.scanlineStride +
            region.sourceX - source.raster.sampleModelTranslateX
        copyArgbRows(
            dataBuffer.data,
            sourceOffset,
            sampleModel.scanlineStride,
            targetPixels,
            region.targetX + region.targetY * target.width,
            target.width,
            region.width,
            region.height,
        )
        return true
    }

    private fun copyGenericArgb(
        source: BufferedImage,
        target: NativeImage,
        targetPixels: IntBuffer,
        region: NativeImageCopyRegion,
        scratchBuffer: IntArray,
    ): IntArray {
        val requiredSize = region.width * region.height
        val argbPixels = scratchBuffer.takeIf { it.size >= requiredSize } ?: IntArray(requiredSize)
        source.getRGB(region.sourceX, region.sourceY, region.width, region.height, argbPixels, 0, region.width)
        copyArgbRows(
            argbPixels,
            0,
            region.width,
            targetPixels,
            region.targetX + region.targetY * target.width,
            target.width,
            region.width,
            region.height,
        )
        return argbPixels
    }

    @Suppress("LongParameterList")
    private fun copyArgbRows(
        source: IntArray,
        sourceOffset: Int,
        sourceStride: Int,
        target: IntBuffer,
        targetOffset: Int,
        targetStride: Int,
        width: Int,
        height: Int,
    ) {
        for (y in 0 until height) {
            val sourceRow = sourceOffset + y * sourceStride
            val targetRow = targetOffset + y * targetStride
            for (x in 0 until width) {
                target.put(targetRow + x, ARGB.toABGR(source[sourceRow + x]))
            }
        }
    }
}
