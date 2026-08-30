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

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.PoseStack
import kotlinx.coroutines.asExecutor
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.io.ensurePngOrConvertJpeg
import okio.buffer
import okio.source
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

fun PoseStack.reset() {
    while (!isEmpty) popPose()
    setIdentity()
}

inline fun ByteBufferBuilder.begin(pipeline: RenderPipeline): BufferBuilder =
    BufferBuilder(
        this,
        pipeline.primitiveTopology,
        requireNotNull(pipeline.getVertexFormatBinding(0)) {
            "Pipeline ${pipeline.location} has no vertex format binding"
        },
    )

inline fun <T> withOutputTextureOverride(
    color: GpuTextureView? = null,
    depth: GpuTextureView? = null,
    block: () -> T,
): T {
    val oldColor = RenderSystem.outputColorTextureOverride
    val oldDepth = RenderSystem.outputDepthTextureOverride

    try {
        RenderSystem.outputColorTextureOverride = color
        RenderSystem.outputDepthTextureOverride = depth
        return block()
    } finally {
        RenderSystem.outputColorTextureOverride = oldColor
        RenderSystem.outputDepthTextureOverride = oldDepth
    }
}

inline fun GpuTexture.clearColor(color: Color4b = Color4b.TRANSPARENT) =
    gpuDevice.createCommandEncoder().clearColorTexture(this, color.toVector4f())

inline fun GpuTexture.clearDepth(depth: Double = 0.0) =
    gpuDevice.createCommandEncoder().clearDepthTexture(this, depth)

fun RenderTarget.clearColorAndDepth(color: Color4b = Color4b.TRANSPARENT, depth: Double = 0.0) {
    val colorAttachment = colorTexture
    val depthAttachment = depthTexture.takeIf { useDepth }

    when {
        colorAttachment != null && depthAttachment != null ->
            gpuDevice.createCommandEncoder().clearColorAndDepthTextures(
                colorAttachment, color.toVector4f(), depthAttachment, depth
            )
        colorAttachment != null -> colorAttachment.clearColor(color)
        depthAttachment != null -> depthAttachment.clearDepth(depth)
    }
}

inline fun GpuTexture.asView(baseMipLevel: Int = 0, mipLevels: Int = this.mipLevels): GpuTextureView =
    gpuDevice.createTextureView(this, baseMipLevel, mipLevels)

inline fun GpuBuffer.mapBuffer(read: Boolean = false, write: Boolean = false): GpuBufferSlice.MappedView =
    this.map(read, write)

inline fun GpuBufferSlice.mapBuffer(read: Boolean = false, write: Boolean = false): GpuBufferSlice.MappedView =
    this.map(read, write)

fun GpuBuffer.readFully(): ByteBuffer = read(0L, this.size())

/**
 * @receiver Should have flag [GpuBuffer.USAGE_MAP_READ]
 * @return A [ByteBuffer] allocated with [MemoryUtil]
 */
fun GpuBuffer.read(offset: Long, length: Long): ByteBuffer = this.map(offset, length, true, false).use {
    val source = it.data
    val result = MemoryUtil.memAlloc(source.remaining())
    try {
        MemoryUtil.memCopy(
            MemoryUtil.memAddress(source),
            MemoryUtil.memAddress(result),
            result.remaining().toLong(),
        )
        result
    } catch (t: Throwable) {
        MemoryUtil.memFree(result)
        throw t
    }
}
