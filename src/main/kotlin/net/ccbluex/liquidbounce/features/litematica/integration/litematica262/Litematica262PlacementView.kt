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

import fi.dy.masa.litematica.schematic.placement.SchematicPlacement
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaBounds
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlacementId
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPosition
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPlacementMetadataSnapshot
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.max
import kotlin.math.min

internal class Litematica262PlacementView private constructor(
    val placement: SchematicPlacement,
    val metadata: LitematicaPlacementMetadataSnapshot,
    private val subRegions: List<SubRegionView>,
    val fingerprint: Int,
) {
    fun desiredAt(position: BlockPos): DesiredCell? {
        if (!metadata.enabled || !metadata.rendered) return null
        if (!metadata.renderLayer.allows(position.toPosition())) return null
        return subRegions.asSequence().mapNotNull { it.desiredAt(position) }.firstOrNull()
    }

    companion object {
        fun create(
            placement: SchematicPlacement,
            renderLayer: Litematica262RenderLayerSnapshot,
        ): Litematica262PlacementView? {
            val allBoxes = placement.getSubRegionBoxes(SubRegionPlacement.RequiredEnabled.ANY)
            if (allBoxes.isEmpty()) return null
            val bounds = allBoxes.values.mapNotNull { box ->
                val first = box.pos1 ?: return@mapNotNull null
                val second = box.pos2 ?: return@mapNotNull null
                blockBounds(first, second)
            }.reduceOrNull(::enclosing) ?: return null
            val id = LitematicaPlacementId(placement.hashId.toString())
            val name = placement.name.takeIf(String::isNotBlank) ?: "Placement ${id.value.take(8)}"
            val rendered = captureRenderedSubRegions(placement)
            val metadata = LitematicaPlacementMetadataSnapshot(
                id = id,
                name = name,
                enabled = placement.isEnabled,
                rendered = renderLayer.renderingEnabled && placement.isRenderingEnabled && rendered.hasBoxes,
                bounds = bounds,
                renderLayer = renderLayer.layer,
            )
            val fingerprint = listOf(
                metadata,
                placement.origin,
                placement.mirror,
                placement.rotation,
                rendered.views.map(SubRegionView::fingerprint),
            ).hashCode()
            return Litematica262PlacementView(placement, metadata, rendered.views, fingerprint)
        }

        private fun captureRenderedSubRegions(placement: SchematicPlacement): RenderedSubRegions {
            val boxes = placement.getSubRegionBoxes(SubRegionPlacement.RequiredEnabled.RENDERING_ENABLED)
            val schematic = placement.schematic
            val views = boxes.keys.mapNotNull { regionName ->
                val relative = placement.getRelativeSubRegionPlacement(regionName) ?: return@mapNotNull null
                val size = schematic.getAreaSize(regionName) ?: return@mapNotNull null
                val container = schematic.getSubRegionContainer(regionName) ?: return@mapNotNull null
                val box = boxes[regionName] ?: return@mapNotNull null
                val first = box.pos1 ?: return@mapNotNull null
                val second = box.pos2 ?: return@mapNotNull null
                SubRegionView.create(
                    placement,
                    relative,
                    size,
                    container,
                    schematic.getBlockEntityMapForRegion(regionName).orEmpty(),
                    blockBounds(first, second),
                )
            }
            return RenderedSubRegions(boxes.isNotEmpty(), views)
        }

        private data class RenderedSubRegions(
            val hasBoxes: Boolean,
            val views: List<SubRegionView>,
        )

        private fun blockBounds(first: BlockPos, second: BlockPos) = LitematicaBounds(
            min = LitematicaPosition(min(first.x, second.x), min(first.y, second.y), min(first.z, second.z)),
            max = LitematicaPosition(max(first.x, second.x), max(first.y, second.y), max(first.z, second.z)),
        )

        private fun enclosing(left: LitematicaBounds, right: LitematicaBounds) = LitematicaBounds(
            min = LitematicaPosition(
                min(left.min.x, right.min.x),
                min(left.min.y, right.min.y),
                min(left.min.z, right.min.z),
            ),
            max = LitematicaPosition(
                max(left.max.x, right.max.x),
                max(left.max.y, right.max.y),
                max(left.max.z, right.max.z),
            ),
        )
    }

    data class DesiredCell(
        val state: BlockState,
        val reproducible: Boolean,
    )

}

private fun BlockPos.toPosition() = LitematicaPosition(x, y, z)
