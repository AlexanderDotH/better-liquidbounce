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

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.renderer.texture.DynamicTexture
import java.awt.image.BufferedImage

fun DynamicTexture.uploadRect(
    mipLevel: Int,
    x: Int, y: Int,
    width: Int, height: Int,
) = this.texture.write(
    source = this.pixels!!,
    mipLevel, depthOrLayer = 0,
    x, y,
    width, height,
    x, y,
)

fun NativeImage.toBufferedImage(): BufferedImage {
    val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    bufferedImage.setRGB(
        0,
        0,
        width,
        height,
        pixels,
        0,
        width
    )

    return bufferedImage
}

fun BufferedImage.toNativeImage(): NativeImage {
    val nativeImage = NativeImage(NativeImage.Format.RGBA, this.width, this.height, false)

    try {
        copyToNativeImage(nativeImage, width = width, height = height)
    } catch (throwable: Throwable) {
        nativeImage.close()
        throw throwable
    }

    return nativeImage
}

@Suppress("LongParameterList")
fun BufferedImage.copyToNativeImage(
    target: NativeImage,
    sourceX: Int = 0,
    sourceY: Int = 0,
    targetX: Int = 0,
    targetY: Int = 0,
    width: Int = this.width,
    height: Int = this.height,
    scratchBuffer: IntArray = IntArray(0),
): IntArray = BufferedImageNativeCopy.copy(
    source = this,
    target = target,
    region = NativeImageCopyRegion(sourceX, sourceY, targetX, targetY, width, height),
    scratchBuffer = scratchBuffer,
)
