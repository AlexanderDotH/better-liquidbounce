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

import net.ccbluex.liquidbounce.features.block.runtime.ChunkScanner
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk

/** A packet-safe copy of a sound event. No packet or mutable Minecraft object is retained. */
internal data class BaseFinderActivitySample(
    val soundPath: String,
    val position: BaseCoordinate,
    val timestampMillis: Long,
)

/** Revision token used to reject work completed after unload, replacement, disable, or world change. */
internal data class BaseFinderScanTicket(
    val chunk: ChunkCoordinate,
    val worldEpoch: Long,
    val revision: Long,
)

/** Stable scanner facade. Mutable acquisition state and policy live in focused collaborators. */
internal object BaseFinderTracker : ChunkScanner.BlockChangeSubscriber {
    private val state = BaseFinderTrackerState()

    override val shouldCallRecordBlockOnChunkUpdate: Boolean = false

    val worldEpoch: Long
        get() = state.worldEpoch

    override fun recordBlock(pos: BlockPos, state: BlockState, cleared: Boolean) {
        this.state.recordBlock(pos, state)
    }

    override fun chunkUpdate(chunk: LevelChunk) = state.chunkUpdate(chunk)

    override fun clearChunk(pos: ChunkPos) = state.clearChunk(pos)

    override fun clearAllChunks() = resetVolatile()

    fun onWorldChanged(): Long {
        resetVolatile()
        return worldEpoch
    }

    fun resetVolatile() = state.resetVolatile()

    fun processDirtyChunks(level: ClientLevel, limit: Int): List<ChunkEvidenceSnapshot> =
        state.processDirtyChunks(level, limit)

    fun sampleEntities(level: ClientLevel): List<ChunkEvidenceSnapshot> = state.sampleEntities(level)

    fun sampleBlockEntities(level: ClientLevel): List<ChunkEvidenceSnapshot> = state.sampleBlockEntities(level)

    fun recordActivity(sample: BaseFinderActivitySample) = state.recordActivity(sample)

    fun currentSnapshots(): List<ChunkEvidenceSnapshot> = state.currentSnapshots()

    internal fun dirtyChunksForTest(): List<ChunkCoordinate> =
        state.dirtyChunks.map { it.toTrackerCoordinate() }

    internal fun drainDirtyChunksForTest(limit: Int): List<ChunkCoordinate> =
        state.drainDirtyChunkKeys(limit).map { it.toTrackerCoordinate() }

    fun ticketFor(chunk: ChunkCoordinate): BaseFinderScanTicket = state.currentTicket(chunk)

    fun seedMismatchUpdatePositionsFor(chunk: ChunkCoordinate): Set<Long> {
        val positions = state.seedMismatchUpdatePositions[chunk.packTrackerKey()] ?: return emptySet()
        return java.util.Set.copyOf(positions)
    }

    fun isTicketCurrent(ticket: BaseFinderScanTicket): Boolean = state.isCurrent(ticket)

    internal fun scanTicketForTest(chunk: ChunkCoordinate): BaseFinderScanTicket = ticketFor(chunk)

    internal fun isCurrentForTest(ticket: BaseFinderScanTicket): Boolean = state.isCurrent(ticket)

    internal fun hasAlignedRunForTest(positions: List<BaseCoordinate>, minimum: Int): Boolean =
        hasAlignedRun(positions, minimum)

    internal fun entityEvidenceForTest(
        samples: List<Pair<BaseFinderEntityCategory, BaseCoordinate>>,
    ): BaseFinderSampledEntityEvidence = EntityAccumulator().apply {
        samples.forEach { (category, position) -> add(category, position) }
    }.toEvidence()

    internal fun scanBlocksForTest(
        blocks: List<Pair<BlockPos, BlockState>>,
        dimensionKey: String = "minecraft:overworld",
    ): ChunkEvidenceSnapshot = ChunkAccumulator(ChunkCoordinate(0, 0), dimensionKey).apply {
        blocks.forEach { (position, state) -> accept(position, state) }
    }.toSnapshot()
}
