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

package net.ccbluex.liquidbounce.utils.render

import com.google.common.base.Suppliers
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import kotlinx.coroutines.asExecutor
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.io.ensurePngOrConvertJpeg
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.function.Supplier

/**
 * Should be called from main thread.
 */
fun NativeImage.registerTexture(identifier: Identifier): DynamicTexture {
    val texture = asTexture(identifier::toString)
    mc.textureManager.register(identifier, texture)
    return texture
}

/**
 * Read and close stream. Accepts PNG.
 */
inline fun InputStream.readNativeImage(): NativeImage = NativeImage.read(this)

/**
 * Read and close source. Accepts JPEG and PNG.
 */
fun BufferedSource.readNativeImage(): NativeImage =
    this.ensurePngOrConvertJpeg().inputStream().readNativeImage()

/**
 * Read from file. Accepts JPEG and PNG.
 */
fun File.readNativeImage(): NativeImage =
    this.source().buffer().readNativeImage()

inline fun NativeImage.asTexture(
    name: String = "Texture NativeImage@${this.hashCode().toString(16)} (${this.width}x${this.height})",
) = DynamicTexture(Suppliers.ofInstance(name), this)

@JvmOverloads
fun NativeImage.asTexture(
    nameSupplier: Supplier<String> = Supplier {
        "Texture NativeImage@${this.hashCode().toString(16)} (${this.width}x${this.height})"
    },
) = DynamicTexture(nameSupplier, this)

val AbstractTexture.textureSetup: TextureSetup
    get() = TextureSetup.singleTexture(textureView, sampler)

inline fun GpuTextureView.asTextureSetup(sampler: GpuSampler): TextureSetup =
    TextureSetup.singleTexture(this, sampler)

inline fun ByteBuffer.toGpuBuffer(
    labelGetter: Supplier<String>? = null,
    usage: @GpuBuffer.Usage Int,
): GpuBuffer = gpuDevice.createBuffer(labelGetter, usage, this)
