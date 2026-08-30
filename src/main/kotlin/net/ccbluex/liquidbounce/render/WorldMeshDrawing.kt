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

package net.ccbluex.liquidbounce.render

import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.ccbluex.fastutil.objectObjectMapOf
import net.ccbluex.liquidbounce.render.engine.type.Vec3f
import net.ccbluex.liquidbounce.render.utils.forEachVertex
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import net.ccbluex.liquidbounce.render.buffer.writeStd140
import net.minecraft.client.renderer.texture.AbstractTexture

/**
 * Variant of [drawCustomMesh] that binds [sampler0] as `Sampler0`.
 */
inline fun WorldRenderEnvironment.drawCustomMeshTextured(
    sampler0: AbstractTexture,
    pipeline: RenderPipeline = ClientRenderPipelines.texQuads(noDepthTest = true),
    uniforms: Map<String, GpuBufferSlice> = emptyMap(),
    drawer: VertexConsumer.(PoseStack.Pose) -> Unit,
) = drawCustomMesh(
    pipeline = pipeline,
    textures = objectObjectMapOf("Sampler0", sampler0),
    uniforms = uniforms,
    drawer = drawer,
)

/**
 * Preferred mesh draw helper for world rendering code.
 */
inline fun WorldRenderEnvironment.drawCustomMesh(
    pipeline: RenderPipeline,
    textures: Map<String, AbstractTexture> = emptyMap(),
    uniforms: Map<String, GpuBufferSlice> = emptyMap(),
    drawer: VertexConsumer.(PoseStack.Pose) -> Unit,
) {
    start(
        pipeline = pipeline,
        textures = textures,
        uniforms = uniforms,
    ).use { scope ->
        drawer(scope.consumer, poseStack.last())
    }
}

/**
 * Draws a line with endpoint [p1] and [p2] and color [argb].
 */
fun WorldRenderEnvironment.drawLine(p1: Vec3f, p2: Vec3f, argb: Int) =
    drawCustomMesh(ClientRenderPipelines.lines(noDepthTest = true)) { pose ->
        addVertex(pose, p1).setColor(argb)
        addVertex(pose, p2).setColor(argb)
    }
