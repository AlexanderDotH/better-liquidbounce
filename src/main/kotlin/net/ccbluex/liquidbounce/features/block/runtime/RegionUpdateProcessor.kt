/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.block.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.ccbluex.fastutil.mapToArray
import net.ccbluex.liquidbounce.utils.block.BlockChangeSubscriber as BlockChangeSubscriberContract
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.kotlin.joinAll
import net.ccbluex.liquidbounce.utils.world.forEachSectionBlock
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import java.util.function.BiConsumer
import kotlin.time.measureTime

internal object RegionUpdateProcessor {

    private val threadLocalBlockPos = ThreadLocal.withInitial(BlockPos::MutableBlockPos)

    suspend fun replaySubscriber(
        subscriber: BlockChangeSubscriberContract,
        chunks: List<LevelChunk>,
    ) {
        val duration = measureTime {
            chunks.forEach {
                subscriber.chunkUpdate(it)
            }
            if (subscriber.shouldCallRecordBlockOnChunkUpdate) {
                chunks.forEach {
                    scanChunkSections(it) { pos, state ->
                        subscriber.recordBlock(pos, state, cleared = true)
                    }
                }
            }
        }

        logger.debug(
            "Scanning ${chunks.size} chunks for ${subscriber.debugName} took ${duration.inWholeMicroseconds}us"
        )
    }

    suspend fun loadChunk(
        scope: CoroutineScope,
        chunk: LevelChunk,
        subscribers: List<BlockChangeSubscriberContract>,
    ) {
        val duration = measureTime {
            subscribers.mapToArray {
                scope.launch {
                    it.clearChunk(chunk.pos)
                    it.chunkUpdate(chunk)
                }
            }.joinAll()

            val subscribersForRecordBlock = subscribers.filter {
                it.shouldCallRecordBlockOnChunkUpdate
            }
            if (subscribersForRecordBlock.isEmpty()) {
                return@measureTime
            }

            scanChunkSections(chunk) { pos, state ->
                subscribersForRecordBlock.forEach { it.recordBlock(pos, state, cleared = true) }
            }
        }

        logger.debug(
            "Scanning chunk (${chunk.pos.x}, ${chunk.pos.z}) took ${duration.inWholeMicroseconds}us"
        )
    }

    fun updateSection(
        packet: ClientboundSectionBlocksUpdatePacket,
        subscribers: List<BlockChangeSubscriberContract>,
    ) {
        packet.runUpdates { blockPos, state ->
            subscribers.forEach {
                it.recordBlock(blockPos, state, cleared = false)
            }
        }
    }

    fun unloadChunk(pos: ChunkPos, subscribers: List<BlockChangeSubscriberContract>) {
        subscribers.forEach {
            it.clearChunk(pos)
        }
    }

    fun updateBlock(
        blockPos: BlockPos,
        newState: BlockState,
        subscribers: List<BlockChangeSubscriberContract>,
    ) {
        subscribers.forEach {
            it.recordBlock(blockPos, newState, cleared = false)
        }
    }

    /**
     * @see LevelChunk.getBlockState
     * @see net.minecraft.world.level.chunk.LevelChunkSection.hasOnlyAir
     */
    private suspend fun scanChunkSections(
        chunk: LevelChunk,
        action: BiConsumer<BlockPos, BlockState>,
    ) = coroutineScope {
        chunk.sections.forEachIndexed { sectionIndex, section ->
            if (!section.hasOnlyAir()) {
                launch {
                    val mutable = threadLocalBlockPos.get()
                    chunk.forEachSectionBlock(sectionIndex, mutable, action::accept)
                }
            }
        }
    }
}
