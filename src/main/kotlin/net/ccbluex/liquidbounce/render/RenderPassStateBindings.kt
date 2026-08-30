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
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem

inline fun RenderPass.bindDefaultUniforms() = RenderSystem.bindDefaultUniforms(this)

inline fun RenderPass.bindProjectionUniform() {
    RenderSystem.getProjectionMatrixBuffer()?.let { setUniform("Projection", it) }
}

inline fun RenderPass.bindFogUniform() {
    RenderSystem.getShaderFog()?.let { setUniform("Fog", it) }
}

inline fun RenderPass.bindGlobalsUniform() {
    RenderSystem.getGlobalSettingsUniform()?.let { setUniform("Globals", it) }
}

inline fun RenderPass.bindLightingUniform() {
    RenderSystem.getShaderLights()?.let { setUniform("Lighting", it) }
}

inline fun RenderPass.bindDynamicTransformsUniform(gpuBufferSlice: GpuBufferSlice) {
    setUniform("DynamicTransforms", gpuBufferSlice)
}

inline fun RenderPass.setupRenderTypeScissor() {
    val scissorState = RenderSystem.getScissorStateForRenderTypeDraws()
    if (scissorState.enabled()) {
        enableScissor(
            scissorState.x(),
            scissorState.y(),
            scissorState.width(),
            scissorState.height(),
        )
    }
}
