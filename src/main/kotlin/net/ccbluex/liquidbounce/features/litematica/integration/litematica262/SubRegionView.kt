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

import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement
import fi.dy.masa.litematica.util.PositionUtils
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaBounds
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPosition
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.abs

internal class SubRegionView private constructor(
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

    fun desiredAt(worldPosition: BlockPos): Litematica262PlacementView.DesiredCell? {
        if (!bounds.contains(worldPosition.toPosition())) return null
        val local = localPosition(worldPosition)
        if (local.x !in 0 until abs(size.x) || local.y !in 0 until abs(size.y) || local.z !in 0 until abs(size.z)) {
            return null
        }
        var state = container.get(local.x, local.y, local.z)
        if (state.block === Blocks.STRUCTURE_VOID) return null
        state = transformState(state)
        return Litematica262PlacementView.DesiredCell(
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

private fun BlockPos.toPosition() = LitematicaPosition(x, y, z)
