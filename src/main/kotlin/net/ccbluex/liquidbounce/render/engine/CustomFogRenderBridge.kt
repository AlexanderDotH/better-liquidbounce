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

import net.minecraft.client.renderer.fog.FogData
import org.joml.Vector4f
import org.joml.Vector4fc

data class CustomFogActivity(
    val customAmbienceRunning: Boolean,
    val shouldRenderUnified: Boolean,
    val shouldRenderBlur: Boolean,
    val shouldRenderVolume: Boolean,
)

data class UnifiedFogHorizonSnapshot(
    val startBlocks: Float,
    val endBlocks: Float,
    val visibleDistanceBlocks: Float,
)

data class CustomFogLayerSettings(
    val enabled: Boolean,
    val spacing: Float,
    val groundDensity: Int,
    val middleDensity: Int,
    val upperDensity: Int,
)

data class CustomFogVolumeSettings(
    val enabled: Boolean,
    val strength: Float,
    val cameraClearRadius: Float,
    val interactionActive: Boolean,
    val layers: CustomFogLayerSettings,
)

data class CustomFogVisualSettings(
    val color: Vector4f,
    val density: Float,
    val silhouetteFeather: Float,
    val volume: CustomFogVolumeSettings,
)

interface CustomFogRenderAdapter {
    fun activity(): CustomFogActivity
    fun modifyFogData(fogData: FogData)
    fun currentUnifiedHorizon(distantHorizonsFarClipBlocks: Float?, vanillaRenderDistanceChunks: Int):
        UnifiedFogHorizonSnapshot
    fun blurStrength(): Float
    fun volumeSettings(): CustomFogVolumeSettings
    fun visualSettings(defaultColor: Vector4fc): CustomFogVisualSettings
    fun publishDebug(state: UnifiedFogDebugState)
}

object CustomFogRenderBridge {

    @Volatile
    private var adapter: CustomFogRenderAdapter = DisabledCustomFogRenderAdapter

    @JvmStatic
    @Synchronized
    fun install(adapter: CustomFogRenderAdapter) {
        check(this.adapter === DisabledCustomFogRenderAdapter) { "Custom fog render adapter is already installed" }
        this.adapter = adapter
    }

    fun activity(): CustomFogActivity = adapter.activity()

    fun modifyFogData(fogData: FogData) = adapter.modifyFogData(fogData)

    fun currentUnifiedHorizon(distantHorizonsFarClipBlocks: Float?, vanillaRenderDistanceChunks: Int) =
        adapter.currentUnifiedHorizon(distantHorizonsFarClipBlocks, vanillaRenderDistanceChunks)

    fun blurStrength(): Float = adapter.blurStrength()

    fun volumeSettings(): CustomFogVolumeSettings = adapter.volumeSettings()

    fun visualSettings(defaultColor: Vector4fc): CustomFogVisualSettings = adapter.visualSettings(defaultColor)

    fun publishDebug(state: UnifiedFogDebugState) = adapter.publishDebug(state)

    @Synchronized
    internal fun <T> withAdapterForTest(candidate: CustomFogRenderAdapter?, block: () -> T): T {
        val previous = adapter
        adapter = candidate ?: DisabledCustomFogRenderAdapter
        return try {
            block()
        } finally {
            adapter = previous
        }
    }
}

private object DisabledCustomFogRenderAdapter : CustomFogRenderAdapter {
    private val activity = CustomFogActivity(false, false, false, false)
    private val disabledLayers = CustomFogLayerSettings(false, 1f, 0, 0, 0)
    private val disabledVolume = CustomFogVolumeSettings(false, 0f, 0f, false, disabledLayers)

    override fun activity(): CustomFogActivity = activity
    override fun modifyFogData(fogData: FogData) = Unit
    override fun blurStrength(): Float = 0f
    override fun volumeSettings(): CustomFogVolumeSettings = disabledVolume
    override fun publishDebug(state: UnifiedFogDebugState) = Unit

    override fun currentUnifiedHorizon(
        distantHorizonsFarClipBlocks: Float?,
        vanillaRenderDistanceChunks: Int,
    ): UnifiedFogHorizonSnapshot {
        val visible = distantHorizonsFarClipBlocks ?: vanillaRenderDistanceChunks.coerceAtLeast(0) * 16f
        return UnifiedFogHorizonSnapshot(0f, visible, visible)
    }

    override fun visualSettings(defaultColor: Vector4fc) = CustomFogVisualSettings(
        color = Vector4f(defaultColor),
        density = 0f,
        silhouetteFeather = 0f,
        volume = disabledVolume,
    )
}
