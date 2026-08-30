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

import net.ccbluex.liquidbounce.features.block.runtime.ChunkScanner
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk

/** Stable facade for the module, commands, HUD, and chunk scanner. */
internal object SeedCrackerRuntime :
    ChunkScanner.BlockChangeSubscriber,
    RuntimeLifecycleContract,
    RuntimeQueryContract,
    RuntimeEvidenceContract,
    RuntimeControlContract {

    override val runtimeState = RuntimeState(this)

    override val shouldCallRecordBlockOnChunkUpdate: Boolean = false

    override fun recordBlock(pos: BlockPos, state: BlockState, cleared: Boolean) {
        runtimeState.recordBlock(pos, state, cleared)
    }

    override fun chunkUpdate(chunk: LevelChunk) {
        runtimeState.chunkUpdate(chunk)
    }

    override fun clearChunk(pos: ChunkPos) {
        runtimeState.clearChunk(pos)
    }

    override fun clearAllChunks() {
        runtimeState.clearAllChunks()
    }
}
