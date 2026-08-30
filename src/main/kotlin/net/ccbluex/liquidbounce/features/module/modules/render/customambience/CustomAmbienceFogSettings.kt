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
package net.ccbluex.liquidbounce.features.module.modules.render.customambience

import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.features.module.modules.render.customambience.ModuleCustomAmbience.FogValueGroup
import net.ccbluex.liquidbounce.features.module.modules.render.customambience.integration.CustomFogSettingsSource
import net.ccbluex.liquidbounce.render.engine.CustomFogActivity
import net.ccbluex.liquidbounce.render.engine.CustomFogInteractionBridge
import net.ccbluex.liquidbounce.render.engine.CustomFogLayerSettings
import net.ccbluex.liquidbounce.render.engine.CustomFogVisualSettings
import net.ccbluex.liquidbounce.render.engine.CustomFogVolumeSettings
import net.ccbluex.liquidbounce.render.engine.UnifiedFogDebugState
import net.ccbluex.liquidbounce.render.engine.UnifiedFogHorizonSnapshot
import net.minecraft.client.renderer.fog.FogData
import org.joml.Vector4f
import org.joml.Vector4fc

internal object CustomAmbienceFogSettings : CustomFogSettingsSource {

    override fun activity() = CustomFogActivity(
        customAmbienceRunning = ModuleCustomAmbience.running,
        shouldRenderUnified = FogValueGroup.shouldRenderUnified,
        shouldRenderBlur = FogValueGroup.shouldRenderBlur,
        shouldRenderVolume = FogValueGroup.shouldRenderVolume,
    )

    override fun modifyFogData(fogData: FogData) = FogValueGroup.modifyFogData(fogData)

    override fun currentUnifiedHorizon(
        distantHorizonsFarClipBlocks: Float?,
        vanillaRenderDistanceChunks: Int,
    ): UnifiedFogHorizonSnapshot {
        val horizon = FogValueGroup.currentUnifiedHorizon(
            distantHorizonsFarClipBlocks,
            vanillaRenderDistanceChunks,
        )
        return UnifiedFogHorizonSnapshot(
            horizon.startBlocks,
            horizon.endBlocks,
            horizon.visibleDistanceBlocks,
        )
    }

    override fun blurStrength(): Float = FogValueGroup.BlurFog.strength

    override fun volumeSettings(): CustomFogVolumeSettings {
        val volume = FogValueGroup.VolumetricFog
        val layers = FogValueGroup.VolumetricFog.MultiLayerFog
        return CustomFogVolumeSettings(
            enabled = volume.running,
            strength = volume.strength,
            cameraClearRadius = volume.cameraClearRadius,
            interactionActive = CustomFogInteractionBridge.active(),
            layers = CustomFogLayerSettings(
                enabled = layers.running,
                spacing = layers.layerSpacing,
                groundDensity = layers.groundDensity,
                middleDensity = layers.middleDensity,
                upperDensity = layers.upperDensity,
            ),
        )
    }

    override fun visualSettings(defaultColor: Vector4fc): CustomFogVisualSettings {
        val fogColor = if (FogValueGroup.FogColorOverride.running) {
            FogValueGroup.FogColorOverride.color.toVector4f()
        } else {
            Vector4f(defaultColor)
        }
        return CustomFogVisualSettings(
            color = fogColor,
            density = FogValueGroup.fogDensity / 100f,
            silhouetteFeather = FogValueGroup.silhouetteFeather,
            volume = volumeSettings(),
        )
    }

    override fun publishDebug(state: UnifiedFogDebugState) {
        ModuleDebug.debugParameter(ModuleCustomAmbience, "UnifiedFog.Engine", state.engine)
        ModuleDebug.debugParameter(ModuleCustomAmbience, "UnifiedFog.VanillaReady", state.vanillaReady)
        ModuleDebug.debugParameter(ModuleCustomAmbience, "UnifiedFog.DhReady", state.distantHorizonsReady)
        ModuleDebug.debugParameter(ModuleCustomAmbience, "UnifiedFog.DhBackend", state.distantHorizonsBackend)
        ModuleDebug.debugParameter(ModuleCustomAmbience, "UnifiedFog.DhApi", state.distantHorizonsApiVersion)
        ModuleDebug.debugParameter(ModuleCustomAmbience, "UnifiedFog.FrameAge", state.frameAge)
        ModuleDebug.debugParameter(
            ModuleCustomAmbience,
            "UnifiedFog.Horizon",
            "${state.horizonStartBlocks}..${state.horizonEndBlocks} blocks",
        )
        ModuleDebug.debugParameter(ModuleCustomAmbience, "UnifiedFog.PassCount", state.passCount)
        ModuleDebug.debugParameter(ModuleCustomAmbience, "UnifiedFog.SkipReason", state.skipReason)
    }
}
