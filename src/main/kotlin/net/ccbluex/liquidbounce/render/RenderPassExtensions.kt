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

import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTextureView
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import net.minecraft.client.renderer.DynamicUniforms
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4fc
import java.util.Optional
import java.util.OptionalDouble
import java.util.function.Supplier

private val VECTOR3F_0 = Vector3f()
private val TEXTURE_MATRIX = Matrix4f()

/**
 * Minecraft keeps the last [DynamicUniforms.Transform] for value-based GPU-slice reuse.
 * Every component therefore has to be a stable snapshot instead of a shared mutable scratch value.
 */
internal fun snapshotDynamicTransform(
    modelView: Matrix4f,
    colorModulator: Color4b = Color4b.WHITE,
    modelOffset: Vector3f = VECTOR3F_0,
    textureMatrix: Matrix4f = TEXTURE_MATRIX,
) = DynamicUniforms.Transform(
    Matrix4f(modelView),
    colorModulator.toVector4f(),
    Vector3f(modelOffset),
    Matrix4f(textureMatrix),
)

@JvmOverloads
fun getDynamicTransformsUniform(
    modelView: Matrix4f? = null,
    colorModulator: Color4b = Color4b.WHITE,
    modelOffset: Vector3f? = null,
): GpuBufferSlice {
    val transform = snapshotDynamicTransform(
        modelView ?: RenderSystem.getModelViewMatrixCopy(),
        colorModulator,
        modelOffset ?: VECTOR3F_0,
    )
    val slice = RenderSystem.getDynamicUniforms().writeTransform(transform)

    return slice
}

private val RENDER_PASS_DEFAULT_LABEL = Supplier { "$RENDER_CLIENT_NAME RenderPass" }

@JvmOverloads
fun RenderTarget.createRenderPass(
    labelGetter: Supplier<String> = RENDER_PASS_DEFAULT_LABEL,
    clearColor: Optional<Vector4fc> = Optional.empty(),
    clearDepth: OptionalDouble = OptionalDouble.empty(),
    useDepthAttachment: Boolean = true,
    allowOverride: Boolean = false,
): RenderPass = newRenderPass(
    labelGetter,
    colorAttachment =
        RenderSystem.outputColorTextureOverride?.takeIf { allowOverride } ?: this.colorTextureView!!,
    clearColor,
    depthAttachment =
        RenderSystem.outputDepthTextureOverride?.takeIf { allowOverride }
            ?: depthTextureView.takeIf { this.useDepth && useDepthAttachment },
    clearDepth,
)

/**
 * Color-only RenderPass.
 */
@JvmOverloads
fun GpuTextureView.createRenderPass(
    labelGetter: Supplier<String> = RENDER_PASS_DEFAULT_LABEL,
    clearColor: Optional<Vector4fc> = Optional.empty(),
    allowOverride: Boolean = false,
): RenderPass = newRenderPass(
    labelGetter,
    colorAttachment = RenderSystem.outputColorTextureOverride?.takeIf { allowOverride } ?: this,
    clearColor,
)

@Suppress("NOTHING_TO_INLINE")
private inline fun newRenderPass(
    labelGetter: Supplier<String> = RENDER_PASS_DEFAULT_LABEL,
    colorAttachment: GpuTextureView,
    clearColor: Optional<Vector4fc> = Optional.empty(),
    depthAttachment: GpuTextureView? = null,
    clearDepth: OptionalDouble = OptionalDouble.empty(),
): RenderPass = gpuDevice.createCommandEncoder().createRenderPass(
    labelGetter,
    colorAttachment,
    clearColor,
    depthAttachment,
    clearDepth,
)
