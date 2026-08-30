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

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import net.ccbluex.liquidbounce.render.buffer.write
import net.minecraft.client.renderer.texture.AbstractTexture
import java.awt.image.BufferedImage
import java.util.function.Supplier

class GlyphAtlasTexture(
    label: Supplier<String>,
    pixels: NativeImage,
    retainPixels: Boolean,
) : AbstractTexture() {

    var pixels: NativeImage? = pixels
        private set

    init {
        require(pixels.format() == NativeImage.Format.LUMINANCE) {
            "Glyph atlas pixels must use LUMINANCE format"
        }

        val device = gpuDevice
        val gpuTexture = device.createTexture(
            label,
            GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_TEXTURE_BINDING,
            GpuFormat.R8_UNORM,
            pixels.width,
            pixels.height,
            1,
            1,
        )
        texture = gpuTexture
        sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)
        textureView = device.createTextureView(gpuTexture)

        try {
            upload(pixels)
        } catch (throwable: Throwable) {
            close()
            throw throwable
        }

        if (!retainPixels) {
            pixels.close()
            this.pixels = null
        }
    }

    fun upload() = upload(requireNotNull(pixels) { "Glyph atlas has no retained pixel data" })

    @Suppress("LongParameterList")
    fun uploadRect(
        mipLevel: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        val pixels = requireNotNull(pixels) { "Glyph atlas has no retained pixel data" }
        getTexture().write(
            source = pixels,
            mipLevel = mipLevel,
            depthOrLayer = 0,
            destX = x,
            destY = y,
            width = width,
            height = height,
            sourceX = x,
            sourceY = y,
        )
    }

    private fun upload(source: NativeImage) {
        getTexture().write(source, width = source.width, height = source.height)
    }

    override fun close() {
        pixels?.close()
        pixels = null
        super.close()
    }
}

internal fun BufferedImage.toLuminanceNativeImage(): NativeImage {
    val nativeImage = NativeImage(NativeImage.Format.LUMINANCE, width, height, false)

    try {
        copyCoverageToNativeImage(nativeImage, width = width, height = height)
    } catch (throwable: Throwable) {
        nativeImage.close()
        throw throwable
    }

    return nativeImage
}

@Suppress("LongParameterList")
internal fun BufferedImage.copyCoverageToNativeImage(
    target: NativeImage,
    sourceX: Int = 0,
    sourceY: Int = 0,
    targetX: Int = 0,
    targetY: Int = 0,
    width: Int = this.width,
    height: Int = this.height,
    scratchBuffer: IntArray = IntArray(0),
): IntArray {
    validateCoverageCopy(target, sourceX, sourceY, targetX, targetY, width, height)
    return GlyphCoverageCopier(
        this,
        target,
        sourceX,
        sourceY,
        targetX,
        targetY,
        width,
        height,
    ).copy(scratchBuffer)
}
