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

import fi.dy.masa.litematica.data.DataManager
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlacementId
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPlacementMetadataSnapshot

internal class Litematica262PlacementIndex private constructor(
    val views: List<Litematica262PlacementView>,
) {
    val metadata: List<LitematicaPlacementMetadataSnapshot> = views.map(Litematica262PlacementView::metadata)
    val fingerprint: Int = views.map(Litematica262PlacementView::fingerprint).hashCode()

    fun placement(id: LitematicaPlacementId): SchematicPlacement? =
        views.firstOrNull { it.metadata.id == id }?.placement

    companion object {
        fun capture(): Litematica262PlacementIndex {
            val layer = Litematica262RenderLayer.snapshot()
            val placements = DataManager.getSchematicPlacementManager().allSchematicsPlacements
            return Litematica262PlacementIndex(placements.mapNotNull { Litematica262PlacementView.create(it, layer) })
        }
    }
}
