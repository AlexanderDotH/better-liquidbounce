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

import com.mojang.blaze3d.textures.GpuTextureView
import net.ccbluex.liquidbounce.render.engine.unifiedfog.ClipDepthRange
import net.ccbluex.liquidbounce.render.engine.unifiedfog.FrameDimensions
import net.ccbluex.liquidbounce.render.engine.unifiedfog.TerrainDepthConvention
import net.ccbluex.liquidbounce.render.engine.unifiedfog.UnifiedFogFrame
import net.ccbluex.liquidbounce.utils.client.clientStartDurationMs
import net.minecraft.client.renderer.state.level.CameraRenderState
import org.joml.Matrix4f
import org.joml.Vector4f

internal object FogUniformFactory {

    fun snapshot(
        frame: UnifiedFogFrame<GpuTextureView>,
        cameraState: CameraRenderState,
    ): UnifiedFogUniform {
        val distantHorizonsSource = frame.distantHorizonsSource
        val settings = CustomFogRenderBridge.visualSettings(cameraState.fogData.color)
        return UnifiedFogUniform(
            inverseProjection = frame.vanillaSource.inverseReconstruction.copy(),
            inverseViewRotation = Matrix4f(cameraState.viewRotationMatrix).invert(),
            dhInverseMvmProjection = distantHorizonsSource?.inverseReconstruction?.copy() ?: Matrix4f(),
            fogColor = settings.color,
            horizonInfo = horizonInfo(frame, settings),
            cameraPositionAndTime = cameraPositionAndTime(cameraState),
            vanillaDepthInfo = frame.vanillaSource.depthConvention.toUniform(available = true),
            dhDepthInfo = distantHorizonsSource?.depthConvention?.toUniform(available = true)
                ?: unavailableDepthInfo(),
            viewportInfo = viewportInfo(frame.dimensions),
            volumeSettings = volumeSettings(settings.volume),
            layerSettings = layerSettings(settings.volume),
        )
    }

    private fun horizonInfo(
        frame: UnifiedFogFrame<GpuTextureView>,
        settings: CustomFogVisualSettings,
    ) = Vector4f(
        frame.horizonRange.startBlocks,
        frame.horizonRange.endBlocks,
        settings.density,
        settings.silhouetteFeather,
    )

    private fun cameraPositionAndTime(cameraState: CameraRenderState) = Vector4f(
        wrapVolumetricFogCoordinate(cameraState.pos.x),
        wrapVolumetricFogCoordinate(cameraState.pos.y),
        wrapVolumetricFogCoordinate(cameraState.pos.z),
        clientStartDurationMs / 1000f * VOLUME_SPEED,
    )

    private fun viewportInfo(dimensions: FrameDimensions) = Vector4f(
        dimensions.width.toFloat(),
        dimensions.height.toFloat(),
        1f / dimensions.width,
        1f / dimensions.height,
    )

    private fun volumeSettings(volume: CustomFogVolumeSettings) = Vector4f(
        if (volume.enabled) 1f else 0f,
        volumetricFogStrength(volume.strength),
        volumetricCameraClearRadius(volume.cameraClearRadius),
        volumetricInteractionStrength(volume.interactionActive),
    )

    private fun layerSettings(volume: CustomFogVolumeSettings): Vector4f {
        val layers = volume.layers
        val normalized = VolumetricFogLayerSettings.from(
            enabled = layers.enabled,
            spacing = layers.spacing,
            groundDensity = layers.groundDensity,
            middleDensity = layers.middleDensity,
            upperDensity = layers.upperDensity,
        )
        return Vector4f(
            normalized.spacing,
            normalized.groundDensity,
            normalized.middleDensity,
            normalized.upperDensity,
        )
    }

    private fun TerrainDepthConvention.toUniform(available: Boolean) = Vector4f(
        clearDepth,
        if (clipDepthRange == ClipDepthRange.ZERO_TO_ONE) 1f else 0f,
        if (available) 1f else 0f,
        CLEAR_DEPTH_EPSILON,
    )

    private fun unavailableDepthInfo() = Vector4f(MC_CLEAR_DEPTH, 0f, 0f, CLEAR_DEPTH_EPSILON)

    private const val MC_CLEAR_DEPTH = 0f
    private const val CLEAR_DEPTH_EPSILON = 1e-6f
    private const val VOLUME_SPEED = 0.15f
}
