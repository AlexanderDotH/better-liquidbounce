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
package net.ccbluex.liquidbounce.utils.block

import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk

/**
 * Receives asynchronous chunk and block changes from the client runtime.
 */
interface BlockChangeSubscriber {
    val debugName: String
        get() = javaClass.simpleName

    /**
     * If true, [recordBlock] is called for full chunk scans as well as individual block updates.
     */
    val shouldCallRecordBlockOnChunkUpdate: Boolean
        get() = true

    /**
     * This callback must be thread-safe. [pos] can be mutable and must be copied when retained.
     */
    fun recordBlock(pos: BlockPos, state: BlockState, cleared: Boolean)

    fun chunkUpdate(chunk: LevelChunk)

    fun clearChunk(pos: ChunkPos)

    fun clearAllChunks()
}
