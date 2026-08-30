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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunk

/**
 * Immutable packed view of selected columns from a client chunk.
 *
 * Columns are keyed by packed local XZ (`localX shl 4 or localZ`). Each column stores one block-registry id
 * per world Y from [minY] inclusive for [height] samples. No Minecraft chunk/state objects are retained.
 */
internal data class ObservedChunkBlocks(
    val chunk: ChunkCoordinate,
    val minY: Int,
    val height: Int,
    val columns: Map<Int, IntArray>,
) {
    init {
        require(height > 0) { "Observed chunk height must be positive" }
        require(columns.values.all { it.size == height }) { "Every packed column must match the chunk height" }
    }

    fun blockId(localX: Int, y: Int, localZ: Int): Int {
        val column = columns[packLocal(localX, localZ)] ?: return AIR_ID
        val index = y - minY
        if (index !in column.indices) return AIR_ID
        return column[index]
    }

    companion object {
        val AIR_ID: Int = BuiltInRegistries.BLOCK.getId(Blocks.AIR)

        fun packLocal(localX: Int, localZ: Int): Int = (localX shl 4) or localZ

        fun sampleColumns(
            chunk: LevelChunk,
            localSamples: Collection<Pair<Int, Int>>,
            sampleMinY: Int = chunk.minY,
            sampleMaxYExclusive: Int = chunk.minY + chunk.height,
        ): ObservedChunkBlocks {
            val chunkMinY = chunk.minY
            val chunkMaxExclusive = chunkMinY + chunk.height
            val minY = sampleMinY.coerceIn(chunkMinY, chunkMaxExclusive - 1)
            val maxExclusive = sampleMaxYExclusive.coerceIn(minY + 1, chunkMaxExclusive)
            val height = maxExclusive - minY
            val mutable = BlockPos.MutableBlockPos()
            val columns = HashMap<Int, IntArray>(localSamples.size)
            val originX = chunk.pos.x shl 4
            val originZ = chunk.pos.z shl 4
            for ((localX, localZ) in localSamples) {
                require(localX in 0..15 && localZ in 0..15) { "Local sample must stay inside the chunk" }
                val column = IntArray(height)
                for (yOffset in 0 until height) {
                    val y = minY + yOffset
                    val state = chunk.getBlockState(mutable.set(originX + localX, y, originZ + localZ))
                    column[yOffset] = BuiltInRegistries.BLOCK.getId(state.block)
                }
                columns[packLocal(localX, localZ)] = column
            }
            return ObservedChunkBlocks(
                chunk = ChunkCoordinate(chunk.pos.x, chunk.pos.z),
                minY = minY,
                height = height,
                columns = columns,
            )
        }
    }
}

/** How far expected columns progressed through vanilla generation. */
internal enum class ExpectedTerrainFidelity {
    /** [ChunkGenerator.getBaseColumn] only — no carvers/features. */
    BASE_COLUMN,
    /** Noise + surface + carvers + biome features (trees/ores), via shadow ProtoChunk regen. */
    FEATURES,
}

/** Expected packed columns produced by the seed-backed generator. Same layout as [ObservedChunkBlocks]. */
internal data class ExpectedChunkBlocks(
    val chunk: ChunkCoordinate,
    val minY: Int,
    val height: Int,
    val columns: Map<Int, IntArray>,
    val fidelity: ExpectedTerrainFidelity = ExpectedTerrainFidelity.BASE_COLUMN,
) {
    init {
        require(height > 0) { "Expected chunk height must be positive" }
        require(columns.values.all { it.size == height }) { "Every packed column must match the chunk height" }
    }

    fun blockId(localX: Int, y: Int, localZ: Int): Int {
        val column = columns[ObservedChunkBlocks.packLocal(localX, localZ)] ?: return ObservedChunkBlocks.AIR_ID
        val index = y - minY
        if (index !in column.indices) return ObservedChunkBlocks.AIR_ID
        return column[index]
    }
}

/**
 * Soft plants/leaves/snow that column-mode should ignore when they sit on expected terrain.
 * Deliberately excludes fluids and cave/void air — those are dig/fill spaces, not decoration.
 */
internal fun isSoftIgnorableSeedDecorationId(blockId: Int): Boolean =
    BaseFinderBlockRegistry.isSoftDecoration(blockId)

/** Air / cave_air / void_air — empty space for sky-open and non-solid checks. */
internal fun isEmptySeedSpaceId(blockId: Int): Boolean = BaseFinderBlockRegistry.isEmptySpace(blockId)

/**
 * Wood-like blocks that can indicate player builds. Natural tree trunks use log/wood/stem in air and are
 * filtered separately via [isNaturalTreeLogMaterialId].
 */
internal fun isSeedMismatchBuildMaterialId(blockId: Int): Boolean =
    BaseFinderBlockRegistry.isBuildMaterial(blockId)

/** Unprocessed trunk blocks that vanilla trees place into air (not planks / stripped). */
internal fun isNaturalTreeLogMaterialId(blockId: Int): Boolean = BaseFinderBlockRegistry.isNaturalLog(blockId)

internal fun isSolidTerrainId(blockId: Int): Boolean = BaseFinderBlockRegistry.isSolidTerrain(blockId)

internal fun isUtilityMismatchId(blockId: Int): Boolean = BaseFinderBlockRegistry.isUtility(blockId)
