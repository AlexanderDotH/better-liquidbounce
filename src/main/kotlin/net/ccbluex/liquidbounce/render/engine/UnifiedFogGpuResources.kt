/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.ccbluex.liquidbounce.render.engine

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import net.ccbluex.liquidbounce.render.ClientUniformDefine
import net.ccbluex.liquidbounce.render.buffers.CachedUniform
import net.ccbluex.liquidbounce.utils.render.clearColor
import org.joml.Matrix4f
import org.joml.Vector4f

internal class UnifiedFogGpuResources : AutoCloseable {

    val terrainMaskTarget = UnifiedFogRenderTarget("LiquidBounce Unified Fog Terrain Mask", GpuFormat.RGBA8_UNORM)
    val fogTarget = UnifiedFogRenderTarget("LiquidBounce Unified Fog", GpuFormat.RGBA16_FLOAT)
    val fogBlurTarget = UnifiedFogRenderTarget("LiquidBounce Unified Fog Blur", GpuFormat.RGBA16_FLOAT)

    val fogData = CachedUniform<UnifiedFogUniform>(ClientUniformDefine.UNIFIED_FOG) { value ->
        putMat4f(value.inverseProjection)
        putMat4f(value.inverseViewRotation)
        putMat4f(value.dhInverseMvmProjection)
        putVec4(value.fogColor)
        putVec4(value.horizonInfo)
        putVec4(value.cameraPositionAndTime)
        putVec4(value.vanillaDepthInfo)
        putVec4(value.dhDepthInfo)
        putVec4(value.viewportInfo)
        putVec4(value.volumeSettings)
        putVec4(value.layerSettings)
    }

    val fogKernelData = CachedUniform<UnifiedFogKernelUniform>(ClientUniformDefine.UNIFIED_FOG_KERNEL) { value ->
        value.pairs.forEach { pair -> putVec4(pair.offset, pair.weight, 0f, 0f) }
    }

    override fun close() {
        terrainMaskTarget.close()
        fogTarget.close()
        fogBlurTarget.close()
        fogData.close()
        fogKernelData.close()
    }
}

internal data class UnifiedFogUniform(
    val inverseProjection: Matrix4f,
    val inverseViewRotation: Matrix4f,
    val dhInverseMvmProjection: Matrix4f,
    val fogColor: Vector4f,
    val horizonInfo: Vector4f,
    val cameraPositionAndTime: Vector4f,
    val vanillaDepthInfo: Vector4f,
    val dhDepthInfo: Vector4f,
    val viewportInfo: Vector4f,
    val volumeSettings: Vector4f,
    val layerSettings: Vector4f,
)

internal data class UnifiedFogKernelUniform(val pairs: List<GaussianPair>) {
    init {
        require(pairs.size == GaussianKernel.PAIR_COUNT) {
            "Unified fog kernel requires ${GaussianKernel.PAIR_COUNT} paired samples"
        }
    }
}

internal class UnifiedFogRenderTarget(
    private val name: String,
    private val format: GpuFormat,
) : AutoCloseable {

    private var raw: RenderTarget? = null

    fun initAndGet(width: Int, height: Int): RenderTarget {
        require(width > 0 && height > 0) { "Unified fog target dimensions must be positive" }
        val target = raw?.also { current ->
            if (current.width != width || current.height != height) current.resize(width, height)
        } ?: TextureTarget(name, width, height, false, format).also { raw = it }
        target.colorTexture!!.clearColor()
        return target
    }

    override fun close() {
        raw?.destroyBuffers()
        raw = null
    }
}
