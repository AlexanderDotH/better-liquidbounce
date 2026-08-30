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
package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker

import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockChunkSnapshot
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockLayer
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureBlockSnapshot
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureChunkSnapshot
import net.ccbluex.liquidbounce.utils.world.forEachSectionBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunk

internal fun netherSnapshot(
    scope: CrackScope,
    chunk: LevelChunk,
    revision: Long,
): NetherBedrockChunkSnapshot {
    val mutable = BlockPos.MutableBlockPos()
    fun plane(y: Int) = NetherBedrockBitPlane.fromPredicate { localX, localZ ->
        mutable.set(chunk.pos.minBlockX + localX, y, chunk.pos.minBlockZ + localZ)
        chunk.getBlockState(mutable).block == Blocks.BEDROCK
    }
    return NetherBedrockChunkSnapshot(
        scope = scope,
        chunk = ChunkCoordinate(chunk.pos.x, chunk.pos.z),
        revision = revision,
        floor = plane(NetherBedrockLayer.FLOOR.blockY),
        roof = plane(NetherBedrockLayer.ROOF.blockY),
    )
}

internal fun structureSnapshot(
    scope: CrackScope,
    chunk: LevelChunk,
    revision: Long,
): StructureChunkSnapshot {
    val blocks = ArrayList<StructureBlockSnapshot>()
    val mutable = BlockPos.MutableBlockPos()
    chunk.sections.forEachIndexed { index, section ->
        if (section.hasOnlyAir()) return@forEachIndexed
        chunk.forEachSectionBlock(index, mutable) { position, state ->
            val blockId = BuiltInRegistries.BLOCK.getKey(state.block).toString()
            if (blockId.toStableStructureBlockId() in RELEVANT_STRUCTURE_BLOCKS) {
                blocks += StructureBlockSnapshot(position.x, position.y, position.z, blockId)
            }
        }
    }
    return StructureChunkSnapshot(
        chunkX = chunk.pos.x,
        chunkZ = chunk.pos.z,
        rawDimensionKey = scope.dimensionKey,
        revision = revision,
        blocks = blocks,
    )
}

internal fun String.toStableStructureBlockId(): String =
    substringAfter(':', this).substringBefore('[').lowercase()
