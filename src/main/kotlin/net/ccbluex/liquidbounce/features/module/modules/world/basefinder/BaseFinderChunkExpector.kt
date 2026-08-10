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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Blocks

/**
 * Produces packed expected columns from a [BaseFinderWorldGenContext].
 *
 * [MinecraftFullBaseFinderChunkExpector] = FEATURES via background MinecraftServer.
 * [MinecraftBaseFinderChunkExpector] = bare [ChunkGenerator.getBaseColumn].
 * Each backend fails hard on its own path — no cross-backend fallback.
 */
internal fun interface BaseFinderChunkExpector {
    fun expectColumns(
        context: BaseFinderWorldGenContext,
        chunk: ChunkCoordinate,
        localSamples: Collection<Pair<Int, Int>>,
    ): ExpectedChunkBlocks
}

internal object MinecraftBaseFinderChunkExpector : BaseFinderChunkExpector {

    override fun expectColumns(
        context: BaseFinderWorldGenContext,
        chunk: ChunkCoordinate,
        localSamples: Collection<Pair<Int, Int>>,
    ): ExpectedChunkBlocks {
        try {
            val minY = context.heightAccessor.minY
            val height = context.heightAccessor.height
            val originX = chunk.x shl 4
            val originZ = chunk.z shl 4
            val columns = HashMap<Int, IntArray>(localSamples.size)
            for ((localX, localZ) in localSamples) {
                require(localX in 0..15 && localZ in 0..15)
                val noiseColumn = context.generator.getBaseColumn(
                    originX + localX,
                    originZ + localZ,
                    context.heightAccessor,
                    context.randomState,
                )
                val packed = IntArray(height)
                for (yOffset in 0 until height) {
                    val state = noiseColumn.getBlock(minY + yOffset)
                    packed[yOffset] = BuiltInRegistries.BLOCK.getId(state.block)
                }
                columns[ObservedChunkBlocks.packLocal(localX, localZ)] = packed
            }
            return ExpectedChunkBlocks(chunk, minY, height, columns)
        } catch (failure: Throwable) {
            throw IllegalStateException(
                "Base column generation failed chunk=${chunk.x},${chunk.z}: " +
                    (failure.message ?: failure::class.java.simpleName),
                failure,
            )
        }
    }
}

/** Test double that returns fixed expected columns without Minecraft worldgen. */
internal class StubBaseFinderChunkExpector(
    private val factory: (ChunkCoordinate, Collection<Pair<Int, Int>>) -> ExpectedChunkBlocks,
) : BaseFinderChunkExpector {
    override fun expectColumns(
        context: BaseFinderWorldGenContext,
        chunk: ChunkCoordinate,
        localSamples: Collection<Pair<Int, Int>>,
    ): ExpectedChunkBlocks = factory(chunk, localSamples)
}

internal fun emptyExpectedColumn(
    chunk: ChunkCoordinate,
    minY: Int,
    height: Int,
    localSamples: Collection<Pair<Int, Int>>,
    fillId: Int = BuiltInRegistries.BLOCK.getId(Blocks.STONE),
): ExpectedChunkBlocks {
    val columns = localSamples.associate { (x, z) ->
        ObservedChunkBlocks.packLocal(x, z) to IntArray(height) { fillId }
    }
    return ExpectedChunkBlocks(chunk, minY, height, columns)
}
