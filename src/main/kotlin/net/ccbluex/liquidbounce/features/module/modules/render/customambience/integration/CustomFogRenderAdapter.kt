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
 */
package net.ccbluex.liquidbounce.features.module.modules.render.customambience.integration

import net.ccbluex.liquidbounce.render.engine.CustomFogActivity
import net.ccbluex.liquidbounce.render.engine.CustomFogRenderAdapter as RenderAdapter
import net.ccbluex.liquidbounce.render.engine.CustomFogRenderBridge
import net.ccbluex.liquidbounce.render.engine.CustomFogVisualSettings
import net.ccbluex.liquidbounce.render.engine.CustomFogVolumeSettings
import net.ccbluex.liquidbounce.render.engine.UnifiedFogDebugState
import net.ccbluex.liquidbounce.render.engine.UnifiedFogHorizonSnapshot
import net.minecraft.client.renderer.fog.FogData
import org.joml.Vector4fc

internal interface CustomFogSettingsSource {
    fun activity(): CustomFogActivity
    fun modifyFogData(fogData: FogData)
    fun currentUnifiedHorizon(distantHorizonsFarClipBlocks: Float?, vanillaRenderDistanceChunks: Int):
        UnifiedFogHorizonSnapshot
    fun blurStrength(): Float
    fun volumeSettings(): CustomFogVolumeSettings
    fun visualSettings(defaultColor: Vector4fc): CustomFogVisualSettings
    fun publishDebug(state: UnifiedFogDebugState)
}

internal class CustomFogRenderAdapter(
    private val source: CustomFogSettingsSource,
) : RenderAdapter {

    override fun activity(): CustomFogActivity = source.activity()

    override fun modifyFogData(fogData: FogData) = source.modifyFogData(fogData)

    override fun currentUnifiedHorizon(
        distantHorizonsFarClipBlocks: Float?,
        vanillaRenderDistanceChunks: Int,
    ): UnifiedFogHorizonSnapshot = source.currentUnifiedHorizon(
        distantHorizonsFarClipBlocks,
        vanillaRenderDistanceChunks,
    )

    override fun blurStrength(): Float = source.blurStrength()

    override fun volumeSettings(): CustomFogVolumeSettings = source.volumeSettings()

    override fun visualSettings(defaultColor: Vector4fc): CustomFogVisualSettings =
        source.visualSettings(defaultColor)

    override fun publishDebug(state: UnifiedFogDebugState) = source.publishDebug(state)

    companion object {
        fun install(source: CustomFogSettingsSource) {
            CustomFogRenderBridge.install(CustomFogRenderAdapter(source))
        }
    }
}
