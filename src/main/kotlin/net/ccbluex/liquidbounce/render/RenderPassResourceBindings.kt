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

@file:JvmName("RenderPassExtensionsKt")
@file:JvmMultifileClass
@file:Suppress("NOTHING_TO_INLINE")

package net.ccbluex.liquidbounce.render

import com.mojang.blaze3d.IndexType
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderPass
import net.minecraft.client.renderer.texture.AbstractTexture

inline fun RenderPass.bindTextures(textures: Map<String, AbstractTexture?>) =
    textures.forEach { bindTexture(it.key, it.value) }

inline fun RenderPass.bindTexture(name: String, texture: AbstractTexture?) =
    bindTexture(name, texture?.textureView, texture?.sampler)

inline fun RenderPass.unbindTexture(name: String) =
    bindTexture(name, null, null)

inline fun RenderPass.setUniforms(uniforms: Map<String, GpuBufferSlice>) =
    uniforms.forEach { setUniform(it.key, it.value) }

/**
 * Set vertex and index buffers for [RenderPass] and call [RenderPass.drawIndexed].
 *
 * This function assumes the [GpuBufferSlice]s are correctly aligned with corresponding vertex/index byte count.
 */
fun RenderPass.bindAndDraw(
    vertexSlice: GpuBufferSlice,
    indexSlice: GpuBufferSlice,
    indexType: IndexType,
    indexCount: Int,
) {
    setVertexBuffer(0, vertexSlice)
    setIndexBuffer(indexSlice.buffer, indexType)
    drawIndexed(
        indexCount,
        1,
        (indexSlice.offset / indexType.bytes).toInt(),
        0,
        0,
    )
}
