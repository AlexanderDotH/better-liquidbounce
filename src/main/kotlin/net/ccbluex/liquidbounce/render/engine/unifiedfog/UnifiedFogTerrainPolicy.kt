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

package net.ccbluex.liquidbounce.render.engine.unifiedfog

internal enum class TerrainLayer {
    SKY,
    DISTANT_HORIZONS,
    VANILLA,
}

internal object UnifiedFogTerrainPolicy {

    fun layerAt(vanillaHasTerrain: Boolean, distantHorizonsHasTerrain: Boolean): TerrainLayer {
        if (vanillaHasTerrain) return TerrainLayer.VANILLA
        if (distantHorizonsHasTerrain) return TerrainLayer.DISTANT_HORIZONS
        return TerrainLayer.SKY
    }

    fun terrainMask(vanillaHasTerrain: Boolean, distantHorizonsHasTerrain: Boolean): Float =
        if (layerAt(vanillaHasTerrain, distantHorizonsHasTerrain) == TerrainLayer.SKY) 0f else 1f

    fun finalFogAlpha(
        generatedFogAlpha: Float,
        layer: TerrainLayer,
        skyDistanceToTerrainPixels: Float,
        silhouetteFeatherPixels: Float,
    ): Float {
        if (layer != TerrainLayer.SKY) return 0f
        val safeAlpha = generatedFogAlpha.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: return 0f
        return safeAlpha * skyFeatherFactor(skyDistanceToTerrainPixels, silhouetteFeatherPixels)
    }

    fun skyFeatherFactor(distanceToTerrainPixels: Float, featherPixels: Float): Float {
        if (featherPixels == 0f) return 1f
        if (!featherPixels.isFinite() || featherPixels < 0f) return 0f
        if (distanceToTerrainPixels == Float.POSITIVE_INFINITY) return 1f
        if (!distanceToTerrainPixels.isFinite()) return 0f

        val progress = (distanceToTerrainPixels / featherPixels).coerceIn(0f, 1f)
        return progress * progress * (3f - 2f * progress)
    }
}
