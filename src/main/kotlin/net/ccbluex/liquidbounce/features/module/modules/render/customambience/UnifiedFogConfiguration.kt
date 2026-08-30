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
package net.ccbluex.liquidbounce.features.module.modules.render.customambience

import net.ccbluex.liquidbounce.common.Tagged

enum class FogEngine(override val tag: String) : Tagged {
    LEGACY("Legacy"),
    UNIFIED("Unified"),
}

internal enum class FogHorizonSource {
    VANILLA,
    DISTANT_HORIZONS,
}

internal data class UnifiedFogHorizon(
    val startBlocks: Float,
    val endBlocks: Float,
    val visibleDistanceBlocks: Float,
    val source: FogHorizonSource,
)

internal fun resolveUnifiedFogHorizon(
    horizonPercent: ClosedFloatingPointRange<Float>,
    distantHorizonsFarClipBlocks: Float?,
    vanillaRenderDistanceChunks: Int,
): UnifiedFogHorizon {
    val distantHorizonsDistance = distantHorizonsFarClipBlocks
        ?.takeIf { it.isFinite() && it > 0f }
    val visibleDistance = distantHorizonsDistance
        ?: vanillaRenderDistanceChunks.coerceAtLeast(0) * BLOCKS_PER_CHUNK
    val source = if (distantHorizonsDistance == null) {
        FogHorizonSource.VANILLA
    } else {
        FogHorizonSource.DISTANT_HORIZONS
    }

    val firstPercent = minOf(horizonPercent.start, horizonPercent.endInclusive).coerceIn(0f, 100f)
    val lastPercent = maxOf(horizonPercent.start, horizonPercent.endInclusive).coerceIn(0f, 100f)

    return UnifiedFogHorizon(
        startBlocks = visibleDistance * firstPercent / 100f,
        endBlocks = visibleDistance * lastPercent / 100f,
        visibleDistanceBlocks = visibleDistance,
        source = source,
    )
}

internal fun shouldApplyUnifiedFog(fogRunning: Boolean, engine: FogEngine): Boolean =
    fogRunning && engine == FogEngine.UNIFIED

private const val BLOCKS_PER_CHUNK = 16f
