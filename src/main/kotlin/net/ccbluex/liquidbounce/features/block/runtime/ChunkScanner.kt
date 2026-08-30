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

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import net.ccbluex.fastutil.mapToArray
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.ChunkLoadEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.block.BlockChangeSubscriber as BlockChangeSubscriberContract
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FIRST_PRIORITY
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Predicate

object ChunkScanner : EventListener {

    private val loadedChunks = LongOpenHashSet()

    private val subscribers = CopyOnWriteArrayList<BlockChangeSubscriberContract>()

    fun subscribe(newSubscriber: BlockChangeSubscriber) {
        subscribeContract(newSubscriber)
    }

    fun subscribe(newSubscriber: BlockChangeSubscriberContract) {
        subscribeContract(newSubscriber)
    }

    private fun subscribeContract(newSubscriber: BlockChangeSubscriberContract) {
        if (!this.subscribers.addIfAbsent(newSubscriber)) {
            error("Subscriber ${newSubscriber.debugName} already registered")
        }

        val world = mc.level ?: return
        if (this.loadedChunks.isEmpty()) return

        val chunkArray = this.loadedChunks.mapToArray { longChunkPos ->
            world.getChunk(
                ChunkPos.getX(longChunkPos),
                ChunkPos.getZ(longChunkPos)
            )
        }
        val chunks = ObjectArrayList.wrap(chunkArray)
        chunks.removeIf(Predicate(LevelChunk::isEmpty))
        if (chunks.isEmpty) return

        UpdateRequest.NewSubscriber(newSubscriber, chunks)
            .runAsync()
    }

    fun unsubscribe(oldSubscriber: BlockChangeSubscriber) {
        unsubscribeContract(oldSubscriber)
    }

    fun unsubscribe(oldSubscriber: BlockChangeSubscriberContract) {
        unsubscribeContract(oldSubscriber)
    }

    private fun unsubscribeContract(oldSubscriber: BlockChangeSubscriberContract) {
        subscribers.remove(oldSubscriber)
        oldSubscriber.clearAllChunks()
    }

    @Suppress("unused")
    private val chunkLoadHandler = handler<ChunkLoadEvent>(READ_FINAL_STATE) { event ->
        val chunk = world.getChunk(event.x, event.z).takeUnless { it.isEmpty } ?: return@handler

        loadedChunks.add(ChunkPos.pack(event.x, event.z))

        if (subscribers.isEmpty()) return@handler

        UpdateRequest.ChunkLoad(chunk).runAsync()
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent>(READ_FINAL_STATE) { event ->
        if (event.isCancelled) return@handler

        when (val packet = event.packet) {
            is ClientboundBlockUpdatePacket -> {
                if (subscribers.isEmpty()) return@handler

                UpdateRequest.BlockUpdate(packet.pos, packet.blockState).runAsync()
            }

            // All updates are in one section
            is ClientboundSectionBlocksUpdatePacket -> {
                if (subscribers.isEmpty()) return@handler

                UpdateRequest.ChunkSectionUpdate(packet).runAsync()
            }

            is ClientboundForgetLevelChunkPacket -> mc.execute {
                loadedChunks.remove(packet.pos.pack())

                if (subscribers.isEmpty()) return@execute

                UpdateRequest.ChunkUnload(packet.pos).runAsync()
            }
        }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent>(FIRST_PRIORITY) {
        cancelCurrentJobs()
        loadedChunks.clear()
        subscribers.forEach(BlockChangeSubscriberContract::clearAllChunks)
    }

    /**
     * When the first request comes in, the dispatcher and the scope will be initialized,
     * and its parallelism cannot be modified
     */
    private val dispatcher = Dispatchers.Default
        .limitedParallelism((Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(2))

    /**
     * The parent job for the current client world.
     * All children will be cancelled on [WorldChangeEvent].
     */
    private val worldJob = SupervisorJob()

    val scope = CoroutineScope(dispatcher + worldJob + CoroutineExceptionHandler { context, throwable ->
        if (throwable !is CancellationException) {
            logger.warn("Chunk update error", throwable)
        }
    })

    /**
     * Cancel all existing enqueue(emit) jobs and scanner jobs
     */
    fun cancelCurrentJobs() {
        worldJob.cancelChildren()
    }

    fun stopThread() {
        worldJob.cancel()
        logger.info("Stopped Chunk Scanner Thread!")
    }

    sealed interface UpdateRequest : suspend (CoroutineScope) -> Unit {
        fun runAsync() {
            scope.launch(block = this)
        }

        /**
         * Scans loaded chunks for new subscriber
         *
         * @param chunks should be non-empty
         */
        class NewSubscriber(
            val subscriber: BlockChangeSubscriberContract,
            val chunks: List<LevelChunk>,
        ) : UpdateRequest {
            override suspend fun invoke(scope: CoroutineScope) {
                RegionUpdateProcessor.replaySubscriber(subscriber, chunks)
            }
        }

        /**
         * Scans single new chunk or replaced chunk.
         *
         * @see net.minecraft.client.multiplayer.ClientChunkCache.replaceWithPacketData
         *
         * @param chunk should be non-empty
         */
        class ChunkLoad(val chunk: LevelChunk) : UpdateRequest {
            override suspend fun invoke(scope: CoroutineScope) {
                RegionUpdateProcessor.loadChunk(scope, chunk, subscribers)
            }
        }

        class ChunkSectionUpdate(val packet: ClientboundSectionBlocksUpdatePacket) : UpdateRequest {
            override suspend fun invoke(scope: CoroutineScope) {
                RegionUpdateProcessor.updateSection(packet, subscribers)
            }
        }

        class ChunkUnload(val pos: ChunkPos) : UpdateRequest {
            override suspend fun invoke(scope: CoroutineScope) {
                RegionUpdateProcessor.unloadChunk(pos, subscribers)
            }
        }

        class BlockUpdate(val blockPos: BlockPos, val newState: BlockState) : UpdateRequest {
            override suspend fun invoke(scope: CoroutineScope) {
                RegionUpdateProcessor.updateBlock(blockPos, newState, subscribers)
            }
        }
    }

    /**
     * Source- and binary-compatible feature facade for existing scanner subscribers.
     */
    interface BlockChangeSubscriber : BlockChangeSubscriberContract

}
