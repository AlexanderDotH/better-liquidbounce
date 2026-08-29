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
package net.ccbluex.liquidbounce.features.litematica.integration.litematica262

import fi.dy.masa.litematica.config.Configs
import fi.dy.masa.litematica.data.DataManager
import fi.dy.masa.malilib.util.LayerMode
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaAxis
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaRenderLayer
import net.minecraft.core.Direction

internal data class Litematica262RenderLayerSnapshot(
    val layer: LitematicaRenderLayer,
    val renderingEnabled: Boolean,
)

internal object Litematica262RenderLayer {
    fun snapshot(): Litematica262RenderLayerSnapshot {
        val range = DataManager.getRenderLayerRange()
        val rendering = Configs.Visuals.ENABLE_RENDERING.booleanValue &&
            Configs.Visuals.ENABLE_SCHEMATIC_RENDERING.booleanValue
        val layer = when (range.layerMode) {
            LayerMode.ALL -> LitematicaRenderLayer.ALL
            LayerMode.SINGLE_LAYER -> LitematicaRenderLayer(
                range.axis.toDomainAxis(),
                range.layerSingle,
                range.layerSingle,
            )
            else -> LitematicaRenderLayer(
                range.axis.toDomainAxis(),
                range.layerRangeMin,
                range.layerRangeMax,
            )
        }
        return Litematica262RenderLayerSnapshot(layer, rendering)
    }
}

private fun Direction.Axis.toDomainAxis(): LitematicaAxis = when (this) {
    Direction.Axis.X -> LitematicaAxis.X
    Direction.Axis.Y -> LitematicaAxis.Y
    Direction.Axis.Z -> LitematicaAxis.Z
}
