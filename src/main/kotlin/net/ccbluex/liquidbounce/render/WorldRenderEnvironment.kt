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
import com.mojang.blaze3d.pipeline.RenderTarget
import net.ccbluex.fastutil.objectObjectMapOf
import net.ccbluex.liquidbounce.event.EnvironmentEvent
import net.ccbluex.liquidbounce.render.utils.DistanceFadeUniformValueGroup
import net.ccbluex.liquidbounce.render.utils.forEachVertex
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import net.ccbluex.liquidbounce.render.buffer.writeStd140
import net.minecraft.core.Vec3i
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3


/**
 * This variable should be used when rendering long lines, meaning longer than ~2 in 3d.
 * [WorldRenderEnvironment.longLines] is available for this.
 *
 * Context:
 * For some reason, newer drivers for AMD Vega iGPUs (about end 2023 until now) fail to correctly smooth lines.
 *
 * This has to be removed or limited to old driver versions when AMD actually fixes the bug in their drivers.
 * But as of now, 01.02.2025, they haven't.
 */
val HAS_AMD_VEGA_APU = gpuDevice.deviceInfo.name.startsWith("AMD Radeon(TM) RX Vega") &&
    gpuDevice.deviceInfo.vendorName == "ATI Technologies Inc."

val FULL_BOX = AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)

val EMPTY_BOX = AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

internal val ROUNDED_RECT_AS_OUTLINE_CIRCLE_UBO by lazy(LazyThreadSafetyMode.NONE) {
    val slice = ClientUniformDefine.ROUNDED_RECT.createSingleBuffer()
    slice.writeStd140 {
        putVec2(1f, 1f)
        putFloat(2f)
    }
    slice
}

inline fun <E> EnvironmentEvent<E>.renderEnvironment(draw: E.() -> Unit) {
    environment.draw()
}

inline fun WorldRenderEnvironment.withPositionRelativeToCamera(draw: WorldRenderEnvironment.() -> Unit) {
    poseStack.withPush {
        translate(camera.position().reverse())
        draw()
    }
}

inline fun WorldRenderEnvironment.withPositionRelativeToCamera(
    x: Double, y: Double, z: Double, draw: WorldRenderEnvironment.() -> Unit
) {
    poseStack.withPush {
        val cameraPos = camera.position()
        translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z)
        draw()
    }
}

/**
 * Positions the render origin at the camera-relative coordinates of [pos] before drawing.
 */
inline fun WorldRenderEnvironment.withPositionRelativeToCamera(pos: Vec3, draw: WorldRenderEnvironment.() -> Unit) =
    withPositionRelativeToCamera(pos.x, pos.y, pos.z, draw)

/**
 * Shortcut of `withPositionRelativeToCamera(Vec3.atLowerCornerOf(pos))`
 */
inline fun WorldRenderEnvironment.withPositionRelativeToCamera(pos: Vec3i, draw: WorldRenderEnvironment.() -> Unit) =
    withPositionRelativeToCamera(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), draw)

internal inline fun RenderTarget.drawGenericBlockESP(
    renderState: CachedMeshStorage,
    pipeline: RenderPipeline,
    distanceFade: DistanceFadeUniformValueGroup,
    dynamicTransforms: () -> GpuBufferSlice = ::getDynamicTransformsUniform,
): Boolean {
    if (!renderState.isReady) return false

    distanceFade.updateIfDirty()
    val dynamicTransforms = dynamicTransforms()
    this.createRenderPass({ renderState.label + " Pass" }).use { pass ->
        pass.setPipeline(pipeline)

        pass.bindProjectionUniform()
        pass.bindGlobalsUniform()
        pass.bindDynamicTransformsUniform(dynamicTransforms)
        renderState.bindUniform(pass)
        distanceFade.bindUniform(pass)
        renderState.bindAndDraw(pass)
    }
    return true
}
