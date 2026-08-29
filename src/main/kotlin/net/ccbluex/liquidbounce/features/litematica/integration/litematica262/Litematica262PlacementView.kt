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
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement
import fi.dy.masa.litematica.util.PositionUtils
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaBounds
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlacementId
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPosition
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPlacementMetadataSnapshot
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
            val renderedBoxes = placement.getSubRegionBoxes(SubRegionPlacement.RequiredEnabled.RENDERING_ENABLED)
            val bounds = allBoxes.values.mapNotNull { box ->
                val first = box.pos1 ?: return@mapNotNull null
                val second = box.pos2 ?: return@mapNotNull null
                blockBounds(first, second)
            }.reduceOrNull(::enclosing) ?: return null
            val id = LitematicaPlacementId(placement.hashId.toString())
            val name = placement.name.takeIf(String::isNotBlank) ?: "Placement ${id.value.take(8)}"
            val metadata = LitematicaPlacementMetadataSnapshot(
                id = id,
                name = name,
                enabled = placement.isEnabled,
                rendered = renderLayer.renderingEnabled && placement.isRenderingEnabled && renderedBoxes.isNotEmpty(),
                bounds = bounds,
                renderLayer = renderLayer.layer,
            )
            val schematic = placement.schematic
            val subRegions = renderedBoxes.keys.mapNotNull { regionName ->
                val relative = placement.getRelativeSubRegionPlacement(regionName) ?: return@mapNotNull null
                val size = schematic.getAreaSize(regionName) ?: return@mapNotNull null
                val container = schematic.getSubRegionContainer(regionName) ?: return@mapNotNull null
                val box = renderedBoxes[regionName] ?: return@mapNotNull null
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
            val fingerprint = listOf(
                metadata,
                placement.origin,
                placement.mirror,
                placement.rotation,
                subRegions.map(SubRegionView::fingerprint),
            ).hashCode()
            return Litematica262PlacementView(placement, metadata, subRegions, fingerprint)
        }

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

    private class SubRegionView(
        private val placement: SchematicPlacement,
        private val relative: SubRegionPlacement,
        private val size: BlockPos,
        private val container: LitematicaBlockStateContainer,
        private val blockEntities: Map<BlockPos, CompoundTag>,
        private val bounds: LitematicaBounds,
        private val minRelative: BlockPos,
        private val transformedRegionPosition: BlockPos,
    ) {
        val fingerprint: Int = listOf(
            relative.name,
            relative.pos,
            relative.mirror,
            relative.rotation,
            relative.isEnabled,
            relative.isRenderingEnabled,
            size,
            bounds,
        ).hashCode()

        fun desiredAt(worldPosition: BlockPos): DesiredCell? {
            if (!bounds.contains(worldPosition.toPosition())) return null
            val local = localPosition(worldPosition)
            if (local.x !in 0 until abs(size.x) || local.y !in 0 until abs(size.y) || local.z !in 0 until abs(size.z)) {
                return null
            }
            var state = container.get(local.x, local.y, local.z)
            if (state.block === Blocks.STRUCTURE_VOID) return null
            state = transformState(state)
            return DesiredCell(
                state = state,
                reproducible = blockEntities[local]?.hasCustomData() != true,
            )
        }

        private fun localPosition(worldPosition: BlockPos): BlockPos {
            val transformed = worldPosition.subtract(placement.origin).subtract(transformedRegionPosition)
            val withoutSub = PositionUtils.getReverseTransformedBlockPos(
                transformed,
                relative.mirror,
                relative.rotation,
            )
            val withoutMain = PositionUtils.getReverseTransformedBlockPos(
                withoutSub,
                placement.mirror,
                placement.rotation,
            )
            return BlockPos(
                withoutMain.x - minRelative.x + relative.pos.x,
                withoutMain.y - minRelative.y + relative.pos.y,
                withoutMain.z - minRelative.z + relative.pos.z,
            )
        }

        private fun transformState(source: BlockState): BlockState {
            var state = source
            if (placement.mirror != Mirror.NONE) state = state.mirror(placement.mirror)
            val subMirror = adjustedSubMirror()
            if (subMirror != Mirror.NONE) state = state.mirror(subMirror)
            val rotation = placement.rotation.getRotated(relative.rotation)
            if (rotation != Rotation.NONE) state = state.rotate(rotation)
            return state
        }

        private fun adjustedSubMirror(): Mirror {
            val mirror = relative.mirror
            if (mirror == Mirror.NONE || placement.rotation !in QUARTER_TURNS) return mirror
            return if (mirror == Mirror.FRONT_BACK) Mirror.LEFT_RIGHT else Mirror.FRONT_BACK
        }

        companion object {
            private val QUARTER_TURNS = setOf(Rotation.CLOCKWISE_90, Rotation.COUNTERCLOCKWISE_90)
            private val POSITION_ONLY_NBT = setOf("id", "x", "y", "z", "keepPacked")

            fun create(
                placement: SchematicPlacement,
                relative: SubRegionPlacement,
                size: BlockPos,
                container: LitematicaBlockStateContainer,
                blockEntities: Map<BlockPos, CompoundTag>,
                bounds: LitematicaBounds,
            ): SubRegionView {
                val end = PositionUtils.getRelativeEndPositionFromAreaSize(size).offset(relative.pos)
                val minRelative = PositionUtils.getMinCorner(relative.pos, end)
                val transformedRegion = PositionUtils.getTransformedBlockPos(
                    relative.pos,
                    placement.mirror,
                    placement.rotation,
                )
                return SubRegionView(
                    placement,
                    relative,
                    size,
                    container,
                    blockEntities,
                    bounds,
                    minRelative,
                    transformedRegion,
                )
            }

            private fun CompoundTag.hasCustomData(): Boolean = keySet().any { it !in POSITION_ONLY_NBT }
        }
    }
}

private fun BlockPos.toPosition() = LitematicaPosition(x, y, z)
