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

internal data class PhysicalFogHorizonRange(
    val startBlocks: Float,
    val endBlocks: Float,
) {
    init {
        require(startBlocks.isFinite() && startBlocks >= 0f) {
            "Fog horizon start must be finite and non-negative"
        }
        require(endBlocks.isFinite() && endBlocks >= startBlocks) {
            "Fog horizon end must be finite and at or beyond its start"
        }
    }

    fun fogFactor(distanceBlocks: Float): Float {
        require(!distanceBlocks.isNaN()) { "Fog distance must not be NaN" }
        if (distanceBlocks <= startBlocks) return 0f
        if (distanceBlocks >= endBlocks) return 1f
        return (distanceBlocks - startBlocks) / (endBlocks - startBlocks)
    }
}
