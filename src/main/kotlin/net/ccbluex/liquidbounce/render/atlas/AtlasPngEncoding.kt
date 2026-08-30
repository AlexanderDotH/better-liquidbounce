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

@file:JvmName("AbstractAtlasRendererKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.render.atlas

import com.mojang.blaze3d.platform.NativeImage
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.render.buffer.clearColorAndDepth
import net.ccbluex.liquidbounce.render.buffer.copyTo
import net.ccbluex.liquidbounce.render.buffer.readFully
import net.ccbluex.liquidbounce.render.buffer.withOutputTextureOverride
import net.minecraft.client.renderer.Rect2i
import net.minecraft.resources.Identifier
import okio.Buffer
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

internal fun encodePngTiles(
    label: String,
    tileSize: Int,
    textureSize: Int,
    atlasPixels: ByteBuffer,
    tileRects: Map<Identifier, Rect2i>,
    result: CompletableFuture<*>,
): Map<Identifier, ByteArray> = buildMap(tileRects.size) {
    NativeImage(tileSize, tileSize, false).use { tileImage ->
        val buffer = Buffer()
        for ((key, rect) in tileRects) {
            if (result.isCancelled) {
                throw CancellationException("$label atlas generation was cancelled")
            }

            atlasPixels.copyRectTo(tileImage, rect, textureSize)
            check(tileImage.writeToChannel(buffer)) { "Failed to encode $label atlas tile $key" }
            this[key] = buffer.readByteArray()
        }
    }
}

internal fun CompletableFuture<*>.completeExceptionally(
    label: String,
    throwable: Throwable,
) {
    if (throwable !is CancellationException) {
        logger.error("Failed to load $label atlas", throwable)
    }
    this.completeExceptionally(throwable)
}

internal fun ByteBuffer.copyRectTo(target: NativeImage, rect: Rect2i, atlasSize: Int) {
    require(target.format() == NativeImage.Format.RGBA)
    require(rect.width == target.width && rect.height == target.height)
    require(
        rect.x >= 0 && rect.y >= 0 &&
            rect.x + rect.width <= atlasSize && rect.y + rect.height <= atlasSize
    )

    val bytesPerPixel = NativeImage.Format.RGBA.components()
    val rowBytes = rect.width * bytesPerPixel.toLong()
    val sourcePixels = MemoryUtil.memAddress(this)
    for (row in 0 until rect.height) {
        // GPU readback rows are bottom-up while atlas rectangles use top-down coordinates.
        val sourceY = atlasSize - rect.y - row - 1
        val sourceOffset = (sourceY * atlasSize + rect.x) * bytesPerPixel.toLong()
        val targetOffset = row * rowBytes
        MemoryUtil.memCopy(sourcePixels + sourceOffset, target.pointer + targetOffset, rowBytes)
    }
}
